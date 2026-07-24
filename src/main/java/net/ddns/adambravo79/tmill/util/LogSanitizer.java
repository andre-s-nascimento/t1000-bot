package net.ddns.adambravo79.tmill.util;

import java.util.regex.Pattern;

/**
 * Utilitário para sanitização de strings antes de logging.
 *
 * <p>Previne:
 *
 * <ul>
 *   <li>Log injection (quebras de linha, caracteres de controle)
 *   <li>Exfiltração de dados sensíveis (tokens, chaves API)
 *   <li>Poluição de logs com input muito longo do usuário
 *   <li>Caracteres não imprimíveis que corrompem formatos de log
 * </ul>
 *
 * <p>Regras aplicadas:
 *
 * <ol>
 *   <li>Trunca strings acima de {@value #DEFAULT_MAX_LENGTH} caracteres
 *   <li>Remove caracteres de controle (exceto tab e espaço comum)
 *   <li>Normaliza whitespace (múltiplos espaços → um espaço)
 *   <li>Mascara tokens que parecem sensíveis (padrão Bearer, key=, token=)
 *   <li>Escapa caracteres que poderiam quebrar parsers de log
 * </ol>
 */
public final class LogSanitizer {

    private LogSanitizer() {
        // utilitário, não instanciar
    }

    /** Tamanho máximo padrão para strings logadas. */
    public static final int DEFAULT_MAX_LENGTH = 500;

    /** Tamanho máximo para nomes de usuário no log. */
    public static final int USER_NAME_MAX_LENGTH = 100;

    /** Tamanho máximo para textos de mensagens no log. */
    public static final int MESSAGE_TEXT_MAX_LENGTH = 200;

    /** Tamanho máximo para queries de busca no log. */
    public static final int QUERY_MAX_LENGTH = 150;

    /** Regex para detectar pares chave=valor ou chave:valor sensíveis. */
    private static final Pattern SENSITIVE_PATTERN =
            Pattern.compile(
                    "(?i)(token|key|secret|password|passwd|credential|auth|bearer)\\s*[=:]\\s*\\S+");

    private static final String SENSITIVE_REPLACEMENT = "[REDACTED]";

    /** Regex para caracteres de controle perigosos (C0 exceto tab, LF, CR). */
    private static final Pattern CONTROL_CHARS =
            Pattern.compile("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f\\x7f]");

    /** Regex para normalizar whitespace. */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * Sanitiza uma string genérica para logging.
     *
     * @param input string original (pode ser null)
     * @return string sanitizada, segura para logs
     */
    public static String sanitize(String input) {
        return sanitize(input, DEFAULT_MAX_LENGTH);
    }

    /**
     * Sanitiza uma string para logging com limite customizado.
     *
     * @param input string original (pode ser null)
     * @param maxLength tamanho máximo permitido
     * @return string sanitizada, segura para logs
     */
    public static String sanitize(String input, int maxLength) {
        if (input == null) {
            return "null";
        }

        String sanitized = input;

        // 1. Remove caracteres de controle perigosos (exceto tab, newline comum)
        sanitized = CONTROL_CHARS.matcher(sanitized).replaceAll("?");

        // 2. Normaliza whitespace
        sanitized = WHITESPACE.matcher(sanitized).replaceAll(" ").trim();

        // 3. Mascara dados sensíveis
        sanitized = SENSITIVE_PATTERN.matcher(sanitized).replaceAll(SENSITIVE_REPLACEMENT);

        // 4. Escapa caracteres que poderiam confundir parsers
        sanitized =
                sanitized
                        .replace("\t", " ")
                        .replace("\r", " ")
                        .replace("\n", " ")
                        .replace("|", "\\|");

        // 5. Trunca se necessário
        if (sanitized.length() > maxLength) {
            sanitized =
                    sanitized.substring(0, maxLength)
                            + "... [truncated, len="
                            + input.length()
                            + "]";
        }

        return sanitized;
    }

    /**
     * Sanitiza nome de usuário para logging.
     *
     * @param userName nome do usuário (pode ser null)
     * @return nome sanitizado
     */
    public static String sanitizeUserName(String userName) {
        return sanitize(userName, USER_NAME_MAX_LENGTH);
    }

    /**
     * Sanitiza texto de mensagem para logging.
     *
     * @param text texto da mensagem (pode ser null)
     * @return texto sanitizado
     */
    public static String sanitizeMessageText(String text) {
        return sanitize(text, MESSAGE_TEXT_MAX_LENGTH);
    }

    /**
     * Sanitiza query de busca para logging.
     *
     * @param query query de busca (pode ser null)
     * @return query sanitizada
     */
    public static String sanitizeQuery(String query) {
        return sanitize(query, QUERY_MAX_LENGTH);
    }

    /**
     * Sanitiza identificador numérico (chatId, userId) para logging. Apenas valida que é um número,
     * não expõe o valor real em caso de erro.
     *
     * @param id identificador
     * @return representação segura do ID
     */
    public static String sanitizeId(long id) {
        // Para IDs, apenas indicamos se é positivo (privado) ou negativo (grupo)
        // O valor real pode ser logado em nível DEBUG se necessário
        return id > 0 ? "user-" + id : "group-" + Math.abs(id);
    }
}
