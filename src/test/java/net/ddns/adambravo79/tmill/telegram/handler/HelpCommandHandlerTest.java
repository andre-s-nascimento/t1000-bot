package net.ddns.adambravo79.tmill.telegram.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;

import io.ksilisk.telegrambot.core.executor.TelegramBotExecutor;
import net.ddns.adambravo79.tmill.telegram.core.GroupAuthorizationService;

@ExtendWith(MockitoExtension.class)
class HelpCommandHandlerTest {

    @Mock private TelegramBotExecutor executor;
    @Mock private GroupAuthorizationService authService;
    @InjectMocks private HelpCommandHandler handler;

    private Update update;

    @BeforeEach
    void setUp() {
        update = mock(Update.class);
    }

    @Test
    @DisplayName("handle: deve enviar a mensagem de ajuda formatada")
    void handle_deveEnviarAjuda() {
        when(authService.isAuthorized(update)).thenReturn(true);

        com.pengrad.telegrambot.model.Message message =
                mock(com.pengrad.telegrambot.model.Message.class);
        com.pengrad.telegrambot.model.Chat chat = mock(com.pengrad.telegrambot.model.Chat.class);
        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(123L);

        handler.handle(update);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(executor).execute(captor.capture());

        String texto = (String) captor.getValue().getParameters().get("text");
        assertThat(texto).contains("Central de Ajuda do T-1000").contains("t1000 buscar");
    }

    @Test
    @DisplayName("handle: não deve enviar se o grupo não for autorizado")
    void handle_naoAutorizado_naoEnvia() {
        when(authService.isAuthorized(update)).thenReturn(false);

        handler.handle(update);

        verify(executor, never()).execute(any());
    }

    @Test
    @DisplayName("commands: deve retornar os comandos de ajuda")
    void commands_deveRetornarLista() {
        Set<String> cmds = handler.commands();
        assertThat(cmds).containsExactlyInAnyOrder("/help", "/ajuda", "/start");
    }
}
