/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.telegram.core;

import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Interface funcional que representa uma ação executável contra a API do Telegram.
 *
 * <p>Permite encapsular chamadas que podem lançar {@link TelegramApiException}, facilitando o uso
 * em políticas de retry ou execução assíncrona.
 *
 * <p>Exemplo de uso:
 *
 * <pre>{@code
 * TelegramAction action = () -> telegramBot.execute(new SendMessage(chatId, "Olá!"));
 * }</pre>
 */
@FunctionalInterface
public interface TelegramAction {
    /**
     * Executa a ação definida.
     *
     * @throws TelegramApiException se ocorrer falha na chamada à API do Telegram.
     */
    void run() throws TelegramApiException;
}
