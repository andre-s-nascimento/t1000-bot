/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO que representa uma mensagem retornada pelo modelo de chat.
 *
 * <p>Campos: - role: papel da mensagem (ex.: "user", "assistant", "system"). - content: conteúdo
 * textual da mensagem.
 *
 * <p>Usado dentro de {@link Choice} e {@link ChatCompletionResponse} para mapear as respostas
 * geradas pelo modelo.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Message(String role, String content) {}
