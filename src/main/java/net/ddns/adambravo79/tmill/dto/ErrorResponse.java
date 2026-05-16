/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.dto;

import java.time.Instant;

/**
 * DTO padronizado para respostas de erro.
 *
 * <p>Campos: - erro: mensagem detalhada da exceção - tipo: tipo da exceção (classe) - timestamp:
 * momento em que o erro ocorreu
 *
 * <p>Esse objeto é retornado pelo {@link net.ddns.adambravo79.tmill.config.GlobalExceptionHandler}
 * para garantir consistência e rastreabilidade nos logs e nas respostas HTTP.
 */
public record ErrorResponse(String erro, String tipo, Instant timestamp) {}
