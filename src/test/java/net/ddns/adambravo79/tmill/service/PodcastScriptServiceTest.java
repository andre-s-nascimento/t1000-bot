/* (c) 2026 | 01/10/2026 */
package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;

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

    private PodcastScriptService podcastScriptService;

    @BeforeEach
    void setUp() {
        podcastScriptService =
                new PodcastScriptService(jdbcTemplate, groqClient, promptRegistryService);

        ReflectionTestUtils.setField(podcastScriptService, "targetUserId", 123456L);
        ReflectionTestUtils.setField(podcastScriptService, "maxTokens", 3000);
        ReflectionTestUtils.setField(podcastScriptService, "digestModel", "llama-model");
    }

    @Test
    @DisplayName("Deve retornar null quando não houver mensagens para o período")
    void shouldReturnNullWhenNoMessagesFound() {
        LocalDate start = LocalDate.now().minusDays(7);
        LocalDate end = LocalDate.now();

        Mockito.when(
                        jdbcTemplate.queryForList(
                                Mockito.anyString(),
                                eq(String.class),
                                eq(123456L),
                                eq(start),
                                eq(end)))
                .thenReturn(List.of());

        String result = podcastScriptService.generateScript(start, end);

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

        Mockito.when(
                        jdbcTemplate.queryForList(
                                Mockito.anyString(),
                                eq(String.class),
                                eq(123456L),
                                eq(start),
                                eq(end)))
                .thenReturn(List.of("Áudio 1", "Áudio 2"));

        Mockito.when(promptRegistryService.getPodcastSystemPrompt())
                .thenReturn("System Prompt do Podcast Carregado");

        // 🔧 FIX: Mockar a chamada do novo userPrompt passando a string combinada
        Mockito.when(promptRegistryService.getPodcastUserPrompt("Áudio 1\n---\nÁudio 2"))
                .thenReturn("User Prompt Completo com: Áudio 1\n---\nÁudio 2");

        Mockito.when(
                        groqClient.chatCompletion(
                                eq("System Prompt do Podcast Carregado"),
                                eq("User Prompt Completo com: Áudio 1\n---\nÁudio 2"),
                                eq("llama-model"),
                                eq(0.7),
                                eq(3000)))
                .thenReturn("Roteiro Final do Podcast");

        String result = podcastScriptService.generateScript(start, end);

        assertThat(result).isEqualTo("Roteiro Final do Podcast");
        Mockito.verify(promptRegistryService).getPodcastSystemPrompt();
        Mockito.verify(promptRegistryService).getPodcastUserPrompt(Mockito.anyString());
    }
}
