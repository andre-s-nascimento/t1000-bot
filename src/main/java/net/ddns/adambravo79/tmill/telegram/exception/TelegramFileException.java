/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.telegram.exception;

/**
 * Exceção customizada para erros relacionados ao download de arquivos do Telegram.
 *
 * <p>Usada pelo {@link net.ddns.adambravo79.tmill.service.TelegramFileService} para sinalizar
 * falhas ao obter ou salvar arquivos recebidos via API do Telegram.
 */
public class TelegramFileException extends RuntimeException {

    /**
     * Cria uma nova instância da exceção.
     *
     * @param message mensagem descritiva do erro.
     * @param cause causa raiz da falha (pode ser {@code null}).
     */
    public TelegramFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
