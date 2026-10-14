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
 * <p><b>Flags registradas</b>:
 *
 * <ul>
 *   <li>{@code worldcup.enabled} — envio de jogos da Copa (editável)
 *   <li>{@code transcription.enabled} — transcrição de áudio (editável)
 *   <li>{@code auto.response.enabled} — respostas automáticas (editável)
 *   <li>{@code digest.enabled} — digest diário (editável)
 *   <li>{@code worldcup.update.enabled} — atualização automática do JSON da Copa (editável)
 *   <li>{@code prompts.external.enabled} — uso de prompts e personas externos via JSON (read-only)
 *   <li>{@code migration.enabled} — migração SQLite (read-only)
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
        // --- Alta prioridade (Onda 3.5.1) ---
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

    /**
     * @deprecated Use {@link #toggleWithCheck(String, boolean)} — que valida a guarda-chuva.
     */
    @Deprecated(since = "2.6.0")
    public void toggle(String key, boolean enabled) {
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

    /**
     * Altera uma flag com verificação da flag guarda-chuva {@code admin.features.toggle-enabled}.
     *
     * <p>Se a guarda-chuva estiver desabilitada, qualquer toggle lança {@link IllegalStateException}.
     * Isso evita que alguém desabilite uma flag por engano enquanto a própria governança está
     * bloqueada.
     */
    public void toggleWithCheck(String key, boolean enabled) {
        if (!flagService.isEnabled("admin.features.toggle-enabled")) {
            throw new IllegalStateException(
                    "Toggle de feature flags desabilitado. Habilite "
                            + "'admin.features.toggle-enabled' para continuar.");
        }
        flagService.toggle(key, enabled);
    }
}
