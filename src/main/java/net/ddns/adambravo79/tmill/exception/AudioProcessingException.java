/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.exception;

/**
 * Exceção lançada quando ocorre falha no processamento de áudio.
 *
 * <p>Permite incluir um contexto adicional (ex.: fileId, chatId) para enriquecer os logs e
 * facilitar a depuração.
 */
public class AudioProcessingException extends RuntimeException {

    private final String contexto;

    public AudioProcessingException(String message) {
        super(message);
        this.contexto = null;
    }

    public AudioProcessingException(String message, Throwable cause) {
        super(message, cause);
        this.contexto = null;
    }

    public AudioProcessingException(String message, String contexto, Throwable cause) {
        super(message, cause);
        this.contexto = contexto;
    }

    public String getContexto() {
        return contexto;
    }

    @Override
    public String toString() {
        return "AudioProcessingException{message=" + getMessage() + ", contexto=" + contexto + "}";
    }
}
