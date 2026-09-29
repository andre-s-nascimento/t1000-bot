/* (c) 2026 | 26/09/2026 */
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
 *   <li>{@code migration.enabled} — migração SQLite (read-only)
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureFlagAdminService {

    private final FeatureFlagService flagService;
    private final Environment environment;

    // Lê o valor inicial de cada flag do application.properties
    // (não usa @Value diretamente para permitir registro dinâmico)

    @PostConstruct
    public void registerKnownFlags() {
        registerBool("worldcup.enabled", false, "Envio de jogos da Copa do Mundo");
        registerBool("transcription.enabled", true, "Transcrição de áudio (Whisper/Groq)");
        registerBool("auto.response.enabled", true, "Respostas automáticas no chat");
        registerBool("digest.enabled", true, "Digest diário (morning/evening)");
        registerBool("worldcup.update.enabled", false, "Atualização automática do JSON da Copa");

        // Read-only — requer restart
        registerBoolReadOnly(
                "migration.enabled", false, "Migração SQLite → Postgres/Mongo (requer restart)");

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
