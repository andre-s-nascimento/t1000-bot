package net.ddns.adambravo79.tmill.telegram.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.SendMessage;

import io.ksilisk.telegrambot.core.executor.TelegramBotExecutor;
import net.ddns.adambravo79.tmill.service.TelegramModerationService;
import net.ddns.adambravo79.tmill.telegram.core.GroupAuthorizationService;

@ExtendWith(MockitoExtension.class)
class MuteCommandHandlerTest {

    @Mock private TelegramBotExecutor executor;
    @Mock private TelegramModerationService moderationService;
    @Mock private GroupAuthorizationService authService;

    @InjectMocks private MuteCommandHandler handler;

    private Update update;
    private Message message;
    private Chat chat;
    private User targetUser;
    private Message replyMessage;

    private static final long CHAT_ID = -100L;
    private static final long TARGET_USER_ID = 123L;

    @BeforeEach
    void setUp() {
        update = mock(Update.class);
        message = mock(Message.class);
        chat = mock(Chat.class);
        targetUser = mock(User.class);
        replyMessage = mock(Message.class);

        lenient().when(update.message()).thenReturn(message);
        lenient().when(message.chat()).thenReturn(chat);
        lenient().when(chat.id()).thenReturn(CHAT_ID);

        lenient().when(message.replyToMessage()).thenReturn(replyMessage);
        lenient().when(replyMessage.from()).thenReturn(targetUser);
        lenient().when(targetUser.id()).thenReturn(TARGET_USER_ID);
        lenient().when(targetUser.firstName()).thenReturn("Fulano");

        lenient().when(authService.isAuthorized(update)).thenReturn(true);
    }

    @Test
    @DisplayName("handle: deve ignorar se for chat privado")
    void handle_chatPrivado() {
        when(chat.id()).thenReturn(123L); // ID positivo = privado
        when(message.text()).thenReturn("/mute");

        handler.handle(update);

        verify(executor).execute(any(SendMessage.class));
        verify(moderationService, never()).muteUser(anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("handle: deve avisar se não for um reply")
    void handle_semReply() {
        when(message.text()).thenReturn("/mute");
        when(message.replyToMessage()).thenReturn(null);

        handler.handle(update);

        verify(executor).execute(any(SendMessage.class));
        verify(moderationService, never()).muteUser(anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("handle: não deve silenciar a si mesmo")
    void handle_mutarASiMesmo() {
        when(message.text()).thenReturn("/mute");
        when(targetUser.id()).thenReturn(7900L); // ID do bot

        handler.handle(update);

        verify(executor).execute(any(SendMessage.class));
        verify(moderationService, never()).muteUser(anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("handle: deve chamar muteUser com tempo padrao (0) se comando for apenas /mute")
    void handle_mutePermanente() {
        when(message.text()).thenReturn("/mute");
        when(moderationService.muteUser(CHAT_ID, TARGET_USER_ID, 0)).thenReturn(true);

        handler.handle(update);

        verify(moderationService).muteUser(CHAT_ID, TARGET_USER_ID, 0);
        verify(executor).execute(any(SendMessage.class));
    }

    @Test
    @DisplayName("handle: deve chamar muteUser com tempo extraido se comando for /mute 60")
    void handle_muteComTempo() {
        when(message.text()).thenReturn("/mute 60");
        when(moderationService.muteUser(CHAT_ID, TARGET_USER_ID, 60)).thenReturn(true);

        handler.handle(update);

        verify(moderationService).muteUser(CHAT_ID, TARGET_USER_ID, 60);
    }

    @Test
    @DisplayName("handle: deve chamar unmuteUser se comando for /unmute")
    void handle_unmute() {
        when(message.text()).thenReturn("/unmute");
        when(moderationService.unmuteUser(CHAT_ID, TARGET_USER_ID)).thenReturn(true);

        handler.handle(update);

        verify(moderationService).unmuteUser(CHAT_ID, TARGET_USER_ID);
        verify(executor).execute(any(SendMessage.class));
    }

    @Test
    @DisplayName("handle: deve mostrar mensagem de erro se muteUser falhar")
    void handle_muteFalha() {
        when(message.text()).thenReturn("/mute");
        when(moderationService.muteUser(anyLong(), anyLong(), anyInt())).thenReturn(false);

        handler.handle(update);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(executor).execute(captor.capture());

        String textoEnviado = (String) captor.getValue().getParameters().get("text");
        assertThat(textoEnviado).contains("Falha ao silenciar");
    }

    @Test
    @DisplayName("handle: não deve fazer nada se for um comando desconhecido (ex: /ban)")
    void handle_comandoDesconhecido_naoFazNada() {
        when(message.text()).thenReturn("/ban");

        handler.handle(update);

        verify(moderationService, never()).muteUser(anyLong(), anyLong(), anyInt());
        verify(moderationService, never()).unmuteUser(anyLong(), anyLong());
    }

    @Test
    @DisplayName("handle: deve avisar se o bot tentar mutar a si mesmo")
    void handle_mutarBot() {
        when(message.text()).thenReturn("/mute");
        when(targetUser.id()).thenReturn(7900L); // ID do bot

        handler.handle(update);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(executor).execute(captor.capture());
        assertThat((String) captor.getValue().getParameters().get("text"))
                .contains("não posso silenciar a mim mesmo");
    }

    @Test
    @DisplayName("handle: deve mostrar erro se unmute falhar")
    void handle_unmuteFalha() {
        when(message.text()).thenReturn("/unmute");
        when(moderationService.unmuteUser(anyLong(), anyLong())).thenReturn(false);

        handler.handle(update);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(executor).execute(captor.capture());

        String texto = (String) captor.getValue().getParameters().get("text");
        assertThat(texto).contains("Falha ao remover o silenciamento");
    }
}
