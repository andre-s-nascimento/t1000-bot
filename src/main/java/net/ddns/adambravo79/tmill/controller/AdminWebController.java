/* (c) 2026 | 26/09/2026 */
package net.ddns.adambravo79.tmill.controller;

import static net.ddns.adambravo79.tmill.constant.BotMessages.BRAZIL_ZONE;
import static net.ddns.adambravo79.tmill.constant.BotMessages.FMT_HH_MM_SS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fasterxml.jackson.core.JsonProcessingException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.client.AzureTtsClient;
import net.ddns.adambravo79.tmill.dto.MigrationResult;
import net.ddns.adambravo79.tmill.model.AutoResponseOverride;
import net.ddns.adambravo79.tmill.repository.BirthdayRepository;
import net.ddns.adambravo79.tmill.repository.ReleaseNotifiedRepository;
import net.ddns.adambravo79.tmill.service.AutoResponseService;
import net.ddns.adambravo79.tmill.service.BirthdayService;
import net.ddns.adambravo79.tmill.service.DailyDigestService;
import net.ddns.adambravo79.tmill.service.DailyReleasesService;
import net.ddns.adambravo79.tmill.service.EasterEggService;
import net.ddns.adambravo79.tmill.service.MigrationService;
import net.ddns.adambravo79.tmill.service.PodcastPublisherService;
import net.ddns.adambravo79.tmill.service.StaticWorldCupService;
import net.ddns.adambravo79.tmill.service.TempDirService;
import net.ddns.adambravo79.tmill.service.WeeklyReminderService;
import net.ddns.adambravo79.tmill.service.WorldCupSchedulerService;
import net.ddns.adambravo79.tmill.service.cache.FileTranscriptionCacheService;
import net.ddns.adambravo79.tmill.service.feature.FeatureFlagAdminService;
import net.ddns.adambravo79.tmill.service.prompt.PromptRegistryService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.util.LogSanitizer;
import tools.jackson.databind.ObjectMapper;

/**
 * Controller do painel administrativo (Thymeleaf).
 *
 * <p>Complementa o {@link AdminController} (API REST) expondo as mesmas funcionalidades para uso
 * via interface web.
 *
 * <p>Diferenças em relação ao REST:
 *
 * <ul>
 *   <li>Retorna strings de redirect + {@link RedirectAttributes} em vez de {@link ResponseEntity}.
 *   <li>Erros 5xx retornam mensagem genérica (nunca expõem stacktrace).
 *   <li>CSRF ignorado via {@code SecurityConfig} (não precisa de hidden nos forms).
 *   <li>Processamento assíncrono usa {@link CompletableFuture#runAsync} (virtual threads).
 * </ul>
 *
 * <p>Utilitários compartilhados com o {@link AdminController} ficam em {@link AdminUtils}.
 */
@Controller
@RequestMapping("/admin-web")
@RequiredArgsConstructor
@Slf4j
public class AdminWebController {

    // =========================================================================
    // CONSTANTES
    // =========================================================================

    private static final String PARAMETRO_MESSAGE_OBRIGATORIO =
            "❌ Parâmetro 'message' é obrigatório.";

    private static final String MSG_ERRO_INTERNO = "Erro interno. Verifique os logs do servidor.";

    private static final String COPA_DESABILITADA = "Copa desabilitada.";
    private static final String SUCCESS = "success";
    private static final String ERROR = "error";
    private static final String REDIRECT_ADMIN_WEB = "redirect:/admin-web";

    // =========================================================================
    // DEPENDÊNCIAS
    // =========================================================================

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
    private final BirthdayService birthdayService;
    private final BirthdayRepository birthdayRepository;
    private final MigrationService migrationService;
    private final FeatureFlagAdminService featureFlagAdminService;
    private final PromptRegistryService promptRegistryService;

    // =========================================================================
    // @Value
    // =========================================================================

    @Value("${worldcup.enabled:false}")
    private boolean worldcupEnabled;

    @Value("${telegram.owner.id:0}")
    private long ownerId;

    @Value("${podcast.publish.chat-id:0}")
    private long publishChatId;

