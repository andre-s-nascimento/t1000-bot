/* (c) 2026 | 12/10/2026 */
package net.ddns.adambravo79.tmill.service.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Externaliza os parâmetros de retry dos clients (GroqClient, TmdbClient).
 *
 * <p>Antes os valores eram hardcoded nas annotations {@code @Retryable} — agora vêm de
 * {@code groq-config.json} e podem ser ajustados sem recompilar.
 *
 * <p><b>Nota:</b> as annotations {@code @Retryable} do Spring não permitem valores dinâmicos.
 * Portanto, este serviço expõe os parâmetros para uso em retry manual (implementado no
 * {@link net.ddns.adambravo79.tmill.util.RetryUtils}).
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
                log.warn("⚠️ groq-config.json não encontrado — mantendo retry specs anteriores.");
                return;
            }

            Object retryNode = loaded.get().get("retry");
            if (!(retryNode instanceof Map<?, ?> rawMap)) {
                log.warn("⚠️ Chave 'retry' ausente — mantendo retry specs anteriores.");
                return;
            }

            Map<String, RetrySpec> novo = new LinkedHashMap<>();
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

    private static RetrySpec parseSpec(Map<?, ?> m) {
        int maxRetries = getInt(m, "max-retries", 3);
        long delay = getLong(m, "delay-ms", 1000);
        int multiplier = getInt(m, "multiplier", 2);
        long maxDelay = getLong(m, "max-delay-ms", 30000);
        return new RetrySpec(maxRetries, delay, multiplier, maxDelay);
    }

    private static int getInt(Map<?, ?> m, String k, int fb) {
        Object v = m.get(k);
        if (v instanceof Number n) return n.intValue();
        if (v != null) {
            try {
                return Integer.parseInt(v.toString());
            } catch (NumberFormatException ignored) {
                // valor malformado no JSON: usa o fallback
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
            } catch (NumberFormatException ignored) {
                // valor malformado no JSON: usa o fallback
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
