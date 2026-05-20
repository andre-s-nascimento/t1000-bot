/* (c) 2026 | 19/05/2026 */
package net.ddns.adambravo79.tmill.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AutoResponseService {

    private final Map<String, AutoResponseRule> triggerToRule = new HashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${auto.response.enabled:false}")
    private boolean enabled;

    @Value("${auto.response.file:classpath:auto-responses.json}")
    private String configFile;

    private final ResourceLoader resourceLoader;

    public AutoResponseService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        if (enabled) {
            loadResponses();
        }
    }

    public void loadResponses() {
        try {
            Resource resource = resourceLoader.getResource(configFile);
            if (!resource.exists()) {
                log.warn("Arquivo de respostas automáticas não encontrado: {}", configFile);
                return;
            }

            // A estrutura agora é Map<String, Map<String, Object>>
            Map<String, Map<String, Object>> config =
                    mapper.readValue(resource.getInputStream(), new TypeReference<>() {});
            triggerToRule.clear();
            for (Map.Entry<String, Map<String, Object>> entry : config.entrySet()) {
                List<String> triggers = (List<String>) entry.getValue().get("triggers");
                String response = (String) entry.getValue().get("response");
                String animation = (String) entry.getValue().get("animation");
                if (triggers != null && response != null) {
                    for (String trigger : triggers) {
                        triggerToRule.put(
                                trigger.toLowerCase(), new AutoResponseRule(response, animation));
                    }
                }
            }
            log.info("✅ Carregadas {} regras de resposta automática", triggerToRule.size());
        } catch (Exception e) {
            log.error("Falha ao carregar respostas automáticas", e);
        }
    }

    public Optional<AutoResponseRule> getResponseRule(String message) {
        if (!enabled || message == null || message.isBlank()) {
            return Optional.empty();
        }
        String lowerMsg = message.toLowerCase();
        for (Map.Entry<String, AutoResponseRule> entry : triggerToRule.entrySet()) {
            if (lowerMsg.contains(entry.getKey())) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    public void reload() {
        loadResponses();
    }
}
