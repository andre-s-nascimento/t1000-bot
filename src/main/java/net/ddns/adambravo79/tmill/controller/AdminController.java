/* (c) 2026 | 09/05/2026 */
package net.ddns.adambravo79.tmill.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.service.DailyDigestService;
import net.ddns.adambravo79.tmill.service.EasterEggService;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final EasterEggService easterEggService;
    private final DailyDigestService dailyDigestService;

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

    // AdminController.java
    @GetMapping("/custom-digest")
    public ResponseEntity<String> customDigest(
            @RequestParam("start") String startDate, @RequestParam("end") String endDate) {

        try {
            LocalDate startLocalDate = null;
            LocalDate endLocalDate = null;

            // Tenta formatos diferentes
            for (String pattern : new String[] {"yyyy-MM-dd", "dd-MM-yyyy"}) {
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
                    startLocalDate = LocalDate.parse(startDate, formatter);
                    endLocalDate = LocalDate.parse(endDate, formatter);
                    break; // saiu do loop, conseguiu parsear
                } catch (DateTimeParseException ignored) {
                    /** */
                }
            }

            if (startLocalDate == null || endLocalDate == null) {
                return ResponseEntity.badRequest()
                        .body(
                                "Formato inválido. Use 'yyyy-MM-dd' ou 'dd-MM-yyyy'. Ex: 2026-05-07"
                                        + " ou 07-05-2026");
            }

            ZoneId zone = ZoneId.of("America/Sao_Paulo");
            LocalDateTime from = startLocalDate.atStartOfDay(zone).toLocalDateTime(); // 00:00
            LocalDateTime to = endLocalDate.atTime(23, 59, 59); // 23:59:59

            dailyDigestService.generateDigestCustom(from, to);
            return ResponseEntity.ok(
                    "Resumo personalizado gerado para período: " + startDate + " até " + endDate);
        } catch (Exception e) {
            log.error("Erro ao processar datas", e);
            return ResponseEntity.internalServerError().body("Erro interno: " + e.getMessage());
        }
    }
}
