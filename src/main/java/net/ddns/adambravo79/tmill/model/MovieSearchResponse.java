/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO que representa a resposta de busca de filmes na API do TMDB com suporte a paginação.
 *
 * <p>Campos: - page: número da página atual. - totalResults: total de resultados encontrados. -
 * totalPages: total de páginas disponíveis. - results: lista de filmes encontrados ({@link
 * MovieRecord}).
 *
 * <p>Usado para mapear o endpoint `/search/movie` da API do TMDB.
 */
public record MovieSearchResponse(
        int page,
        @JsonProperty("total_results") int totalResults,
        @JsonProperty("total_pages") int totalPages,
        List<MovieRecord> results) {}
