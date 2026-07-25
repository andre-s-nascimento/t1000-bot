package net.ddns.adambravo79.tmill.telegram.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;

class GroupAuthorizationServiceTest {

    private GroupAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new GroupAuthorizationService();
    }

    // ===================== TESTES DE INICIALIZAÇÃO =====================

    @Test
    void init_devePermitirTodosQuandoListaVazia() {
        ReflectionTestUtils.setField(service, "allowedChatsStr", "");
        service.init();
        assertThat(service.isAuthorized(updateComGrupo(-100L))).isTrue();
    }

    @Test
    void init_devePermitirTodosQuandoListaNull() {
        ReflectionTestUtils.setField(service, "allowedChatsStr", null);
        service.init();
        assertThat(service.isAuthorized(updateComGrupo(-100L))).isTrue();
    }

    @Test
    void init_deveIgnorarIdsInvalidos() {
        ReflectionTestUtils.setField(service, "allowedChatsStr", "-100abc,-100123,xyz");
        service.init();

        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(-100123L);

        assertThat(service.isAuthorized(update)).isTrue();
    }

    @Test
    void init_deveIgnorarIdsPositivos() {
        ReflectionTestUtils.setField(service, "allowedChatsStr", "123,-100456");
        service.init();

        Update updateGroup = mock(Update.class);
        Message messageGroup = mock(Message.class);
        Chat chatGroup = mock(Chat.class);
        when(updateGroup.message()).thenReturn(messageGroup);
        when(messageGroup.chat()).thenReturn(chatGroup);
        when(chatGroup.id()).thenReturn(-100456L);
        assertThat(service.isAuthorized(updateGroup)).isTrue();

        Update updateOther = mock(Update.class);
        Message messageOther = mock(Message.class);
        Chat chatOther = mock(Chat.class);
        when(updateOther.message()).thenReturn(messageOther);
        when(messageOther.chat()).thenReturn(chatOther);
        when(chatOther.id()).thenReturn(-100789L);
        assertThat(service.isAuthorized(updateOther)).isFalse();
    }

    // ===================== TESTES DE AUTORIZAÇÃO (PARAMETRIZADOS) =====================

    @ParameterizedTest
    @MethodSource("authorizationTestProvider")
    void deveValidarAutorizacao(
            String allowedChatsStr, long chatId, boolean expected, String testName) {

        ReflectionTestUtils.setField(service, "allowedChatsStr", allowedChatsStr);
        service.init();

        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(chatId);

        assertThat(service.isAuthorized(update)).as("Cenário: %s", testName).isEqualTo(expected);
    }

    static Stream<Arguments> authorizationTestProvider() {
        return Stream.of(
                Arguments.of("-100123", 12345L, true, "Chat privado autorizado"),
                Arguments.of("-100123,-100456", -100123L, true, "Grupo autorizado"),
                Arguments.of("-100123", -100456L, false, "Grupo não autorizado"),
                Arguments.of("", -100999L, true, "Lista vazia permite todos"),
                Arguments.of(null, -100999L, true, "Lista nula permite todos"));
    }

    // ===================== TESTES COM CALLBACK QUERY =====================

    @Test
    void deveExtrairChatIdDoCallbackQuery() {
        ReflectionTestUtils.setField(service, "allowedChatsStr", "-100123");
        service.init();

        Update update = mock(Update.class);
        when(update.message()).thenReturn(null);

        CallbackQuery callback = mock(CallbackQuery.class);
        when(update.callbackQuery()).thenReturn(callback);

        Message callbackMessage = mock(Message.class);
        when(callback.maybeInaccessibleMessage()).thenReturn(callbackMessage);

        Chat chat = mock(Chat.class);
        when(callbackMessage.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(-100123L);

        assertThat(service.isAuthorized(update)).isTrue();
    }

    @Test
    void deveExtrairChatIdDoCallbackQueryComMensagemInacessivel() {
        ReflectionTestUtils.setField(service, "allowedChatsStr", "-100123");
        service.init();

        Update update = mock(Update.class);
        when(update.message()).thenReturn(null);

        CallbackQuery callback = mock(CallbackQuery.class);
        when(update.callbackQuery()).thenReturn(callback);
        when(callback.maybeInaccessibleMessage()).thenReturn(null);

        assertThat(service.isAuthorized(update)).isTrue();
    }

    @Test
    void deveExtrairChatIdDoCallbackQueryQuandoCallbackNull() {
        ReflectionTestUtils.setField(service, "allowedChatsStr", "-100123");
        service.init();

        Update update = mock(Update.class);
        when(update.message()).thenReturn(null);
        when(update.callbackQuery()).thenReturn(null);

        assertThat(service.isAuthorized(update)).isTrue();
    }

    // ===================== MÉTODO AUXILIAR =====================

    private Update updateComGrupo(long groupId) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(groupId);
        return update;
    }
}
