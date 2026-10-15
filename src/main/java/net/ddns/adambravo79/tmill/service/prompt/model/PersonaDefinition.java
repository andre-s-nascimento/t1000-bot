/* (c) 2026 | 30/09/2026 */
package net.ddns.adambravo79.tmill.service.prompt.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Representa a definição de uma persona de síntese de conteúdo/digest.
 *
 * @param id Identificador único da persona (ex: "T1000", "BICENTENNIAL")
 * @param name Nome legível/exibição
 * @param systemPrompt Instruções do sistema (System Prompt) passadas ao LLM
 * @param description Breve descrição do tom e estilo da persona
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PersonaDefinition(String id, String name, String systemPrompt, String description) {}
