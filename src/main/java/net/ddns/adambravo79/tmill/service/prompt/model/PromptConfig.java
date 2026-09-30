/* (c) 2026 | 30/09/2026 */
package net.ddns.adambravo79.tmill.service.prompt.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Objeto raiz para o arquivo de configuração de personas do digest (digest-personas.json).
 *
 * @param defaultPersona ID da persona a ser usada como fallback padrão
 * @param personas Lista de personas disponíveis
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PromptConfig(String defaultPersona, List<PersonaDefinition> personas) {}
