/* (c) 2026 | 30/09/2026 */
package net.ddns.adambravo79.tmill.service.prompt.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Representa a definição de um contexto de período (ex: Madrugada, Dia).
 *
 * @param id Identificador único do contexto (ex: "MADRUGADA", "DIA")
 * @param prompt Texto do prompt complementar associado ao período
 * @param matchLabels Rótulos ou gatilhos associados a esse contexto
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContextDefinition(String id, String prompt, List<String> matchLabels) {}
