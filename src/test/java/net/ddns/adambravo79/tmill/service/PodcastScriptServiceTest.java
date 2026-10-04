/* (c) 2026 | 01/10/2026 */
package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import net.ddns.adambravo79.tmill.client.GroqClient;
import net.ddns.adambravo79.tmill.service.prompt.PromptRegistryService;

@ExtendWith(MockitoExtension.class)
class PodcastScriptServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private GroqClient groqClient;
    @Mock private PromptRegistryService promptRegistryService;

    private PodcastScriptService service;

    @BeforeEach
    void setUp() {
        service = new PodcastScriptService(jdbcTemplate, groqClient, promptRegistryService);

        ReflectionTestUtils.setField(service, "targetUserId", 123456L);
        ReflectionTestUtils.setField(service, "maxTokens", 3000);
        ReflectionTestUtils.setField(service, "digestModel", "llama-model");
    }

    @Test
    @DisplayName("Deve retornar null quando não houver mensagens para o período")
    void shouldReturnNullWhenNoMessagesFound() {
        LocalDate start = LocalDate.now().minusDays(7);
        LocalDate end = LocalDate.now();

        when(jdbcTemplate.queryForList(
                        Mockito.anyString(), eq(String.class), eq(123456L), eq(start), eq(end)))
                .thenReturn(List.of());

        String result = service.generateScript(start, end);

        assertThat(result).isNull();
        Mockito.verifyNoInteractions(promptRegistryService, groqClient);
    }

    @Test
    @DisplayName(
            "Deve gerar o roteiro do podcast consumindo o System e User Prompts do"
                    + " PromptRegistryService")
    void shouldGeneratePodcastScriptSuccessfully() {
        LocalDate start = LocalDate.now().minusDays(7);
        LocalDate end = LocalDate.now();
        when(promptRegistryService.getPodcastTemperature()).thenReturn(0.7);

        when(jdbcTemplate.queryForList(
                        Mockito.anyString(), eq(String.class), eq(123456L), eq(start), eq(end)))
                .thenReturn(List.of("Áudio 1", "Áudio 2"));

        when(promptRegistryService.getPodcastSystemPrompt())
                .thenReturn("System Prompt do Podcast Carregado");

        // 🔧 FIX: Mockar a chamada do novo userPrompt passando a string combinada
        when(promptRegistryService.getPodcastUserPrompt("Áudio 1\n---\nÁudio 2"))
                .thenReturn("User Prompt Completo com: Áudio 1\n---\nÁudio 2");

        when(groqClient.chatCompletion(
                        "System Prompt do Podcast Carregado",
                        "User Prompt Completo com: Áudio 1\n---\nÁudio 2",
                        "llama-model",
                        0.7,
                        3000))
                .thenReturn("Roteiro Final do Podcast");

        String result = service.generateScript(start, end);

        assertThat(result).isEqualTo("Roteiro Final do Podcast");
        verify(promptRegistryService).getPodcastSystemPrompt();
        verify(promptRegistryService).getPodcastUserPrompt(Mockito.anyString());
    }
}
