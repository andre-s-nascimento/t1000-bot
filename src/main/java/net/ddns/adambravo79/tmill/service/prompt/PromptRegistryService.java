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
    private static final String PODCAST_SYSTEM_RESOURCE = "prompts/podcast-system.json";
    private static final String PODCAST_CONFIG_RESOURCE = "prompts/podcast-config.json";

    private final String overridePath;
    private final String podcastSystemOverridePath;
    private final String podcastConfigOverridePath;

    private final Map<String, Object> promptCache = new ConcurrentHashMap<>();

    public PromptRegistryService(
            JsonConfigLoader jsonConfigLoader,
            @Value("${t1000.prompts.override-path:#{null}}") String overridePath,
            @Value("${t1000.prompts.podcast-system.override-path:#{null}}")
                    String podcastSystemOverridePath,
            @Value("${t1000.prompts.podcast-config.override-path:#{null}}")
                    String podcastConfigOverridePath) {
        this.jsonConfigLoader = jsonConfigLoader;
        this.overridePath = overridePath;
        this.podcastSystemOverridePath = podcastSystemOverridePath;
        this.podcastConfigOverridePath = podcastConfigOverridePath;
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
                    "⚠️ Nenhum arquivo de prompt/persona foi encontrado no override ou classpath.");
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

    // =========================================================================
    // MÉTODOS DE CONVENIÊNCIA (Mapeamento dos testes de paridade)
    // =========================================================================

    /**
     * Constrói o System Prompt de uma persona do Digest aplicando o contexto do período.
     */
    @SuppressWarnings("unchecked")
    public String getDigestSystemPrompt(String personaName, String periodLabel) {
        Map<String, Object> personas = (Map<String, Object>) promptCache.get("personas");
        String basePrompt = "";

        if (personas != null && personas.containsKey(personaName)) {
            Map<String, Object> personaMap = (Map<String, Object>) personas.get(personaName);
            basePrompt = (String) personaMap.get("systemPrompt");
        }

        String context = getPeriodContext(periodLabel);
        if (context.isBlank()) {
            return basePrompt;
        }
        return basePrompt + "\n\n\n" + context;
    }

    /**
     * Formata o User Prompt do Digest substituindo as mensagens no template configurado.
     */
    public String getDigestUserPrompt(String messages) {
        String template = (String) promptCache.get("userPromptTemplate");
        if (template == null) {
            return messages;
        }
        return String.format(template, messages);
    }

    /**
     * Carrega e combina o System Prompt do Podcast com a linha de encerramento configurada.
     */
    @SuppressWarnings("unchecked")
    public String getPodcastSystemPrompt() {
        Optional<Map> systemMap =
                jsonConfigLoader.loadConfig(
                        PODCAST_SYSTEM_RESOURCE, Map.class, podcastSystemOverridePath);
        Optional<Map> configMap =
                jsonConfigLoader.loadConfig(
                        PODCAST_CONFIG_RESOURCE, Map.class, podcastConfigOverridePath);

        String baseSystemPrompt = "";
        if (systemMap.isPresent() && systemMap.get().containsKey("systemPrompt")) {
            baseSystemPrompt = (String) systemMap.get().get("systemPrompt");
        }

        String closingLine = "E caso eu não veja vocês, bom dia, boa tarde e boa noite!";
        if (configMap.isPresent() && configMap.get().containsKey("rules")) {
            Map<String, Object> rules = (Map<String, Object>) configMap.get().get("rules");
            if (rules != null && rules.get("closingLine") != null) {
                closingLine = (String) rules.get("closingLine");
            }
        }

        return baseSystemPrompt + "\n- Encerre com: \"" + closingLine + "\"";
    }

    @SuppressWarnings("unchecked")
    private String getPeriodContext(String periodLabel) {
        Map<String, Object> contexts = (Map<String, Object>) promptCache.get("periodContexts");
        if (contexts == null) {
            return "";
        }

        if (periodLabel != null && periodLabel.contains("MADRUGADA")) {
            return (String) contexts.getOrDefault("MADRUGADA", "");
        }
        return (String) contexts.getOrDefault("DEFAULT", "");
    }
}
