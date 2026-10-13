/* (c) 2026 | 12/10/2026 */
package net.ddns.adambravo79.tmill.service.config;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RefinementConfigService {

    private static final RefinementSpec DEFAULT = new RefinementSpec(0.30, 200, 4000, 0.18);

    private final JsonConfigLoader jsonConfigLoader;

    @Value("${refinement.config.file:classpath:refinement-config.json}")
    private String refinementConfigFile;

    private final AtomicReference<RefinementSpec> specRef = new AtomicReference<>(DEFAULT);

    public RefinementConfigService(JsonConfigLoader jsonConfigLoader) {
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
                            "refinement-config.json",
                            Map.class,
                            refinementConfigFile != null && !refinementConfigFile.isBlank()
                                    ? refinementConfigFile
                                    : null);

            if (loaded.isEmpty()) {
                log.warn("⚠️ refinement-config.json não encontrado — usando defaults.");
                return;
            }

            double maxReducao =
                    getDouble(
                            loaded.get(),
                            "max-refinamento-reducao",
                            DEFAULT.maxRefinamentoReducao());
            int minLength =
                    getInt(
                            loaded.get(),
                            "min-length-para-checar-truncamento",
                            DEFAULT.minLengthParaChecarTruncamento());
            int maxTokens =
                    getInt(loaded.get(), "refinement-max-tokens", DEFAULT.refinementMaxTokens());
            double temperature =
                    getDouble(
                            loaded.get(),
                            "refinement-temperature",
                            DEFAULT.refinementTemperature());

            specRef.set(new RefinementSpec(maxReducao, minLength, maxTokens, temperature));
            log.info(
                    "✅ RefinementConfig: maxReducao={} minLen={} maxTokens={} temp={}",
                    maxReducao,
                    minLength,
                    maxTokens,
                    temperature);
        } catch (Exception e) {
            log.error("❌ Falha ao carregar refinement-config — mantendo anterior", e);
        }
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

    public RefinementSpec getSpec() {
        return specRef.get();
    }

    public record RefinementSpec(
            double maxRefinamentoReducao,
            int minLengthParaChecarTruncamento,
            int refinementMaxTokens,
            double refinementTemperature) {}
}
