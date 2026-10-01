/* (c) 2026 | 01/10/2026 */
package net.ddns.adambravo79.tmill.service.digest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import net.ddns.adambravo79.tmill.service.prompt.PromptRegistryService;

@ExtendWith(MockitoExtension.class)
class DigestPromptFactoryTest {

    @Mock private PromptRegistryService promptRegistryService;

    private DigestPromptFactory digestPromptFactory;

    @BeforeEach
    void setUp() {
        digestPromptFactory = new DigestPromptFactory(promptRegistryService);
    }

    @Test
    @DisplayName("Deve delegar a construção do System Prompt para a persona informada")
    void shouldBuildSystemPromptForGivenPersona() {
        when(promptRegistryService.getDigestSystemPrompt("EDGARD_EDGARDINO", "MANHÃ"))
                .thenReturn("Prompt do Edgardino");

        String result = digestPromptFactory.buildSystemPrompt("EDGARD_EDGARDINO", "MANHÃ");

        assertThat(result).isEqualTo("Prompt do Edgardino");
        verify(promptRegistryService).getDigestSystemPrompt("EDGARD_EDGARDINO", "MANHÃ");
    }

    @Test
    @DisplayName("Deve usar ANALISTA como fallback de persona se personaName for nula ou vazia")
    void shouldFallbackToAnalistaWhenPersonaNameIsBlank() {
        when(promptRegistryService.getDigestSystemPrompt("ANALISTA", "DEFAULT"))
                .thenReturn("Prompt Padrão Analista");

        String resultNull = digestPromptFactory.buildSystemPrompt(null, "DEFAULT");
        String resultBlank = digestPromptFactory.buildSystemPrompt("  ", "DEFAULT");

        assertThat(resultNull).isEqualTo("Prompt Padrão Analista");
        assertThat(resultBlank).isEqualTo("Prompt Padrão Analista");
        verify(promptRegistryService).getDigestSystemPrompt("ANALISTA", "DEFAULT");
    }

    @Test
    @DisplayName("Deve delegar a construção do User Prompt formatado")
    void shouldBuildUserPrompt() {
        when(promptRegistryService.getDigestUserPrompt("Notícias do dia"))
                .thenReturn("Notícias formatadas: Notícias do dia");

        String result = digestPromptFactory.buildUserPrompt("Notícias do dia");

        assertThat(result).isEqualTo("Notícias formatadas: Notícias do dia");
        verify(promptRegistryService).getDigestUserPrompt("Notícias do dia");
    }
}
