/* (c) 2026 | 21/05/2026 */
package net.ddns.adambravo79.tmill.service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

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
    private static final ZoneId BRAZIL_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

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

            Map<String, Map<String, Object>> config =
                    mapper.readValue(resource.getInputStream(), new TypeReference<>() {});
            triggerToRule.clear();

            for (Map.Entry<String, Map<String, Object>> entry : config.entrySet()) {
                List<String> triggers = (List<String>) entry.getValue().get("triggers");
                String response = (String) entry.getValue().get("response");
                String animation = (String) entry.getValue().get("animation");

                // Extrai timeRange
                LocalTime startTime = null, endTime = null;
                Map<String, String> timeRange =
                        (Map<String, String>) entry.getValue().get("timeRange");
                if (timeRange != null) {
                    startTime = LocalTime.parse(timeRange.get("start"), TIME_FORMATTER);
                    endTime = LocalTime.parse(timeRange.get("end"), TIME_FORMATTER);
                }

                // Extrai userOverrides (formato unificado ou separado)
                Map<String, AutoResponseOverride> userOverrides = new HashMap<>();

                // 1) Tenta o formato "userOverrides" (recomendado)
                Map<String, Map<String, Object>> overridesRaw =
                        (Map<String, Map<String, Object>>) entry.getValue().get("userOverrides");
                if (overridesRaw != null) {
                    for (Map.Entry<String, Map<String, Object>> ov : overridesRaw.entrySet()) {
                        String userId = ov.getKey();
                        String ovResponse = (String) ov.getValue().get("response");
                        String ovAnimation = (String) ov.getValue().get("animation");
                        if (ovResponse != null) {
                            userOverrides.put(
                                    userId, new AutoResponseOverride(ovResponse, ovAnimation));
                        }
                    }
                } else {
                    // 2) Fallback: formato separado "userResponse" e "userAnimation"
                    Map<String, String> userResponseRaw =
                            (Map<String, String>) entry.getValue().get("userResponse");
                    Map<String, String> userAnimationRaw =
                            (Map<String, String>) entry.getValue().get("userAnimation");
                    if (userResponseRaw != null) {
                        for (Map.Entry<String, String> uv : userResponseRaw.entrySet()) {
                            String userId = uv.getKey();
                            String ovResponse = uv.getValue();
                            String ovAnimation =
                                    (userAnimationRaw != null)
                                            ? userAnimationRaw.get(userId)
                                            : null;
                            userOverrides.put(
                                    userId, new AutoResponseOverride(ovResponse, ovAnimation));
                        }
                    }
                }

                if (triggers != null && response != null) {
                    for (String trigger : triggers) {
                        if (trigger != null && !trigger.isBlank()) {
                            triggerToRule.put(
                                    trigger.toLowerCase(),
                                    new AutoResponseRule(
                                            response,
                                            animation,
                                            startTime,
                                            endTime,
                                            userOverrides));
                        }
                    }
                }
            }

            log.info("✅ Carregadas {} regras de resposta automática", triggerToRule.size());
            log.debug("Triggers carregados: {}", triggerToRule.keySet());
        } catch (Exception e) {
            log.error("Falha ao carregar respostas automáticas", e);
        }
    }

    private boolean containsExactWord(String text, String word) {
        Pattern pattern =
                Pattern.compile("\\b" + Pattern.quote(word) + "\\b", Pattern.CASE_INSENSITIVE);
        return pattern.matcher(text).find();
    }

    private boolean isTimeInRange(LocalTime now, LocalTime start, LocalTime end) {
        if (start == null || end == null) return true;
        if (start.isBefore(end) || start.equals(end)) {
            return !now.isBefore(start) && !now.isAfter(end);
        } else {
            return now.isAfter(start) || now.isBefore(end);
        }
    }

    public Optional<AutoResponseOverride> getResponseRule(Long userId, String message) {
        if (!enabled || message == null || message.isBlank()) {
            return Optional.empty();
        }

        String lowerMsg = message.toLowerCase();
        // Ordena triggers por tamanho (mais específicos primeiro)
        List<Map.Entry<String, AutoResponseRule>> sorted =
                new ArrayList<>(triggerToRule.entrySet());
        sorted.sort((a, b) -> b.getKey().length() - a.getKey().length());

        LocalTime now = ZonedDateTime.now(BRAZIL_ZONE).toLocalTime();

        for (Map.Entry<String, AutoResponseRule> entry : sorted) {
            String trigger = entry.getKey();
            AutoResponseRule rule = entry.getValue();

            if (trigger.length() < 3) continue; // ignora triggers muito curtos
            if (!containsExactWord(lowerMsg, trigger)) continue;
            if (!isTimeInRange(now, rule.getStartTime(), rule.getEndTime())) continue;

            log.info("✅ Trigger '{}' ativado pela mensagem: '{}'", trigger, lowerMsg);

            // Verifica se há override para este userId
            String userIdKey = userId != null ? String.valueOf(userId) : null;
            if (userIdKey != null
                    && rule.getUserOverrides() != null
                    && rule.getUserOverrides().containsKey(userIdKey)) {
                AutoResponseOverride ov = rule.getUserOverrides().get(userIdKey);
                log.info("🎯 Usando resposta personalizada para userId={}", userId);
                return Optional.of(ov);
            }

            // Usa a resposta padrão da regra
            return Optional.of(new AutoResponseOverride(rule.getResponse(), rule.getAnimation()));
        }
        return Optional.empty();
    }

    public void reload() {
        loadResponses();
    }
}
