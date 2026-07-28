/* (c) 2026 | 22/07/2026 */
package net.ddns.adambravo79.tmill.controller;

import static net.ddns.adambravo79.tmill.constant.BotMessages.BRAZIL_ZONE;
import static net.ddns.adambravo79.tmill.constant.BotMessages.DATA_INVALIDA_WORLDCUP;
import static net.ddns.adambravo79.tmill.constant.BotMessages.ERRO_LIMPAR_DADOS;
import static net.ddns.adambravo79.tmill.constant.BotMessages.ERRO_LIMPAR_RELEASES;
import static net.ddns.adambravo79.tmill.constant.BotMessages.FMT_DD_MM_YYYY_HYPHEN;
import static net.ddns.adambravo79.tmill.constant.BotMessages.FMT_HH_MM;
import static net.ddns.adambravo79.tmill.constant.BotMessages.FMT_HH_MM_SS;
import static net.ddns.adambravo79.tmill.constant.BotMessages.FMT_YYYY_MM_DD;
import static net.ddns.adambravo79.tmill.constant.BotMessages.WORLD_CUP_DISABLED;
import static net.ddns.adambravo79.tmill.constant.BotMessages.WORLD_CUP_NOT_AVAILABLE;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.fasterxml.jackson.core.JsonProcessingException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.model.AutoResponseOverride;
import net.ddns.adambravo79.tmill.repository.ReleaseNotifiedRepository;
import net.ddns.adambravo79.tmill.service.AutoResponseService;
import net.ddns.adambravo79.tmill.service.DailyDigestService;
import net.ddns.adambravo79.tmill.service.DailyReleasesService;
import net.ddns.adambravo79.tmill.service.EasterEggService;
import net.ddns.adambravo79.tmill.service.StaticWorldCupService;
import net.ddns.adambravo79.tmill.service.WeeklyReminderService;
import net.ddns.adambravo79.tmill.service.WorldCupSchedulerService;
import net.ddns.adambravo79.tmill.service.cache.FileTranscriptionCacheService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import tools.jackson.databind.ObjectMapper;

