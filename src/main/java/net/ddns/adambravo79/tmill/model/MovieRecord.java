/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO que representa os dados básicos de um filme retornado pela API do TMDB.
 *
 * <p>Campos: - id: identificador único do filme no TMDB. - title: título oficial do filme. -
 * releaseDate: data de lançamento (formato yyyy-MM-dd). - overview: sinopse do filme. - popularity:
 * índice de popularidade do filme. - voteAverage: média de votos dos usuários. - posterPath:
 * caminho relativo para o poster do filme. - originCountry: lista de países de origem (ex.:
 * ["US"]).
 *
 * <p>Usado em buscas e listagens de filmes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MovieRecord(
        @JsonProperty("id") Long id,
        String title,
        @JsonProperty("original_title") String originalTitle, // 🆕
        @JsonProperty("release_date") String releaseDate,
        String overview,
        Double popularity,
        @JsonProperty("vote_average") Double voteAverage,
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("origin_country") List<String> originCountry) {}
