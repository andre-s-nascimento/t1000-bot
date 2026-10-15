/* (c) 2026 | 28/09/2026 */
package net.ddns.adambravo79.tmill.service.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.env.Environment;

import tools.jackson.databind.ObjectMapper;

/**
 * Testes do {@link FeatureFlagAdminService}.
 *
 * <p>Verifica:
 *
 * <ul>
 *   <li>Registro das 23 flags conhecidas no {@code @PostConstruct}
 *   <li>Leitura correta dos defaults do {@code Environment}
 *   <li>Delegação para o {@link FeatureFlagService}
 *   <li>Flags read-only são registradas como tal
 *   <li>{@code toggleWithCheck} respeita a guarda-chuva {@code admin.features.toggle-enabled}
 * </ul>
 *
 * <p>Usa o {@link FeatureFlagService} <b>real</b> (sem persistência) para validar a integração
 * entre os dois serviços — não há mock do {@code FeatureFlagService}, só do {@code Environment}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeatureFlagAdminServiceTest {

    private static final int TOTAL_FLAGS = 23;

    @Mock private Environment environment;

    private FeatureFlagService flagService;
    private FeatureFlagAdminService adminService;

    @BeforeEach
    void setUp() {
        // Sem persistência em disco — não toca em filesystem
        flagService = new FeatureFlagService(new ObjectMapper(), false, "/dev/null");
        adminService = new FeatureFlagAdminService(flagService, environment);
    }

    // =========================================================================
    // REGISTRO DAS FLAGS CONHECIDAS
    // =========================================================================

    @Nested
    @DisplayName("registerKnownFlags")
    class Registro {

        @Test
        @DisplayName("registra exatamente 23 flags no boot")
        void registers23Flags() {
            adminService.registerKnownFlags();

            assertThat(flagService.list()).hasSize(TOTAL_FLAGS);
        }

        @Test
        @DisplayName("registra todas as chaves esperadas")
        void registersExpectedKeys() {
            adminService.registerKnownFlags();

            // 🔧 FIX: Expressão lambda explícita evita o aviso de Null Type Safety do JDT
            List<String> keys = flagService.list().stream().map(state -> state.key()).toList();

            assertThat(keys)
                    .containsExactlyInAnyOrder(
                            // ===== Alta prioridade — Onda 3.5.1 =====
                            "podcast.enabled",
                            "weekly.reminder.enabled",
                            "daily.releases.enabled",
                            "worldcup.reminders30min.enabled",
                            "easter.eggs.enabled",
                            "admin.features.toggle-enabled",
                            // ===== Média prioridade — Onda 3.5.2 =====
                            "weekly.digest.enabled",
                            "birthday.enabled",
                            "birthday.group-notification.enabled",
                            "podcast.fallback-text.enabled",
                            "ai.digest.custom-period.enabled",
                            // ===== Baixa prioridade — Onda 3.5.3 =====
                            "metrics.prometheus.enabled",
                            "admin.debug-cache.enabled",
                            "tmdb.cache.enabled",
                            "telegram.error-fallback.enabled",
                            "kafka.audit.enabled",
                            // ===== Já existentes =====
                            "worldcup.enabled",
                            "transcription.enabled",
                            "auto.response.enabled",
                            "digest.enabled",
                            "worldcup.update.enabled",
                            "prompts.external.enabled",
                            "migration.enabled");
        }

        @Test
        @DisplayName("migration.enabled é registrada como read-only")
        void migrationIsReadOnly() {
            adminService.registerKnownFlags();

            assertThat(flagService.get("migration.enabled")).isPresent();
            assertThat(flagService.get("migration.enabled").get().readOnly()).isTrue();
        }

        @Test
        @DisplayName("demais flags são editáveis (readOnly=false)")
        void othersAreEditable() {
            adminService.registerKnownFlags();

            // ===== Alta prioridade =====
            assertThat(flagService.get("podcast.enabled").get().readOnly()).isFalse();
            assertThat(flagService.get("weekly.reminder.enabled").get().readOnly()).isFalse();
            assertThat(flagService.get("daily.releases.enabled").get().readOnly()).isFalse();
            assertThat(flagService.get("worldcup.reminders30min.enabled").get().readOnly())
                    .isFalse();
            assertThat(flagService.get("easter.eggs.enabled").get().readOnly()).isFalse();
            assertThat(flagService.get("admin.features.toggle-enabled").get().readOnly()).isFalse();

            // ===== Média prioridade =====
            assertThat(flagService.get("weekly.digest.enabled").get().readOnly()).isFalse();
            assertThat(flagService.get("birthday.enabled").get().readOnly()).isFalse();
            assertThat(flagService.get("birthday.group-notification.enabled").get().readOnly())
                    .isFalse();
            assertThat(flagService.get("podcast.fallback-text.enabled").get().readOnly()).isFalse();
            assertThat(flagService.get("ai.digest.custom-period.enabled").get().readOnly())
                    .isFalse();

            // ===== Baixa prioridade =====
            assertThat(flagService.get("metrics.prometheus.enabled").get().readOnly()).isFalse();
            assertThat(flagService.get("admin.debug-cache.enabled").get().readOnly()).isFalse();
            assertThat(flagService.get("tmdb.cache.enabled").get().readOnly()).isFalse();
            assertThat(flagService.get("telegram.error-fallback.enabled").get().readOnly())
                    .isFalse();
            assertThat(flagService.get("kafka.audit.enabled").get().readOnly()).isFalse();

            // ===== Já existentes =====
            assertThat(flagService.get("worldcup.enabled").get().readOnly()).isFalse();
            assertThat(flagService.get("transcription.enabled").get().readOnly()).isFalse();
            assertThat(flagService.get("auto.response.enabled").get().readOnly()).isFalse();
            assertThat(flagService.get("digest.enabled").get().readOnly()).isFalse();
            assertThat(flagService.get("worldcup.update.enabled").get().readOnly()).isFalse();
            assertThat(flagService.get("prompts.external.enabled").get().readOnly()).isFalse();
        }

        @Test
        @DisplayName("todas as flags têm descrição não vazia")
        void allFlagsHaveDescriptions() {
            adminService.registerKnownFlags();

            flagService
                    .list()
                    .forEach(
                            flag ->
                                    assertThat(flag.description())
                                            .as("Descrição da flag " + flag.key())
                                            .isNotBlank());
        }

        @Test
        @DisplayName("registerKnownFlags é idempotente (chamar 2x não duplica)")
        void idempotent() {
            adminService.registerKnownFlags();
            adminService.registerKnownFlags();

            assertThat(flagService.list()).hasSize(TOTAL_FLAGS);
        }
    }

    // =========================================================================
    // LEITURA DOS DEFAULTS DO ENVIRONMENT
    // =========================================================================

    @Nested
    @DisplayName("Defaults do Environment")
    class Defaults {

        @Test
        @DisplayName("usa valores do Environment quando presentes")
        void usesEnvironmentValues() {
            when(environment.getProperty("worldcup.enabled")).thenReturn("true");
            when(environment.getProperty("transcription.enabled")).thenReturn("false");

            adminService.registerKnownFlags();

            assertThat(flagService.isEnabled("worldcup.enabled")).isTrue();
            assertThat(flagService.isEnabled("transcription.enabled")).isFalse();
        }

        @Test
        @DisplayName("usa default true quando Environment retorna null (transcription)")
        void usesDefaultTrueWhenAbsent() {

            adminService.registerKnownFlags();

            assertThat(flagService.isEnabled("transcription.enabled")).isTrue();
        }

        @Test
        @DisplayName("usa default false quando Environment retorna null (worldcup)")
        void usesDefaultFalseWhenAbsent() {

            adminService.registerKnownFlags();

            assertThat(flagService.isEnabled("worldcup.enabled")).isFalse();
        }

        @Test
        @DisplayName("usa default quando Environment retorna string em branco")
        void usesDefaultWhenBlank() {
            when(environment.getProperty("worldcup.enabled")).thenReturn("   ");

            adminService.registerKnownFlags();

            assertThat(flagService.isEnabled("worldcup.enabled")).isFalse();
        }

        @Test
        @DisplayName("case-insensitive: 'TRUE' é interpretado como true")
        void caseInsensitiveTrue() {
            when(environment.getProperty("worldcup.enabled")).thenReturn("TRUE");

            adminService.registerKnownFlags();

            assertThat(flagService.isEnabled("worldcup.enabled")).isTrue();
        }

        @Test
        @DisplayName("trata espaços ao redor do valor (' true ' → true)")
        void trimsWhitespace() {
            when(environment.getProperty("worldcup.enabled")).thenReturn("  true  ");

            adminService.registerKnownFlags();

            assertThat(flagService.isEnabled("worldcup.enabled")).isTrue();
        }

        @Test
        @DisplayName("valor inválido ('not-a-bool') → false")
        void invalidValueIsFalse() {
            when(environment.getProperty("worldcup.enabled")).thenReturn("not-a-bool");

            adminService.registerKnownFlags();

            assertThat(flagService.isEnabled("worldcup.enabled")).isFalse();
        }

        // =====================================================================
        // 🆕 Onda 3.5.1 — defaults das novas flags de alta prioridade
        // =====================================================================

        @Test
        @DisplayName("alta prioridade: todas as 6 flags usam default true")
        void altaPrioridade_defaultTrue() {
            adminService.registerKnownFlags();

            assertThat(flagService.isEnabled("podcast.enabled")).isTrue();
            assertThat(flagService.isEnabled("weekly.reminder.enabled")).isTrue();
            assertThat(flagService.isEnabled("daily.releases.enabled")).isTrue();
            assertThat(flagService.isEnabled("worldcup.reminders30min.enabled")).isTrue();
            assertThat(flagService.isEnabled("easter.eggs.enabled")).isTrue();
            assertThat(flagService.isEnabled("admin.features.toggle-enabled")).isTrue();
        }

        @Test
        @DisplayName("alta prioridade: Environment sobrescreve o default")
        void altaPrioridade_envOverride() {
            when(environment.getProperty("podcast.enabled")).thenReturn("false");
            when(environment.getProperty("weekly.reminder.enabled")).thenReturn("false");

            adminService.registerKnownFlags();

            assertThat(flagService.isEnabled("podcast.enabled")).isFalse();
            assertThat(flagService.isEnabled("weekly.reminder.enabled")).isFalse();
            // As demais mantêm o default
            assertThat(flagService.isEnabled("daily.releases.enabled")).isTrue();
            assertThat(flagService.isEnabled("easter.eggs.enabled")).isTrue();
        }

        // =====================================================================
        // 🆕 Onda 3.5.2 — defaults das flags de média prioridade
        // =====================================================================

        @Test
        @DisplayName("média prioridade: todas as 5 flags usam default true")
        void mediaPrioridade_defaultTrue() {
            adminService.registerKnownFlags();

            assertThat(flagService.isEnabled("weekly.digest.enabled")).isTrue();
            assertThat(flagService.isEnabled("birthday.enabled")).isTrue();
            assertThat(flagService.isEnabled("birthday.group-notification.enabled")).isTrue();
            assertThat(flagService.isEnabled("podcast.fallback-text.enabled")).isTrue();
            assertThat(flagService.isEnabled("ai.digest.custom-period.enabled")).isTrue();
        }

        // =====================================================================
        // 🆕 Onda 3.5.3 — defaults das flags de baixa prioridade
        // =====================================================================

        @Test
        @DisplayName("baixa prioridade: 4 flags default true + kafka.audit default false")
        void baixaPrioridade_defaults() {
            adminService.registerKnownFlags();

            assertThat(flagService.isEnabled("metrics.prometheus.enabled")).isTrue();
            assertThat(flagService.isEnabled("admin.debug-cache.enabled")).isTrue();
            assertThat(flagService.isEnabled("tmdb.cache.enabled")).isTrue();
            assertThat(flagService.isEnabled("telegram.error-fallback.enabled")).isTrue();
            // ⚠️ kafka.audit é a única com default false
            assertThat(flagService.isEnabled("kafka.audit.enabled")).isFalse();
        }

        @Test
        @DisplayName("baixa prioridade: Environment sobrescreve defaults")
        void baixaPrioridade_envOverride() {
            when(environment.getProperty("metrics.prometheus.enabled")).thenReturn("false");
            when(environment.getProperty("kafka.audit.enabled")).thenReturn("true");

            adminService.registerKnownFlags();

            assertThat(flagService.isEnabled("metrics.prometheus.enabled")).isFalse();
            assertThat(flagService.isEnabled("kafka.audit.enabled")).isTrue();
        }
    }

    // =========================================================================
    // DELEGAÇÃO PARA O FeatureFlagService
    // =========================================================================

    @Nested
    @DisplayName("Delegação")
    class Delegacao {

        @Test
        @DisplayName("list delega e retorna as 23 flags")
        void listDelegates() {
            adminService.registerKnownFlags();

            List<FeatureFlagState> list = adminService.list();

            assertThat(list).hasSize(TOTAL_FLAGS);
        }

        @Test
        @DisplayName("listAsMap delega e inclui todas as chaves")
        void listAsMapDelegates() {
            adminService.registerKnownFlags();

            Map<String, Map<String, Object>> map = adminService.listAsMap();

            assertThat(map)
                    .hasSize(TOTAL_FLAGS)
                    .containsKeys(
                            // Alta prioridade
                            "podcast.enabled",
                            "weekly.reminder.enabled",
                            "daily.releases.enabled",
                            "worldcup.reminders30min.enabled",
                            "easter.eggs.enabled",
                            "admin.features.toggle-enabled",
                            // Média prioridade
                            "weekly.digest.enabled",
                            "birthday.enabled",
                            "birthday.group-notification.enabled",
                            "podcast.fallback-text.enabled",
                            "ai.digest.custom-period.enabled",
                            // Baixa prioridade
                            "metrics.prometheus.enabled",
                            "admin.debug-cache.enabled",
                            "tmdb.cache.enabled",
                            "telegram.error-fallback.enabled",
                            "kafka.audit.enabled",
                            // Já existentes
                            "worldcup.enabled",
                            "transcription.enabled",
                            "auto.response.enabled",
                            "digest.enabled",
                            "worldcup.update.enabled",
                            "migration.enabled",
                            "prompts.external.enabled");
        }

        @Test
        @DisplayName("isEnabled delega corretamente")
        void isEnabledDelegates() {
            when(environment.getProperty("worldcup.enabled")).thenReturn("true");
            adminService.registerKnownFlags();

            assertThat(adminService.isEnabled("worldcup.enabled")).isTrue();
            assertThat(adminService.isEnabled("nao.existe")).isFalse();
        }

        @Test
        @DisplayName("toggle delega e altera o valor")
        void toggleDelegates() {
            when(environment.getProperty("worldcup.enabled")).thenReturn("false");
            adminService.registerKnownFlags();

            adminService.toggle("worldcup.enabled", true);

            assertThat(adminService.isEnabled("worldcup.enabled")).isTrue();
        }

        @Test
        @DisplayName("toggle em flag read-only propaga IllegalStateException")
        void toggleReadOnlyThrows() {
            adminService.registerKnownFlags();

            assertThatThrownBy(() -> adminService.toggle("migration.enabled", true))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("read-only");
        }

        @Test
        @DisplayName("toggle em flag desconhecida propaga IllegalArgumentException")
        void toggleUnknownThrows() {
            adminService.registerKnownFlags();

            assertThatThrownBy(() -> adminService.toggle("nao.existe", true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Flag desconhecida");
        }
    }

    // =========================================================================
    // toggleWithCheck — guarda-chuva admin.features.toggle-enabled
    // =========================================================================

    @Nested
    @DisplayName("toggleWithCheck (guarda-chuva)")
    class ToggleWithCheck {

        @Test
        @DisplayName("guarda-chuva habilitada: toggle funciona normalmente")
        void guardaChuvaHabilitada_toggleFunciona() {
            when(environment.getProperty("podcast.enabled")).thenReturn("false");
            adminService.registerKnownFlags();

            // Default é true, então toggleWithCheck deve funcionar
            adminService.toggleWithCheck("podcast.enabled", true);

            assertThat(adminService.isEnabled("podcast.enabled")).isTrue();
        }

        @Test
        @DisplayName("guarda-chuva desabilitada: toggleWithCheck lança IllegalStateException")
        void guardaChuvaDesabilitada_lanca() {
            when(environment.getProperty("admin.features.toggle-enabled")).thenReturn("false");
            when(environment.getProperty("podcast.enabled")).thenReturn("false");
            adminService.registerKnownFlags();

            assertThatThrownBy(() -> adminService.toggleWithCheck("podcast.enabled", true))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Toggle de feature flags desabilitado");

            // A flag alvo NÃO foi alterada
            assertThat(adminService.isEnabled("podcast.enabled")).isFalse();
        }

        @Test
        @DisplayName("guarda-chuva desabilitada: toggle (sem check) ainda funciona")
        void guardaChuvaDesabilitada_toggleSemCheckFunciona() {
            when(environment.getProperty("admin.features.toggle-enabled")).thenReturn("false");
            when(environment.getProperty("podcast.enabled")).thenReturn("false");
            adminService.registerKnownFlags();

            // O método toggle (sem check) continua permitindo a alteração
            adminService.toggle("podcast.enabled", true);

            assertThat(adminService.isEnabled("podcast.enabled")).isTrue();
        }

        @Test
        @DisplayName("guarda-chuva habilitada: toggle em flag read-only propaga o erro original")
        void guardaChuvaHabilitada_toggleReadOnly() {
            adminService.registerKnownFlags();

            assertThatThrownBy(() -> adminService.toggleWithCheck("migration.enabled", true))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("read-only");
        }
    }
}
