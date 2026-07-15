package net.ddns.adambravo79.tmill.telegram.handler;

import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.model.Update;

import io.ksilisk.telegrambot.core.handler.update.UpdateHandler;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class EditedMessageHandler implements UpdateHandler {
    @Override
    public void handle(Update update) {
        if (update.editedMessage() != null) {
            log.debug("Mensagem editada recebida (ignorada): {}", update.editedMessage().text());
        }
    }
}
