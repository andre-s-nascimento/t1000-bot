package net.ddns.adambravo79.tmill.telegram.handler;

import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.model.Update;

import io.ksilisk.telegrambot.core.handler.update.UpdateHandler;
import io.ksilisk.telegrambot.core.matcher.Matcher;
import io.ksilisk.telegrambot.core.rule.UpdateRule;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class EditedMessageRuleHandler implements UpdateRule<Update>, UpdateHandler {

    @Override
    public Matcher<Update> matcher() {
        return update -> update != null && update.editedMessage() != null;
    }

    @Override
    public UpdateHandler handler() {
        return this;
    }

    @Override
    public void handle(Update update) {
        if (update.editedMessage() != null) {
            log.debug("📝 Mensagem editada recebida: {}", update.editedMessage().text());
            // Adicione lógica adicional se desejar
        }
    }
}
