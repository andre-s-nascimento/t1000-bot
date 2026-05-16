/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO que representa os provedores de streaming disponíveis para um filme, organizados por país.
 *
 * <p>Campos: - results: mapa de países (código ISO, ex.: "US", "BR") para seus respectivos
 * provedores ({@link CountryProviders}).
 *
 * <p>Usado para mapear o endpoint `/movie/{id}/watch/providers` da API do TMDB.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WatchProviderResponse(
        @JsonProperty("results") Map<String, CountryProviders> results) {}
