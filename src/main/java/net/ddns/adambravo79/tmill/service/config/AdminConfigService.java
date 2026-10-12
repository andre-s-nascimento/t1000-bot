/* (c) 2026 | 12/10/2026 */
package net.ddns.adambravo79.tmill.service.config;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Carrega configurações administrativas (ex.: chat showcase padrão) de
 * {@code admin-config.json}, permitindo alteração sem recompilar.
 *
 * <p>Se o arquivo estiver ausente ou inválido, cai em defaults seguros
 * (chat showcase conhecido) e apenas loga um warning.
 */
@Slf4j
@Service
public class AdminConfigService {

    /** Fallback histórico — mantido para não quebrar em ambientes sem JSON. */
    public static final long FALLBACK_SHOWCASE_CHAT_ID = -1003703557250L;

    private final JsonConfigLoader jsonConfigLoader;

    @Value("${admin.config.file:classpath:admin-config.json}")
    private String adminConfigFile;

    private volatile long showcaseChatId = FALLBACK_SHOWCASE_CHAT_ID;

    public AdminConfigService(JsonConfigLoader jsonConfigLoader) {
        this.jsonConfigLoader = jsonConfigLoader;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    /** Recarrega o JSON. Em caso de falha, mantém o valor atual. */
    public synchronized void reload() {
        try {
            var loaded =
                    jsonConfigLoader.loadConfig(
                            "admin-config.json",
                            Map.class,
                            adminConfigFile != null && !adminConfigFile.isBlank()
                                    ? adminConfigFile
                                    : null);

            if (loaded.isEmpty()) {
                log.warn(
                        "⚠️ admin-config.json não encontrado — usando showcase={} (fallback)",
                        showcaseChatId);
                return;
            }

            Object raw = loaded.get().get("showcaseChatId");
            applyShowcaseChatId(raw);
        } catch (Exception e) {
            log.error("❌ Falha ao carregar admin-config.json — mantendo {}", showcaseChatId, e);
        }
    }

    private void applyShowcaseChatId(Object raw) {
        if (raw instanceof Number n) {
            this.showcaseChatId = n.longValue();
            log.info("✅ AdminConfig: showcaseChatId={}", showcaseChatId);
            return;
        }

        if (raw != null) {
            setShowcaseChatIdFromString(raw);
            return;
        }

        log.warn("⚠️ Chave 'showcaseChatId' ausente — mantendo {}", showcaseChatId);
    }

    private void setShowcaseChatIdFromString(Object raw) {
        try {
            this.showcaseChatId = Long.parseLong(raw.toString());
            log.info("✅ AdminConfig: showcaseChatId={}", showcaseChatId);
        } catch (NumberFormatException ex) {
            log.warn("⚠️ showcaseChatId inválido no JSON: '{}'", raw);
        }
    }

    public long getShowcaseChatId() {
        return showcaseChatId;
    }
}
