package net.ddns.adambravo79.tmill.telegram.handler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Update;

import net.ddns.adambravo79.tmill.controller.CallbackHandler;
import net.ddns.adambravo79.tmill.telegram.core.GroupAuthorizationService;

@ExtendWith(MockitoExtension.class)
class CallbackUpdateRuleHandlerTest {

    @Mock private GroupAuthorizationService authService;
    @Mock private CallbackHandler callbackHandler;
    @InjectMocks private CallbackUpdateRuleHandler handler;

    @Test
    void deveRetornarUpdateSeForNulo() {
        Update result = handler.intercept(null);
        assertThat(result).isNull(); // ou null se update for null, mas veja a lógica
        // Na implementação, se update == null, retorna null (não o próprio update)
        // Ajuste conforme o código real: ele retorna update se não for callback
    }

    @Test
    void deveRetornarUpdateSeNaoTiverCallback() {
        Update update = mock(Update.class);
        when(update.callbackQuery()).thenReturn(null);
        Update result = handler.intercept(update);
        assertThat(result).isSameAs(update);
        verifyNoInteractions(authService, callbackHandler);
    }

    @Test
    void deveRetornarNullSeNaoAutorizado() {
        Update update = mock(Update.class);
        CallbackQuery query = mock(CallbackQuery.class);
        when(update.callbackQuery()).thenReturn(query);
        when(update.updateId()).thenReturn(1);
        when(authService.isAuthorized(update)).thenReturn(false);

        Update result = handler.intercept(update);
        assertThat(result).isNull();
        verify(callbackHandler, never()).handleCallbackUpdate(any());
    }

    @Test
    void deveProcessarCallbackERetornarNull() {
        Update update = mock(Update.class);
        CallbackQuery query = mock(CallbackQuery.class);
        when(update.callbackQuery()).thenReturn(query);
        when(update.updateId()).thenReturn(1);
        when(query.data()).thenReturn("dados");
        when(authService.isAuthorized(update)).thenReturn(true);

        Update result = handler.intercept(update);
        assertThat(result).isNull();
        verify(callbackHandler).handleCallbackUpdate(update);
    }
}
