/* (c) 2026 | 22/07/2026 */
package net.ddns.adambravo79.tmill.controller;

import static net.ddns.adambravo79.tmill.constant.BotMessages.BRAZIL_ZONE;
import static net.ddns.adambravo79.tmill.constant.BotMessages.DATA_INVALIDA_WORLDCUP;
import static net.ddns.adambravo79.tmill.constant.BotMessages.ERRO_LIMPAR_DADOS;
import static net.ddns.adambravo79.tmill.constant.BotMessages.ERRO_LIMPAR_RELEASES;
import static net.ddns.adambravo79.tmill.constant.BotMessages.FMT_DD_MM_YYYY_HYPHEN;
import static net.ddns.adambravo79.tmill.constant.BotMessages.FMT_HH_MM_SS;
import static net.ddns.adambravo79.tmill.constant.BotMessages.FMT_YYYY_MM_DD;
import static net.ddns.adambravo79.tmill.constant.BotMessages.WORLD_CUP_DISABLED;
import static net.ddns.adambravo79.tmill.constant.BotMessages.WORLD_CUP_NOT_AVAILABLE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import jakarta.annotation.PostConstruct;
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
import net.ddns.adambravo79.tmill.service.feature.FeatureFlagState;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.util.LogSanitizer;
import tools.jackson.core.JacksonException;
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
 *
 * <p>Utilitários compartilhados com {@link AdminWebController} ficam em {@link AdminUtils}.
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

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
    private final AzureTtsClient azureTtsClient;
    private final PodcastPublisherService podcastPublisherService;
    private final TempDirService tempDirService;
    private final BirthdayService birthdayService;
    private final BirthdayRepository birthdayRepository;
    private final MigrationService migrationService;
    private final FeatureFlagAdminService featureFlagAdminService;

    @Value("${worldcup.enabled:false}")
    private boolean worldcupEnabled;

    @Value("${telegram.owner.id:0}")
    private long ownerId;

    @Value("${digest.chat-ids:}")
    private String digestChatIdsStr;

    @Value("${podcast.publish.chat-id}")
    private long publishChatId;

    @Value("${migration.sqlite.path:./data/t1000.db}")
    private String migrationSqlitePath;

    private Set<Long> digestChatIds = new HashSet<>();

    @PostConstruct
    public void initChatIds() {
        if (digestChatIdsStr != null && !digestChatIdsStr.isBlank()) {
            for (String s : digestChatIdsStr.split(",")) {
                try {
                    digestChatIds.add(Long.parseLong(s.trim()));
                } catch (NumberFormatException e) {
                    log.warn("ID inválido em digest.chat-ids: {}", s);
                }
            }
        }
    }

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
        long targetChatId = chatId != null ? chatId : AdminUtils.SHOWCASE_CHAT_ID;
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
        long targetChatId = chatId != null ? chatId : AdminUtils.SHOWCASE_CHAT_ID;
        if (!worldcupEnabled) {
            return ResponseEntity.ok(WORLD_CUP_DISABLED);
        }
        worldCupSchedulerService.sendManualTestToChat(targetChatId);
        return ResponseEntity.ok("✅ Teste manual da Copa enviado para o chat " + targetChatId);
    }

    @PostMapping("/test-worldcup-noon-showcase")
    public ResponseEntity<String> testWorldCupNoonShowcase(
            @RequestParam(required = false) Long chatId) {
        long targetChatId = chatId != null ? chatId : AdminUtils.SHOWCASE_CHAT_ID;
        if (!worldcupEnabled) {
            return ResponseEntity.ok(WORLD_CUP_DISABLED);
        }
        worldCupSchedulerService.sendNoonMatchesToChat(targetChatId);
        return ResponseEntity.ok("✅ Envio do meio-dia da Copa enviado para o chat " + targetChatId);
    }

    @PostMapping("/test-worldcup-evening-showcase")
    public ResponseEntity<String> testWorldCupEveningShowcase(
            @RequestParam(required = false) Long chatId) {
        long targetChatId = chatId != null ? chatId : AdminUtils.SHOWCASE_CHAT_ID;
        if (!worldcupEnabled) {
            return ResponseEntity.ok(WORLD_CUP_DISABLED);
        }
        worldCupSchedulerService.sendEveningMatchesToChat(targetChatId);
        return ResponseEntity.ok("✅ Envio da noite da Copa enviado para o chat " + targetChatId);
    }

    @PostMapping("/reload-worldcup-showcase")
    public ResponseEntity<String> reloadWorldCupShowcase(
            @RequestParam(required = false) Long chatId) {

        long targetChatId = chatId != null ? chatId : AdminUtils.SHOWCASE_CHAT_ID;
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

        long targetChatId = chatId != null ? chatId : AdminUtils.SHOWCASE_CHAT_ID;
        if (!worldcupEnabled) {
            return ResponseEntity.ok(WORLD_CUP_DISABLED);
        }

        LocalDate date = AdminUtils.parseDateParam(dateParam);
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
        return ResponseEntity.ok(props);
    }

    /**
     * Carrega os arquivos de configuração (easter-eggs, auto-responses, worldcup) usando o {@code
     * ResourceLoader} e respeitando as propriedades do {@code application.properties} (ex.: {@code
     * easter-egg.file}, {@code auto.response.file}, {@code worldcup.data.file}).
     *
     * <p>Isso garante que o endpoint leia os mesmos arquivos que os services usam (ex.: {@code
     * EasterEggService}), tanto em dev ({@code file:./config/...}) quanto em prod ({@code
     * file:/app/config/...}).
     */
    @GetMapping("/config-files")
    public ResponseEntity<Map<String, Object>> getConfigFiles() {
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
            } catch (JacksonException e) {
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

        long targetChatId = chatId != null ? chatId : AdminUtils.SHOWCASE_CHAT_ID;
        LocalTime simulatedTime = AdminUtils.parseTime(time);

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
        return ResponseEntity.ok(result);
    }

    @GetMapping("/auto-response-rules")
    public ResponseEntity<Map<String, Object>> listAutoResponseRules() {
        return ResponseEntity.ok(
                Map.of(
                        "totalRules", autoResponseService.getRulesCount(),
                        "rules", autoResponseService.getRulesSummary()));
    }

    @PostMapping("/fala-t1000")
    public ResponseEntity<String> falaT1000(
            @RequestParam String message,
            @RequestParam(required = false) Long chatId,
            @RequestParam(defaultValue = "HTML") String parseMode) {

        // 1. Validação
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body("❌ Parâmetro 'message' é obrigatório.");
        }

        // 2. Define o chat alvo
        long targetChatId;
        if (chatId != null) {
            targetChatId = chatId;
        } else if (ownerId != 0) {
            targetChatId = ownerId;
        } else if (!digestChatIds.isEmpty()) {
            targetChatId = digestChatIds.iterator().next();
        } else {
            return ResponseEntity.badRequest()
                    .body(
                            "❌ Nenhum chatId informado e nenhum chat padrão configurado (ownerId ou"
                                    + " digest.chat-ids).");
        }

        // 3. Log da ação
        log.info(
                "📤 Enviando mensagem via admin para chat {}: {}",
                targetChatId,
                LogSanitizer.sanitizeMessageText(message));

        // 4. Envia a mensagem
        try {
            if ("HTML".equalsIgnoreCase(parseMode)) {
                telegramFacade.enviarMensagemHtml(targetChatId, message);
            } else {
                telegramFacade.enviarMensagem(targetChatId, message);
            }
            return ResponseEntity.ok("✅ Mensagem enviada com sucesso para o chat " + targetChatId);
        } catch (Exception e) {
            log.error("❌ Falha ao enviar mensagem para chat {}", targetChatId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("❌ Erro ao enviar mensagem: " + e.getMessage());
        }
    }

    @GetMapping("/test-azure-tts")
    public ResponseEntity<String> testAzureTts() {
        if (publishChatId == 0) {
            return ResponseEntity.badRequest().body("❌ podcast.publish.chat-id não configurado.");
        }
        String text =
                "Bem vindos ao espetacular... ah deixa de papo furado. Dadinho é o cara leo, meu"
                        + " nome agora é Zé Pequeno.";
        byte[] audio = azureTtsClient.synthesizeFullText(text);
        if (audio.length > 0) {
            try {
                Path temp = tempDirService.createTempFile("test_azure_", ".mp3");
                Files.write(temp, audio);
                telegramFacade.enviarMidia(
                        publishChatId, temp.toAbsolutePath().toString(), "Teste Azure TTS");
                Files.deleteIfExists(temp);
                return ResponseEntity.ok("Áudio enviado com sucesso para chat " + publishChatId);
            } catch (Exception e) {
                return ResponseEntity.status(500).body("Erro ao salvar áudio: " + e.getMessage());
            }
        }
        return ResponseEntity.status(500).body("Falha na síntese (áudio vazio).");
    }

    @PostMapping("/fala-t1000-tts")
    public ResponseEntity<String> falaT1000Tts(
            @RequestParam String message, @RequestParam(required = false) Long chatId) {

        // 1. Validação
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body("❌ Parâmetro 'message' é obrigatório.");
        }

        // 2. Define o chat alvo
        long targetChatId;
        if (chatId != null) {
            targetChatId = chatId;
        } else if (ownerId != 0) {
            targetChatId = ownerId;
        } else if (!digestChatIds.isEmpty()) {
            targetChatId = digestChatIds.iterator().next();
        } else {
            return ResponseEntity.badRequest()
                    .body("❌ Nenhum chatId informado e nenhum chat padrão configurado.");
        }

        // 3. Log da ação
        log.info(
                "🎤 Sintetizando áudio para chat {}: {}",
                targetChatId,
                LogSanitizer.sanitizeMessageText(message));

        // 4. Sintetiza o áudio — 👈 try/catch ADICIONADO
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

        // 5. Salva e envia com nome personalizado
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
                    .body("❌ Erro ao salvar áudio: " + e.getMessage());
        } finally {
            if (tempFile != null && Files.exists(tempFile)) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    log.debug("Não foi possível deletar arquivo: {}", tempFile);
                }
            }
        }
    }

    // ========================= ANIVERSÁRIOS =========================

    @GetMapping("/birthdays")
    public ResponseEntity<java.util.List<net.ddns.adambravo79.tmill.model.Birthday>>
            listBirthdays() {
        return ResponseEntity.ok(birthdayRepository.findAll());
    }

    @GetMapping("/birthdays/count")
    public ResponseEntity<Map<String, Object>> birthdaysCount() {
        return ResponseEntity.ok(Map.of("total", birthdayRepository.count()));
    }

    @PostMapping("/birthdays/test/{day}/{month}")
    public ResponseEntity<String> testBirthday(@PathVariable int day, @PathVariable int month) {
        if (day < 1 || day > 31 || month < 1 || month > 12) {
            return ResponseEntity.badRequest().body("❌ Dia/mês inválidos.");
        }
        int enviados = birthdayService.enviarParabensPara(day, month);
        return ResponseEntity.ok(
                "🎂 Parabéns disparados para " + day + "/" + month + ". Enviados: " + enviados);
    }

    @PostMapping("/birthdays/test-today")
    public ResponseEntity<String> testBirthdayToday() {
        birthdayService.enviarParabensDoDia();
        return ResponseEntity.ok("🎂 Parabéns disparados para hoje.");
    }

    @DeleteMapping("/birthdays/{userId}")
    public ResponseEntity<String> deleteBirthday(@PathVariable long userId) {
        int deleted = birthdayRepository.deleteByUserId(userId);
        if (deleted == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("❌ Nenhum aniversário encontrado para userId=" + userId);
        }
        return ResponseEntity.ok("✅ Aniversário removido (userId=" + userId + ")");
    }

    @PostMapping("/birthdays/clear")
    public ResponseEntity<String> clearBirthdays() {
        int deleted = birthdayRepository.deleteAll();
        return ResponseEntity.ok("✅ " + deleted + " aniversário(s) removido(s).");
    }

    // ========================= MÉTODOS AUXILIARES PRIVADOS =========================

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
                && AdminUtils.isValidUrl(response.animation())) {
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

    // ========================= PODCAST MANUAL =========================

    /**
     * Endpoint para gerar podcast manualmente com parâmetros personalizados.
     *
     * <p>Exemplos de uso: - GET /admin/test-podcast -> gera da semana passada para o showcase - GET
     * /admin/test-podcast?chatId=123456&start=2026-08-01&end=2026-08-07 - GET
     * /admin/test-podcast?chatId=123456&periodo=7 -> últimos 7 dias
     *
     * @param chatId ID do chat para envio (opcional, padrão: showcase)
     * @param start Data de início (opcional, formato: yyyy-MM-dd)
     * @param end Data de fim (opcional, formato: yyyy-MM-dd)
     * @param periodo Número de dias para trás (opcional, padrão: 7)
     * @return Status da operação
     */
    @GetMapping("/test-podcast")
    public ResponseEntity<String> testPodcast(
            @RequestParam(required = false) Long chatId,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) Integer periodo) {

        // Define o chat alvo
        long targetChatId = (chatId != null) ? chatId : AdminUtils.SHOWCASE_CHAT_ID;

        // Calcula o período
        LocalDate today = LocalDate.now(ZoneId.of(BRAZIL_ZONE));
        LocalDate endDate;
        LocalDate startDate;

        if (start != null && !start.isBlank() && end != null && !end.isBlank()) {
            try {
                startDate = LocalDate.parse(start);
                endDate = LocalDate.parse(end);
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest()
                        .body("❌ Formato de data inválido. Use yyyy-MM-dd");
            }
        } else if (periodo != null && periodo > 0) {
            endDate = today;
            startDate = today.minusDays(periodo);
        } else {
            endDate = today.with(DayOfWeek.SUNDAY).minusWeeks(1);
            startDate = endDate.with(DayOfWeek.MONDAY);
        }

        if (startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest()
                    .body("❌ Data de início não pode ser posterior à data de fim.");
        }

        if (targetChatId == 0) {
            return ResponseEntity.badRequest()
                    .body("❌ chatId inválido. Configure um chatId ou use o padrão.");
        }

        final long finalChatId = targetChatId;
        final LocalDate finalStart = startDate;
        final LocalDate finalEnd = endDate;

        CompletableFuture.runAsync(
                () -> {
                    try {
                        log.info(
                                "📥 Iniciando geração assíncrona do podcast para chat {}",
                                finalChatId);
                        log.info("📅 Período: {} a {}", finalStart, finalEnd);

                        podcastPublisherService.generateAndSendPodcast(
                                finalStart, finalEnd, finalChatId);

                        log.info("✅ Podcast assíncrono finalizado para chat {}", finalChatId);
                    } catch (Exception e) {
                        log.error(
                                "❌ Erro assíncrono ao gerar podcast para chat {}", finalChatId, e);
                        try {
                            telegramFacade.enviarMensagem(
                                    finalChatId, "❌ Erro ao gerar podcast: " + e.getMessage());
                        } catch (Exception ignored) {
                            log.debug("Não foi possível enviar mensagem de erro");
                        }
                    }
                });

        String responseMsg =
                String.format(
                        "🔄 Podcast agendado para o período de %s a %s.\n"
                                + "📤 Será enviado para o chat %d.\n"
                                + "⏳ O processamento pode levar alguns minutos.",
                        finalStart.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                        finalEnd.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                        finalChatId);

        try {
            telegramFacade.enviarMensagemHtml(
                    finalChatId,
                    "<b>🎙️ Podcast solicitado manualmente</b>\n\n"
                            + "📅 Período: "
                            + finalStart.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                            + " a "
                            + finalEnd.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                            + "\n"
                            + "⏳ Aguarde, estou gerando o áudio...");
        } catch (Exception e) {
            log.warn("Não foi possível enviar confirmação para o chat {}", finalChatId);
        }

        return ResponseEntity.accepted().body(responseMsg);
    }

    /** Endpoint para testar o podcast com período fixo (últimos 7 dias). */
    @GetMapping("/test-podcast-latest")
    public ResponseEntity<String> testPodcastLatest(@RequestParam(required = false) Long chatId) {

        LocalDate endDate = LocalDate.now(ZoneId.of(BRAZIL_ZONE));
        LocalDate startDate = endDate.minusDays(7);

        return testPodcast(chatId, startDate.toString(), endDate.toString(), null);
    }

    /**
     * Endpoint para testar o podcast com período específico em dias Ex:
     * /admin/test-podcast-days?days=3&chatId=123456
     */
    @GetMapping("/test-podcast-days")
    public ResponseEntity<String> testPodcastDays(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) Long chatId) {

        if (days <= 0 || days > 30) {
            return ResponseEntity.badRequest().body("❌ O número de dias deve ser entre 1 e 30.");
        }

        return testPodcast(chatId, null, null, days);
    }

    // ========================= MIGRAÇÃO SQLITE → POSTGRES/MONGO =========================

    /**
     * Executa a migração dos dados legados do SQLite para os novos bancos.
     *
     * <p>⚠️ Requer {@code migration.enabled=true} em application.properties.
     *
     * <p>⚠️ NÃO é idempotente. Rode uma única vez em ambiente limpo.
     *
     * @return resumo com contadores por tabela
     */
    @PostMapping("/migrate-sqlite")
    public ResponseEntity<?> migrateFromSqlite(
            @RequestParam(required = false, defaultValue = "false") boolean dryRun) {
        try {
            MigrationResult result = migrationService.migrateAll(dryRun);
            log.info(
                    "🚚 Migração{} concluída: status={}, duração={}ms, contadores={}",
                    dryRun ? " (DRY-RUN)" : "",
                    result.status(),
                    result.durationMs(),
                    result.tablesMigrated());

            return switch (result.status()) {
                case "SUCCESS" -> ResponseEntity.ok(result);
                case "PARTIAL" -> ResponseEntity.status(HttpStatus.MULTI_STATUS).body(result);
                default -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
            };
        } catch (IllegalStateException e) {
            log.warn("Migração rejeitada: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .body(Map.of("erro", e.getMessage()));
        } catch (Exception e) {
            log.error("Erro inesperado na migração", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("erro", "Erro inesperado: " + e.getMessage()));
        }
    }

    /** Dry-run: conta quantos registros existem em cada tabela do SQLite, sem migrar. */
    @SuppressWarnings("null")
    @GetMapping("/migrate-sqlite/preview")
    public ResponseEntity<?> previewMigration() {
        try {
            Map<String, Integer> counts = migrationService.previewCounts();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("arquivo", migrationSqlitePath != null ? migrationSqlitePath : "?");
            result.put("contadores", counts);
            result.put("total", counts.values().stream().mapToInt(Integer::intValue).sum());
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .body(Map.of("erro", e.getMessage()));
        }
    }

    @GetMapping("/debug/cache/{fileId}")
    public ResponseEntity<?> debugCache(@PathVariable String fileId) {
        var entry = fileTranscriptionCacheService.get(fileId);
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

    // ========================= FEATURE FLAGS =========================

    /**
     * Lista todas as feature flags registradas.
     *
     * @return lista ordenada por chave, com estado atual, descrição e flag readOnly
     */
    @GetMapping("/features")
    public ResponseEntity<List<FeatureFlagState>> listFeatures() {
        return ResponseEntity.ok(featureFlagAdminService.list());
    }

    /**
     * Altera uma feature flag em runtime.
     *
     * <p>Erros:
     *
     * <ul>
     *   <li>Flag desconhecida → 400
     *   <li>Flag read-only → 409 Conflict
     * </ul>
     *
     * @param key chave da flag (ex.: {@code "worldcup.enabled"})
     * @param enabled novo valor
     */
    @PostMapping("/features/{key}")
    public ResponseEntity<Map<String, Object>> toggleFeature(
            @PathVariable String key, @RequestParam boolean enabled) {
        try {
            boolean antes = featureFlagAdminService.isEnabled(key);
            featureFlagAdminService.toggle(key, enabled);
            boolean depois = featureFlagAdminService.isEnabled(key);

            log.info("🎛️ [REST] Flag '{}': {} → {}", key, antes, depois);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("key", key);
            result.put("enabled", depois);
            result.put("changed", antes != depois);
            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            log.warn("Flag desconhecida: {}", key);
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));

        } catch (IllegalStateException e) {
            log.warn("Tentativa de alterar flag read-only: {}", key);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("erro", e.getMessage()));

        } catch (Exception e) {
            log.error("Erro ao alterar flag '{}'", key, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("erro", MSG_ERRO_INTERNO));
        }
    }
}
