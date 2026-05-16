/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO que representa uma opção retornada pelo modelo de chat.
 *
 * <p>Campos: - message: mensagem associada à opção (conteúdo gerado pelo modelo).
 *
 * <p>Usado dentro de {@link ChatCompletionResponse} para mapear a lista de respostas possíveis.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Choice(Message message) {}
