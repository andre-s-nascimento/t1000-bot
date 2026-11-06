/* (c) 2026 | 05/11/2026 */
package net.ddns.adambravo79.tmill.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.ddns.adambravo79.tmill.model.TorrentLaunch;

/**
 * Espelho do arquivo {@code torrent-lancamentos.json}.
 *
 * <p>Estrutura raiz do JSON com metadados (versão, mês de referência, ano) e a lista de
 * lançamentos.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TorrentJson(
        @JsonProperty("versao") int versao,
        @JsonProperty("mes_referencia") String mesReferencia,
        @JsonProperty("ano") int ano,
        @JsonProperty("lancamentos") List<TorrentLaunch> lancamentos) {}
