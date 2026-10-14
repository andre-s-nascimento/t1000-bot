/* (c) 2026 | 30/09/2026 */
package net.ddns.adambravo79.tmill.service.feature;

import java.util.List;
import java.util.Map;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fachada administrativa do {@link FeatureFlagService}.
 *
 * <p>Responsabilidades:
 *
 * <ul>
 *   <li>Registrar as flags conhecidas do projeto no boot, lendo o valor inicial do {@code
 *       application.properties} / variáveis de ambiente.
 *   <li>Expor operações de leitura e toggle para os controllers (REST e Web).
 * </ul>
 *
 * <p><b>Flags registradas</b> (23 no total):
 *
 * <h3>Alta prioridade — Onda 3.5.1</h3>
 *
 * <ul>
 *   <li>{@code podcast.enabled}
 *   <li>{@code weekly.reminder.enabled}
 *   <li>{@code daily.releases.enabled}
 *   <li>{@code worldcup.reminders30min.enabled}
 *   <li>{@code easter.eggs.enabled}
 *   <li>{@code admin.features.toggle-enabled}
 * </ul>
 *
 * <h3>Média prioridade — Onda 3.5.2</h3>
 *
 * <ul>
 *   <li>{@code weekly.digest.enabled}
 *   <li>{@code birthday.enabled}
 *   <li>{@code birthday.group-notification.enabled}
 *   <li>{@code podcast.fallback-text.enabled}
 *   <li>{@code ai.digest.custom-period.enabled}
 * </ul>
 *
 * <h3>Baixa prioridade — Onda 3.5.3</h3>
 *
 * <ul>
 *   <li>{@code metrics.prometheus.enabled}
 *   <li>{@code admin.debug-cache.enabled}
 *   <li>{@code tmdb.cache.enabled}
 *   <li>{@code telegram.error-fallback.enabled}
 *   <li>{@code kafka.audit.enabled}
 * </ul>
 *
 * <h3>Já existentes</h3>
 *
 * <ul>
 *   <li>{@code worldcup.enabled}
 *   <li>{@code transcription.enabled}
 *   <li>{@code auto.response.enabled}
 *   <li>{@code digest.enabled}
 *   <li>{@code worldcup.update.enabled}
 *   <li>{@code prompts.external.enabled}
 *   <li>{@code migration.enabled} (read-only)
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureFlagAdminService {

    private final FeatureFlagService flagService;
    private final Environment environment;

    @PostConstruct
    public void registerKnownFlags() {
        // =====================================================================
        // Alta prioridade — Onda 3.5.1
        // =====================================================================
        registerBool("podcast.enabled", true, "Publicação automática semanal do podcast");
        registerBool("weekly.reminder.enabled", true, "Lembrete semanal de quarta-feira");
        registerBool("daily.releases.enabled", true, "Notificação de lançamentos a cada 6h");
        registerBool(
                "worldcup.reminders30min.enabled", true, "Aviso 30 min antes de cada jogo da Copa");
        registerBool("easter.eggs.enabled", true, "Easter eggs nas buscas de filmes");
        registerBool(
                "admin.features.toggle-enabled",
                true,
                "Permitir alteração de flags via painel admin");

        // =====================================================================
        // Média prioridade — Onda 3.5.2
        // =====================================================================
        registerBool("weekly.digest.enabled", true, "Giro semanal dos streamings (quinta-feira)");
        registerBool("birthday.enabled", true, "Parabéns automáticos de aniversário");
        registerBool(
                "birthday.group-notification.enabled",
                true,
                "Notificar aniversário também nos grupos (senão só no privado)");
        registerBool(
                "podcast.fallback-text.enabled",
                true,
                "Enviar roteiro como texto quando o áudio do podcast falha");
        registerBool(
                "ai.digest.custom-period.enabled",
                true,
                "Permitir custom-digest com período arbitrário");

        // =====================================================================
        // Baixa prioridade — Onda 3.5.3
        // =====================================================================
        registerBool("metrics.prometheus.enabled", true, "Expor endpoint /actuator/prometheus");
        registerBool(
                "admin.debug-cache.enabled",
                true,
                "Expor endpoints de debug de cache (pode conter texto de transcrição)");
        registerBool(
                "tmdb.cache.enabled", true, "Cache de detalhes de filme (@Cacheable movieDetails)");
        registerBool(
                "telegram.error-fallback.enabled",
                true,
                "Fallback genérico do TelegramSafeExecutor em caso de erro");
        registerBool(
                "kafka.audit.enabled", false, "Persistir eventos Kafka no MongoDB (audit trail)");

        // =====================================================================
        // Já existentes
        // =====================================================================
        registerBool("worldcup.enabled", false, "Envio de jogos da Copa do Mundo");
        registerBool("transcription.enabled", true, "Transcrição de áudio (Whisper/Groq)");
        registerBool("auto.response.enabled", true, "Respostas automáticas no chat");
        registerBool("digest.enabled", true, "Digest diário (morning/evening)");
        registerBool("worldcup.update.enabled", false, "Atualização automática do JSON da Copa");
        registerBool(
                "prompts.external.enabled",
                false,
                "Uso de prompts e personas externalizados em JSON");
        registerBoolReadOnly(
                "migration.enabled", false, "Migração SQLite → Postgres/Mongo (requer restart)");

        flagService.init();

        log.info("🎛️ {} feature flags registradas", flagService.list().size());
    }

    // =========================================================================
    // API PÚBLICA (consumida pelos controllers)
    // =========================================================================

    public List<FeatureFlagState> list() {
        return flagService.list();
    }

    public Map<String, Map<String, Object>> listAsMap() {
        return flagService.listAsMap();
    }

    public boolean isEnabled(String key) {
        return flagService.isEnabled(key);
    }

    public void toggle(String key, boolean enabled) {
        flagService.toggle(key, enabled);
    }

    /**
     * Altera uma flag com verificação da flag guarda-chuva {@code admin.features.toggle-enabled}.
     *
     * @throws IllegalStateException se {@code admin.features.toggle-enabled} estiver desabilitada
     * @throws IllegalArgumentException se a flag alvo não existir
     * @throws IllegalStateException se a flag alvo for read-only
     */
    public void toggleWithCheck(String key, boolean enabled) {
        if (!flagService.isEnabled("admin.features.toggle-enabled")) {
            throw new IllegalStateException(
                    "Toggle de feature flags desabilitado. Habilite "
                            + "'admin.features.toggle-enabled' para continuar.");
        }
        flagService.toggle(key, enabled);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private void registerBool(String key, boolean defaultValue, String description) {
        boolean initial = readBool(key, defaultValue);
        flagService.register(key, initial, description);
    }

    private void registerBoolReadOnly(String key, boolean defaultValue, String description) {
        boolean initial = readBool(key, defaultValue);
        flagService.registerReadOnly(key, initial, description);
    }

    private boolean readBool(String key, boolean defaultValue) {
        String raw = environment.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(raw.trim());
    }
}