    @Value("${bot.allowed-chats:}")
    private String botAllowedChats;

    @Value("${digest.chat-ids:}")
    private String digestChatIdsStr;

    @Value("${migration.sqlite.path:./data/t1000.db}")
    private String migrationSqlitePath;

    // =========================================================================
    // PÁGINA PRINCIPAL
    // =========================================================================

    @GetMapping
    public String adminPage(Model model) {
        model.addAttribute("worldcupEnabled", worldcupEnabled);
        model.addAttribute("ownerId", ownerId);
        model.addAttribute("publishChatId", publishChatId);
        model.addAttribute(
                "now",
                LocalDateTime.now(ZoneId.of(BRAZIL_ZONE))
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));

        // Unifica botAllowedChats + digestChatIds em um Set para a UI
        Set<String> allChats = new LinkedHashSet<>();
        if (botAllowedChats != null && !botAllowedChats.isBlank()) {
            Arrays.stream(botAllowedChats.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(allChats::add);
        }
        if (digestChatIdsStr != null && !digestChatIdsStr.isBlank()) {
            Arrays.stream(digestChatIdsStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(allChats::add);
        }
        model.addAttribute("availableChatIds", allChats);

        return "admin";
    }

    // =========================================================================
    // ADMINISTRAÇÃO (LIMPEZA E RECARREGAMENTO)
    // =========================================================================

    @PostMapping("/reload-prompts")
    public String reloadPrompts(RedirectAttributes redirectAttrs) {
        try {
            promptRegistryService.reload();
            log.info("🌐 Prompts e personas recarregados via painel web admin.");
            redirectAttrs.addFlashAttribute(
                    SUCCESS, "Prompts e personas recarregados com sucesso.");
        } catch (Exception e) {
            log.error("Erro ao recarregar prompts e personas", e);
            redirectAttrs.addFlashAttribute(ERROR, MSG_ERRO_INTERNO);
        }
        return REDIRECT_ADMIN_WEB;
    }

    // =========================================================================
    // MENSAGENS E ÁUDIO
    // =========================================================================

    @PostMapping("/fala-t1000")
    @ResponseBody
    public ResponseEntity<String> falaT1000(
            @RequestParam("message") String message,
            @RequestParam(value = "chatId", required = false) Long chatId,
            @RequestParam(value = "parseMode", defaultValue = "HTML") String parseMode) {

        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(PARAMETRO_MESSAGE_OBRIGATORIO);
        }

        long targetChatId = resolveTargetChatId(chatId);
        if (targetChatId == 0) {
            return ResponseEntity.badRequest()
                    .body("❌ Nenhum chatId informado e nenhum chat padrão configurado.");
        }

        log.info(
                "📤 [web] Enviando mensagem para chat {}: {}",
                targetChatId,
                LogSanitizer.sanitizeMessageText(message));

        try {
            if ("HTML".equalsIgnoreCase(parseMode)) {
                telegramFacade.enviarMensagemHtml(targetChatId, message);
            } else {
                telegramFacade.enviarMensagem(targetChatId, message);
            }
            return ResponseEntity.ok("✅ Mensagem enviada para o chat " + targetChatId);
        } catch (Exception e) {
            log.error("❌ Erro ao enviar mensagem para chat {}", targetChatId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("❌ " + MSG_ERRO_INTERNO);
        }
    }

    @PostMapping("/fala-t1000-tts")
    @ResponseBody
    public ResponseEntity<String> falaT1000Tts(
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "chatId", required = false) Long chatId) {

        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(PARAMETRO_MESSAGE_OBRIGATORIO);
        }

        long targetChatId = resolveTargetChatId(chatId);
        if (targetChatId == 0) {
            return ResponseEntity.badRequest()
                    .body("❌ Nenhum chatId informado e nenhum chat padrão configurado.");
        }

        log.info(
                "🎤 [web] Sintetizando áudio para chat {}: {}",
                targetChatId,
                LogSanitizer.sanitizeMessageText(message));

