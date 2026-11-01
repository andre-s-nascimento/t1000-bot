/* (c) 2026 */
package net.ddns.adambravo79.tmill.telegram.handler;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;

import io.ksilisk.telegrambot.core.executor.TelegramBotExecutor;
import io.ksilisk.telegrambot.core.handler.update.command.CommandUpdateHandler;
import io.ksilisk.telegrambot.core.update.Updates;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.telegram.core.GroupAuthorizationService;

@Component
@Slf4j
public class HelpCommandHandler implements CommandUpdateHandler {

    private final TelegramBotExecutor executor;
    private final GroupAuthorizationService authService;

    public HelpCommandHandler(TelegramBotExecutor executor, GroupAuthorizationService authService) {
        this.executor = executor;
        this.authService = authService;
        log.info("✅ HelpCommandHandler registrado");
    }

    @Override
    public void handle(Update update) {
        // Verifica autorização (se for grupo não autorizado, ignora)
        if (!authService.isAuthorized(update)) {
            return;
        }

        long chatId = Updates.chatId(update);

        // Envia a mensagem formatada usando a constante centralizada
        executor.execute(new SendMessage(chatId, BotMessages.HELP_TEXT).parseMode(ParseMode.HTML));
    }

    @Override
    public Set<String> commands() {
        return Set.of("/help", "/ajuda", "/start");
    }
}
