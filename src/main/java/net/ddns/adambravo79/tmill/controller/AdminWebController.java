package net.ddns.adambravo79.tmill.controller;

import static net.ddns.adambravo79.tmill.constant.BotMessages.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fasterxml.jackson.core.JsonProcessingException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.client.AzureTtsClient;
import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.model.AutoResponseOverride;
import net.ddns.adambravo79.tmill.repository.ReleaseNotifiedRepository;
import net.ddns.adambravo79.tmill.service.*;
import net.ddns.adambravo79.tmill.service.cache.FileTranscriptionCacheService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import tools.jackson.databind.ObjectMapper;

@Controller
@RequestMapping("/admin-web")
@RequiredArgsConstructor
@Slf4j
public class AdminWebController {

    private static final long SHOWCASE_CHAT_ID = -5283244164L;
    private static final String COPA_DESABILITADA = "Copa desabilitada.";
    private static final String ERRO = "Erro: ";
    private static final String SUCCESS = "success";
    private static final String ERROR = "error";
    private static final String REDIRECT_ADMIN_WEB = "redirect:/admin-web";

    // Serviços injetados
    private final EasterEggService easterEggService;
    private final DailyDigestService dailyDigestService;
    private final WeeklyReminderService weeklyReminderService;
    private final AutoResponseService autoResponseService;
    private final WorldCupSchedulerService worldCupSchedulerService;
    private final StaticWorldCupService staticWorldCupService;
    private final TelegramFacade telegramFacade;
    private final DailyReleasesService dailyReleasesService;
    private final ReleaseNotifiedRepository releaseNotifiedRepository;
    private final AzureTtsClient azureTtsClient;
    private final PodcastPublisherService podcastPublisherService;
    private final FileTranscriptionCacheService cacheService;
    private final Environment environment;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final TempDirService tempDirService;

    @Value("${worldcup.enabled:false}")
    private boolean worldcupEnabled;

    @Value("${telegram.owner.id:0}")
    private long ownerId;

    @Value("${podcast.publish.chat-id:0}")
    private long publishChatId;

