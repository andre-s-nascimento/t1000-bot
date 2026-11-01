package net.ddns.adambravo79.tmill.service.config;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SalaVipConfigService {

    // Fallbacks seguros
    private static final String DEFAULT_CRON = "0 0 12 * * TUE";
    private static final String DEFAULT_ZONE = "America/Sao_Paulo";
    private static final String DEFAULT_URL =
            "https://letterboxd.com/adambravo79/list/sala-vip-do-rapaduracast/";

    private final JsonConfigLoader jsonConfigLoader;

    @Value("${t1000.salavip.config.file:classpath:salavip-config.json}")
    private String configFile;

    private volatile String cron = DEFAULT_CRON;
    private volatile String zone = DEFAULT_ZONE;
    private volatile String url = DEFAULT_URL;

    public SalaVipConfigService(JsonConfigLoader jsonConfigLoader) {
        this.jsonConfigLoader = jsonConfigLoader;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public synchronized void reload() {
        try {
            var loaded = jsonConfigLoader.loadConfig("salavip-config.json", Map.class, configFile);

            if (loaded.isEmpty()) {
                log.warn("⚠️ salavip-config.json não encontrado — usando fallbacks.");
                return;
            }

            Map<String, Object> map = loaded.get();
            this.cron = getString(map, "cron", DEFAULT_CRON);
            this.zone = getString(map, "zone", DEFAULT_ZONE);
            this.url = getString(map, "url", DEFAULT_URL);

            log.info("✅ SalaVipConfig: cron='{}', zone='{}', url='{}'", cron, zone, url);
        } catch (Exception e) {
            log.error("❌ Falha ao carregar salavip-config.json — mantendo valores anteriores", e);
        }
    }

    private static String getString(Map<String, Object> map, String key, String fallback) {
        Object v = map.get(key);
        return (v != null && !v.toString().isBlank()) ? v.toString() : fallback;
    }

    public String getCron() {
        return cron;
    }

    public String getZone() {
        return zone;
    }

    public String getUrl() {
        return url;
    }
}