        byte[] audio;
        try {
            audio = azureTtsClient.synthesizeFullText(message);
        } catch (Exception e) {
            log.error("❌ Erro na síntese TTS", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("❌ " + MSG_ERRO_INTERNO);
        }

        if (audio == null || audio.length == 0) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("❌ Falha na síntese (áudio vazio).");
        }

        Path tempFile = null;
        try {
            String fileName =
                    String.format("Cronicas-do-T1000-Audio-%d.mp3", System.currentTimeMillis());
            tempFile = tempDirService.createTempFile("tts_audio_", ".mp3");
            Path finalFile = tempFile.resolveSibling(fileName);
            Files.write(tempFile, audio);
            Files.move(tempFile, finalFile);

            telegramFacade.enviarMidia(
                    targetChatId,
                    finalFile.toAbsolutePath().toString(),
                    "🔊 Áudios para a futura Skynet");

            Files.deleteIfExists(finalFile);
            return ResponseEntity.ok("✅ Áudio enviado com sucesso para o chat " + targetChatId);

        } catch (Exception e) {
            log.error("❌ Erro ao salvar ou enviar áudio", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("❌ " + MSG_ERRO_INTERNO);
        } finally {
            deleteSilently(tempFile);
        }
    }

    /**
     * Testa a síntese de voz com uma mensagem fixa, enviando para o chat de publicação do podcast.
     */
    @PostMapping("/test-azure-tts")
    public String testAzureTts(RedirectAttributes redirectAttrs) {
        if (publishChatId == 0) {
            redirectAttrs.addFlashAttribute(ERROR, "publishChatId não configurado.");
            return REDIRECT_ADMIN_WEB;
        }

        try {
            String text =
                    "Bem vindos ao espetacular... ah deixa de papo furado. Dadinho é o cara leo,"
                            + " meu nome agora é Zé Pequeno.";
            byte[] audio = azureTtsClient.synthesizeFullText(text);

            if (audio == null || audio.length == 0) {
                redirectAttrs.addFlashAttribute(ERROR, "Falha na síntese (áudio vazio).");
                return REDIRECT_ADMIN_WEB;
            }

            Path tempFile = tempDirService.createTempFile("test_azure_", ".mp3");
            try {
                Files.write(tempFile, audio);
                telegramFacade.enviarMidia(
                        publishChatId, tempFile.toAbsolutePath().toString(), "Teste Azure TTS");
                redirectAttrs.addFlashAttribute(
                        SUCCESS, "Áudio de teste enviado para " + publishChatId);
            } finally {
                deleteSilently(tempFile);
            }
        } catch (Exception e) {
            log.error("❌ Erro no teste Azure TTS", e);
            redirectAttrs.addFlashAttribute(ERROR, MSG_ERRO_INTERNO);
        }
        return REDIRECT_ADMIN_WEB;
    }

