package net.ddns.adambravo79.tmill.telegram.exception;

/** Exceção base para erros relacionados ao Telegram. */
public class TelegramBotException extends RuntimeException {
    public TelegramBotException(String message) {
        super(message);
    }

    public TelegramBotException(String message, Throwable cause) {
        super(message, cause);
    }
}
