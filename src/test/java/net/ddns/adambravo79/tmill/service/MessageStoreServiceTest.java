package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class MessageStoreServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks private MessageStoreService service;

    private static final long CHAT_ID = 12345L;
    private static final long USER_ID = 999L;
    private static final String USER_NAME = "Testador Silva";
    private static final String TEXT = "Mensagem de teste";

    @Test
    void deveSalvarMensagemComSucesso() {
        service.saveMessage(CHAT_ID, USER_ID, USER_NAME, TEXT);

        verify(jdbcTemplate)
                .update(
                        eq(
                                "INSERT INTO messages (chat_id, user_id, user_name, text) VALUES"
                                        + " (?, ?, ?, ?)"),
                        eq(CHAT_ID),
                        eq(USER_ID),
                        eq(USER_NAME),
                        eq(TEXT));
    }

    @Test
    void deveTratarExcecaoAoSalvar() {
        // Configura o mock para lançar exceção
        doThrow(new RuntimeException("Erro no banco"))
                .when(jdbcTemplate)
                .update(anyString(), any(Object[].class));

        // O método não deve lançar exceção (captura e loga)
        assertThatCode(() -> service.saveMessage(CHAT_ID, USER_ID, USER_NAME, TEXT))
                .doesNotThrowAnyException();

        verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }
}
