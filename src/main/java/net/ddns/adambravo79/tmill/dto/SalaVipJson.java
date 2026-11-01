package net.ddns.adambravo79.tmill.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Espelho do arquivo {@code src/main/resources/config/sala-vip.json}.
 *
 * <p>Versionado no repositório; lido pelo {@code SalaVipJsonLoader} no boot
 * e 1x/dia. NÃO é raspado do Letterboxd em runtime.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SalaVipJson(int version, String updatedAt, String listUrl, List<Item> filmes) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            int numero, int episodio, String slug, String titulo, int ano, String nota) {}
}
