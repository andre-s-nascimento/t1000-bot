/* (c) 2026 | 28/09/2026 */
package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import net.ddns.adambravo79.tmill.client.GroqClient;

@ExtendWith(MockitoExtension.class)
class PodcastScriptServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private GroqClient groqClient;

    @InjectMocks private PodcastScriptService service;

    private static final LocalDate START = LocalDate.of(2026, 9, 21);
    private static final LocalDate END = LocalDate.of(2026, 9, 27);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "targetUserId", 5003155230L);
        ReflectionTestUtils.setField(service, "maxTokens", 3000);
        ReflectionTestUtils.setField(service, "digestModel", "llama-3.1-8b-instant");
    }

    @Test
    @DisplayName("generateScript retorna null quando não há transcrições")
    void generateScript_noTranscripts_returnsNull() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), anyLong(), any(), any()))
                .thenReturn(List.of());

        String result = service.generateScript(START, END);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("generateScript retorna roteiro quando há transcrições")
    void generateScript_withTranscripts_returnsScript() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), anyLong(), any(), any()))
                .thenReturn(List.of("Áudio 1", "Áudio 2", "Áudio 3"));
        when(groqClient.chatCompletion(
                        anyString(), anyString(), anyString(), anyDouble(), anyInt()))
                .thenReturn("Roteiro do podcast");

        String result = service.generateScript(START, END);

        assertThat(result).isEqualTo("Roteiro do podcast");
        verify(groqClient)
                .chatCompletion(
                        anyString(),
                        anyString(),
                        eq("llama-3.1-8b-instant"),
                        anyDouble(),
                        eq(3000));
    }

    @Test
    @DisplayName("generateScript limita a 20 mensagens mais recentes")
    void generateScript_limitsTo20Messages() {
        List<String> many = new java.util.ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            many.add("Áudio " + i);
        }
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), anyLong(), any(), any()))
                .thenReturn(many);
        when(groqClient.chatCompletion(
                        anyString(), anyString(), anyString(), anyDouble(), anyInt()))
                .thenReturn("Roteiro");

        String result = service.generateScript(START, END);

        assertThat(result).isEqualTo("Roteiro");
        // Verifica que o prompt contém a mensagem mais recente e não a mais antiga
        verify(groqClient)
                .chatCompletion(
                        anyString(),
                        org.mockito.ArgumentMatchers.argThat(
                                prompt ->
                                        prompt.contains("Áudio 30")
                                                && !prompt.contains("Áudio 1\n")),
                        anyString(),
                        anyDouble(),
                        anyInt());
    }

    @Test
    @DisplayName("generateScript trunca prompt muito longo")
    void generateScript_truncatesLongPrompt() {
        String longText = "a".repeat(20000);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), anyLong(), any(), any()))
                .thenReturn(List.of(longText));
        when(groqClient.chatCompletion(
                        anyString(), anyString(), anyString(), anyDouble(), anyInt()))
                .thenReturn("Roteiro truncado");

        String result = service.generateScript(START, END);

        assertThat(result).isEqualTo("Roteiro truncado");
        verify(groqClient)
                .chatCompletion(
                        anyString(),
                        org.mockito.ArgumentMatchers.argThat(
                                prompt -> prompt.contains("[corte por limite de contexto]")),
                        anyString(),
                        anyDouble(),
                        anyInt());
    }
}
