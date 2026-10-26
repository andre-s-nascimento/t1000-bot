/* (c) 2026 */
package net.ddns.adambravo79.tmill.telegram.handler;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;

import io.ksilisk.telegrambot.core.executor.TelegramBotExecutor;
import io.ksilisk.telegrambot.core.handler.update.command.CommandUpdateHandler;
import io.ksilisk.telegrambot.core.update.Updates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.service.TelegramModerationService;
import net.ddns.adambravo79.tmill.telegram.core.GroupAuthorizationService;

@Slf4j
@Component
@RequiredArgsConstructor
public class MuteCommandHandler implements CommandUpdateHandler {

    private final TelegramBotExecutor executor;
    private final TelegramModerationService moderationService;
    private final GroupAuthorizationService authService;

    @Override
    public Set<String> commands() {
        return Set.of("/mute", "/silenciar", "/unmute", "/desmutar");
    }

    @Override
    public void handle(Update update) {
        // Verifica se o grupo é autorizado
        if (!authService.isAuthorized(update)) {
            return;
        }

        Message message = update.message();
        long chatId = Updates.chatId(update);
        String text = message.text().trim();

        // Ignora se for chat privado (o bot só modera grupos)
        if (chatId > 0) {
            SendMessage errorMsg =
                    new SendMessage(chatId, "❌ Este comando só pode ser usado em grupos.");
            executor.execute(errorMsg);
            return;
        }

        // Verifica se o comando foi uma resposta (reply)
        if (message.replyToMessage() == null || message.replyToMessage().from() == null) {
            SendMessage errorMsg =
                    new SendMessage(
                                    chatId,
                                    "❌ Você precisa <b>responder</b> a mensagem da pessoa que"
                                            + " deseja silenciar.")
                            .parseMode(ParseMode.HTML);
            executor.execute(errorMsg);
            return;
        }

        long targetUserId = message.replyToMessage().from().id();
        String targetName = message.replyToMessage().from().firstName();

        // Não permite mutar o próprio bot (substitua 7900L pelo ID real do seu bot)
        if (targetUserId == 7900L) {
            SendMessage errorMsg =
                    new SendMessage(chatId, "🤖 Eu não posso silenciar a mim mesmo!");
            executor.execute(errorMsg);
            return;
        }

        // Identifica se é MUTE ou UNMUTE
        boolean isUnmute = text.startsWith("/unmute") || text.startsWith("/desmutar");
        boolean isMute = text.startsWith("/mute") || text.startsWith("/silenciar");

        if (isUnmute) {
            processarUnmute(chatId, targetUserId, targetName);
        } else if (isMute) {
            processarMute(chatId, text, targetUserId, targetName);
        }
    }

    private void processarMute(long chatId, String text, long targetUserId, String targetName) {
        int durationMinutes = 0; // Padrão: Permanente

        // Tenta extrair o tempo do comando. Ex: "/mute 60"
        String[] parts = text.split("\\s+");
        if (parts.length > 1) {
            try {
                durationMinutes = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                // Se o usuário digitou algo errado, ignora e assume permanente
                durationMinutes = 0;
            }
        }

        boolean sucesso = moderationService.muteUser(chatId, targetUserId, durationMinutes);

        if (sucesso) {
            String tempoStr = durationMinutes > 0 ? durationMinutes + " minutos" : "permanente";
            String msg =
                    String.format(
                            "🔇 <b>%s</b> foi silenciado por <b>%s</b>.", targetName, tempoStr);

            SendMessage successMsg = new SendMessage(chatId, msg).parseMode(ParseMode.HTML);
            executor.execute(successMsg);

            log.info("Comando /mute executado: alvo={} tempo={}min", targetUserId, durationMinutes);
        } else {
            SendMessage failMsg =
                    new SendMessage(
                            chatId,
                            "❌ Falha ao silenciar. Verifique se eu sou admin e tenho permissão para"
                                    + " restringir membros.");
            executor.execute(failMsg);

            log.warn("Falha ao executar /mute no chat {}", chatId);
        }
    }

    private void processarUnmute(long chatId, long targetUserId, String targetName) {
        boolean sucesso = moderationService.unmuteUser(chatId, targetUserId);

        if (sucesso) {
            String msg = String.format("🔊 <b>%s</b> não está mais silenciado.", targetName);

            SendMessage successMsg = new SendMessage(chatId, msg).parseMode(ParseMode.HTML);
            executor.execute(successMsg);

            log.info("Comando /unmute executado: alvo={}", targetUserId);
        } else {
            SendMessage failMsg =
                    new SendMessage(
                            chatId,
                            "❌ Falha ao remover o silenciamento. Verifique se eu sou admin.");
            executor.execute(failMsg);

            log.warn("Falha ao executar /unmute no chat {}", chatId);
        }
    }
}
