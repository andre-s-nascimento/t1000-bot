package net.ddns.adambravo79.tmill.telegram.handler;

import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;

import io.ksilisk.telegrambot.core.handler.update.UpdateHandler;
import io.ksilisk.telegrambot.core.matcher.Matcher;
import io.ksilisk.telegrambot.core.rule.MessageUpdateRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.controller.AudioHandler;
import net.ddns.adambravo79.tmill.controller.CommandHandler;
import net.ddns.adambravo79.tmill.telegram.core.GroupAuthorizationService;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageUpdateRuleHandler implements UpdateHandler, MessageUpdateRule {

    private final GroupAuthorizationService authService;
    private final CommandHandler commandHandler;
    private final AudioHandler audioHandler;

    @Override
    public Matcher<Message> matcher() {
        return message -> true;
    }

    @Override
    public UpdateHandler handler() {
        return this;
    }

    @Override
    public void handle(Update update) {
        if (!authService.isAuthorized(update)) {
            return;
        }

        if (update.message() != null) {
            long chatId = update.message().chat().id();

            if (update.message().audio() != null || update.message().voice() != null) {
                log.info(
                        "🎙️ Mídia de áudio detectada no chat {}. Enviando para AudioHandler.",
                        chatId);
                audioHandler.handleAudioUpdate(update);
            } else if (update.message().text() != null) {
                String text = update.message().text();
                // Log apenas se for comando (inicia com /, t1000, t-1000) ou contém link
                if (text.startsWith("/") || text.toLowerCase().matches("t-?1000.*")) {
                    log.info("📝 Comando detectado no chat {}: '{}'", chatId, text);
                } else if (text.contains("http://") || text.contains("https://")) {
                    log.info("🔗 Link detectado no chat {}: '{}'", chatId, text);
                } else {
                    log.debug("📝 Texto ignorado no chat {}: '{}'", chatId, text);
                }
                commandHandler.handleTextUpdate(update);
            }
        }
    }
}
