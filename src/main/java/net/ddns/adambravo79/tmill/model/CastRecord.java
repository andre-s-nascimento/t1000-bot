/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO que representa um membro do elenco de um filme.
 *
 * <p>Campos: - name: nome do ator/atriz - character: personagem interpretado
 *
 * <p>Usado para mapear a resposta da API do TMDB (endpoint de créditos).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CastRecord(String name, String character) {}
