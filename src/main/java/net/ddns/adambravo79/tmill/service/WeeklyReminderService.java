/* (c) 2026 | 20/05/2026 */
package net.ddns.adambravo79.tmill.service;

import java.time.ZoneId;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.service.config.JsonConfigLoader;
import net.ddns.adambravo79.tmill.service.feature.FeatureFlagAdminService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReminderService implements SchedulingConfigurer {

    private static final String REMINDER_MESSAGE =
            """
            <i>"São quatro horas da tarde de uma quarta-feira, não é? Semana praticamente encerrada...</i>"

            <b>Muito Prazer (1979) - David Neves</b>
            """;

    private final TelegramFacade telegramFacade;
    private final JsonConfigLoader jsonConfigLoader;
    private final FeatureFlagAdminService featureFlags;

    @Value("${bot.allowed-chats:}")
    private String allowedChatsStr;

    @Value("${weekly.reminder.media-file:}")
    private String mediaFilePath;

    private final Set<Long> allowedGroups = new HashSet<>();

    @PostConstruct
    public void init() {
        log.info("📹 mediaFilePath = '{}'", mediaFilePath);
        if (allowedChatsStr != null && !allowedChatsStr.isBlank()) {
            for (String s : allowedChatsStr.split(",")) {
                try {
                    long id = Long.parseLong(s.trim());
                    if (id < 0) {
                        allowedGroups.add(id);
                    }
                } catch (NumberFormatException e) {
                    log.warn("Chat ID inválido na lista de autorizados: {}", s);
                }
            }
            log.info("🗓️ WeeklyReminderService – grupos autorizados: {}", allowedGroups);
        } else {
            log.info("Nenhum grupo autorizado configurado – o lembrete semanal não será enviado.");
        }
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(
                this::sendWednesdayReminder,
                ctx ->
                        new CronTrigger(getCron(), ZoneId.of("America/Sao_Paulo"))
                                .nextExecution(ctx));
    }

    private String getCron() {
        return jsonConfigLoader
                .loadConfig("config/weekly-reminder.json", Map.class, "config/weekly-reminder.json")
                .map(m -> (String) m.get("cron"))
                .orElse("0 0 16 * * 3");
    }

    /**
     * Envio automático do lembrete semanal (quarta-feira).
     *
     * <p><b>Feature flag:</b> se {@code weekly.reminder.enabled} estiver desabilitada, este método
     * apenas loga e retorna. O admin ainda pode disparar manualmente via {@link
     * #sendReminderToChat(Long)} (endpoint {@code /admin/test-weekly-reminder-showcase}).
     */
    public void sendWednesdayReminder() {
        if (!featureFlags.isEnabled("weekly.reminder.enabled")) {
            log.info("📅 Lembrete semanal desabilitado por feature flag — pulando envio.");
            return;
        }

        if (allowedGroups.isEmpty()) {
            log.info("Nenhum grupo configurado para receber o lembrete semanal.");
            return;
        }

        for (Long groupId : allowedGroups) {
            try {
                sendReminder(groupId);
                log.info("Lembrete semanal enviado para grupo {}", groupId);
            } catch (Exception e) {
                log.error("Erro ao enviar lembrete para grupo {}: {}", groupId, e.getMessage());
            }
        }
    }

    /**
     * Envio manual do lembrete para um chat específico.
     *
     * <p><b>Não valida feature flag</b> — é um comando explícito do admin.
     */
    public void sendReminderToChat(Long chatId) {
        if (chatId == null) {
            log.warn("chatId nulo, ignorando envio de lembrete");
            return;
        }
        try {
            sendReminder(chatId);
            log.info("Lembrete semanal enviado para chat {}", chatId);
        } catch (Exception e) {
            log.error("Erro ao enviar lembrete para chat {}: {}", chatId, e.getMessage());
        }
    }

    // =========================================================================
    // HELPERS PRIVADOS
    // =========================================================================

    private void sendReminder(long chatId) {
        if (mediaFilePath != null && !mediaFilePath.isBlank()) {
            telegramFacade.enviarMidia(chatId, mediaFilePath, REMINDER_MESSAGE);
        } else {
            telegramFacade.enviarMensagemHtml(chatId, REMINDER_MESSAGE);
        }
    }
}
