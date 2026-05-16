/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.exception;

/**
 * Exceção lançada quando ocorre falha ao manipular arquivos recebidos do Telegram.
 *
 * <p>Permite incluir contexto adicional (ex.: fileId, chatId) para enriquecer os logs e facilitar a
 * depuração.
 */
public class TelegramFileException extends RuntimeException {

    private final String contexto;

    public TelegramFileException(String message, Throwable cause) {
        super(message, cause);
        this.contexto = null;
    }

    public TelegramFileException(String message, String contexto, Throwable cause) {
        super(message, cause);
        this.contexto = contexto;
    }

    public String getContexto() {
        return contexto;
    }

    @Override
    public String toString() {
        return "TelegramFileException{message=" + getMessage() + ", contexto=" + contexto + "}";
    }
}
