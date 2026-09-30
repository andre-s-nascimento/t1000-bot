/* (c) 2026 | 30/09/2026 */
package net.ddns.adambravo79.tmill.service.prompt.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class PromptModelTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName(
            "Deve desserializar PromptConfig e PersonaDefinition ignorando propriedades"
                    + " desconhecidas")
    void shouldDeserializePromptConfigWithUnknownProperties() throws Exception {
        String json =
                """
                {
                    "defaultPersona": "T1000",
                    "extraRootField": "ignored",
                    "personas": [
                        {
                            "id": "T1000",
                            "name": "T-1000",
                            "systemPrompt": "Você é o T-1000...",
                            "description": "Frio e eficiente",
                            "unknownField": 123
                        }
                    ]
                }
                """;

        PromptConfig config = objectMapper.readValue(json, PromptConfig.class);

        assertThat(config).isNotNull();
        assertThat(config.defaultPersona()).isEqualTo("T1000");
        assertThat(config.personas()).hasSize(1);

        PersonaDefinition persona = config.personas().get(0);
        assertThat(persona.id()).isEqualTo("T1000");
        assertThat(persona.name()).isEqualTo("T-1000");
        assertThat(persona.systemPrompt()).isEqualTo("Você é o T-1000...");
        assertThat(persona.description()).isEqualTo("Frio e eficiente");
    }

    @Test
    @DisplayName("Deve desserializar ContextDefinition corretamente")
    void shouldDeserializeContextDefinition() throws Exception {
        String json =
                """
                {
                    "id": "MADRUGADA",
                    "prompt": "Resumo do período noturno",
                    "matchLabels": ["night", "madrugada"],
                    "unusedParam": true
                }
                """;

        ContextDefinition context = objectMapper.readValue(json, ContextDefinition.class);

        assertThat(context).isNotNull();
        assertThat(context.id()).isEqualTo("MADRUGADA");
        assertThat(context.prompt()).isEqualTo("Resumo do período noturno");
        assertThat(context.matchLabels()).containsExactly("night", "madrugada");
    }
}
