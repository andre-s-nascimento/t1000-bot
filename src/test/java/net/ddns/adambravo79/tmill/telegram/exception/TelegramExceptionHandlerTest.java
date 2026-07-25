package net.ddns.adambravo79.tmill.telegram.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import com.pengrad.telegrambot.TelegramException;

import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.exception.AudioProcessingException;
import net.ddns.adambravo79.tmill.telegram.core.TelegramSender;

@ExtendWith(MockitoExtension.class)
class TelegramExceptionHandlerTest {

    @Mock private TelegramSender sender;

    private TelegramExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TelegramExceptionHandler();
    }

    // ===================== TESTES DO MÉTODO handle() =====================

    @Test
    void handle_deveEnviarMensagemParaTelegramFileException() throws TelegramException {
        Exception ex = new TelegramFileException("Erro", null);
        handler.handle(ex, 123L, sender);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sender).enviar(eq(123L), captor.capture());
        assertThat(captor.getValue()).isEqualTo("⚠️ Não consegui baixar o áudio.");
    }

    @Test
    void handle_deveEnviarMensagemParaAudioProcessingException() throws TelegramException {
        Exception ex = new AudioProcessingException("Erro");
        handler.handle(ex, 123L, sender);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sender).enviar(eq(123L), captor.capture());
        assertThat(captor.getValue()).isEqualTo("🎧 Erro ao processar o áudio.");
    }

    @Test
    void handle_deveEnviarMensagemParaIOException() throws TelegramException {
        Exception ex = new IOException("Erro de I/O");
        handler.handle(ex, 123L, sender);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sender).enviar(eq(123L), captor.capture());
        assertThat(captor.getValue()).isEqualTo("📡 Problema de comunicação com o servidor.");
    }

    @Test
    void handle_deveEnviarMensagemParaResourceAccessException() throws TelegramException {
        Exception ex = new ResourceAccessException("Timeout");
        handler.handle(ex, 123L, sender);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sender).enviar(eq(123L), captor.capture());
        assertThat(captor.getValue())
                .isEqualTo("⏱️ Timeout na comunicação com o servidor. Tente novamente.");
    }

    @Test
    void handle_deveEnviarMensagemParaTelegramExceptionComMensagemConhecida()
            throws TelegramException {
        TelegramException ex = mock(TelegramException.class);
        when(ex.getMessage()).thenReturn("429 Too Many Requests");

        handler.handle(ex, 123L, sender);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sender).enviar(eq(123L), captor.capture());
        assertThat(captor.getValue())
                .isEqualTo("⏳ Muitas requisições. Tente novamente em alguns segundos.");
    }

    @Test
    void handle_deveEnviarMensagemParaTelegramExceptionComMensagemDesconhecida()
            throws TelegramException {
        TelegramException ex = mock(TelegramException.class);
        when(ex.getMessage()).thenReturn("Erro desconhecido");

        handler.handle(ex, 123L, sender);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sender).enviar(eq(123L), captor.capture());
        assertThat(captor.getValue()).isEqualTo("⚠️ Ocorreu um erro inesperado: Erro desconhecido");
    }

    @Test
    void handle_deveEnviarMensagemGenericaParaExcecaoDesconhecida() throws TelegramException {
        Exception ex = new RuntimeException("Erro qualquer");
        handler.handle(ex, 123L, sender);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sender).enviar(eq(123L), captor.capture());
        assertThat(captor.getValue()).isEqualTo("⚠️ Ocorreu um erro inesperado: Erro qualquer");
    }

    @Test
    void handle_deveCapturarExcecaoAoSenderSeFalhar() throws TelegramException {
        Exception ex = new RuntimeException("Erro");
        doThrow(new RuntimeException("Falha ao enviar"))
                .when(sender)
                .enviar(anyLong(), anyString());

        handler.handle(ex, 123L, sender);

        verify(sender).enviar(eq(123L), anyString());
    }

    // ===================== TESTES DO MÉTODO mapearPorMensagem (via reflexão) =====================

    @Test
    void mapearPorMensagem_deveRetornarGenericoParaNull() throws Exception {
        Method method =
                TelegramExceptionHandler.class.getDeclaredMethod("mapearPorMensagem", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(handler, (Object) null);
        assertThat(result).isEqualTo(BotMessages.ERRO_GENERICO);
    }

    @Test
    void mapearPorMensagem_deveRetornarFallbackParaMensagemDesconhecida() throws Exception {
        Method method =
                TelegramExceptionHandler.class.getDeclaredMethod("mapearPorMensagem", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(handler, "algo completamente diferente");
        assertThat(result).isEqualTo("⚠️ Ocorreu um erro inesperado: algo completamente diferente");
    }

    @ParameterizedTest
    @MethodSource("mensagemParaMapeamentoProvider")
    void mapearPorMensagem_deveMapearCorretamente(String input, String expected) throws Exception {
        Method method =
                TelegramExceptionHandler.class.getDeclaredMethod("mapearPorMensagem", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(handler, input);
        assertThat(result).isEqualTo(expected);
    }

    static Stream<Arguments> mensagemParaMapeamentoProvider() {
        return Stream.of(
                Arguments.of(
                        "429 Too Many Requests",
                        "⏳ Muitas requisições. Tente novamente em alguns segundos."),
                Arguments.of(
                        "timeout",
                        "⏱️ O servidor demorou a responder. Tente novamente em instantes."),
                Arguments.of(
                        "unauthorized",
                        "🔑 Token inválido ou expirado. Verifique suas credenciais."),
                Arguments.of(
                        "chat not found",
                        "❌ Não consegui encontrar este chat. Verifique se o ID está correto."),
                Arguments.of(
                        "file is too big",
                        "📂 O arquivo enviado é muito grande. Tente reduzir o tamanho."),
                Arguments.of(
                        "wrong file type",
                        "🛑 Formato de arquivo não suportado. Envie em outro formato."));
    }

    // ===================== TESTES DO MÉTODO mapearMensagem (via reflexão) =====================

    @Test
    void mapearMensagem_deveMapearTelegramFileException() throws Exception {
        Method method =
                TelegramExceptionHandler.class.getDeclaredMethod("mapearMensagem", Exception.class);
        method.setAccessible(true);

        Exception ex = new TelegramFileException("Erro", null);
        String result = (String) method.invoke(handler, ex);
        assertThat(result).isEqualTo("⚠️ Não consegui baixar o áudio.");
    }

    @Test
    void mapearMensagem_deveMapearAudioProcessingException() throws Exception {
        Method method =
                TelegramExceptionHandler.class.getDeclaredMethod("mapearMensagem", Exception.class);
        method.setAccessible(true);

        Exception ex = new AudioProcessingException("Erro");
        String result = (String) method.invoke(handler, ex);
        assertThat(result).isEqualTo("🎧 Erro ao processar o áudio.");
    }

    @Test
    void mapearMensagem_deveMapearIOException() throws Exception {
        Method method =
                TelegramExceptionHandler.class.getDeclaredMethod("mapearMensagem", Exception.class);
        method.setAccessible(true);

        Exception ex = new IOException("Erro");
        String result = (String) method.invoke(handler, ex);
        assertThat(result).isEqualTo("📡 Problema de comunicação com o servidor.");
    }

    @Test
    void mapearMensagem_deveMapearResourceAccessException() throws Exception {
        Method method =
                TelegramExceptionHandler.class.getDeclaredMethod("mapearMensagem", Exception.class);
        method.setAccessible(true);

        Exception ex = new ResourceAccessException("Timeout");
        String result = (String) method.invoke(handler, ex);
        assertThat(result).isEqualTo("⏱️ Timeout na comunicação com o servidor. Tente novamente.");
    }

    @Test
    void mapearMensagem_deveMapearTelegramException() throws Exception {
        Method method =
                TelegramExceptionHandler.class.getDeclaredMethod("mapearMensagem", Exception.class);
        method.setAccessible(true);

        TelegramException ex = mock(TelegramException.class);
        when(ex.getMessage()).thenReturn("429 Too Many Requests");
        String result = (String) method.invoke(handler, ex);
        assertThat(result).isEqualTo("⏳ Muitas requisições. Tente novamente em alguns segundos.");
    }

    @Test
    void mapearMensagem_deveMapearExcecaoGenerica() throws Exception {
        Method method =
                TelegramExceptionHandler.class.getDeclaredMethod("mapearMensagem", Exception.class);
        method.setAccessible(true);

        Exception ex = new RuntimeException("Erro qualquer");
        String result = (String) method.invoke(handler, ex);
        assertThat(result).isEqualTo("⚠️ Ocorreu um erro inesperado: Erro qualquer");
    }
}
