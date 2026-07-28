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
import net.ddns.adambravo79.tmill.model.AutoResponseConfig;
import net.ddns.adambravo79.tmill.model.AutoResponseOverride;
import net.ddns.adambravo79.tmill.model.AutoResponseRule;
import net.ddns.adambravo79.tmill.model.AutoResponseRuleEntry;
import net.ddns.adambravo79.tmill.model.UserOverride;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
public class AutoResponseService {

    private final Map<String, AutoResponseRule> triggerToRule = new HashMap<>();
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private static final ZoneId BRAZIL_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @Value("${auto.response.enabled:false}")
    private boolean enabled;

    @Value("${auto.response.file:classpath:auto-responses.json}")
    private String configFile;

    public AutoResponseService(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
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

            AutoResponseConfig config =
                    objectMapper.readValue(resource.getInputStream(), AutoResponseConfig.class);

            if (config == null || config.rules() == null) {
                log.warn("Arquivo de respostas automáticas vazio ou inválido.");
                return;
            }

            triggerToRule.clear();
            for (Map.Entry<String, AutoResponseRuleEntry> entry : config.rules().entrySet()) {
                String ruleName = entry.getKey();
                AutoResponseRuleEntry ruleEntry = entry.getValue();
                processRuleEntry(ruleName, ruleEntry);
            }

            log.info("✅ Carregadas {} regras de resposta automática", triggerToRule.size());
            log.debug("Triggers carregados: {}", triggerToRule.keySet());

        } catch (Exception e) {
            log.error("Falha ao carregar respostas automáticas", e);
        }
    }

    private void processRuleEntry(String ruleName, AutoResponseRuleEntry entry) {
        if (entry.triggers() == null || entry.response() == null) {
            log.warn("Regra '{}' ignorada: triggers ou response nulos", ruleName);
            return;
        }

        LocalTime startTime = parseTime(entry.timeRange(), "start");
        LocalTime endTime = parseTime(entry.timeRange(), "end");
        Map<String, AutoResponseOverride> overrides = buildOverrides(entry);

        for (String trigger : entry.triggers()) {
            if (trigger != null && !trigger.isBlank()) {
                triggerToRule.put(
                        trigger.toLowerCase(),
                        new AutoResponseRule(
                                entry.response(),
                                entry.animation(),
                                startTime,
                                endTime,
                                overrides));
            }
        }
    }

    private LocalTime parseTime(Map<String, String> timeRange, String key) {
        if (timeRange == null) return null;
        String value = timeRange.get(key);
        if (value == null) return null;
        try {
            return LocalTime.parse(value, TIME_FORMATTER);
        } catch (Exception e) {
            log.warn("Formato de hora inválido para {}: {}", key, value);
            return null;
        }
    }

    /**
     * Builds a map of user-specific overrides. Uses computeIfAbsent to avoid explicit
     * containsKey+put.
     */
    private Map<String, AutoResponseOverride> buildOverrides(AutoResponseRuleEntry entry) {
        Map<String, AutoResponseOverride> overrides = new HashMap<>();

        // 1. New format: userOverrides
        if (entry.userOverrides() != null) {
            for (Map.Entry<String, UserOverride> ov : entry.userOverrides().entrySet()) {
                overrides.put(
                        ov.getKey(),
                        new AutoResponseOverride(
                                ov.getValue().response(), ov.getValue().animation()));
            }
        }

        // 2. Legacy fallback: userResponse + userAnimation
        if (entry.userResponse() != null) {
            for (Map.Entry<String, String> uv : entry.userResponse().entrySet()) {
                String userId = uv.getKey();
                String response = uv.getValue();
                String animation =
                        entry.userAnimation() != null ? entry.userAnimation().get(userId) : null;
                // Only add if not already present (new format takes precedence)
                overrides.computeIfAbsent(
                        userId, k -> new AutoResponseOverride(response, animation));
            }
        }

        return overrides;
    }

    // ========================= LÓGICA DE TRIGGER =========================

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

    public Optional<AutoResponseOverride> getResponseRule(
            Long userId, String message, LocalTime time) {
        if (!enabled || message == null || message.isBlank()) {
            return Optional.empty();
        }

        String lowerMsg = message.toLowerCase();
        LocalTime now = time != null ? time : LocalTime.now(BRAZIL_ZONE);

        return triggerToRule.entrySet().stream()
                .sorted((a, b) -> b.getKey().length() - a.getKey().length())
                .filter(entry -> entry.getKey().length() >= 3)
                .filter(entry -> containsExactWord(lowerMsg, entry.getKey()))
                .filter(
                        entry ->
                                isTimeInRange(
                                        now,
                                        entry.getValue().startTime(),
                                        entry.getValue().endTime()))
                .map(
                        entry -> {
                            String userIdKey = userId != null ? String.valueOf(userId) : null;
                            AutoResponseRule rule = entry.getValue();

                            if (userIdKey != null
                                    && rule.userOverrides() != null
                                    && rule.userOverrides().containsKey(userIdKey)) {
                                log.info("🎯 Usando resposta personalizada para userId={}", userId);
                                return rule.userOverrides().get(userIdKey);
                            }

                            log.info("✅ Trigger '{}' ativado (horário: {})", entry.getKey(), now);
                            return new AutoResponseOverride(rule.response(), rule.animation());
                        })
                .findFirst();
    }

    public Optional<AutoResponseOverride> getResponseRule(Long userId, String message) {
        return getResponseRule(userId, message, LocalTime.now(BRAZIL_ZONE));
    }

    public void reload() {
        loadResponses();
    }

    // ========================= MÉTODOS DE ESTATÍSTICA E DEBUG =========================

    public boolean isEnabled() {
        return enabled;
    }

    public int getRulesCount() {
        return triggerToRule.size();
    }

    public Map<String, String> getRulesSummary() {
        Map<String, String> summary = new LinkedHashMap<>();
        for (Map.Entry<String, AutoResponseRule> entry : triggerToRule.entrySet()) {
            AutoResponseRule rule = entry.getValue();
            summary.put(
                    entry.getKey(),
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
