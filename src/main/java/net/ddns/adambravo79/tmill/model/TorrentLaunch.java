/* (c) 2026 | 05/11/2026 */
package net.ddns.adambravo79.tmill.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Record que representa um lançamento do módulo Torrent.
 *
 * <p>Espelha um item do array {@code lancamentos} em {@code torrent-lancamentos.json}.
 *
 * <p>Campos:
 *
 * <ul>
 *   <li>{@code titulo}: nome do filme ou série.
 *   <li>{@code dataLancamento}: data no formato {@code DD/MM} (ex.: {@code "02/11"}).
 *   <li>{@code tipo}: {@code "filme"} ou {@code "serie"}.
 *   <li>{@code diretor}: nome do diretor (pode ser {@code null} para séries).
 *   <li>{@code atores}: lista de atores principais (pode ser vazia).
 *   <li>{@code letterboxdUrl}: URL completa no Letterboxd.
 *   <li>{@code temporada}: número da temporada (apenas para séries; {@code null} para filmes).
 * </ul>
 *
 * <p>Imutável, seguro para uso concorrente.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TorrentLaunch(
        @JsonProperty("titulo") String titulo,
        @JsonProperty("data_lancamento") String dataLancamento,
        @JsonProperty("tipo") String tipo,
        @JsonProperty("diretor") String diretor,
        @JsonProperty("atores") List<String> atores,
        @JsonProperty("letterboxd_url") String letterboxdUrl,
        @JsonProperty("temporada") Integer temporada) {

    /** Verifica se o lançamento é uma série. */
    public boolean isSerie() {
        return "serie".equalsIgnoreCase(tipo) || "série".equalsIgnoreCase(tipo);
    }

    /** Verifica se o lançamento é um filme. */
    public boolean isFilme() {
        return "filme".equalsIgnoreCase(tipo);
    }
}
