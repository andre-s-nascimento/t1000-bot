/* (c) 2026 | 12/10/2026 */
package net.ddns.adambravo79.tmill.service.config;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Externaliza os parâmetros de retry do {@code @Retryable} (GroqClient, TmdbClient).
 *
 * <p>Antes os valores eram hardcoded nas annotations — agora vêm de
 * {@code groq-config.json} e podem ser ajustados sem recompilar.
 *
 * <p><b>Nota:</b> as annotations {@code @Retryable} do Spring não permitem valores
 * dinâmicos. Portanto, este serviço expõe os parâmetros para uso em retry manual
 * (implementado no {@link net.ddns.adambravo79.tmill.util.RetryUtils}).
 */
@Slf4j
@Service
public class RetryConfigService {

    private final JsonConfigLoader jsonConfigLoader;

    @Value("${groq.config.file:classpath:groq-config.json}")
    private String groqConfigFile;

    private final AtomicReference<Map<String, RetrySpec>> retrySpecsRef =
            new AtomicReference<>(Map.of());

    public RetryConfigService(JsonConfigLoader jsonConfigLoader) {
        this.jsonConfigLoader = jsonConfigLoader;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void reload() {
        try {
            var loaded =
                    jsonConfigLoader.loadConfig(
                            "groq-config.json",
                            Map.class,
                            groqConfigFile != null && !groqConfigFile.isBlank()
                                    ? groqConfigFile
                                    : null);

            if (loaded.isEmpty()) {
                log.warn("⚠️ groq-config.json não encontrado — mantendo anteriores.");
                return;
            }

            Object retryNode = loaded.get().get("retry");
            if (!(retryNode instanceof Map<?, ?> rawMap)) {
                log.warn("⚠️ Chave 'retry' ausente — mantendo anteriores.");
                return;
            }

            java.util.Map<String, RetrySpec> novo = new java.util.LinkedHashMap<>();
            rawMap.forEach(
                    (k, v) -> {
                        if (k == null || !(v instanceof Map<?, ?> m)) return;
                        novo.put(k.toString(), parseSpec(m));
                    });
            retrySpecsRef.set(Map.copyOf(novo));

            log.info("✅ RetryConfig carregado: {} specs", novo.size());
        } catch (Exception e) {
            log.error("❌ Falha ao carregar retry config — mantendo anteriores", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static RetrySpec parseSpec(Map<?, ?> m) {
        int maxRetries = getInt(m, "max-retries", 3);
        long delay = getLong(m, "delay-ms", 1000);
        int multiplier = getInt(m, "multiplier", 2);
        long maxDelay = getLong(m, "max-delay-ms", 30000);
        return new RetrySpec(maxRetries, delay, multiplier, maxDelay);
    }

    private static Map<String, RetrySpec> defaultSpecs() {
        return Map.of(
                "transcription", new RetrySpec(2, 1000, 2, 5000),
                "refinement", new RetrySpec(3, 2000, 3, 30000),
                "chat-completion", new RetrySpec(4, 2000, 3, 60000),
                "tmdb-search", new RetrySpec(2, 1000, 2, 5000),
                "tmdb-details", new RetrySpec(2, 1000, 2, 5000),
                "tmdb-credits", new RetrySpec(1, 500, 2, 5000));
    }

    private static int getInt(Map<?, ?> m, String k, int fb) {
        Object v = m.get(k);
        if (v instanceof Number n) return n.intValue();
        if (v != null) {
            try {
                return Integer.parseInt(v.toString());
            } catch (Exception ignored) {
            }
        }
        return fb;
    }

    private static long getLong(Map<?, ?> m, String k, long fb) {
        Object v = m.get(k);
        if (v instanceof Number n) return n.longValue();
        if (v != null) {
            try {
                return Long.parseLong(v.toString());
            } catch (Exception ignored) {
            }
        }
        return fb;
    }

    /** Retorna o spec para uma chave, ou um spec default se ausente. */
    public RetrySpec get(String key) {
        RetrySpec spec = retrySpecsRef.get().get(key);
        return spec != null ? spec : new RetrySpec(3, 1000, 2, 30000);
    }

    public record RetrySpec(int maxRetries, long delayMs, int multiplier, long maxDelayMs) {}
}
