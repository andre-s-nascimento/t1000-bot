/* (c) 2026 | 19/05/2026 */
package net.ddns.adambravo79.tmill.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.service.AutoResponseService;
import net.ddns.adambravo79.tmill.service.DailyDigestService;
import net.ddns.adambravo79.tmill.service.EasterEggService;
import net.ddns.adambravo79.tmill.service.StaticWorldCupService;
import net.ddns.adambravo79.tmill.service.WeeklyReminderService;
import net.ddns.adambravo79.tmill.service.WorldCupSchedulerService;
import net.ddns.adambravo79.tmill.service.cache.FileTranscriptionCacheService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private static final String SERVICO_DA_COPA_DESATIVADO = "⛔ Serviço da Copa desativado.";
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

    @Value("${worldcup.enabled:false}")
    private boolean worldcupEnabled;

    private static final long SHOWCASE_CHAT_ID = -5283244164L; // -1003265590455L;

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
    public ResponseEntity<String> testWeeklyReminderShowcase() {
        weeklyReminderService.sendReminderToChat(SHOWCASE_CHAT_ID);
        return ResponseEntity.ok(
                "Lembrete semanal enviado para o grupo showcase (" + SHOWCASE_CHAT_ID + ")");
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

        try {
            LocalDate[] dates = parseDateRange(startDate, endDate);
            if (dates.length == 0) {
                return ResponseEntity.badRequest()
                        .body(
                                "Formato inválido. Use 'yyyy-MM-dd' ou 'dd-MM-yyyy'. Ex: 2026-05-07"
                                        + " ou 07-05-2026");
            }

            ZoneId zone = ZoneId.of("America/Sao_Paulo");
            LocalDateTime from = dates[0].atStartOfDay(zone).toLocalDateTime();
            LocalDateTime to = dates[1].atTime(23, 59, 59);

            dailyDigestService.generateDigestCustom(from, to, chatId);

            String message =
                    "Resumo personalizado gerado para período: " + startDate + " até " + endDate;
            if (chatId != null) {
                message += " (enviado apenas para o chat " + chatId + ")";
            } else {
                message += " (enviado para todos os chats configurados)";
            }
            return ResponseEntity.ok(message);

        } catch (Exception e) {
            log.error("Erro ao processar datas", e);
            return ResponseEntity.internalServerError().body("Erro interno: " + e.getMessage());
        }
    }

    @PostMapping("/test-worldcup-noon")
    public ResponseEntity<String> testWorldCupNoon() {
        if (worldCupSchedulerService != null) {
            worldCupSchedulerService.sendNoonMatches();
            return ResponseEntity.ok("Envio de jogos do meio-dia executado (simulado)");
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Serviço de Copa não disponível");
    }

    @PostMapping("/test-worldcup-evening")
    public ResponseEntity<String> testWorldCupEvening() {
        if (worldCupSchedulerService != null) {
            worldCupSchedulerService.sendEveningMatches();
            return ResponseEntity.ok("Envio de jogos da noite executado (simulado)");
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Serviço de Copa não disponível");
    }

    @PostMapping("/test-worldcup")
    public ResponseEntity<String> testWorldCup() {
        if (!worldcupEnabled) {
            return ResponseEntity.ok(SERVICO_DA_COPA_DESATIVADO);
        }
        worldCupSchedulerService.sendManualTest();
        return ResponseEntity.ok("✅ Envio manual disparado! Verifique os logs.");
    }

    // Teste da Copa – lista de jogos do dia (showcase)
    @PostMapping("/test-worldcup-showcase")
    public ResponseEntity<String> testWorldCupShowcase() {
        if (!worldcupEnabled) {
            return ResponseEntity.ok(SERVICO_DA_COPA_DESATIVADO);
        }
        worldCupSchedulerService.sendManualTestToChat(SHOWCASE_CHAT_ID);
        return ResponseEntity.ok("✅ Teste manual da Copa enviado para o showcase.");
    }

    // Teste da Copa – meio-dia (showcase)
    @PostMapping("/test-worldcup-noon-showcase")
    public ResponseEntity<String> testWorldCupNoonShowcase() {
        if (!worldcupEnabled) {
            return ResponseEntity.ok(SERVICO_DA_COPA_DESATIVADO);
        }
        worldCupSchedulerService.sendNoonMatchesToChat(SHOWCASE_CHAT_ID);
        return ResponseEntity.ok("✅ Envio do meio-dia da Copa enviado para o showcase.");
    }

    // Teste da Copa – noite (showcase)
    @PostMapping("/test-worldcup-evening-showcase")
    public ResponseEntity<String> testWorldCupEveningShowcase() {
        if (!worldcupEnabled) {
            return ResponseEntity.ok(SERVICO_DA_COPA_DESATIVADO);
        }
        worldCupSchedulerService.sendEveningMatchesToChat(SHOWCASE_CHAT_ID);
        return ResponseEntity.ok("✅ Envio da noite da Copa enviado para o showcase.");
    }

    // Recarregar dados da Copa e enviar confirmação para o showcase
    @PostMapping("/reload-worldcup-showcase")
    public ResponseEntity<String> reloadWorldCupShowcase() {
        staticWorldCupService.reload();
        String msg =
                "✅ Dados da Copa recarregados do arquivo JSON às "
                        + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        telegramFacade.enviarMensagemHtml(SHOWCASE_CHAT_ID, msg);
        return ResponseEntity.ok(msg + " (enviado para o showcase)");
    }

    @PostMapping("/test-worldcup-results-showcase")
    public ResponseEntity<String> testWorldCupResultsShowcase(
            @RequestParam(defaultValue = "ontem") String dateParam) {
        if (!worldcupEnabled) {
            return ResponseEntity.ok(SERVICO_DA_COPA_DESATIVADO);
        }
        LocalDate date = parseDateParam(dateParam);
        if (date == null) {
            return ResponseEntity.badRequest()
                    .body("❓ Data inválida. Use 'ontem', 'hoje' ou AAAA-MM-DD.");
        }
        worldCupSchedulerService.sendResultsToChat(SHOWCASE_CHAT_ID, date);
        return ResponseEntity.ok("✅ Resultados enviados para o showcase (data: " + date + ")");
    }

    private LocalDate parseDateParam(String param) {
        if (param == null || param.isBlank()) return null;
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        if (param.equalsIgnoreCase("ontem")) {
            return today.minusDays(1);
        } else if (param.equalsIgnoreCase("hoje")) {
            return today;
        } else {
            try {
                return LocalDate.parse(param);
            } catch (DateTimeParseException e) {
                return null;
            }
        }
    }

    private LocalDate[] parseDateRange(String startDate, String endDate) {
        for (String pattern : new String[] {"yyyy-MM-dd", "dd-MM-yyyy"}) {
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

    @PostMapping("/reload-worldcup")
    public ResponseEntity<String> reloadWorldCup() {
        staticWorldCupService.reload();
        return ResponseEntity.ok("Dados da Copa recarregados do arquivo JSON");
    }

    @GetMapping("/properties")
    public ResponseEntity<Map<String, Object>> getProperties() {
        Map<String, Object> props = new LinkedHashMap<>();

        // Propriedades gerais
        props.put("spring.application.name", environment.getProperty("spring.application.name"));
        props.put("server.port", environment.getProperty("server.port"));
        props.put(
                "spring.threads.virtual.enabled",
                environment.getProperty("spring.threads.virtual.enabled"));

        // Telegram
        props.put("telegram.bot.username", environment.getProperty("telegram.bot.username"));
        props.put(
                "telegram.bot.polling.enabled",
                environment.getProperty("telegram.bot.polling.enabled"));
        props.put(
                "telegram.bot.polling.timeout",
                environment.getProperty("telegram.bot.polling.timeout"));
        props.put("telegram.message.limit", environment.getProperty("telegram.message.limit"));
        props.put("telegram.owner.id", environment.getProperty("telegram.owner.id"));

        // Tokens (mascarados)
        props.put("telegram.bot.token", maskToken(environment.getProperty("telegram.bot.token")));
        props.put("groq.api.key", maskToken(environment.getProperty("groq.api.key")));
        props.put("tmdb.token", maskToken(environment.getProperty("tmdb.token")));

        // Modelos Groq
        props.put("groq.model.transcription", environment.getProperty("groq.model.transcription"));
        props.put("groq.model.refinement", environment.getProperty("groq.model.refinement"));
        props.put("groq.model.digest", environment.getProperty("groq.model.digest"));

        // Cache
        props.put(
                "cache.transcription.enabled",
                environment.getProperty("cache.transcription.enabled"));
        props.put(
                "cache.transcription.ttl-seconds",
                environment.getProperty("cache.transcription.ttl-seconds"));

        // Digest
        props.put("digest.enabled", environment.getProperty("digest.enabled"));
        props.put("digest.chat-ids", environment.getProperty("digest.chat-ids"));

        // World Cup
        props.put("worldcup.enabled", environment.getProperty("worldcup.enabled"));
        props.put("worldcup.data.file", environment.getProperty("worldcup.data.file"));
        props.put("worldcup.update.enabled", environment.getProperty("worldcup.update.enabled"));

        // Auto-response
        props.put("auto.response.enabled", environment.getProperty("auto.response.enabled"));
        props.put("auto.response.file", environment.getProperty("auto.response.file"));

        // Easter egg
        props.put("easter-egg.file", environment.getProperty("easter-egg.file"));

        // Weekly reminder
        props.put(
                "weekly.reminder.media-file",
                environment.getProperty("weekly.reminder.media-file"));

        // Áudio
        props.put(
                "t1000.features.transcription-enabled",
                environment.getProperty("t1000.features.transcription-enabled"));
        props.put("t1000.audio.max-size-mb", environment.getProperty("t1000.audio.max-size-mb"));

        // Grupos autorizados
        props.put("bot.allowed-chats", environment.getProperty("bot.allowed-chats"));

        return ResponseEntity.ok(props);
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) return "***";
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }

    // Endpoint GET /admin/config-files
    @GetMapping("/config-files")
    public ResponseEntity<Map<String, Object>> getConfigFiles() {
        Map<String, Object> result = new LinkedHashMap<>();

        // Lista de arquivos para carregar
        String[] files = {"easter-eggs.json", "auto-responses.json", "worldcup2026.json"};

        for (String fileName : files) {
            try {
                // Tenta carregar do classpath primeiro
                Resource resource = resourceLoader.getResource("classpath:" + fileName);
                if (!resource.exists()) {
                    // Se não existir no classpath, tenta do sistema de arquivos
                    resource = resourceLoader.getResource("file:/app/config/" + fileName);
                    if (!resource.exists()) {
                        result.put(fileName, "❌ Arquivo não encontrado");
                        continue;
                    }
                }

                // Lê o conteúdo como JSON
                Object content = objectMapper.readValue(resource.getInputStream(), Object.class);
                result.put(fileName, content);

            } catch (Exception e) {
                log.error("Erro ao carregar arquivo: {}", fileName, e);
                result.put(fileName, "❌ Erro ao ler: " + e.getMessage());
            }
        }

        return ResponseEntity.ok(result);
    }
}
