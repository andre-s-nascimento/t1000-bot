package net.ddns.adambravo79.tmill.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class TranscriptStoreServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks private TranscriptStoreService service;

    @Test
    void saveTranscriptWithRaw_deveExecutarInsertComSucesso() {
        long chatId = 12345L;
        long userId = 999L;
        String userName = "Usuário Teste";
        String rawText = "Texto bruto";
        String refinedText = "Texto refinado";

        service.saveTranscriptWithRaw(chatId, userId, userName, rawText, refinedText);

        verify(jdbcTemplate)
                .update(
                        anyString(),
                        eq(chatId),
                        eq(userId),
                        eq(userName),
                        eq(refinedText),
                        eq(rawText));
    }

    @Test
    void saveTranscript_deveExecutarInsertSemRawComSucesso() {
        long chatId = 12345L;
        long userId = 999L;
        String userName = "Usuário Teste";
        String text = "Texto da transcrição";

        service.saveTranscript(chatId, userId, userName, text);

        verify(jdbcTemplate).update(anyString(), eq(chatId), eq(userId), eq(userName), eq(text));
    }

    @Test
    void saveTranscriptWithRaw_deveCapturarExcecaoESemPropagar() {
        long chatId = 12345L;
        long userId = 999L;
        String userName = "Usuário Teste";
        String rawText = "Texto bruto";
        String refinedText = "Texto refinado";

        doThrow(new RuntimeException("Erro no banco"))
                .when(jdbcTemplate)
                .update(anyString(), anyLong(), anyLong(), anyString(), anyString(), anyString());

        // Não deve lançar exceção
        service.saveTranscriptWithRaw(chatId, userId, userName, rawText, refinedText);

        verify(jdbcTemplate)
                .update(
                        anyString(),
                        eq(chatId),
                        eq(userId),
                        eq(userName),
                        eq(refinedText),
                        eq(rawText));
    }

    @Test
    void saveTranscript_deveCapturarExcecaoESemPropagar() {
        long chatId = 12345L;
        long userId = 999L;
        String userName = "Usuário Teste";
        String text = "Texto da transcrição";

        doThrow(new RuntimeException("Erro no banco"))
                .when(jdbcTemplate)
                .update(anyString(), anyLong(), anyLong(), anyString(), anyString());

        // Não deve lançar exceção
        service.saveTranscript(chatId, userId, userName, text);

        verify(jdbcTemplate).update(anyString(), eq(chatId), eq(userId), eq(userName), eq(text));
    }
}
