/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO que representa os provedores de streaming disponíveis em um país específico.
 *
 * <p>Campos: - flatrate: lista de provedores que oferecem o filme em streaming (assinatura).
 *
 * <p>Usado dentro de {@link WatchProviderResponse} para mapear os provedores por país.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CountryProviders(@JsonProperty("flatrate") List<Provider> flatrate) {}
