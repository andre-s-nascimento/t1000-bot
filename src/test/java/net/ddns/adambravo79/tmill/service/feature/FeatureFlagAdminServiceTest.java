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
import org.springframework.core.env.Environment;

import tools.jackson.databind.ObjectMapper;

/**
 * Testes do {@link FeatureFlagAdminService}.
 *
 * <p>Verifica:
 *
 * <ul>
 *   <li>Registro das 7 flags conhecidas no {@code @PostConstruct}
 *   <li>Leitura correta dos defaults do {@code Environment}
 *   <li>Delegação para o {@link FeatureFlagService}
 *   <li>Flags read-only são registradas como tal
 * </ul>
 *
 * <p>Usa o {@link FeatureFlagService} <b>real</b> (sem persistência) para validar a integração
 * entre os dois serviços — não há mock do {@code FeatureFlagService}, só do {@code Environment}.
 */
@ExtendWith(MockitoExtension.class)
class FeatureFlagAdminServiceTest {

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
        @DisplayName("registra exatamente 7 flags no boot")
        void registersSixFlags() {
            adminService.registerKnownFlags();

            assertThat(flagService.list()).hasSize(7);
        }

        @Test
        @DisplayName("registra todas as chaves esperadas")
        void registersExpectedKeys() {
            adminService.registerKnownFlags();

            List<String> keys = flagService.list().stream().map(FeatureFlagState::key).toList();

            assertThat(keys)
                    .containsExactlyInAnyOrder(
                            "worldcup.enabled",
                            "transcription.enabled",
                            "auto.response.enabled",
                            "digest.enabled",
                            "worldcup.update.enabled",
                            "migration.enabled",
                            "prompts.external.enabled");
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

            assertThat(flagService.list()).hasSize(7);
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
    }

    // =========================================================================
    // DELEGAÇÃO PARA O FeatureFlagService
    // =========================================================================

    @Nested
    @DisplayName("Delegação")
    class Delegacao {

        @Test
        @DisplayName("list delega e retorna as 7 flags")
        void listDelegates() {
            adminService.registerKnownFlags();

            List<FeatureFlagState> list = adminService.list();

            assertThat(list).hasSize(7);
        }

        @Test
        @DisplayName("listAsMap delega e inclui todas as chaves")
        void listAsMapDelegates() {
            adminService.registerKnownFlags();

            Map<String, Map<String, Object>> map = adminService.listAsMap();

            assertThat(map)
                    .hasSize(7)
                    .containsKeys(
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
}
