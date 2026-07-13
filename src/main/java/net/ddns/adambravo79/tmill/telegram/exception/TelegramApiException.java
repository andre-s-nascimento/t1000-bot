package net.ddns.adambravo79.tmill.telegram.exception;

/** Exceção para erros de chamadas à API do Telegram (rate limit, timeout, etc). */
public class TelegramApiException extends TelegramBotException {
    public TelegramApiException(String message) {
        super(message);
    }

    public TelegramApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
