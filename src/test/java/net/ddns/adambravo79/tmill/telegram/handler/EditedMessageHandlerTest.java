package net.ddns.adambravo79.tmill.telegram.handler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;

@ExtendWith(MockitoExtension.class)
class EditedMessageHandlerTest {

    @InjectMocks private EditedMessageHandler handler;

    @Test
    void deveIgnorarMensagemEditadaSemErro() {
        Update update = mock(Update.class);
        Message edited = mock(Message.class);
        when(update.editedMessage()).thenReturn(edited);
        when(edited.text()).thenReturn("texto editado");

        assertThatCode(() -> handler.handle(update)).doesNotThrowAnyException();
        // Opcional: verificar log (com captura de log)
    }

    @Test
    void deveNaoFazerNadaSeNaoHouverMensagemEditada() {
        Update update = mock(Update.class);
        when(update.editedMessage()).thenReturn(null);

        assertThatCode(() -> handler.handle(update)).doesNotThrowAnyException();
    }
}