    // =========================================================================
    // COPA DO MUNDO
    // =========================================================================

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
            @RequestParam(value = "chatId", required = false) Long chatId,
            RedirectAttributes redirectAttrs) {
        if (!worldcupEnabled) {
            redirectAttrs.addFlashAttribute(ERROR, COPA_DESABILITADA);
            return REDIRECT_ADMIN_WEB;
        }
        long targetChatId = resolveTargetChatId(chatId);
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
            @RequestParam(value = "chatId", required = false) Long chatId,
            RedirectAttributes redirectAttrs) {
        if (!worldcupEnabled) {
            redirectAttrs.addFlashAttribute(ERROR, COPA_DESABILITADA);
            return REDIRECT_ADMIN_WEB;
        }
        long targetChatId = resolveTargetChatId(chatId);
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
            @RequestParam(value = "chatId", required = false) Long chatId,
            RedirectAttributes redirectAttrs) {
        if (!worldcupEnabled) {
            redirectAttrs.addFlashAttribute(ERROR, COPA_DESABILITADA);
            return REDIRECT_ADMIN_WEB;
        }
        long targetChatId = resolveTargetChatId(chatId);
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
            @RequestParam(value = "chatId", required = false) Long chatId,
            RedirectAttributes redirectAttrs) {
        long targetChatId = resolveTargetChatId(chatId);
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
            @RequestParam(value = "dateParam", defaultValue = "ontem") String dateParam,
            @RequestParam(value = "chatId", required = false) Long chatId,
            RedirectAttributes redirectAttrs) {
        if (!worldcupEnabled) {
            redirectAttrs.addFlashAttribute(ERROR, COPA_DESABILITADA);
            return REDIRECT_ADMIN_WEB;
        }
        LocalDate date = AdminUtils.parseDateParam(dateParam);
        if (date == null) {
            redirectAttrs.addFlashAttribute(ERROR, "Data inválida.");
            return REDIRECT_ADMIN_WEB;
        }
        long targetChatId = resolveTargetChatId(chatId);
        worldCupSchedulerService.sendResultsToChat(targetChatId, date);
        redirectAttrs.addFlashAttribute(SUCCESS, "Resultados enviados para o chat " + targetChatId);
        return REDIRECT_ADMIN_WEB;
    }

    // =========================================================================
    // DIGEST E RELEASES
    // =========================================================================

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
            @RequestParam(value = "start") String start,
            @RequestParam(value = "end") String end,
            @RequestParam(value = "chatId", required = false) Long chatId,
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
        } catch (DateTimeParseException e) {
            redirectAttrs.addFlashAttribute(ERROR, "Formato de data inválido. Use yyyy-MM-dd.");
        } catch (Exception e) {
            log.error("Erro ao gerar digest customizado", e);
            redirectAttrs.addFlashAttribute(ERROR, MSG_ERRO_INTERNO);
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

    // =========================================================================
    // LEMBRETES
    // =========================================================================

    @PostMapping("/test-weekly-reminder")
    public String testWeeklyReminder(RedirectAttributes redirectAttrs) {
        weeklyReminderService.sendWednesdayReminder();
        redirectAttrs.addFlashAttribute(SUCCESS, "Lembrete semanal disparado.");
        return REDIRECT_ADMIN_WEB;
    }

    @PostMapping("/test-weekly-reminder-showcase")
    public String testWeeklyReminderShowcase(
            @RequestParam(value = "chatId", required = false) Long chatId,
            RedirectAttributes redirectAttrs) {
        long targetChatId = resolveTargetChatId(chatId);
        weeklyReminderService.sendReminderToChat(targetChatId);
        redirectAttrs.addFlashAttribute(
                SUCCESS, "Lembrete semanal enviado para o chat " + targetChatId);
        return REDIRECT_ADMIN_WEB;
    }

    // =========================================================================
    // AUTO-RESPONSE
    // =========================================================================

    @PostMapping("/test-auto-response")
    @ResponseBody
    public ResponseEntity<String> testAutoResponse(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "message") String message,
            @RequestParam(value = "chatId", required = false) Long chatId,
            @RequestParam(value = "time", required = false) String time) {

        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(PARAMETRO_MESSAGE_OBRIGATORIO);
        }

        long targetChatId = resolveTargetChatId(chatId);
        LocalTime simulatedTime = AdminUtils.parseTime(time);

        try {
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
                    && AdminUtils.isValidUrl(response.animation())) {
                telegramFacade.enviarMidia(targetChatId, response.animation(), finalMsg);
            } else {
                telegramFacade.enviarMensagemHtml(targetChatId, finalMsg);
            }
            return ResponseEntity.ok("✅ Resposta automática enviada para o chat " + targetChatId);

        } catch (Exception e) {
            log.error("Erro no teste de auto-response", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("❌ " + MSG_ERRO_INTERNO);
        }
    }

    @GetMapping("/debug-auto-response")
    @ResponseBody
    public Map<String, Object> debugAutoResponse(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "message") String message,
            @RequestParam(value = "time", required = false) String time) {

        LocalTime simulatedTime = AdminUtils.parseTime(time);
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

    // =========================================================================
    // ADMINISTRAÇÃO (LIMPEZA E RECARREGAMENTO)
    // =========================================================================

    @PostMapping("/clear-releases")
    public String clearReleases(RedirectAttributes redirectAttrs) {
        try {
            releaseNotifiedRepository.clearAll();
            redirectAttrs.addFlashAttribute(SUCCESS, "Tabela de lançamentos limpa.");
        } catch (Exception e) {
            log.error("Erro ao limpar releases", e);
            redirectAttrs.addFlashAttribute(ERROR, MSG_ERRO_INTERNO);
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
            log.error("Erro ao limpar todos os dados", e);
            redirectAttrs.addFlashAttribute(ERROR, MSG_ERRO_INTERNO);
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

    // =========================================================================
    // PODCAST
    // =========================================================================

    @PostMapping("/test-podcast")
    public String testPodcast(
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end,
            @RequestParam(value = "chatId", required = false) Long chatId,
            RedirectAttributes redirectAttrs) {

        LocalDate today = LocalDate.now(ZoneId.of(BRAZIL_ZONE));
        LocalDate endDate;
        LocalDate startDate;

        try {
            endDate = (end != null && !end.isBlank()) ? LocalDate.parse(end) : today;
            startDate =
                    (start != null && !start.isBlank())
                            ? LocalDate.parse(start)
                            : today.minusDays(7);
        } catch (DateTimeParseException e) {
            redirectAttrs.addFlashAttribute(ERROR, "Formato de data inválido. Use yyyy-MM-dd.");
            return REDIRECT_ADMIN_WEB;
        }

        if (startDate.isAfter(endDate)) {
            redirectAttrs.addFlashAttribute(
                    ERROR, "Data de início não pode ser posterior à data de fim.");
            return REDIRECT_ADMIN_WEB;
        }

        long targetChatId = resolveTargetChatId(chatId);
        final LocalDate finalStart = startDate;
        final LocalDate finalEnd = endDate;
        final long finalChatId = targetChatId;

        // Assíncrono via virtual threads (AsyncTaskExecutor configurado no AppConfig)
        CompletableFuture.runAsync(
                () -> {
                    try {
                        log.info(
                                "📥 [web] Iniciando geração assíncrona de podcast para chat {}",
                                finalChatId);
                        podcastPublisherService.generateAndSendPodcast(
                                finalStart, finalEnd, finalChatId);
                        log.info("✅ [web] Podcast finalizado para chat {}", finalChatId);
                    } catch (Exception e) {
                        log.error("❌ Erro ao gerar podcast para chat {}", finalChatId, e);
                        try {
                            telegramFacade.enviarMensagem(
                                    finalChatId, "❌ Erro ao gerar podcast. Verifique os logs.");
                        } catch (Exception ignored) {
                            log.debug("Não foi possível enviar mensagem de erro");
                        }
                    }
                });

        redirectAttrs.addFlashAttribute(
                SUCCESS,
                "Podcast agendado para "
                        + finalStart
                        + " a "
                        + finalEnd
                        + ". Será enviado para o chat "
                        + finalChatId
                        + " em alguns minutos.");
        return REDIRECT_ADMIN_WEB;
    }

    @PostMapping("/test-podcast-latest")
    public String testPodcastLatest(
            @RequestParam(value = "chatId", required = false) Long chatId,
            RedirectAttributes redirectAttrs) {
        LocalDate endDate = LocalDate.now(ZoneId.of(BRAZIL_ZONE));
        LocalDate startDate = endDate.minusDays(7);
        return testPodcast(startDate.toString(), endDate.toString(), chatId, redirectAttrs);
    }

    @PostMapping("/test-podcast-days")
    public String testPodcastDays(
            @RequestParam(value = "days", defaultValue = "7") int days,
            @RequestParam(value = "chatId", required = false) Long chatId,
            RedirectAttributes redirectAttrs) {

        if (days <= 0 || days > 30) {
            redirectAttrs.addFlashAttribute(ERROR, "O número de dias deve ser entre 1 e 30.");
            return REDIRECT_ADMIN_WEB;
        }

        LocalDate endDate = LocalDate.now(ZoneId.of(BRAZIL_ZONE));
        LocalDate startDate = endDate.minusDays(days);
        return testPodcast(startDate.toString(), endDate.toString(), chatId, redirectAttrs);
    }

    // =========================================================================
    // ANIVERSÁRIOS
    // =========================================================================

    @PostMapping("/test-birthday")
    public String testBirthday(
            @RequestParam(value = "day") int day,
            @RequestParam(value = "month") int month,
            RedirectAttributes redirectAttrs) {
        if (day < 1 || day > 31 || month < 1 || month > 12) {
            redirectAttrs.addFlashAttribute(ERROR, "Dia/mês inválidos.");
            return REDIRECT_ADMIN_WEB;
        }
        try {
            int enviados = birthdayService.enviarParabensPara(day, month);
            redirectAttrs.addFlashAttribute(
                    SUCCESS,
                    "🎂 Parabéns disparados para "
                            + String.format("%02d/%02d", day, month)
                            + " ("
                            + enviados
                            + " enviados)");
        } catch (Exception e) {
            log.error("Erro ao disparar parabéns para {}/{}", day, month, e);
            redirectAttrs.addFlashAttribute(ERROR, MSG_ERRO_INTERNO);
        }
        return REDIRECT_ADMIN_WEB;
    }

    @PostMapping("/test-birthday-today")
    public String testBirthdayToday(RedirectAttributes redirectAttrs) {
        try {
            birthdayService.enviarParabensDoDia();
            redirectAttrs.addFlashAttribute(SUCCESS, "🎂 Parabéns disparados para hoje.");
        } catch (Exception e) {
            log.error("Erro ao disparar parabéns de hoje", e);
            redirectAttrs.addFlashAttribute(ERROR, MSG_ERRO_INTERNO);
        }
        return REDIRECT_ADMIN_WEB;
    }

    @PostMapping("/delete-birthday")
    public String deleteBirthday(
            @RequestParam(value = "userId") long userId, RedirectAttributes redirectAttrs) {
        try {
            int deleted = birthdayRepository.deleteByUserId(userId);
            if (deleted == 0) {
                redirectAttrs.addFlashAttribute(ERROR, "Nenhum aniversário para userId=" + userId);
            } else {
                redirectAttrs.addFlashAttribute(SUCCESS, "✅ Aniversário removido.");
            }
        } catch (Exception e) {
            log.error("Erro ao remover aniversário userId={}", userId, e);
            redirectAttrs.addFlashAttribute(ERROR, MSG_ERRO_INTERNO);
        }
        return REDIRECT_ADMIN_WEB;
    }

    @PostMapping("/clear-birthdays")
    public String clearBirthdays(RedirectAttributes redirectAttrs) {
        try {
            int deleted = birthdayRepository.deleteAll();
            redirectAttrs.addFlashAttribute(
                    SUCCESS, "✅ " + deleted + " aniversário(s) removido(s).");
        } catch (Exception e) {
            log.error("Erro ao limpar aniversários", e);
            redirectAttrs.addFlashAttribute(ERROR, MSG_ERRO_INTERNO);
        }
        return REDIRECT_ADMIN_WEB;
    }

    @GetMapping("/birthdays-json")
    @ResponseBody
    public Map<String, Object> birthdaysJson() {
        return Map.of(
                "total", birthdayRepository.count(),
                "birthdays", birthdayRepository.findAll());
    }

    // =========================================================================
    // MIGRAÇÃO SQLITE → POSTGRES/MONGO
    // =========================================================================

    @PostMapping("/migrate-sqlite")
    public String migrateFromSqlite(
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun,
            RedirectAttributes redirectAttrs) {
        try {
            MigrationResult result = migrationService.migrateAll(dryRun);
            log.info(
                    "🚚 [web] Migração{} concluída: status={}, duração={}ms",
                    dryRun ? " (DRY-RUN)" : "",
                    result.status(),
                    result.durationMs());

            String msg =
                    String.format(
                            "Migração%s: status=%s, duração=%dms, contadores=%s",
                            dryRun ? " (DRY-RUN)" : "",
                            result.status(),
                            result.durationMs(),
                            result.tablesMigrated());
            redirectAttrs.addFlashAttribute(SUCCESS, msg);

        } catch (IllegalStateException e) {
            log.warn("Migração rejeitada: {}", e.getMessage());
            redirectAttrs.addFlashAttribute(ERROR, e.getMessage());
        } catch (Exception e) {
            log.error("Erro inesperado na migração", e);
            redirectAttrs.addFlashAttribute(ERROR, MSG_ERRO_INTERNO);
        }
        return REDIRECT_ADMIN_WEB;
    }

    @GetMapping("/migrate-sqlite/preview")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> previewMigration() {
        try {
            Map<String, Integer> counts = migrationService.previewCounts();
            return ResponseEntity.ok(
                    Map.of(
                            "arquivo", migrationSqlitePath,
                            "contadores", counts,
                            "total", counts.values().stream().mapToInt(Integer::intValue).sum()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .body(Map.of("erro", e.getMessage()));
        }
    }

    // =========================================================================
    // FEATURE FLAGS
    // =========================================================================

    /**
     * Lista todas as feature flags no formato mapa (casa com o JS do admin.html).
     *
     * <p>Formato:
     *
     * <pre>
     * {
     *   "worldcup.enabled": { "enabled": true, "description": "...", "readOnly": false },
     *   ...
     * }
     * </pre>
     */
    @GetMapping("/features")
    @ResponseBody
    public Map<String, Map<String, Object>> listFeatures() {
        return featureFlagAdminService.listAsMap();
    }

    /**
     * Altera uma feature flag em runtime, redirecionando de volta ao painel.
     *
     * <p>Erros:
     *
     * <ul>
     *   <li>Flag desconhecida → flash error
     *   <li>Flag read-only → flash error
     * </ul>
     */
    @PostMapping("/features/{key}")
    public String toggleFeature(
            @PathVariable(value = "key") String key,
            @RequestParam(value = "enabled") boolean enabled,
            RedirectAttributes redirectAttrs) {
        try {
            boolean antes = featureFlagAdminService.isEnabled(key);
            featureFlagAdminService.toggle(key, enabled);
            boolean depois = featureFlagAdminService.isEnabled(key);

            log.info("🎛️ [web] Flag '{}': {} → {}", key, antes, depois);

            if (antes == depois) {
                redirectAttrs.addFlashAttribute(
                        SUCCESS, "Flag '" + key + "' já estava em " + enabled + ".");
            } else {
                redirectAttrs.addFlashAttribute(
                        SUCCESS,
                        "Flag '" + key + "' → " + (enabled ? "ativada" : "desativada") + ".");
            }

        } catch (IllegalArgumentException e) {
            log.warn("Flag desconhecida: {}", key);
            redirectAttrs.addFlashAttribute(ERROR, e.getMessage());

        } catch (IllegalStateException e) {
            log.warn("Tentativa de alterar flag read-only: {}", key);
            redirectAttrs.addFlashAttribute(ERROR, e.getMessage());

        } catch (Exception e) {
            log.error("Erro ao alterar flag '{}'", key, e);
            redirectAttrs.addFlashAttribute(ERROR, MSG_ERRO_INTERNO);
        }
        return REDIRECT_ADMIN_WEB;
    }

    // =========================================================================
    // MONITORAMENTO
    // =========================================================================

    @GetMapping("/cache-stats")
    @ResponseBody
    public Map<String, Long> cacheStats() {
        return cacheService.getStats();
    }

    @GetMapping("/debug/cache/{fileId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> debugCache(@PathVariable("fileId") String fileId) {
        var entry = cacheService.get(fileId);
        if (entry == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("erro", "fileId não encontrado no cache", "fileId", fileId));
        }

        String bruto = entry.textoBruto();
        String refinado = entry.textoRefinado();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileId", fileId);
        result.put("brutoLength", bruto == null ? 0 : bruto.length());
        result.put("refinadoLength", refinado == null ? 0 : refinado.length());
        result.put("brutoVazio", bruto == null || bruto.isBlank());
        result.put("refinadoVazio", refinado == null || refinado.isBlank());
        result.put("timestamp", entry.timestamp());
        result.put(
                "primeiros200Bruto",
                bruto == null ? "" : bruto.substring(0, Math.min(200, bruto.length())));
        result.put(
                "primeiros200Refinado",
                refinado == null ? "" : refinado.substring(0, Math.min(200, refinado.length())));
        return ResponseEntity.ok(result);
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
        props.put(
                "telegram.bot.token",
                AdminUtils.maskToken(environment.getProperty("telegram.bot.token")));
        props.put("groq.api.key", AdminUtils.maskToken(environment.getProperty("groq.api.key")));
        props.put("tmdb.token", AdminUtils.maskToken(environment.getProperty("tmdb.token")));
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
        props.put("transcription.enabled", environment.getProperty("transcription.enabled"));
        props.put("t1000.audio.max-size-mb", environment.getProperty("t1000.audio.max-size-mb"));
        props.put("bot.allowed-chats", environment.getProperty("bot.allowed-chats"));
        props.put("migration.sqlite.path", migrationSqlitePath);
        return props;
    }

    /**
     * Carrega os arquivos de configuração (easter-eggs, auto-responses, worldcup) usando o {@code
     * ResourceLoader} e respeitando as propriedades do {@code application.properties} (ex.: {@code
     * easter-egg.file}, {@code auto.response.file}, {@code worldcup.data.file}).
     *
     * <p>Isso garante que o painel leia os mesmos arquivos que os services usam (ex.: {@code
     * EasterEggService}), tanto em dev ({@code file:./config/...}) quanto em prod ({@code
     * file:/app/config/...}).
     */
    @GetMapping("/config-files")
    @ResponseBody
    public Map<String, Object> configFiles() {
        Map<String, Object> result = new LinkedHashMap<>();

        // Definição: nome do arquivo → (chave da propriedade, default)
        record ConfigFile(String name, String propertyKey, String defaultLocation) {}

        List<ConfigFile> files =
                List.of(
                        new ConfigFile(
                                "easter-eggs.json",
                                "easter-egg.file",
                                "classpath:easter-eggs.json"),
                        new ConfigFile(
                                "auto-responses.json",
                                "auto.response.file",
                                "classpath:auto-responses.json"),
                        new ConfigFile(
                                "worldcup2026.json",
                                "worldcup.data.file",
                                "classpath:worldcup2026.json"));

        for (ConfigFile file : files) {
            try {
                Object content =
                        AdminUtils.loadConfigFile(
                                resourceLoader,
                                environment,
                                objectMapper,
                                file.propertyKey(),
                                file.name(),
                                file.defaultLocation());
                result.put(file.name(), content);
            } catch (JsonProcessingException e) {
                log.error("Erro ao parsear JSON do arquivo: {}", file.name(), e);
                result.put(file.name(), "❌ Erro ao parsear JSON");
            } catch (IOException e) {
                log.warn(
                        "Arquivo de configuração não encontrado: {} (propriedade='{}')",
                        file.name(),
                        file.propertyKey());
                result.put(file.name(), "❌ Arquivo não encontrado");
            }
        }
        return result;
    }

    // =========================================================================
    // HELPERS PRIVADOS
    // =========================================================================

    /**
     * Resolve o chat alvo da ação seguindo a ordem: parâmetro explícito → ownerId → primeiro
     * digestChatId → showcase. Retorna {@code 0} se nada estiver disponível.
     */
    private long resolveTargetChatId(Long chatIdParam) {
        if (chatIdParam != null) {
            return chatIdParam;
        }
        if (ownerId != 0) {
            return ownerId;
        }
        if (digestChatIdsStr != null && !digestChatIdsStr.isBlank()) {
            for (String s : digestChatIdsStr.split(",")) {
                try {
                    return Long.parseLong(s.trim());
                } catch (NumberFormatException ignored) {
                    // continua
                }
            }
        }
        return AdminUtils.SHOWCASE_CHAT_ID;
    }

    /** Deleta arquivo temporário silenciosamente. */
    private void deleteSilently(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            log.debug("Não foi possível deletar arquivo: {}", file);
        }
    }
}
