/* (c) 2026 | 12/10/2026 */
package net.ddns.adambravo79.tmill.service.config;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Configurações do Groq: modelos, temperature e maxTokens por tipo de chamada.
 */
@Slf4j
@Service
public class GroqConfigService {

    private static final DigestSpec DEFAULT_DIGEST = new DigestSpec(0.5, 2200);
    private static final RefinementSpec DEFAULT_REFINEMENT = new RefinementSpec(0.18, 4000);

    private final JsonConfigLoader jsonConfigLoader;

    @Value("${groq.config.file:classpath:groq-config.json}")
    private String groqConfigFile;

    private final AtomicReference<DigestSpec> digestRef = new AtomicReference<>(DEFAULT_DIGEST);
    private final AtomicReference<RefinementSpec> refinementRef =
            new AtomicReference<>(DEFAULT_REFINEMENT);

    public GroqConfigService(JsonConfigLoader jsonConfigLoader) {
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
                log.warn("⚠️ groq-config.json não encontrado — usando defaults.");
                return;
            }

            applyDigest(loaded.get().get("digest"));
            applyRefinement(loaded.get().get("refinement"));

            log.info(
                    "✅ GroqConfig: digest={}, refinement={}", digestRef.get(), refinementRef.get());
        } catch (Exception e) {
            log.error("❌ Falha ao carregar groq-config — mantendo anteriores", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void applyDigest(Object node) {
        if (!(node instanceof Map<?, ?> m)) return;
        double temp = getDouble(m, "temperature", DEFAULT_DIGEST.temperature());
        int maxTokens = getInt(m, "max-tokens", DEFAULT_DIGEST.maxTokens());
        digestRef.set(new DigestSpec(temp, maxTokens));
    }

    @SuppressWarnings("unchecked")
    private void applyRefinement(Object node) {
        if (!(node instanceof Map<?, ?> m)) return;
        double temp = getDouble(m, "temperature", DEFAULT_REFINEMENT.temperature());
        int maxTokens = getInt(m, "max-tokens", DEFAULT_REFINEMENT.maxTokens());
        refinementRef.set(new RefinementSpec(temp, maxTokens));
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

    private static double getDouble(Map<?, ?> m, String k, double fb) {
        Object v = m.get(k);
        if (v instanceof Number n) return n.doubleValue();
        if (v != null) {
            try {
                return Double.parseDouble(v.toString());
            } catch (Exception ignored) {
            }
        }
        return fb;
    }

    public DigestSpec getDigestSpec() {
        return digestRef.get();
    }

    public RefinementSpec getRefinementSpec() {
        return refinementRef.get();
    }

    public record DigestSpec(double temperature, int maxTokens) {}

    public record RefinementSpec(double temperature, int maxTokens) {}
}
