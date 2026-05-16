/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.model;

/**
 * DTO que transporta o texto formatado e a URL da foto de um filme.
 *
 * <p>Campos: - textoFormatado: descrição detalhada do filme (ex.: título, sinopse, elenco). -
 * urlFoto: URL da imagem oficial do filme (poster ou capa).
 *
 * <p>Usado pelo {@link net.ddns.adambravo79.tmill.service.MovieService} e pelo {@link
 * net.ddns.adambravo79.tmill.controller.TelegramController} para enviar respostas formatadas ao
 * usuário.
 */
public record MovieOrchestrationResponse(String textoFormatado, String urlFoto) {}
