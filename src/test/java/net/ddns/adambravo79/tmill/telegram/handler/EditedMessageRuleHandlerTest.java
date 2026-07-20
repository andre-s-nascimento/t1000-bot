package net.ddns.adambravo79.tmill.telegram.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;

import io.ksilisk.telegrambot.core.matcher.Matcher;

@ExtendWith(MockitoExtension.class)
class EditedMessageRuleHandlerTest {

    @InjectMocks private EditedMessageRuleHandler handler;

    @Test
    void matcher_deveRetornarTrueQuandoUpdateTemEditedMessage() {
        Update update = mock(Update.class);
        when(update.editedMessage()).thenReturn(mock(Message.class));

        Matcher<Update> matcher = handler.matcher();
        assertThat(matcher.match(update)).isTrue();
    }

    @Test
    void matcher_deveRetornarFalseQuandoUpdateNaoTemEditedMessage() {
        Update update = mock(Update.class);
        when(update.editedMessage()).thenReturn(null);

        Matcher<Update> matcher = handler.matcher();
        assertThat(matcher.match(update)).isFalse();
    }

    @Test
    void matcher_deveRetornarFalseQuandoUpdateForNull() {
        Matcher<Update> matcher = handler.matcher();
        assertThat(matcher.match(null)).isFalse();
    }

    @Test
    void handler_deveRetornarProprioObjeto() {
        assertThat(handler.handler()).isSameAs(handler);
    }

    @Test
    void handle_deveExecutarSemErroParaMensagemEditada() {
        Update update = mock(Update.class);
        Message edited = mock(Message.class);
        when(update.editedMessage()).thenReturn(edited);
        when(edited.text()).thenReturn("texto editado");

        assertThatCode(() -> handler.handle(update)).doesNotThrowAnyException();
        // Pode verificar log se necessário
    }

    @Test
    void handle_deveExecutarSemErroQuandoNaoHaMensagemEditada() {
        Update update = mock(Update.class);
        when(update.editedMessage()).thenReturn(null);

        assertThatCode(() -> handler.handle(update)).doesNotThrowAnyException();
    }
}
