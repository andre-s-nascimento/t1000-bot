/* (c) 2026 | 30/09/2026 */
package net.ddns.adambravo79.tmill.exception;

/**
 * Exceção lançada quando ocorre falha irrecuperável na leitura ou parse de um arquivo de
 * configuração JSON.
 */
public class ConfigLoadException extends RuntimeException {

    private final String resourcePath;

    public ConfigLoadException(String resourcePath, String message, Throwable cause) {
        super(String.format("Falha ao carregar config [%s]: %s", resourcePath, message), cause);
        this.resourcePath = resourcePath;
    }

    public ConfigLoadException(String resourcePath, String message) {
        super(String.format("Falha ao carregar config [%s]: %s", resourcePath, message));
        this.resourcePath = resourcePath;
    }

    public String getResourcePath() {
        return resourcePath;
    }
}
