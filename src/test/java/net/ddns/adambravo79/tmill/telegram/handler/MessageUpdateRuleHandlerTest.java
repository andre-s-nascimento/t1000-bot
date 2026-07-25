package net.ddns.adambravo79.tmill.telegram.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pengrad.telegrambot.model.Audio;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.Voice;

import io.ksilisk.telegrambot.core.matcher.Matcher;
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

    // ===================== TESTE PARAMETRIZADO =====================

    @ParameterizedTest
    @MethodSource("messageScenariosProvider")
    void deveProcessarDiferentesTiposDeMensagem(
            MessageSetup setup, boolean esperaAudioHandler, boolean esperaCommandHandler) {

        long chatId = 123L;
        Message message = mockMessageWithChat(chatId);
        setup.configure(message);

        Update update = mockUpdateWithMessage(message);
        when(authService.isAuthorized(update)).thenReturn(true);

        handler.handle(update);

        if (esperaAudioHandler) {
            verify(audioHandler).handleAudioUpdate(update);
        } else {
            verifyNoInteractions(audioHandler);
        }

        if (esperaCommandHandler) {
            verify(commandHandler).handleTextUpdate(update);
        } else {
            verifyNoInteractions(commandHandler);
        }
    }

    @FunctionalInterface
    interface MessageSetup {
        void configure(Message message);
    }

    static Stream<Arguments> messageScenariosProvider() {
        return Stream.of(
                // Audio
                Arguments.of(
                        (MessageSetup) msg -> when(msg.audio()).thenReturn(mock(Audio.class)),
                        true,
                        false),
                // Voice
                Arguments.of(
                        (MessageSetup)
                                msg -> {
                                    when(msg.audio()).thenReturn(null);
                                    when(msg.voice()).thenReturn(mock(Voice.class));
                                },
                        true,
                        false),
                // Comando
                Arguments.of(
                        (MessageSetup)
                                msg -> {
                                    when(msg.audio()).thenReturn(null);
                                    when(msg.voice()).thenReturn(null);
                                    when(msg.text()).thenReturn("/start");
                                },
                        false,
                        true),
                // Link
                Arguments.of(
                        (MessageSetup)
                                msg -> {
                                    when(msg.audio()).thenReturn(null);
                                    when(msg.voice()).thenReturn(null);
                                    when(msg.text()).thenReturn("https://exemplo.com");
                                },
                        false,
                        true),
                // Texto comum
                Arguments.of(
                        (MessageSetup)
                                msg -> {
                                    when(msg.audio()).thenReturn(null);
                                    when(msg.voice()).thenReturn(null);
                                    when(msg.text()).thenReturn("Olá mundo");
                                },
                        false,
                        true));
    }

    // ===================== TESTES ADICIONAIS =====================

    @Test
    void deveNaoProcessarSeNaoAutorizado() {
        Update update = mock(Update.class);
        when(authService.isAuthorized(update)).thenReturn(false);

        handler.handle(update);

        verifyNoInteractions(commandHandler, audioHandler);
    }

    @Test
    void deveNaoProcessarSeNaoHouverMessage() {
        Update update = mock(Update.class);
        when(update.message()).thenReturn(null);
        when(authService.isAuthorized(update)).thenReturn(true);

        handler.handle(update);

        verifyNoInteractions(commandHandler, audioHandler);
    }

    @Test
    void deveProcessarMessageSemAudioVoiceText() {
        long chatId = 123L;
        Message message = mockMessageWithChat(chatId);
        when(message.audio()).thenReturn(null);
        when(message.voice()).thenReturn(null);
        when(message.text()).thenReturn(null);
        Update update = mockUpdateWithMessage(message);
        when(authService.isAuthorized(update)).thenReturn(true);

        handler.handle(update);

        verifyNoInteractions(commandHandler, audioHandler);
    }

    @ParameterizedTest
    @ValueSource(strings = {"t1000, execute", "t-1000, execute"})
    void deveProcessarTextoComPadraoT1000(String text) {
        long chatId = 123L;
        Message message = mockMessageWithChat(chatId);
        when(message.audio()).thenReturn(null);
        when(message.voice()).thenReturn(null);
        when(message.text()).thenReturn(text);
        Update update = mockUpdateWithMessage(message);
        when(authService.isAuthorized(update)).thenReturn(true);

        handler.handle(update);

        verify(commandHandler).handleTextUpdate(update);
        verifyNoInteractions(audioHandler);
    }

    @Test
    void deveCobrirMatcher() {
        Matcher<Message> matcher = handler.matcher();
        assertThat(matcher).isNotNull();
        assertThat(matcher.match(mock(Message.class))).isTrue();
    }

    @Test
    void deveCobrirHandler() {
        assertThat(handler.handler()).isSameAs(handler);
    }
}
