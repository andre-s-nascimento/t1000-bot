package net.ddns.adambravo79.tmill.telegram.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import net.ddns.adambravo79.tmill.exception.AudioProcessingException;
import net.ddns.adambravo79.tmill.service.config.JsonConfigLoader;
import net.ddns.adambravo79.tmill.telegram.core.TelegramSender;

@ExtendWith(MockitoExtension.class)
class TelegramExceptionHandlerTest {

    @Mock private TelegramSender sender;
    @Mock private JsonConfigLoader jsonConfigLoader;

    private TelegramExceptionHandler handler;

    private static final Map<String, Object> DEFAULT_RULES =
            Map.of(
                    "rules",
                    List.of(
                            Map.of(
                                    "contains",
                                    "429",
                                    "message",
                                    "⏳ Muitas requisições. Tente novamente em alguns segundos."),
                            Map.of(
                                    "contains",
                                    "timeout",
                                    "message",
                                    "⏱️ O servidor demorou a responder. Tente novamente em"
                                            + " instantes."),
                            Map.of(
                                    "contains",
                                    "unauthorized",
                                    "message",
                                    "🔑 Token inválido ou expirado. Verifique suas credenciais."),
                            Map.of(
                                    "contains",
                                    "chat not found",
                                    "message",
                                    "❌ Não consegui encontrar este chat. Verifique se o ID está"
                                            + " correto."),
                            Map.of(
                                    "contains",
                                    "file is too big",
                                    "message",
                                    "📂 O arquivo enviado é muito grande. Tente reduzir o"
                                            + " tamanho."),
                            Map.of(
                                    "contains",
                                    "wrong file type",
                                    "message",
                                    "🛑 Formato de arquivo não suportado. Envie em outro"
                                            + " formato.")));

    @BeforeEach
    void setUp() {
        doReturn(Optional.of(DEFAULT_RULES))
                .when(jsonConfigLoader)
                .loadConfig(eq("telegram-error-messages.json"), eq(Map.class), any());

        handler = new TelegramExceptionHandler(jsonConfigLoader);
        handler.init();
    }

    // ===================== handle() =====================

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

    // ===================== getMappedMessageFor (exposição para testes) =====================

    @ParameterizedTest
    @MethodSource("mappedMessagesProvider")
    @DisplayName("getMappedMessageFor: mapeia conforme regras do JSON")
    void getMappedMessageFor_corretamente(String input, String expected) {
        assertThat(handler.getMappedMessageFor(input)).contains(expected);
    }

    static Stream<Arguments> mappedMessagesProvider() {
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

    @Test
    @DisplayName("getMappedMessageFor: null retorna vazio")
    void getMappedMessageFor_null() {
        assertThat(handler.getMappedMessageFor(null)).isEmpty();
    }

    @Test
    @DisplayName("getMappedMessageFor: mensagem desconhecida retorna vazio")
    void getMappedMessageFor_desconhecida() {
        assertThat(handler.getMappedMessageFor("algo completamente diferente")).isEmpty();
    }

    // ===================== reload =====================

    @Test
    @DisplayName("reload: substitui regras")
    void reload_substitui() {
        // Limpa o stub do @BeforeEach para evitar UnnecessaryStubbingException
        reset(jsonConfigLoader);

        doReturn(
                        Optional.of(
                                Map.of(
                                        "rules",
                                        List.of(
                                                Map.of(
                                                        "contains",
                                                        "nova",
                                                        "message",
                                                        "Nova mensagem")))))
                .when(jsonConfigLoader)
                .loadConfig(eq("telegram-error-messages.json"), eq(Map.class), any());

        handler.reload();

        assertThat(handler.getMappedMessageFor("nova ocorrência")).contains("Nova mensagem");
        assertThat(handler.getMappedMessageFor("429")).isEmpty(); // regra antiga removida
        assertThat(handler.getErrorRules()).hasSize(1);
    }

    @Test
    @DisplayName("reload: JSON ausente mantém regras")
    void reload_jsonAusente_mantem() {
        reset(jsonConfigLoader);

        doReturn(Optional.empty())
                .when(jsonConfigLoader)
                .loadConfig(eq("telegram-error-messages.json"), eq(Map.class), any());

        handler.reload();

        assertThat(handler.getMappedMessageFor("429")).isPresent();
        assertThat(handler.getErrorRules()).hasSize(6);
    }

    @Test
    @DisplayName("reload: JSON com 'rules' inválido mantém regras")
    void reload_rulesInvalido_mantem() {
        reset(jsonConfigLoader);

        doReturn(Optional.of(Map.of("rules", "não é lista")))
                .when(jsonConfigLoader)
                .loadConfig(eq("telegram-error-messages.json"), eq(Map.class), any());

        handler.reload();

        assertThat(handler.getMappedMessageFor("429")).isPresent();
        assertThat(handler.getErrorRules()).hasSize(6);
    }

    @Test
    @DisplayName("reload: lista com entradas inválidas é filtrada")
    void reload_entradasInvalidas_filtra() {
        reset(jsonConfigLoader);

        doReturn(
                        Optional.of(
                                Map.of(
                                        "rules",
                                        List.of(
                                                Map.of(
                                                        "contains",
                                                        "valida",
                                                        "message",
                                                        "Mensagem válida"),
                                                Map.of("contains", "sem-mensagem"),
                                                Map.of("message", "sem-contains"),
                                                "não é mapa",
                                                Map.of("contains", "ok", "message", "Ok")))))
                .when(jsonConfigLoader)
                .loadConfig(eq("telegram-error-messages.json"), eq(Map.class), any());

        handler.reload();

        // Apenas as 2 entradas válidas (com contains + message) devem permanecer
        assertThat(handler.getErrorRules()).hasSize(2);
    }

    @Test
    @DisplayName("init: carrega regras do JSON")
    void init_carregaRegras() {
        assertThat(handler.getErrorRules()).hasSize(6);
    }
}
