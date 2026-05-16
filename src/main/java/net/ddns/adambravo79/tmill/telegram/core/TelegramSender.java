/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.telegram.core;

import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Interface funcional que representa um remetente de mensagens para o Telegram.
 *
 * <p>Usada como fallback ou executor seguro em {@link TelegramSafeExecutor}, permitindo enviar
 * mensagens mesmo em caso de falha na ação principal.
 *
 * <p>Exemplo de uso:
 *
 * <pre>{@code
 * TelegramSender sender = (chatId, mensagem) -> telegramBot.execute(
 *     SendMessage.builder().chatId(String.valueOf(chatId)).text(mensagem).build()
 * );
 * }</pre>
 */
@FunctionalInterface
public interface TelegramSender {

    /**
     * Envia uma mensagem para um chat específico no Telegram.
     *
     * @param chatId identificador único do chat.
     * @param mensagem conteúdo da mensagem.
     * @throws TelegramApiException se ocorrer falha na chamada à API do Telegram.
     */
    void enviar(long chatId, String mensagem) throws TelegramApiException;
}
