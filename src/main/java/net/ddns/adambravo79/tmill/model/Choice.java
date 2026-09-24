/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO que representa uma opção retornada pelo modelo de chat.
 *
 * <p>Campos:
 *
 * <ul>
 *   <li>{@code message} — mensagem associada à opção.
 *   <li>{@code finishReason} — motivo pelo qual o modelo parou de gerar tokens. Valores possíveis:
 *       {@code "stop"}, {@code "length"}, {@code "content_filter"}, {@code "tool_calls"}, {@code
 *       "function_call"}.
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Choice(Message message, @JsonProperty("finish_reason") String finishReason) {

    /**
     * Construtor de conveniência para uso em testes (assume {@code finishReason = "stop"}).
     *
     * @param message mensagem associada à opção
     */
    public Choice(Message message) {
        this(message, "stop");
    }
}
