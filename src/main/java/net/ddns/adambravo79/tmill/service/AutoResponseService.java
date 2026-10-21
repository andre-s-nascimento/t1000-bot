/* (c) 21/10/2026 */
package net.ddns.adambravo79.tmill.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
import net.ddns.adambravo79.tmill.model.ResponseAnimationPair;
import net.ddns.adambravo79.tmill.model.UserOverride;
import net.ddns.adambravo79.tmill.service.feature.FeatureFlagAdminService;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
public class AutoResponseService {

    private static final String FEATURE_FLAG_AUTO_RESPONSE = "auto.response.enabled";

    private final Map<String, AutoResponseRule> triggerToRule = new HashMap<>();
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final MetricsService metricsService;
    private final FeatureFlagAdminService featureFlags;

    private static final ZoneId BRAZIL_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @Value("${auto.response.file:classpath:auto-responses.json}")
    private String configFile;

    @Value("${auto.response.once-per-day-triggers:bom dia}")
    private String oncePerDayTriggersRaw;

    private Set<String> oncePerDayTriggers = new HashSet<>();

    // Mapa: userId -> (trigger -> último dia em que respondeu)
    private final Map<Long, Map<String, LocalDate>> userTriggerCooldown = new HashMap<>();

    public AutoResponseService(
            ResourceLoader resourceLoader,
            ObjectMapper objectMapper,
            MetricsService metricsService,
            FeatureFlagAdminService featureFlags) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
        this.featureFlags = featureFlags;
    }

    @PostConstruct
    public void init() {
        parseOncePerDayTriggers();
        if (featureFlags.isEnabled(FEATURE_FLAG_AUTO_RESPONSE)) {
            loadResponses();
        }
    }

    @SuppressWarnings(
            "null") // 🔧 FIX: Silencia o JDT Null Type Safety para priorizar o Method Reference do
    // Sonar
    private void parseOncePerDayTriggers() {
        oncePerDayTriggers =
                Arrays.stream(oncePerDayTriggersRaw.split(","))
                        .map(String::trim)
                        .map(String::toLowerCase)
                        .filter(s -> !s.isBlank())
                        .collect(Collectors.toSet());
        log.info("🔁 Triggers de uma vez ao dia: {}", oncePerDayTriggers);
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

    private void recordResponse(Long userId, String trigger) {
        if (userId == null) return;
        if (!isOncePerDayTrigger(trigger)) return;

        userTriggerCooldown
                .computeIfAbsent(userId, k -> new HashMap<>())
                .put(trigger, LocalDate.now(BRAZIL_ZONE));
    }

    private boolean shouldSuppressResponse(Long userId, String trigger) {
        if (userId == null) return false;
        if (!isOncePerDayTrigger(trigger)) return false;

        Map<String, LocalDate> userTriggers = userTriggerCooldown.get(userId);
        if (userTriggers == null) return false;

        LocalDate lastResponse = userTriggers.get(trigger);
        if (lastResponse == null) return false;

        return lastResponse.equals(LocalDate.now(BRAZIL_ZONE));
    }

    /**
     * Resolve a lista efetiva de pares (texto, animação) de uma regra, aplicando a precedência:
     *
     * <ol>
     *   <li>{@code responses} — pares completos, cada um com seu próprio texto. Precedência máxima.
     *   <li>{@code animations} — lista de animações; texto vem do campo {@code response} padrão.
     *   <li>{@code animation} — animação única; texto vem do campo {@code response} padrão.
     *   <li>Nenhum dos três → lista vazia (a regra envia apenas texto, sem mídia).
     * </ol>
     *
     * <p>Quando a fonte são {@code animations}/{@code animation}, os pares sintéticos usam o
     * {@code response} padrão da regra (pode ser {@code null}, virando par só de animação).
     *
     * @param entry entrada da regra vinda do JSON
     * @return lista imutável de pares (nunca {@code null}; pode ser vazia)
     */
    private List<ResponseAnimationPair> resolverPares(AutoResponseRuleEntry entry) {
        // 1. Formato novo: lista de pares explícitos
        if (entry.responses() != null && !entry.responses().isEmpty()) {
            return List.copyOf(entry.responses());
        }

        // 2. Formato intermediário: lista de animações + response padrão
        if (entry.animations() != null && !entry.animations().isEmpty()) {
            String textoPadrao = entry.response();
            return entry.animations().stream()
                    .map(anim -> new ResponseAnimationPair(textoPadrao, anim))
                    .toList();
        }

        // 3. Formato legado: animação única + response padrão
        if (entry.animation() != null && !entry.animation().isBlank()) {
            return List.of(new ResponseAnimationPair(entry.response(), entry.animation()));
        }

        // 4. Nenhuma animação — regra apenas de texto (comportamento atual preservado)
        return List.of();
    }

    /**
     * Escolhe um par (texto, animação) aleatório da lista.
     *
     * <p>Este método é {@code protected} para permitir que testes o sobrescrevam e retornem um
     * índice determinístico, sem precisar mockar {@link ThreadLocalRandom} (que é final e não
     * deve ser mockado).
     *
     * @param pares lista de pares disponíveis
     * @param indiceSorteado índice retornado pelo gerador (0 <= indice < pares.size())
     * @return par no índice informado, ou {@code null} se a lista estiver vazia
     */
    private ResponseAnimationPair escolherPar(List<ResponseAnimationPair> pares) {
        if (pares == null || pares.isEmpty()) {
            return null;
        }
        if (pares.size() == 1) {
            return pares.getFirst();
        }
        int indice = sortearIndice(pares.size());
        return pares.get(indice);
    }

    /**
     * Sorteia um índice no intervalo {@code [0, bound)}.
     *
     * <p>Extraído como método {@code protected} para que testes possam sobrescrevê-lo e retornar
     * um índice fixo, tornando o teste determinístico sem mockar {@link ThreadLocalRandom}.
     *
     * @param bound limite superior exclusivo (deve ser > 0)
     * @return índice sorteado
     */
    protected int sortearIndice(int bound) {
        return ThreadLocalRandom.current().nextInt(bound);
    }

    private void processRuleEntry(String ruleName, AutoResponseRuleEntry entry) {
        if (entry.triggers() == null) {
            log.warn("Regra '{}' ignorada: triggers nulos", ruleName);
            return;
        }

        boolean temAlgumaResposta =
                entry.response() != null
                        || (entry.responses() != null && !entry.responses().isEmpty())
                        || (entry.animations() != null && !entry.animations().isEmpty())
                        || (entry.animation() != null && !entry.animation().isBlank());

        if (!temAlgumaResposta) {
            log.warn("Regra '{}' ignorada: nenhuma resposta ou animação configurada", ruleName);
            return;
        }

        LocalTime startTime = parseTime(entry.timeRange(), "start");
        LocalTime endTime = parseTime(entry.timeRange(), "end");
        Map<String, AutoResponseOverride> overrides = buildOverrides(entry);
        List<ResponseAnimationPair> responsePairs = resolverPares(entry);

        for (String trigger : entry.triggers()) {
            if (trigger != null && !trigger.isBlank()) {
                triggerToRule.put(
                        trigger.toLowerCase(),
                        new AutoResponseRule(
                                entry.response(),
                                entry.animation(),
                                entry.animations() != null
                                        ? List.copyOf(entry.animations())
                                        : List.of(),
                                responsePairs,
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
        if (!featureFlags.isEnabled(FEATURE_FLAG_AUTO_RESPONSE)
                || message == null
                || message.isBlank()) {
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
                .findFirst()
                .flatMap(entry -> resolverOverride(userId, entry.getKey(), entry.getValue(), now));
    }

    /**
     * Resolve o {@link AutoResponseOverride} para uma regra encontrada, aplicando supressão,
     * override por usuário e sorteio de par (texto + animação).
     *
     * <p>Extraído de {@link #getResponseRule} para reduzir a complexidade cognitiva do método
     * principal (Sonar {@code java:S3776}).
     *
     * @param userId ID do usuário (pode ser {@code null})
     * @param trigger chave do trigger que casou
     * @param rule regra associada ao trigger
     * @param now horário efetivo (para log)
     * @return {@code Optional} com o override, ou vazio se suprimido
     */
    private Optional<AutoResponseOverride> resolverOverride(
            Long userId, String trigger, AutoResponseRule rule, LocalTime now) {

        if (shouldSuppressResponse(userId, trigger)) {
            log.debug(
                    "⏭️ Resposta suprimida para userId={}, trigger='{}' (já recebeu hoje)",
                    userId,
                    trigger);
            metricsService.success("auto_response_suprimida");
            return Optional.empty();
        }

        AutoResponseOverride override = resolverOverridePersonalizado(userId, rule);

        if (override == null) {
            override = resolverOverrideSorteado(rule);
        }

        recordResponse(userId, trigger);
        log.info("✅ Trigger '{}' ativado (horário: {})", trigger, now);
        metricsService.success("auto_response_disparada");
        return Optional.of(override);
    }

    /**
     * Retorna o override específico do usuário, se configurado; caso contrário, {@code null}.
     */
    private AutoResponseOverride resolverOverridePersonalizado(Long userId, AutoResponseRule rule) {
        if (userId == null || rule.userOverrides() == null) {
            return null;
        }
        String userIdKey = String.valueOf(userId);
        AutoResponseOverride override = rule.userOverrides().get(userIdKey);
        if (override != null) {
            log.info("🎯 Usando resposta personalizada para userId={}", userId);
        }
        return override;
    }

    /**
     * Sorteia um par (texto, animação) e monta o override. Se não houver pares configurados,
     * cai no comportamento legado (response + animation).
     */
    private AutoResponseOverride resolverOverrideSorteado(AutoResponseRule rule) {
        ResponseAnimationPair parSorteado = escolherPar(rule.responsePairs());

        if (parSorteado != null) {
            String texto = resolverTexto(parSorteado, rule);
            return new AutoResponseOverride(texto, parSorteado.animation());
        }

        return new AutoResponseOverride(rule.response(), rule.animation());
    }

    /**
     * Resolve o texto final do par sorteado, aplicando a precedência:
     * {@code par.text()} > {@code rule.response()} > {@code "Olá!"} (fallback genérico).
     */
    private String resolverTexto(ResponseAnimationPair par, AutoResponseRule rule) {
        if (par.text() != null) {
            return par.text();
        }
        if (rule.response() != null) {
            return rule.response();
        }
        return "Olá!";
    }

    public Optional<AutoResponseOverride> getResponseRule(Long userId, String message) {
        return getResponseRule(userId, message, LocalTime.now(BRAZIL_ZONE));
    }

    public void reload() {
        loadResponses();
    }

    // ========================= MÉTODOS DE ESTATÍSTICA E DEBUG
    // =========================

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
                            "response='%s', start=%s, end=%s, overrides=%d, pares=%d",
                            rule.response(),
                            rule.startTime(),
                            rule.endTime(),
                            rule.userOverrides() != null ? rule.userOverrides().size() : 0,
                            rule.responsePairs() != null ? rule.responsePairs().size() : 0));
        }
        return summary;
    }

    /**
     * Verifica se um trigger está na lista "once-per-day".
     *
     * <p>Match exato OU prefixo seguido de espaço. Assim:
     *
     * <ul>
     *   <li>"bom dia" casa com "bom dia" e "bom dia família"
     *   <li>NÃO casa com "bom diabo" (não tem espaço depois do prefixo)
     * </ul>
     *
     * <p>Para variantes com pontuação (ex: "bom dia!"), inclua-as explicitamente no .env
     * (AUTO_RESPONSE_ONCE_PER_DAY).
     */
    private boolean isOncePerDayTrigger(String trigger) {
        String t = trigger.toLowerCase();
        return oncePerDayTriggers.stream()
                .anyMatch(prefix -> t.equals(prefix) || t.startsWith(prefix + " "));
    }

    /** Exposto para health checks e para o controller consultar o estado da flag. */
    public boolean isEnabled() {
        return featureFlags.isEnabled(FEATURE_FLAG_AUTO_RESPONSE);
    }
}
