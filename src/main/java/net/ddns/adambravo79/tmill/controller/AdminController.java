/* (c) 2026 | 06/05/2026 */
package net.ddns.adambravo79.tmill.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import net.ddns.adambravo79.tmill.cache.TranscriptionCacheService;
import net.ddns.adambravo79.tmill.service.DailyDigestService;
import net.ddns.adambravo79.tmill.service.EasterEggService;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final EasterEggService easterEggService;
    private final DailyDigestService dailyDigestService;
    private final TranscriptionCacheService transcriptionCacheService;

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
}
