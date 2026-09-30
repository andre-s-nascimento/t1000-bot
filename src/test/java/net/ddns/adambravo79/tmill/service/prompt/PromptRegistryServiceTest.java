/* (c) 2026 | 30/09/2026 */
package net.ddns.adambravo79.tmill.service.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import net.ddns.adambravo79.tmill.service.config.JsonConfigLoader;

@ExtendWith(MockitoExtension.class)
class PromptRegistryServiceTest {

    @Mock private JsonConfigLoader jsonConfigLoader;

    private PromptRegistryService promptRegistryService;

    @BeforeEach
    void setUp() {
        Map<String, Object> initialPrompts =
                Map.of("defaultPersona", "Especialista em resumos", "maxTokens", 1500);

        when(jsonConfigLoader.loadConfig(eq("prompts/digest-personas.json"), eq(Map.class), any()))
                .thenReturn(Optional.of(initialPrompts));

        promptRegistryService =
                new PromptRegistryService(jsonConfigLoader, "custom/path/prompts.json");
    }

    @Test
    @DisplayName("Deve inicializar e carregar prompts com sucesso")
    void shouldInitializeAndLoadPromptsSuccessfully() {
        Map<String, Object> allPrompts = promptRegistryService.getAllPrompts();

        assertThat(allPrompts).hasSize(2);
        assertThat(allPrompts).containsEntry("defaultPersona", "Especialista em resumos");
    }

    @Test
    @DisplayName("Deve obter prompt com tipagem correta através de getPrompt")
    void shouldGetPromptWithCorrectType() {
        Optional<String> persona = promptRegistryService.getPrompt("defaultPersona", String.class);
        Optional<Integer> tokens = promptRegistryService.getPrompt("maxTokens", Integer.class);
        Optional<String> nonExistent = promptRegistryService.getPrompt("unknownKey", String.class);
        Optional<Integer> wrongType =
                promptRegistryService.getPrompt("defaultPersona", Integer.class);

        assertThat(persona).contains("Especialista em resumos");
        assertThat(tokens).contains(1500);
        assertThat(nonExistent).isEmpty();
        assertThat(wrongType).isEmpty();
    }

    @Test
    @DisplayName("Deve recarregar os prompts quando reload() for chamado")
    void shouldReloadPromptsWhenReloadIsCalled() {
        Map<String, Object> updatedPrompts = Map.of("defaultPersona", "Nova Persona Recarregada");
        when(jsonConfigLoader.loadConfig(eq("prompts/digest-personas.json"), eq(Map.class), any()))
                .thenReturn(Optional.of(updatedPrompts));

        promptRegistryService.reload();

        Optional<String> persona = promptRegistryService.getPrompt("defaultPersona", String.class);
        assertThat(persona).contains("Nova Persona Recarregada");
        verify(jsonConfigLoader, atLeastOnce())
                .loadConfig(eq("prompts/digest-personas.json"), eq(Map.class), any());
    }

    @Test
    @DisplayName("Deve lidar graciosamente quando nenhum prompt for retornado pelo loader")
    void shouldHandleEmptyResultFromLoader() {
        when(jsonConfigLoader.loadConfig(eq("prompts/digest-personas.json"), eq(Map.class), any()))
                .thenReturn(Optional.empty());

        PromptRegistryService emptyService = new PromptRegistryService(jsonConfigLoader, null);

        assertThat(emptyService.getAllPrompts()).isEmpty();
    }
}
