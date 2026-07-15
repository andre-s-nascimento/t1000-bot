package net.ddns.adambravo79.tmill.telegram.core;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;

class GroupAuthorizationServiceTest {

    private GroupAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new GroupAuthorizationService();
    }

    @Test
    void devePermitirChatPrivado() {
        ReflectionTestUtils.setField(service, "allowedChatsStr", "-100123");
        service.init();

        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(12345L); // privado

        assertThat(service.isAuthorized(update)).isTrue();
    }

    @Test
    void devePermitirGrupoAutorizado() {
        ReflectionTestUtils.setField(service, "allowedChatsStr", "-100123,-100456");
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
    void deveNegarGrupoNaoAutorizado() {
        ReflectionTestUtils.setField(service, "allowedChatsStr", "-100123");
        service.init();

        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(-100456L);

        assertThat(service.isAuthorized(update)).isFalse();
    }

    @Test
    void devePermitirTodosOsGruposQuandoListaVazia() {
        ReflectionTestUtils.setField(service, "allowedChatsStr", "");
        service.init();

        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(-100999L);

        assertThat(service.isAuthorized(update)).isTrue();
    }
}
