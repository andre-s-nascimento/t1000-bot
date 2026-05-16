/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.exception;

/**
 * Exceção lançada quando um filme não é encontrado no TMDB.
 *
 * <p>Permite incluir contexto adicional (ex.: query de busca) para enriquecer os logs e facilitar a
 * depuração.
 */
public class MovieNotFoundException extends RuntimeException {

    private final String contexto;

    public MovieNotFoundException(String message) {
        super(message);
        this.contexto = null;
    }

    public MovieNotFoundException(String message, Throwable cause) {
        super(message, cause);
        this.contexto = null;
    }

    public MovieNotFoundException(String message, String contexto, Throwable cause) {
        super(message, cause);
        this.contexto = contexto;
    }

    public String getContexto() {
        return contexto;
    }

    @Override
    public String toString() {
        return "MovieNotFoundException{message=" + getMessage() + ", contexto=" + contexto + "}";
    }
}
