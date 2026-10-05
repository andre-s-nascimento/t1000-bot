/* (c) 2026 | 01/10/2026 */
package net.ddns.adambravo79.tmill.service.digest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import net.ddns.adambravo79.tmill.prompt.DigestPersona;
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
    @DisplayName("Deve delegar a construção do System Prompt para a persona informada por String")
    void shouldBuildSystemPromptForGivenPersonaString() {
        when(promptRegistryService.getDigestSystemPrompt("EDGARD_EDGARDINO", "MANHÃ"))
                .thenReturn("Prompt do Edgardino");

        String result = digestPromptFactory.buildSystemPrompt("EDGARD_EDGARDINO", "MANHÃ");

        assertThat(result).isEqualTo("Prompt do Edgardino");
        verify(promptRegistryService).getDigestSystemPrompt("EDGARD_EDGARDINO", "MANHÃ");
    }

    @Test
    @DisplayName("Deve delegar a construção do System Prompt para o objeto DigestPersona")
    void shouldBuildSystemPromptForGivenDigestPersona() {
        when(promptRegistryService.getDigestSystemPrompt("T1000", "MANHÃ"))
                .thenReturn("Prompt do T1000");

        String result = digestPromptFactory.buildSystemPrompt(DigestPersona.T1000, "MANHÃ");

        assertThat(result).isEqualTo("Prompt do T1000");
        verify(promptRegistryService).getDigestSystemPrompt("T1000", "MANHÃ");
    }

    @Test
    @DisplayName("Deve usar getActivePersonaName como fallback se personaName for nula ou vazia")
    void shouldFallbackToActivePersonaWhenPersonaNameIsBlank() {
        // 🔧 Mock do novo comportamento dinâmico
        when(promptRegistryService.getActivePersonaName()).thenReturn("T1000");

        when(promptRegistryService.getDigestSystemPrompt("T1000", "DEFAULT"))
                .thenReturn("Prompt Padrão T1000");

        String resultNull = digestPromptFactory.buildSystemPrompt((String) null, "DEFAULT");
        String resultBlank = digestPromptFactory.buildSystemPrompt("  ", "DEFAULT");

        assertThat(resultNull).isEqualTo("Prompt Padrão T1000");
        assertThat(resultBlank).isEqualTo("Prompt Padrão T1000");

        verify(promptRegistryService, times(2)).getActivePersonaName();
        verify(promptRegistryService, times(2)).getDigestSystemPrompt("T1000", "DEFAULT");
    }

    @Test
    @DisplayName("Deve usar getActivePersonaName quando a DigestPersona for nula")
    void shouldFallbackToActivePersonaWhenDigestPersonaIsNull() {
        when(promptRegistryService.getActivePersonaName()).thenReturn("T1000");

        when(promptRegistryService.getDigestSystemPrompt("T1000", "MANHÃ"))
                .thenReturn("Prompt Padrão T1000");

        String result = digestPromptFactory.buildSystemPrompt((DigestPersona) null, "MANHÃ");

        assertThat(result).isEqualTo("Prompt Padrão T1000");
        verify(promptRegistryService).getActivePersonaName();
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
