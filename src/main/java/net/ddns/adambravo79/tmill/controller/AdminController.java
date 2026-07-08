/* (c) 2026 | 19/05/2026 */
package net.ddns.adambravo79.tmill.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.cache.TranscriptionCacheService;
import net.ddns.adambravo79.tmill.service.AutoResponseService;
import net.ddns.adambravo79.tmill.service.DailyDigestService;
import net.ddns.adambravo79.tmill.service.EasterEggService;
import net.ddns.adambravo79.tmill.service.StaticWorldCupService;
import net.ddns.adambravo79.tmill.service.WeeklyReminderService;
import net.ddns.adambravo79.tmill.service.WorldCupSchedulerService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private static final String SERVICO_DA_COPA_DESATIVADO = "⛔ Serviço da Copa desativado.";
    private final EasterEggService easterEggService;
    private final DailyDigestService dailyDigestService;
    private final TranscriptionCacheService transcriptionCacheService;
    private final WeeklyReminderService weeklyReminderService;
    private final AutoResponseService autoResponseService;
    private final WorldCupSchedulerService worldCupSchedulerService;
    private final StaticWorldCupService staticWorldCupService;
    private final TelegramFacade telegramFacade;

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
        return ResponseEntity.ok(transcriptionCacheService.getStats());
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
}
