package net.ddns.adambravo79.tmill.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.service.config.SalaVipConfigService;
import net.ddns.adambravo79.tmill.service.config.SalaVipJsonLoader;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "t1000.salavip.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SalaVipScheduler {

    private final SalaVipJsonLoader jsonLoader;

    @SuppressWarnings({"java:S1068", "unused"}) // usado via SpEL no @Scheduled
    private final SalaVipConfigService configService;

    @Scheduled(
            cron = "#{@salaVipConfigService.getCron()}",
            zone = "#{@salaVipConfigService.getZone()}")
    public void syncSemanal() {
        log.info("⏰ Agendamento semanal da Sala VIP iniciado.");
        try {
            jsonLoader.carregar();
        } catch (Exception e) {
            log.error("❌ Falha ao sincronizar Sala VIP no agendamento semanal", e);
        }
    }
}
