/* (c) 2026 | 13/05/2026 */
package net.ddns.adambravo79.tmill.service;

import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReminderService {

    private final TelegramFacade telegramFacade;

    @Value("${bot.allowed-chats:}")
    private String allowedChatsStr;

    private final Set<Long> allowedGroups = new HashSet<>();

    @PostConstruct
    public void init() {
        if (allowedChatsStr != null && !allowedChatsStr.isBlank()) {
            for (String s : allowedChatsStr.split(",")) {
                try {
                    long id = Long.parseLong(s.trim());
                    // Aceita apenas IDs negativos (grupos/canais)
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

    @Scheduled(cron = "0 0 16 * * 3", zone = "America/Sao_Paulo")
    public void sendWednesdayReminder() {
        if (allowedGroups.isEmpty()) {
            log.info("Nenhum grupo configurado para receber o lembrete semanal.");
            return;
        }

        String message =
                "<i>\"São quatro horas da tarde de uma quarta-feira, não é? Semana praticamente"
                        + " encerrada...</i>\""
                        + "\n\n"
                        + "<b>Muito Prazer (1979) - David Neves</b>\n";

        for (Long groupId : allowedGroups) {
            try {
                telegramFacade.enviarMensagemHtml(groupId, message);
                log.info("Lembrete semanal enviado para grupo {}", groupId);
            } catch (Exception e) {
                log.error("Erro ao enviar lembrete para grupo {}: {}", groupId, e.getMessage());
            }
        }
    }
}
