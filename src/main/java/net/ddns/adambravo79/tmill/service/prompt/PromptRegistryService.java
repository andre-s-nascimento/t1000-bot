/* (c) 2026 | 30/09/2026 */
package net.ddns.adambravo79.tmill.service.prompt;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.service.config.JsonConfigLoader;

@Slf4j
@Service
public class PromptRegistryService {

    private final JsonConfigLoader jsonConfigLoader;
    private static final String DEFAULT_PROMPTS_RESOURCE = "prompts/digest-personas.json";
    private final String overridePath;
    private final Map<String, Object> promptCache = new ConcurrentHashMap<>();

    public PromptRegistryService(
            JsonConfigLoader jsonConfigLoader,
            @Value("${t1000.prompts.override-path:#{null}}") String overridePath) {
        this.jsonConfigLoader = jsonConfigLoader;
        this.overridePath = overridePath;
        loadPrompts();
    }

    /**
     * Carrega os prompts usando o JsonConfigLoader (suporta overridePath e fallback no classpath).
     */
    @SuppressWarnings("unchecked")
    public synchronized void loadPrompts() {
        log.info("🔄 Carregando configurações de prompts e personas...");

        Optional<Map> loadedMap =
                jsonConfigLoader.loadConfig(
                        DEFAULT_PROMPTS_RESOURCE,
                        Map.class,
                        overridePath != null && !overridePath.isBlank()
                                ? overridePath
                                : "config/prompts/digest-personas.json");

        promptCache.clear();
        if (loadedMap.isPresent()) {
            promptCache.putAll((Map<String, Object>) loadedMap.get());
            log.info(
                    "✅ Prompts carregados com sucesso. Total de chaves topo: {}",
                    promptCache.size());
        } else {
            log.warn(
                    "⚠️ Nenhum arquivo de prompt/persona foi encontrado no override ou"
                            + " classpath.");
        }
    }

    /**
     * Executa o recarregamento síncrono dos prompts do disco/classpath.
     */
    public void reload() {
        loadPrompts();
    }

    /**
     * Retorna o mapa completo de prompts carregados (unmodifiable).
     */
    public Map<String, Object> getAllPrompts() {
        return Collections.unmodifiableMap(promptCache);
    }

    /**
     * Obtém um prompt específico do mapa pelo seu nome/chave.
     */
    public <T> Optional<T> getPrompt(String key, Class<T> targetType) {
        Object val = promptCache.get(key);
        if (val == null) {
            return Optional.empty();
        }
        if (targetType.isInstance(val)) {
            return Optional.of(targetType.cast(val));
        }
        return Optional.empty();
    }
}
