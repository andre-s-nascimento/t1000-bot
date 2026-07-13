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
        log.info(
                "✉️ [MESSAGE] Update ID {} capturado no MessageUpdateRuleHandler",
                update.updateId());

        if (!authService.isAuthorized(update)) {
            log.warn("⛔ Update ID {} recusado por regras de autorização.", update.updateId());
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
                log.info(
                        "📝 Texto detectado no chat {}: '{}'. Enviando para CommandHandler.",
                        chatId,
                        update.message().text());
                commandHandler.handleTextUpdate(update);
            }
        }
    }
}