    // Página principal
    @GetMapping
    public String adminPage(Model model) {
        model.addAttribute("worldcupEnabled", worldcupEnabled);
        model.addAttribute("ownerId", ownerId);
        model.addAttribute("publishChatId", publishChatId);
        model.addAttribute(
                "now",
                LocalDateTime.now(ZoneId.of(BRAZIL_ZONE))
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        return "admin";
    }

    // ========================= MENSAGENS E ÁUDIO =========================

    @PostMapping("/fala-t1000")
    @ResponseBody
    public ResponseEntity<String> falaT1000(
            @RequestParam String message,
            @RequestParam(required = false) Long chatId,
            @RequestParam(defaultValue = "HTML") String parseMode) {
        try {
            long targetChatId = (chatId != null) ? chatId : ownerId;
            if (targetChatId == 0) {
                return ResponseEntity.badRequest()
                        .body("❌ Nenhum chatId informado e ownerId não configurado.");
            }
            if ("HTML".equalsIgnoreCase(parseMode)) {
                telegramFacade.enviarMensagemHtml(targetChatId, message);
            } else {
                telegramFacade.enviarMensagem(targetChatId, message);
            }
            return ResponseEntity.ok("✅ Mensagem enviada para o chat " + targetChatId);
        } catch (Exception e) {
            log.error("Erro ao enviar mensagem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("❌ Erro: " + e.getMessage());
        }
    }

    @PostMapping("/fala-t1000-tts")
    @ResponseBody
    public ResponseEntity<String> falaT1000Tts(
            @RequestParam String message, @RequestParam(required = false) Long chatId) {
        try {
            long targetChatId = (chatId != null) ? chatId : ownerId;
            if (targetChatId == 0) {
                return ResponseEntity.badRequest()
                        .body("❌ Nenhum chatId informado e ownerId não configurado.");
            }
            byte[] audio = azureTtsClient.synthesizeFullText(message);
            if (audio == null || audio.length == 0) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("❌ Falha na síntese (áudio vazio).");
            }
            // Salva e envia (código existente)
            Path tempFile = tempDirService.createTempFile("tts_audio_", ".mp3");
            Files.write(tempFile, audio);
            telegramFacade.enviarMidia(
                    targetChatId, tempFile.toAbsolutePath().toString(), "🔊 Áudio sintetizado");
            Files.deleteIfExists(tempFile);
            return ResponseEntity.ok("✅ Áudio enviado para o chat " + targetChatId);
        } catch (Exception e) {
            log.error("Erro no TTS", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("❌ Erro: " + e.getMessage());
        }
    }

    @PostMapping("/test-azure-tts")
    public String testAzureTts(RedirectAttributes redirectAttrs) {
        try {
            String text =
                    "Bem vindos ao espetacular... ah deixa de papo furado. Dadinho é o cara leo,"
                            + " meu nome agora é Zé Pequeno.";
            byte[] audio = azureTtsClient.synthesizeFullText(text);
            if (audio.length > 0) {
                // Envia para o chat de publicação
                if (publishChatId == 0) {
                    redirectAttrs.addFlashAttribute(ERROR, "publishChatId não configurado.");
                    return REDIRECT_ADMIN_WEB;
                }
                Path tempFile = tempDirService.createTempFile("test_azure_", ".mp3");
                Files.write(tempFile, audio);
                telegramFacade.enviarMidia(
                        publishChatId, tempFile.toAbsolutePath().toString(), "Teste Azure TTS");
                Files.deleteIfExists(tempFile);
                redirectAttrs.addFlashAttribute(
                        SUCCESS, "Áudio de teste enviado para " + publishChatId);
            } else {
                redirectAttrs.addFlashAttribute(ERROR, "Falha na síntese (áudio vazio).");
            }
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute(ERROR, ERRO + e.getMessage());
        }
        return REDIRECT_ADMIN_WEB;
    }

    // ========================= COPA DO MUNDO =========================

    @PostMapping("/test-worldcup")
    public String testWorldCup(RedirectAttributes redirectAttrs) {
        if (!worldcupEnabled) {
            redirectAttrs.addFlashAttribute(ERROR, COPA_DESABILITADA);
            return REDIRECT_ADMIN_WEB;
        }
        worldCupSchedulerService.sendManualTest();
        redirectAttrs.addFlashAttribute(
                SUCCESS, "Envio manual da Copa disparado (todos os chats).");
        return REDIRECT_ADMIN_WEB;
    }

    @PostMapping("/test-worldcup-showcase")
    public String testWorldCupShowcase(
            @RequestParam(required = false) Long chatId, RedirectAttributes redirectAttrs) {
        if (!worldcupEnabled) {
            redirectAttrs.addFlashAttribute(ERROR, COPA_DESABILITADA);
            return REDIRECT_ADMIN_WEB;
        }
        long targetChatId = (chatId != null) ? chatId : SHOWCASE_CHAT_ID;
        worldCupSchedulerService.sendManualTestToChat(targetChatId);
        redirectAttrs.addFlashAttribute(
                SUCCESS, "Envio manual da Copa enviado para o chat " + targetChatId);
        return REDIRECT_ADMIN_WEB;
    }

    @PostMapping("/test-worldcup-noon")
    public String testWorldCupNoon(RedirectAttributes redirectAttrs) {
        if (!worldcupEnabled) {
            redirectAttrs.addFlashAttribute(ERROR, COPA_DESABILITADA);
            return REDIRECT_ADMIN_WEB;
        }
        worldCupSchedulerService.sendNoonMatches();
        redirectAttrs.addFlashAttribute(SUCCESS, "Envio de jogos do meio-dia executado.");
        return REDIRECT_ADMIN_WEB;
    }

    @PostMapping("/test-worldcup-noon-showcase")
    public String testWorldCupNoonShowcase(
            @RequestParam(required = false) Long chatId, RedirectAttributes redirectAttrs) {
        if (!worldcupEnabled) {
            redirectAttrs.addFlashAttribute(ERROR, COPA_DESABILITADA);
            return REDIRECT_ADMIN_WEB;
        }
        long targetChatId = (chatId != null) ? chatId : SHOWCASE_CHAT_ID;
        worldCupSchedulerService.sendNoonMatchesToChat(targetChatId);
        redirectAttrs.addFlashAttribute(
                SUCCESS, "Envio do meio-dia da Copa enviado para o chat " + targetChatId);
        return REDIRECT_ADMIN_WEB;
    }

    @PostMapping("/test-worldcup-evening")
    public String testWorldCupEvening(RedirectAttributes redirectAttrs) {
        if (!worldcupEnabled) {
            redirectAttrs.addFlashAttribute(ERROR, COPA_DESABILITADA);
            return REDIRECT_ADMIN_WEB;
        }
        worldCupSchedulerService.sendEveningMatches();
        redirectAttrs.addFlashAttribute(SUCCESS, "Envio de jogos da noite executado.");
        return REDIRECT_ADMIN_WEB;
    }

    @PostMapping("/test-worldcup-evening-showcase")
    public String testWorldCupEveningShowcase(
            @RequestParam(required = false) Long chatId, RedirectAttributes redirectAttrs) {
        if (!worldcupEnabled) {
            redirectAttrs.addFlashAttribute(ERROR, COPA_DESABILITADA);
            return REDIRECT_ADMIN_WEB;
        }
        long targetChatId = (chatId != null) ? chatId : SHOWCASE_CHAT_ID;
        worldCupSchedulerService.sendEveningMatchesToChat(targetChatId);
        redirectAttrs.addFlashAttribute(
                SUCCESS, "Envio da noite da Copa enviado para o chat " + targetChatId);
        return REDIRECT_ADMIN_WEB;
    }

    @PostMapping("/reload-worldcup")
    public String reloadWorldCup(RedirectAttributes redirectAttrs) {
        staticWorldCupService.reload();
        redirectAttrs.addFlashAttribute(SUCCESS, "Dados da Copa recarregados do arquivo JSON.");
        return REDIRECT_ADMIN_WEB;
    }

    @PostMapping("/reload-worldcup-showcase")
    public String reloadWorldCupShowcase(
            @RequestParam(required = false) Long chatId, RedirectAttributes redirectAttrs) {
        long targetChatId = (chatId != null) ? chatId : SHOWCASE_CHAT_ID;
        staticWorldCupService.reload();
        String msg =
                "✅ Dados da Copa recarregados do arquivo JSON às "
                        + LocalDateTime.now(ZoneId.of(BRAZIL_ZONE))
                                .format(DateTimeFormatter.ofPattern(FMT_HH_MM_SS));
        try {
            telegramFacade.enviarMensagemHtml(targetChatId, msg);
        } catch (HttpClientErrorException | ResourceAccessException e) {
            log.warn("Erro ao enviar notificação de reload para chat {}", targetChatId, e);
        }
        redirectAttrs.addFlashAttribute(
                SUCCESS, "Dados recarregados e notificação enviada para " + targetChatId);
        return REDIRECT_ADMIN_WEB;
    }

    @PostMapping("/test-worldcup-results")
    public String testWorldCupResults(
            @RequestParam(defaultValue = "ontem") String dateParam,
            @RequestParam(required = false) Long chatId,
            RedirectAttributes redirectAttrs) {
        if (!worldcupEnabled) {
            redirectAttrs.addFlashAttribute(ERROR, COPA_DESABILITADA);
            return REDIRECT_ADMIN_WEB;
        }
        LocalDate date = parseDateParam(dateParam);
        if (date == null) {
            redirectAttrs.addFlashAttribute(ERROR, "Data inválida.");
            return REDIRECT_ADMIN_WEB;
        }
        long targetChatId = (chatId != null) ? chatId : SHOWCASE_CHAT_ID;
        worldCupSchedulerService.sendResultsToChat(targetChatId, date);
        redirectAttrs.addFlashAttribute(SUCCESS, "Resultados enviados para o chat " + targetChatId);
        return REDIRECT_ADMIN_WEB;
    }

    // ========================= DIGEST E RELEASES =========================

    @PostMapping("/test-morning-digest")
    public String testMorningDigest(RedirectAttributes redirectAttrs) {
        dailyDigestService.generateMorningDigest();
        redirectAttrs.addFlashAttribute(SUCCESS, "Resumo da manhã disparado.");
        return REDIRECT_ADMIN_WEB;
    }

    @PostMapping("/test-evening-digest")
    public String testEveningDigest(RedirectAttributes redirectAttrs) {
        dailyDigestService.generateEveningDigest();
        redirectAttrs.addFlashAttribute(SUCCESS, "Resumo da noite disparado.");
        return REDIRECT_ADMIN_WEB;
    }

    @PostMapping("/custom-digest")
    public String customDigest(
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam(required = false) Long chatId,
            RedirectAttributes redirectAttrs) {
        try {
            LocalDate startDate = LocalDate.parse(start);
            LocalDate endDate = LocalDate.parse(end);
            ZoneId zone = ZoneId.of(BRAZIL_ZONE);
            LocalDateTime from = startDate.atStartOfDay(zone).toLocalDateTime();
            LocalDateTime to = endDate.atTime(23, 59, 59);
            dailyDigestService.generateDigestCustom(from, to, chatId);
            redirectAttrs.addFlashAttribute(
                    SUCCESS, "Digest personalizado gerado para " + start + " até " + end);
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute(ERROR, ERRO + e.getMessage());
        }
        return REDIRECT_ADMIN_WEB;
    }

    @PostMapping("/test-daily-releases")
    public String testDailyReleases(RedirectAttributes redirectAttrs) {
        dailyReleasesService.sendHourlyReleases();
        redirectAttrs.addFlashAttribute(SUCCESS, "Verificação horária de lançamentos executada.");
        return REDIRECT_ADMIN_WEB;
    }

    @PostMapping("/test-weekly-digest")
    public String testWeeklyDigest(RedirectAttributes redirectAttrs) {
        dailyReleasesService.sendWeeklyDigest();
        redirectAttrs.addFlashAttribute(SUCCESS, "Giro semanal executado.");
        return REDIRECT_ADMIN_WEB;
    }

    // ========================= LEMBRETES =========================

    @PostMapping("/test-weekly-reminder")
    public String testWeeklyReminder(RedirectAttributes redirectAttrs) {
        weeklyReminderService.sendWednesdayReminder();
        redirectAttrs.addFlashAttribute(SUCCESS, "Lembrete semanal disparado.");
        return REDIRECT_ADMIN_WEB;
    }

    @PostMapping("/test-weekly-reminder-showcase")
    public String testWeeklyReminderShowcase(
            @RequestParam(required = false) Long chatId, RedirectAttributes redirectAttrs) {
        long targetChatId = (chatId != null) ? chatId : SHOWCASE_CHAT_ID;
        weeklyReminderService.sendReminderToChat(targetChatId);
        redirectAttrs.addFlashAttribute(
                SUCCESS, "Lembrete semanal enviado para o chat " + targetChatId);
        return REDIRECT_ADMIN_WEB;
    }

    // ========================= AUTO-RESPONSE =========================

    @PostMapping("/test-auto-response")
    @ResponseBody
    public ResponseEntity<String> testAutoResponse(
            @RequestParam(required = false) Long userId,
            @RequestParam String message,
            @RequestParam(required = false) Long chatId,
            @RequestParam(required = false) String time) {
        try {
            long targetChatId = (chatId != null) ? chatId : SHOWCASE_CHAT_ID;
            LocalTime simulatedTime = parseTime(time);
            Optional<AutoResponseOverride> responseOpt =
                    autoResponseService.getResponseRule(userId, message, simulatedTime);
            if (responseOpt.isEmpty()) {
                return ResponseEntity.ok(
                        "⚠️ Nenhuma resposta automática encontrada para essa mensagem.");
            }
            AutoResponseOverride response = responseOpt.get();
            String finalMsg =
                    "🧪 *Teste de Auto-Response*\n\n"
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
            if (response.animation() != null
                    && !response.animation().isBlank()
                    && isValidUrl(response.animation())) {
                telegramFacade.enviarMidia(targetChatId, response.animation(), finalMsg);
            } else {
                telegramFacade.enviarMensagemHtml(targetChatId, finalMsg);
            }
            return ResponseEntity.ok("✅ Resposta automática enviada para o chat " + targetChatId);
        } catch (Exception e) {
            log.error("Erro no auto-response test", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("❌ Erro: " + e.getMessage());
        }
    }

    @GetMapping("/debug-auto-response")
    @ResponseBody
    public Map<String, Object> debugAutoResponse(
            @RequestParam(required = false) Long userId,
            @RequestParam String message,
            @RequestParam(required = false) String time) {
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
        return result;
    }

    @GetMapping("/auto-response-rules")
    @ResponseBody
    public Map<String, Object> listAutoResponseRules() {
        return Map.of(
                "totalRules", autoResponseService.getRulesCount(),
                "rules", autoResponseService.getRulesSummary());
    }

    // ========================= ADMINISTRAÇÃO (LIMPEZA E RECARREGAMENTO) =========================

    @PostMapping("/clear-releases")
    public String clearReleases(RedirectAttributes redirectAttrs) {
        try {
            releaseNotifiedRepository.clearAll();
            redirectAttrs.addFlashAttribute(SUCCESS, "Tabela de lançamentos limpa.");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute(ERROR, "Erro ao limpar: " + e.getMessage());
        }
        return REDIRECT_ADMIN_WEB;
    }

    @PostMapping("/clear-all-data")
    public String clearAllData(RedirectAttributes redirectAttrs) {
        try {
            int deleted = releaseNotifiedRepository.deleteAll();
            redirectAttrs.addFlashAttribute(
                    SUCCESS, "Dados removidos: " + deleted + " lançamentos deletados.");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute(
                    ERROR, "Erro ao limpar todos os dados: " + e.getMessage());
        }
        return REDIRECT_ADMIN_WEB;
    }

    @PostMapping("/reload-auto-responses")
    public String reloadAutoResponses(RedirectAttributes redirectAttrs) {
        autoResponseService.reload();
        redirectAttrs.addFlashAttribute(SUCCESS, "Auto-respostas recarregadas.");
        return REDIRECT_ADMIN_WEB;
    }

    @PostMapping("/reload-easter-eggs")
    public String reloadEasterEggs(RedirectAttributes redirectAttrs) {
        easterEggService.reload();
        redirectAttrs.addFlashAttribute(SUCCESS, "Easter eggs recarregados.");
        return REDIRECT_ADMIN_WEB;
    }

    // ========================= PODCAST =========================

    @PostMapping("/test-podcast")
    public String testPodcast(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) Long chatId,
            RedirectAttributes redirectAttrs) {
        try {
            LocalDate today = LocalDate.now(ZoneId.of(BRAZIL_ZONE));
            LocalDate endDate = (end != null && !end.isBlank()) ? LocalDate.parse(end) : today;
            LocalDate startDate =
                    (start != null && !start.isBlank())
                            ? LocalDate.parse(start)
                            : today.minusDays(7);
            if (startDate.isAfter(endDate)) {
                redirectAttrs.addFlashAttribute(
                        ERROR, "Data de início não pode ser posterior à data de fim.");
                return REDIRECT_ADMIN_WEB;
            }
            long targetChatId = (chatId != null) ? chatId : SHOWCASE_CHAT_ID;
            // Executa assíncrono (como no original)
            new Thread(
                            () -> {
                                try {
                                    podcastPublisherService.generateAndSendPodcast(
                                            startDate, endDate, targetChatId);
                                } catch (Exception e) {
                                    log.error("Erro ao gerar podcast", e);
                                }
                            })
                    .start();
            redirectAttrs.addFlashAttribute(
                    SUCCESS,
                    "Podcast agendado para o período "
                            + startDate
                            + " a "
                            + endDate
                            + ". Você receberá em breve no chat "
                            + targetChatId);
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute(ERROR, ERRO + e.getMessage());
        }
        return REDIRECT_ADMIN_WEB;
    }

    // ========================= MONITORAMENTO =========================

    @GetMapping("/cache-stats")
    @ResponseBody
    public Map<String, Long> cacheStats() {
        return cacheService.getStats();
    }

    @GetMapping("/properties")
    @ResponseBody
    public Map<String, Object> properties() {
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
        return props;
    }

    @GetMapping("/config-files")
    @ResponseBody
    public Map<String, Object> configFiles() {
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
        return result;
    }

    // ========================= MÉTODOS AUXILIARES (copiados do AdminController)
    // =========================

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

    private String maskToken(String token) {
        if (token == null || token.length() < 8) return "***";
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
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
        parsed = tryParseFallback(param, "dd/MM", "dd-MM");
        return parsed;
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
            }
        }
        return null;
    }

    private LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) return null;
        try {
            return LocalTime.parse(timeStr, DateTimeFormatter.ofPattern(FMT_HH_MM));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private boolean isValidUrl(String url) {
        if (url == null || url.isBlank()) return false;
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
