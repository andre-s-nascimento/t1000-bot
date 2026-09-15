/* (c) 2026 | 15/09/2026 */
package net.ddns.adambravo79.tmill.service;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.constant.BotMessages;

@Slf4j
@Component
@RequiredArgsConstructor
public class BirthdayScheduler {

    private final BirthdayService birthdayService;

    /** Roda todo dia às 00:01 (horário de Brasília). */
    @Scheduled(cron = "0 1 0 * * *", zone = BotMessages.BRAZIL_ZONE)
    public void dispararParabens() {
        log.info("⏰ [BirthdayScheduler] Verificando aniversariantes do dia...");
        try {
            LocalDate hoje = LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE));
            int enviados =
                    birthdayService.enviarParabensPara(hoje.getDayOfMonth(), hoje.getMonthValue());
            log.info("⏰ [BirthdayScheduler] Concluído: {} parabéns enviados.", enviados);
        } catch (Exception e) {
            log.error("❌ Erro ao processar aniversariantes", e);
        }
    }
}
