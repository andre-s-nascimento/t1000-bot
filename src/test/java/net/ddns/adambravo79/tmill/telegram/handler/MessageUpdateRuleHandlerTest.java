package net.ddns.adambravo79.tmill.telegram.handler;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pengrad.telegrambot.model.Audio;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.Voice;

import net.ddns.adambravo79.tmill.controller.AudioHandler;
import net.ddns.adambravo79.tmill.controller.CommandHandler;
import net.ddns.adambravo79.tmill.telegram.core.GroupAuthorizationService;

@ExtendWith(MockitoExtension.class)
class MessageUpdateRuleHandlerTest {

    @Mock private GroupAuthorizationService authService;
    @Mock private CommandHandler commandHandler;
    @Mock private AudioHandler audioHandler;
    @InjectMocks private MessageUpdateRuleHandler handler;

    private Update mockUpdateWithMessage(Message message) {
        Update update = mock(Update.class);
        when(update.message()).thenReturn(message);
        return update;
    }

    private Message mockMessageWithChat(long chatId) {
        Message msg = mock(Message.class);
        Chat chat = mock(Chat.class);
        when(chat.id()).thenReturn(chatId);
        when(msg.chat()).thenReturn(chat);
        return msg;
    }

    @Test
    void deveNaoProcessarSeNaoAutorizado() {
        Update update = mock(Update.class);
        when(authService.isAuthorized(update)).thenReturn(false);

        handler.handle(update);

        verifyNoInteractions(commandHandler, audioHandler);
    }

    @Test
    void deveProcessarAudio() {
        long chatId = 123L;
        Message message = mockMessageWithChat(chatId);
        when(message.audio()).thenReturn(mock(Audio.class)); // tem áudio
        Update update = mockUpdateWithMessage(message);
        when(authService.isAuthorized(update)).thenReturn(true);

        handler.handle(update);

        verify(audioHandler).handleAudioUpdate(update);
        verifyNoInteractions(commandHandler);
    }

    @Test
    void deveProcessarVoice() {
        long chatId = 123L;
        Message message = mockMessageWithChat(chatId);
        when(message.audio()).thenReturn(null);
        when(message.voice()).thenReturn(mock(Voice.class)); // tem voice
        Update update = mockUpdateWithMessage(message);
        when(authService.isAuthorized(update)).thenReturn(true);

        handler.handle(update);

        verify(audioHandler).handleAudioUpdate(update);
        verifyNoInteractions(commandHandler);
    }

    @Test
    void deveProcessarComando() {
        long chatId = 123L;
        Message message = mockMessageWithChat(chatId);
        when(message.audio()).thenReturn(null);
        when(message.voice()).thenReturn(null);
        when(message.text()).thenReturn("/start");
        Update update = mockUpdateWithMessage(message);
        when(authService.isAuthorized(update)).thenReturn(true);

        handler.handle(update);

        verify(commandHandler).handleTextUpdate(update);
        verifyNoInteractions(audioHandler);
    }

    @Test
    void deveProcessarLink() {
        long chatId = 123L;
        Message message = mockMessageWithChat(chatId);
        when(message.audio()).thenReturn(null);
        when(message.voice()).thenReturn(null);
        when(message.text()).thenReturn("https://exemplo.com");
        Update update = mockUpdateWithMessage(message);
        when(authService.isAuthorized(update)).thenReturn(true);

        handler.handle(update);

        verify(commandHandler).handleTextUpdate(update);
        verifyNoInteractions(audioHandler);
    }

    @Test
    void deveProcessarTextoComum() {
        long chatId = 123L;
        Message message = mockMessageWithChat(chatId);
        when(message.audio()).thenReturn(null);
        when(message.voice()).thenReturn(null);
        when(message.text()).thenReturn("Olá mundo");
        Update update = mockUpdateWithMessage(message);
        when(authService.isAuthorized(update)).thenReturn(true);

        handler.handle(update);

        // Mesmo sendo texto comum, o handler chama commandHandler (conforme código atual)
        verify(commandHandler).handleTextUpdate(update);
        verifyNoInteractions(audioHandler);
    }

    @Test
    void deveNaoProcessarSeNaoHouverMessage() {
        Update update = mock(Update.class);
        when(update.message()).thenReturn(null);
        when(authService.isAuthorized(update)).thenReturn(true);

        handler.handle(update);

        verifyNoInteractions(commandHandler, audioHandler);
    }
}
