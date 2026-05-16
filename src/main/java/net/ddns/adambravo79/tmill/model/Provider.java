/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO que representa um provedor de streaming disponível para um filme ou série.
 *
 * <p>Campos: - name: nome do provedor (ex.: Netflix, Amazon Prime). - id: identificador único do
 * provedor no TMDB. - logoPath: caminho relativo para o logo do provedor.
 *
 * <p>Usado dentro de {@link CountryProviders} e {@link WatchProviderResponse} para mapear os
 * provedores disponíveis por país.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Provider(
        @JsonProperty("provider_name") String name,
        @JsonProperty("provider_id") Integer id,
        @JsonProperty("logo_path") String logoPath) {}
