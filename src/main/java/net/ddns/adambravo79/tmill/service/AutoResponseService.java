/* (c) 2026 | 20/07/2026 */
package net.ddns.adambravo79.tmill.service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.model.AutoResponseOverride;
import net.ddns.adambravo79.tmill.model.AutoResponseRule;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

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
                LocalTime startTime = null;
                LocalTime endTime = null;
                Map<String, String> timeRange =
                        (Map<String, String>) entry.getValue().get("timeRange");
                if (timeRange != null) {
                    startTime = LocalTime.parse(timeRange.get("start"), TIME_FORMATTER);
                    endTime = LocalTime.parse(timeRange.get("end"), TIME_FORMATTER);
                }

                // Extrai userOverrides
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

    /**
     * Obtém a regra de resposta para um usuário e mensagem, com horário simulado. Útil para testes e
     * simulações.
     */
    public Optional<AutoResponseOverride> getResponseRule(
            Long userId, String message, LocalTime time) {
        if (!enabled || message == null || message.isBlank()) {
            return Optional.empty();
        }

        String lowerMsg = message.toLowerCase();
        List<Map.Entry<String, AutoResponseRule>> sorted =
                new ArrayList<>(triggerToRule.entrySet());
        sorted.sort((a, b) -> b.getKey().length() - a.getKey().length());

        LocalTime now = time != null ? time : LocalTime.now(BRAZIL_ZONE);

        for (Map.Entry<String, AutoResponseRule> entry : sorted) {
            String trigger = entry.getKey();
            AutoResponseRule rule = entry.getValue();

            if (trigger.length() < 3) continue;
            if (!containsExactWord(lowerMsg, trigger)) continue;
            if (!isTimeInRange(now, rule.startTime(), rule.endTime())) continue;

            String userIdKey = userId != null ? String.valueOf(userId) : null;
            if (userIdKey != null
                    && rule.userOverrides() != null
                    && rule.userOverrides().containsKey(userIdKey)) {
                AutoResponseOverride ov = rule.userOverrides().get(userIdKey);
                log.info(
                        "🎯 Usando resposta personalizada para userId={} via horário simulado",
                        userId);
                return Optional.of(ov);
            }

            log.info("✅ Trigger '{}' ativado (horário simulado: {})", trigger, now);
            return Optional.of(new AutoResponseOverride(rule.response(), rule.animation()));
        }
        return Optional.empty();
    }

    /**
     * Obtém a regra de resposta para um usuário e mensagem, usando o horário real atual. Mantido para
     * compatibilidade com o código existente.
     */
    public Optional<AutoResponseOverride> getResponseRule(Long userId, String message) {
        return getResponseRule(userId, message, LocalTime.now(BRAZIL_ZONE));
    }

    public void reload() {
        loadResponses();
    }

    // =========================
    // MÉTODOS DE ESTATÍSTICA E DEBUG
    // =========================

    public boolean isEnabled() {
        return enabled;
    }

    public int getRulesCount() {
        return triggerToRule.size();
    }

    /** Retorna um resumo de todas as regras carregadas, útil para debug e listagem. */
    public Map<String, String> getRulesSummary() {
        Map<String, String> summary = new LinkedHashMap<>();
        for (Map.Entry<String, AutoResponseRule> entry : triggerToRule.entrySet()) {
            String key = entry.getKey();
            AutoResponseRule rule = entry.getValue();
            summary.put(
                    key,
                    String.format(
                            "response='%s', start=%s, end=%s, overrides=%d",
                            rule.response(),
                            rule.startTime(),
                            rule.endTime(),
                            rule.userOverrides() != null ? rule.userOverrides().size() : 0));
        }
        return summary;
    }
}