/**
 * Controller administrativo para testes, limpeza de dados e monitoramento.
 *
 * <p>Exception handling strategy:
 *
 * <ul>
 *   <li>Erros de validação (input inválido) → HTTP 400 com mensagem clara.
 *   <li>Erros de negócio (serviço indisponível) → HTTP 503 com mensagem apropriada.
 *   <li>Erros de banco (DataAccessException) → HTTP 500 genérico (não expõe detalhes).
 *   <li>Erros de conectividade (ResourceAccessException) → HTTP 502/503.
 *   <li>Erros fatais (Error, InterruptedException) → NUNCA engolidos.
 *   <li>Mensagens de erro interno NUNCA expostas na resposta HTTP.
 * </ul>
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private static final long SHOWCASE_CHAT_ID = -5283244164L;
    private static final String MSG_ERRO_INTERNO =
            "Erro interno do servidor. Contate o administrador.";

    private final EasterEggService easterEggService;
    private final DailyDigestService dailyDigestService;
    private final FileTranscriptionCacheService fileTranscriptionCacheService;
    private final WeeklyReminderService weeklyReminderService;
    private final AutoResponseService autoResponseService;
    private final WorldCupSchedulerService worldCupSchedulerService;
    private final StaticWorldCupService staticWorldCupService;
    private final TelegramFacade telegramFacade;
    private final Environment environment;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final DailyReleasesService dailyReleasesService;
    private final ReleaseNotifiedRepository releaseNotifiedRepository;

    @Value("${worldcup.enabled:false}")
    private boolean worldcupEnabled;

    // ========================= LIMPEZA DE DADOS =========================

    @PostMapping("/clear-releases")
    public ResponseEntity<String> clearReleases() {
        try {
            releaseNotifiedRepository.clearAll();
            log.info("Tabela releases_notified limpa via endpoint.");
            return ResponseEntity.ok("✅ Tabela de lançamentos limpa com sucesso.");

        } catch (DataAccessException e) {
            log.error("Erro de banco ao limpar releases_notified", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ERRO_LIMPAR_RELEASES);
        }
    }

    @PostMapping("/clear-all-data")
    public ResponseEntity<String> clearAllData() {
        try {
            int deletedReleases = releaseNotifiedRepository.deleteAll();
            log.info("Dados limpos via endpoint admin.");
            return ResponseEntity.ok(
                    String.format("✅ Dados removidos: %d lançamentos deletados.", deletedReleases));

        } catch (DataAccessException e) {
            log.error("Erro de banco ao limpar dados", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ERRO_LIMPAR_DADOS);
        }
    }

    // ========================= MÉTODOS EXISTENTES =========================

    @PostMapping("/reload-auto-responses")
    public ResponseEntity<String> reloadAutoResponses() {
        autoResponseService.reload();
        return ResponseEntity.ok("Respostas automáticas recarregadas");
    }

    @PostMapping("/test-weekly-reminder")
    public ResponseEntity<String> testWeeklyReminder() {
        weeklyReminderService.sendWednesdayReminder();
        return ResponseEntity.ok("Lembrete semanal disparado manualmente.");
    }

    @PostMapping("/test-weekly-reminder-showcase")
    public ResponseEntity<String> testWeeklyReminderShowcase(
            @RequestParam(required = false) Long chatId) {
        long targetChatId = chatId != null ? chatId : SHOWCASE_CHAT_ID;
        weeklyReminderService.sendReminderToChat(targetChatId);
        return ResponseEntity.ok("Lembrete semanal enviado para o chat " + targetChatId);
    }

    @PostMapping("/reload-easter-eggs")
    public ResponseEntity<String> reloadEasterEggs() {
        easterEggService.reload();
        return ResponseEntity.ok("Easter eggs recarregados");
    }

    @GetMapping("/test-morning-digest")
    public ResponseEntity<String> testMorningDigest() {
        dailyDigestService.generateMorningDigest();
        return ResponseEntity.ok("Resumo da manhã disparado.");
    }

    @GetMapping("/test-evening-digest")
    public ResponseEntity<String> testEveningDigest() {
        dailyDigestService.generateEveningDigest();
        return ResponseEntity.ok("Resumo da noite disparado.");
    }

    @GetMapping("/cache-stats")
    public ResponseEntity<Map<String, Long>> getCacheStats() {
        return ResponseEntity.ok(fileTranscriptionCacheService.getStats());
    }

    @GetMapping("/custom-digest")
    public ResponseEntity<String> customDigest(
            @RequestParam("start") String startDate,
            @RequestParam("end") String endDate,
            @RequestParam(value = "chatId", required = false) Long chatId) {

        // --- Validação de entrada ---
        if (startDate == null || startDate.isBlank() || endDate == null || endDate.isBlank()) {
            return ResponseEntity.badRequest().body("Parâmetros 'start' e 'end' são obrigatórios.");
        }

        LocalDate[] dates = parseDateRange(startDate, endDate);
        if (dates.length == 0) {
            return ResponseEntity.badRequest()
                    .body("Formato inválido. Use 'yyyy-MM-dd' ou 'dd-MM-yyyy'.");
        }

        ZoneId zone = ZoneId.of(BRAZIL_ZONE);
        LocalDateTime from = dates[0].atStartOfDay(zone).toLocalDateTime();
        LocalDateTime to = dates[1].atTime(23, 59, 59);

        try {
            dailyDigestService.generateDigestCustom(from, to, chatId);
        } catch (IllegalArgumentException e) {
            log.warn("Parâmetros inválidos para custom digest: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Parâmetros inválidos: " + e.getMessage());
        } catch (RuntimeException e) {
            log.error("Erro ao gerar digest customizado", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(MSG_ERRO_INTERNO);
        }

        String message =
                "Resumo personalizado gerado para período: " + startDate + " até " + endDate;
        if (chatId != null) {
            message += " (enviado apenas para o chat " + chatId + ")";
        } else {
            message += " (enviado para todos os chats configurados)";
        }
        return ResponseEntity.ok(message);
    }

    // ========================= WORLD CUP =========================

    @PostMapping("/test-worldcup-noon")
    public ResponseEntity<String> testWorldCupNoon() {
        if (worldCupSchedulerService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(WORLD_CUP_NOT_AVAILABLE);
        }
        worldCupSchedulerService.sendNoonMatches();
        return ResponseEntity.ok("Envio de jogos do meio-dia executado (simulado)");
    }

    @PostMapping("/test-worldcup-evening")
    public ResponseEntity<String> testWorldCupEvening() {
        if (worldCupSchedulerService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(WORLD_CUP_NOT_AVAILABLE);
        }
        worldCupSchedulerService.sendEveningMatches();
        return ResponseEntity.ok("Envio de jogos da noite executado (simulado)");
    }

    @PostMapping("/test-worldcup")
    public ResponseEntity<String> testWorldCup() {
        if (!worldcupEnabled) {
            return ResponseEntity.ok(WORLD_CUP_DISABLED);
        }
        worldCupSchedulerService.sendManualTest();
        return ResponseEntity.ok("✅ Envio manual disparado! Verifique os logs.");
    }

    @PostMapping("/test-worldcup-showcase")
    public ResponseEntity<String> testWorldCupShowcase(
            @RequestParam(required = false) Long chatId) {
        long targetChatId = chatId != null ? chatId : SHOWCASE_CHAT_ID;
        if (!worldcupEnabled) {
            return ResponseEntity.ok(WORLD_CUP_DISABLED);
        }
        worldCupSchedulerService.sendManualTestToChat(targetChatId);
        return ResponseEntity.ok("✅ Teste manual da Copa enviado para o chat " + targetChatId);
    }

    @PostMapping("/test-worldcup-noon-showcase")
    public ResponseEntity<String> testWorldCupNoonShowcase(
            @RequestParam(required = false) Long chatId) {
        long targetChatId = chatId != null ? chatId : SHOWCASE_CHAT_ID;
        if (!worldcupEnabled) {
            return ResponseEntity.ok(WORLD_CUP_DISABLED);
        }
        worldCupSchedulerService.sendNoonMatchesToChat(targetChatId);
        return ResponseEntity.ok("✅ Envio do meio-dia da Copa enviado para o chat " + targetChatId);
    }

    @PostMapping("/test-worldcup-evening-showcase")
    public ResponseEntity<String> testWorldCupEveningShowcase(
            @RequestParam(required = false) Long chatId) {
        long targetChatId = chatId != null ? chatId : SHOWCASE_CHAT_ID;
        if (!worldcupEnabled) {
            return ResponseEntity.ok(WORLD_CUP_DISABLED);
        }
        worldCupSchedulerService.sendEveningMatchesToChat(targetChatId);
        return ResponseEntity.ok("✅ Envio da noite da Copa enviado para o chat " + targetChatId);
    }

    @PostMapping("/reload-worldcup-showcase")
    public ResponseEntity<String> reloadWorldCupShowcase(
            @RequestParam(required = false) Long chatId) {

        long targetChatId = chatId != null ? chatId : SHOWCASE_CHAT_ID;
        staticWorldCupService.reload();

        String msg =
                "✅ Dados da Copa recarregados do arquivo JSON às "
                        + LocalDateTime.now(ZoneId.of(BRAZIL_ZONE))
                                .format(DateTimeFormatter.ofPattern(FMT_HH_MM_SS));

        try {
            telegramFacade.enviarMensagemHtml(targetChatId, msg);
        } catch (HttpClientErrorException e) {
            log.warn(
                    "Erro HTTP ao enviar notificação de reload para chatId={}: {}",
                    targetChatId,
                    e.getStatusCode());
        } catch (ResourceAccessException e) {
            log.warn(
                    "Falha de conectividade ao enviar notificação de reload para chatId={}",
                    targetChatId);
        }

        return ResponseEntity.ok(msg + " (enviado para o chat " + targetChatId + ")");
    }

    @PostMapping("/test-worldcup-results-showcase")
    public ResponseEntity<String> testWorldCupResultsShowcase(
            @RequestParam(defaultValue = "ontem") String dateParam,
            @RequestParam(required = false) Long chatId) {

        long targetChatId = chatId != null ? chatId : SHOWCASE_CHAT_ID;
        if (!worldcupEnabled) {
            return ResponseEntity.ok(WORLD_CUP_DISABLED);
        }

        LocalDate date = parseDateParam(dateParam);
        if (date == null) {
            return ResponseEntity.badRequest().body(DATA_INVALIDA_WORLDCUP);
        }

        worldCupSchedulerService.sendResultsToChat(targetChatId, date);
        return ResponseEntity.ok(
                "✅ Resultados enviados para o chat " + targetChatId + " (data: " + date + ")");
    }

    @PostMapping("/reload-worldcup")
    public ResponseEntity<String> reloadWorldCup() {
        staticWorldCupService.reload();
        return ResponseEntity.ok("Dados da Copa recarregados do arquivo JSON");
    }

    // ========================= CONFIG & PROPERTIES =========================

    @GetMapping("/properties")
    public ResponseEntity<Map<String, Object>> getProperties() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("spring.application.name", environment.getProperty("spring.application.name"));
        props.put("server.port", environment.getProperty("server.port"));
        props.put(
                "spring.threads.virtual.enabled",
                environment.getProperty("spring.threads.virtual.enabled"));
        props.put("telegram.bot.username", environment.getProperty("telegram.bot.username"));
        props.put(
                "telegram.bot.polling.enabled",
                environment.getProperty("telegram.bot.polling.enabled"));
        props.put(
                "telegram.bot.polling.timeout",
                environment.getProperty("telegram.bot.polling.timeout"));
        props.put("telegram.message.limit", environment.getProperty("telegram.message.limit"));
        props.put("telegram.owner.id", environment.getProperty("telegram.owner.id"));
        props.put("telegram.bot.token", maskToken(environment.getProperty("telegram.bot.token")));
        props.put("groq.api.key", maskToken(environment.getProperty("groq.api.key")));
        props.put("tmdb.token", maskToken(environment.getProperty("tmdb.token")));
        props.put("groq.model.transcription", environment.getProperty("groq.model.transcription"));
        props.put("groq.model.refinement", environment.getProperty("groq.model.refinement"));
        props.put("groq.model.digest", environment.getProperty("groq.model.digest"));
        props.put(
                "cache.transcription.enabled",
                environment.getProperty("cache.transcription.enabled"));
        props.put(
                "cache.transcription.ttl-seconds",
                environment.getProperty("cache.transcription.ttl-seconds"));
        props.put("digest.enabled", environment.getProperty("digest.enabled"));
        props.put("digest.chat-ids", environment.getProperty("digest.chat-ids"));
        props.put("worldcup.enabled", environment.getProperty("worldcup.enabled"));
        props.put("worldcup.data.file", environment.getProperty("worldcup.data.file"));
        props.put("worldcup.update.enabled", environment.getProperty("worldcup.update.enabled"));
        props.put("auto.response.enabled", environment.getProperty("auto.response.enabled"));
        props.put("auto.response.file", environment.getProperty("auto.response.file"));
        props.put("easter-egg.file", environment.getProperty("easter-egg.file"));
        props.put(
                "weekly.reminder.media-file",
                environment.getProperty("weekly.reminder.media-file"));
        props.put(
                "t1000.features.transcription-enabled",
                environment.getProperty("t1000.features.transcription-enabled"));
        props.put("t1000.audio.max-size-mb", environment.getProperty("t1000.audio.max-size-mb"));
        props.put("bot.allowed-chats", environment.getProperty("bot.allowed-chats"));
        return ResponseEntity.ok(props);
    }

    @GetMapping("/config-files")
    public ResponseEntity<Map<String, Object>> getConfigFiles() {
        Map<String, Object> result = new LinkedHashMap<>();
        String[] files = {"easter-eggs.json", "auto-responses.json", "worldcup2026.json"};

        for (String fileName : files) {
            try {
                Object content = loadConfigFile(fileName);
                result.put(fileName, content);
            } catch (JsonProcessingException e) {
                log.error("Erro ao parsear JSON do arquivo: {}", fileName, e);
                result.put(fileName, "❌ Erro ao parsear JSON");
            } catch (IOException e) {
                log.warn("Arquivo de configuração não encontrado ou ilegível: {}", fileName);
                result.put(fileName, "❌ Arquivo não encontrado");
            }
        }
        return ResponseEntity.ok(result);
    }

    // ========================= DAILY RELEASES =========================

    @PostMapping("/test-daily-releases")
    public ResponseEntity<String> testDailyReleases() {
        dailyReleasesService.sendHourlyReleases();
        return ResponseEntity.ok("Verificação horária de lançamentos executada (teste).");
    }

    @PostMapping("/test-weekly-digest")
    public ResponseEntity<String> testWeeklyDigest() {
        dailyReleasesService.sendWeeklyDigest();
        return ResponseEntity.ok("Giro semanal executado (teste).");
    }

    // ========================= TESTES DE AUTO-RESPONSES =========================

    @PostMapping("/test-auto-response")
    public ResponseEntity<String> testAutoResponse(
            @RequestParam(required = false) Long userId,
            @RequestParam String message,
            @RequestParam(required = false) Long chatId,
            @RequestParam(required = false) String time) {

        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body("Parâmetro 'message' é obrigatório.");
        }

        long targetChatId = chatId != null ? chatId : SHOWCASE_CHAT_ID;
        LocalTime simulatedTime = parseTime(time);

        Optional<AutoResponseOverride> responseOpt =
                autoResponseService.getResponseRule(userId, message, simulatedTime);

        if (responseOpt.isEmpty()) {
            return ResponseEntity.ok(
                    "⚠️ Nenhuma resposta automática encontrada para essa mensagem.");
        }

        AutoResponseOverride response = responseOpt.get();
        String finalMsg = buildTestResponseMessage(userId, message, simulatedTime, response);

        sendTestResponse(targetChatId, response, finalMsg);

        return ResponseEntity.ok("✅ Resposta enviada para o chat " + targetChatId);
    }

    @GetMapping("/debug-auto-response")
    public ResponseEntity<Map<String, Object>> debugAutoResponse(
            @RequestParam(required = false) Long userId,
            @RequestParam String message,
            @RequestParam(required = false) String time) {

        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Parâmetro 'message' é obrigatório."));
        }

        LocalTime simulatedTime = parseTime(time);
        Optional<AutoResponseOverride> responseOpt =
                autoResponseService.getResponseRule(userId, message, simulatedTime);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("message", message);
        result.put("simulatedTime", simulatedTime != null ? simulatedTime.toString() : "atual");
        result.put("found", responseOpt.isPresent());

        if (responseOpt.isPresent()) {
            result.put("response", responseOpt.get().response());
            result.put("animation", responseOpt.get().animation());
        } else {
            result.put("response", "Nenhuma regra encontrada");
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/auto-response-rules")
    public ResponseEntity<Map<String, Object>> listAutoResponseRules() {
        return ResponseEntity.ok(
                Map.of(
                        "totalRules", autoResponseService.getRulesCount(),
                        "rules", autoResponseService.getRulesSummary()));
    }

    // ========================= MÉTODOS AUXILIARES PRIVADOS =========================

    /** Carrega e parseia um arquivo de configuração do classpath ou do diretório /app/config/. */
    private Object loadConfigFile(String fileName) throws IOException {
        Resource resource = resourceLoader.getResource("classpath:" + fileName);
        if (!resource.exists()) {
            resource = resourceLoader.getResource("file:/app/config/" + fileName);
            if (!resource.exists()) {
                throw new IOException("Arquivo não encontrado: " + fileName);
            }
        }
        return objectMapper.readValue(resource.getInputStream(), Object.class);
    }

    private String buildTestResponseMessage(
            Long userId, String message, LocalTime simulatedTime, AutoResponseOverride response) {
        return "🧪 *Teste de Auto-Response*\n\n"
                + "👤 Usuário: "
                + (userId != null ? userId : "NÃO DEFINIDO")
                + "\n"
                + "📝 Mensagem: "
                + message
                + "\n"
                + "🕒 Horário simulado: "
                + (simulatedTime != null ? simulatedTime : "atual")
                + "\n\n"
                + "✅ Resposta: "
                + response.response();
    }

    private void sendTestResponse(
            long targetChatId, AutoResponseOverride response, String finalMsg) {
        if (response.animation() != null
                && !response.animation().isBlank()
                && isValidUrl(response.animation())) {
            try {
                telegramFacade.enviarMidia(targetChatId, response.animation(), finalMsg);
            } catch (HttpClientErrorException e) {
                log.warn(
                        "Erro HTTP ao enviar mídia para chatId={}: {}",
                        targetChatId,
                        e.getStatusCode());
                fallbackToText(targetChatId, finalMsg);
            } catch (ResourceAccessException e) {
                log.warn("Falha de conectividade ao enviar mídia para chatId={}", targetChatId);
                fallbackToText(targetChatId, finalMsg);
            }
        } else {
            fallbackToText(targetChatId, finalMsg);
        }
    }

    private void fallbackToText(long chatId, String message) {
        try {
            telegramFacade.enviarMensagemHtml(chatId, message);
        } catch (HttpClientErrorException e) {
            log.warn(
                    "Erro HTTP ao enviar mensagem de fallback para chatId={}: {}",
                    chatId,
                    e.getStatusCode());
        } catch (ResourceAccessException e) {
            log.warn("Falha de conectividade ao enviar fallback para chatId={}", chatId);
        }
    }

    private LocalDate parseDateParam(String param) {
        if (param == null || param.isBlank()) return null;
        String lower = param.toLowerCase().trim();
        if (lower.equals("hoje") || lower.equals("de hoje"))
            return LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE));
        if (lower.equals("ontem") || lower.equals("de ontem"))
            return LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE)).minusDays(1);

        String cleaned = param.replaceAll("(?i)\\b(do|dia|de|da|as|os|dias)\\b", " ").trim();
        LocalDate parsed = tryParseWithPattern(cleaned);
        if (parsed != null) return parsed;

        // fallback attempts
        parsed = tryParseFallback(param, "dd/MM", "dd-MM");
        if (parsed != null) return parsed;
        return null;
    }

    private LocalDate tryParseWithPattern(String cleaned) {
        Pattern pattern =
                Pattern.compile("\\b(\\d{1,2}[/-]\\d{2}(?:[/-]\\d{4})?|\\d{4}-\\d{2}-\\d{2})\\b");
        Matcher m = pattern.matcher(cleaned);
        if (m.find()) {
            String dateStr = m.group(1).trim();
            try {
                if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}")) return LocalDate.parse(dateStr);
                if (dateStr.matches("\\d{1,2}[/-]\\d{2}")) {
                    DateTimeFormatter fmt =
                            new DateTimeFormatterBuilder()
                                    .appendPattern(dateStr.contains("/") ? "dd/MM" : "dd-MM")
                                    .parseDefaulting(ChronoField.YEAR, 2026)
                                    .toFormatter();
                    return LocalDate.parse(dateStr, fmt);
                }
            } catch (DateTimeParseException ignored) {
                /* log if needed */
            }
        }
        return null;
    }

    private LocalDate tryParseFallback(String param, String... patterns) {
        for (String p : patterns) {
            try {
                DateTimeFormatter fmt =
                        new DateTimeFormatterBuilder()
                                .appendPattern(p)
                                .parseDefaulting(ChronoField.YEAR, 2026)
                                .toFormatter();
                return LocalDate.parse(param, fmt);
            } catch (DateTimeParseException ignored) {
                /* continue */
            }
        }
        return null;
    }

    private LocalDate[] parseDateRange(String startDate, String endDate) {
        for (String pattern : new String[] {FMT_YYYY_MM_DD, FMT_DD_MM_YYYY_HYPHEN}) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
                LocalDate start = LocalDate.parse(startDate, formatter);
                LocalDate end = LocalDate.parse(endDate, formatter);
                return new LocalDate[] {start, end};
            } catch (DateTimeParseException ignored) {
                // tenta próximo padrão
            }
        }
        return new LocalDate[0];
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "***";
        }
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }

    private LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(timeStr, DateTimeFormatter.ofPattern(FMT_HH_MM));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private boolean isValidUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank();
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
