package net.ddns.adambravo79.tmill.service.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SalaVipBootstrap {

    private final SalaVipJsonLoader loader;

    /**
     * Carrega o JSON no boot. Se falhar, o bot SOBE mesmo assim —
     * a Sala VIP fica indisponível até o próximo {@code @Scheduled} ou
     * até um "Forçar Sync" manual no admin.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            loader.carregar();
        } catch (Exception e) {
            log.error(
                    "Falha ao carregar Sala VIP no boot. Bot continua sem Sala VIP. " + "Causa: {}",
                    e.getMessage(),
                    e);
        }
    }
}
