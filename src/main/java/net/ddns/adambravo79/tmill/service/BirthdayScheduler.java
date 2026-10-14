/* (c) 2026 | 15/09/2026 */
package net.ddns.adambravo79.tmill.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.service.config.JsonConfigLoader;

@Slf4j
@Component
@RequiredArgsConstructor
public class BirthdayScheduler implements SchedulingConfigurer {

    private final BirthdayService birthdayService;
    private final JsonConfigLoader jsonConfigLoader;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(
                this::dispararParabens,
                ctx ->
                        new CronTrigger(getCron(), ZoneId.of(BotMessages.BRAZIL_ZONE))
                                .nextExecution(ctx));
    }

    private String getCron() {
        return jsonConfigLoader
                .loadConfig("config/birthday-config.json", Map.class, "config/birthday-config.json")
                .map(m -> (String) m.get("cron"))
                .orElse("0 1 0 * * *");
    }

    /** Roda todos os dias às 00:01 (horário de Brasília). */
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
