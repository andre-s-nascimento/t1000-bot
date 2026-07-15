package net.ddns.adambravo79.tmill.telegram.core;

import com.pengrad.telegrambot.TelegramException;

/**
 * Interface funcional que representa um remetente de mensagens para o Telegram.
 *
 * <p>Usada como fallback ou executor seguro em {@link TelegramSafeExecutor}.
 */
@FunctionalInterface
public interface TelegramSender {

    /**
     * Envia uma mensagem para um chat específico no Telegram.
     *
     * @param chatId identificador único do chat.
     * @param mensagem conteúdo da mensagem.
     * @throws Exception se ocorrer falha na chamada à API do Telegram.
     */
    void enviar(long chatId, String mensagem) throws TelegramException;
}
