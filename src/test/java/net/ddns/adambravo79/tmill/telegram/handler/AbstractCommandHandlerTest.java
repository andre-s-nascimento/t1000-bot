/* (c) 2026 | 04/11/2026 */
package net.ddns.adambravo79.tmill.telegram.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;

import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.telegram.core.GroupAuthorizationService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.TelegramUtils;

/**
 * Testes da superclasse {@link AbstractCommandHandler}.
 *
 * <p>Como a classe é abstrata, usamos uma subclasse de teste ({@link FakeHandler}) que expõe
 * ganchos ({@code exposedHandleCommand}, {@code exposedExecutarComProtecao}, etc.) e permite
 * verificar cada comportamento isoladamente.
 *
 * <p>Cobre:
 *
 * <ul>
 *   <li>{@link AbstractCommandHandler#processarComando}: fluxo de autorização + normalização +
 *       delegação
 *   <li>{@link AbstractCommandHandler#executarComProtecao}: captura de exceções (HTTP, rede,
 *       runtime) e notificação
 *   <li>Wrappers de envio: {@code sendHtml}, {@code sendPlain}, {@code sendWithButtons},
 *       {@code sendPhoto}
 *   <li>Helpers de extração: {@code chatId}, {@code userId}, {@code userName}, {@code isGroupChat},
 *       {@code textoBruto}
 *   <li>Helpers de texto: {@code normalizarTexto}, {@code argumentoApos}
 *   <li>Helpers de data/hora: {@code hoje}, {@code agora}, {@code isValidUrl}
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // ← ADICIONAR ESTA LINHA
class AbstractCommandHandlerTest {

    @Mock private TelegramFacade telegramFacade;
    @Mock private GroupAuthorizationService authService;
    @Mock private TelegramUtils utils;

    private FakeHandler handler;

    private static final long CHAT_ID = 12345L;
    private static final long USER_ID = 999L;
    private static final String USER_NAME = "Testador Silva";

    @BeforeEach
    void setUp() {
        handler = new FakeHandler(telegramFacade, authService, utils);

        lenient().when(authService.isAuthorized(any(Update.class))).thenReturn(true);
        lenient().when(utils.buildFullName(any(User.class))).thenReturn(USER_NAME);
    }

    // =========================================================================
    // FIXTURES
    // =========================================================================

    private Update mockUpdate(String texto) {
        return mockUpdate(texto, Chat.Type.Private);
    }

    private Update mockUpdate(String texto, Chat.Type chatType) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.text()).thenReturn(texto);
        when(message.chat()).thenReturn(chat);
        when(message.from()).thenReturn(user);
        when(chat.id()).thenReturn(CHAT_ID);
        when(chat.type()).thenReturn(chatType);
        when(user.id()).thenReturn(USER_ID);
        when(user.firstName()).thenReturn("Testador");
        when(user.lastName()).thenReturn("Silva");

        return update;
    }

    // =========================================================================
    // processarComando — fluxo feliz
    // =========================================================================

    @Nested
    @DisplayName("processarComando")
    class ProcessarComando {

        @Test
        @DisplayName("update com mensagem autorizada: normaliza e delega para handleCommand")
        void updateAutorizadoDelega() {
            Update update = mockUpdate("T1000 SALAVIP 15");

            handler.processarComando(update);

            assertThat(handler.lastTextoNormalizado).isEqualTo("t1000 salavip 15");
            assertThat(handler.handleCommandCount).isEqualTo(1);
        }

        @Test
        @DisplayName("update com mensagem não autorizada: NÃO chama handleCommand")
        void updateNaoAutorizadoNaoDelega() {
            when(authService.isAuthorized(any(Update.class))).thenReturn(false);
            Update update = mockUpdate("t1000 salavip 15");

            handler.processarComando(update);

            assertThat(handler.handleCommandCount).isZero();
        }

        @Test
        @DisplayName("update nulo: não faz nada (early return)")
        void updateNulo() {
            handler.processarComando(null);

            assertThat(handler.handleCommandCount).isZero();
            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("update sem mensagem: não faz nada")
        void updateSemMensagem() {
            Update update = mock(Update.class);
            when(update.message()).thenReturn(null);

            handler.processarComando(update);

            assertThat(handler.handleCommandCount).isZero();
            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("texto null: normaliza para string vazia e delega")
        void textoNull() {
            Update update = mockUpdate(null);

            handler.processarComando(update);

            assertThat(handler.lastTextoNormalizado).isEmpty();
            assertThat(handler.handleCommandCount).isEqualTo(1);
        }
    }

    // =========================================================================
    // executarComProtecao — captura de exceções
    // =========================================================================

    @Nested
    @DisplayName("executarComProtecao")
    class ExecutarComProtecao {

        @Test
        @DisplayName("handleCommand com sucesso: não envia mensagem de erro")
        void sucessoNaoEnviaErro() {
            Update update = mockUpdate("t1000 teste");
            handler.failWith = null;

            handler.executarComProtecao(update, "t1000 teste");

            verify(telegramFacade, never()).enviarMensagem(anyLong(), anyString());
        }

        @Test
        @DisplayName("handleCommand lança HttpClientErrorException: loga e notifica usuário")
        void httpClientErrorNotifica() {
            Update update = mockUpdate("t1000 teste");
            handler.failWith =
                    HttpClientErrorException.create(
                            org.springframework.http.HttpStatus.BAD_REQUEST,
                            "Bad Request",
                            null,
                            null,
                            null);

            handler.executarComProtecao(update, "t1000 teste");

            verify(telegramFacade).enviarMensagem(CHAT_ID, BotMessages.ERRO_GENERICO);
        }

        @Test
        @DisplayName("handleCommand lança ResourceAccessException: loga e notifica usuário")
        void resourceAccessNotifica() {
            Update update = mockUpdate("t1000 teste");
            handler.failWith = new ResourceAccessException("Timeout");

            handler.executarComProtecao(update, "t1000 teste");

            verify(telegramFacade).enviarMensagem(CHAT_ID, BotMessages.ERRO_GENERICO);
        }

        @Test
        @DisplayName("handleCommand lança RuntimeException: loga e notifica usuário")
        void runtimeNotifica() {
            Update update = mockUpdate("t1000 teste");
            handler.failWith = new RuntimeException("Boom");

            handler.executarComProtecao(update, "t1000 teste");

            verify(telegramFacade).enviarMensagem(CHAT_ID, BotMessages.ERRO_GENERICO);
        }

        @Test
        @DisplayName("mensagemErroUsuario() null suprime notificação mesmo em erro")
        void mensagemErroNullSuprime() {
            Update update = mockUpdate("t1000 teste");
            handler.failWith = new RuntimeException("Boom");
            handler.erroUsuario = null; // suprime

            handler.executarComProtecao(update, "t1000 teste");

            verify(telegramFacade, never()).enviarMensagem(anyLong(), anyString());
        }

        @Test
        @DisplayName("falha ao notificar usuário: não propaga (apenas loga)")
        void falhaAoNotificar() {
            Update update = mockUpdate("t1000 teste");
            handler.failWith = new RuntimeException("Boom");
            doThrow(new RuntimeException("Falha no envio"))
                    .when(telegramFacade)
                    .enviarMensagem(anyLong(), anyString());

            assertThatCode(() -> handler.executarComProtecao(update, "t1000 teste"))
                    .doesNotThrowAnyException();
        }
    }

    // =========================================================================
    // normalizarTexto
    // =========================================================================

    @Nested
    @DisplayName("normalizarTexto")
    class NormalizarTexto {

        @Test
        @DisplayName("lowercase + trim")
        void lowercaseTrim() {
            assertThat(handler.exposedNormalizarTexto("  T1000 TESTE  ")).isEqualTo("t1000 teste");
        }

        @Test
        @DisplayName("remove barra inicial")
        void removeBarraInicial() {
            assertThat(handler.exposedNormalizarTexto("/start")).isEqualTo("start");
        }

        @Test
        @DisplayName("normaliza t-1000 para t1000")
        void normalizaHifen() {
            assertThat(handler.exposedNormalizarTexto("t-1000 buscar")).isEqualTo("t1000 buscar");
        }

        @Test
        @DisplayName("t-1000 com barra inicial")
        void t1000ComBarraEHifen() {
            assertThat(handler.exposedNormalizarTexto("/t-1000 buscar")).isEqualTo("t1000 buscar");
        }

        @Test
        @DisplayName("null retorna vazio")
        void nullRetornaVazio() {
            assertThat(handler.exposedNormalizarTexto(null)).isEmpty();
        }

        @Test
        @DisplayName("vazio retorna vazio")
        void vazioRetornaVazio() {
            assertThat(handler.exposedNormalizarTexto("")).isEmpty();
        }
    }

    // =========================================================================
    // argumentoApos
    // =========================================================================

    @Nested
    @DisplayName("argumentoApos")
    class ArgumentoApos {

        @Test
        @DisplayName("extrai o argumento após o prefixo")
        void extraiArgumento() {
            assertThat(handler.exposedArgumentoApos("t1000 buscar duna", "t1000 buscar"))
                    .isEqualTo("duna");
        }

        @Test
        @DisplayName("texto == prefixo: retorna vazio")
        void textoIgualPrefixo() {
            assertThat(handler.exposedArgumentoApos("t1000 buscar", "t1000 buscar")).isEmpty();
        }

        @Test
        @DisplayName("texto não começa com prefixo: retorna vazio")
        void textoNaoComecaComPrefixo() {
            assertThat(handler.exposedArgumentoApos("t1000 salavip", "t1000 buscar")).isEmpty();
        }

        @Test
        @DisplayName("null em qualquer parâmetro: retorna vazio")
        void nullEmQualquerParametro() {
            assertThat(handler.exposedArgumentoApos(null, "t1000 buscar")).isEmpty();
            assertThat(handler.exposedArgumentoApos("t1000 buscar duna", null)).isEmpty();
        }
    }

    // =========================================================================
    // Wrappers de envio
    // =========================================================================

    @Nested
    @DisplayName("sendHtml")
    class SendHtml {

        @Test
        @DisplayName("envia mensagem HTML curta")
        void enviaCurta() {
            handler.exposedSendHtml(CHAT_ID, "Olá");

            verify(telegramFacade).enviarMensagemHtml(CHAT_ID, "Olá");
        }

        @Test
        @DisplayName("mensagem longa é dividida em partes")
        void divideLonga() {
            String longa = "a".repeat(5000);
            when(utils.splitMessage(anyString(), eq(4000)))
                    .thenReturn(List.of("a".repeat(4000), "a".repeat(1000)));

            handler.exposedSendHtml(CHAT_ID, longa);

            verify(telegramFacade).enviarMensagemHtml(CHAT_ID, "a".repeat(4000));
            verify(telegramFacade).enviarMensagemHtml(CHAT_ID, "a".repeat(1000));
        }

        @Test
        @DisplayName("texto null ou vazio não envia")
        void textoVazioNaoEnvia() {
            handler.exposedSendHtml(CHAT_ID, null);
            handler.exposedSendHtml(CHAT_ID, "");
            handler.exposedSendHtml(CHAT_ID, "   ");

            verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
        }

        @Test
        @DisplayName("HttpClientErrorException no envio: loga e não propaga")
        void httpErroNaoPropaga() {
            doThrow(
                            HttpClientErrorException.create(
                                    org.springframework.http.HttpStatus.BAD_REQUEST,
                                    "Bad Request",
                                    null,
                                    null,
                                    null))
                    .when(telegramFacade)
                    .enviarMensagemHtml(anyLong(), anyString());

            assertThatCode(() -> handler.exposedSendHtml(CHAT_ID, "Olá"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("sendPlain")
    class SendPlain {

        @Test
        @DisplayName("envia mensagem plain curta")
        void enviaCurta() {
            handler.exposedSendPlain(CHAT_ID, "Olá");

            verify(telegramFacade).enviarMensagem(CHAT_ID, "Olá");
        }

        @Test
        @DisplayName("mensagem longa é dividida em partes")
        void divideLonga() {
            String longa = "a".repeat(5000);
            when(utils.splitMessage(anyString(), eq(4000)))
                    .thenReturn(List.of("a".repeat(4000), "a".repeat(1000)));

            handler.exposedSendPlain(CHAT_ID, longa);

            verify(telegramFacade).enviarMensagem(CHAT_ID, "a".repeat(4000));
            verify(telegramFacade).enviarMensagem(CHAT_ID, "a".repeat(1000));
        }

        @Test
        @DisplayName("texto null ou vazio não envia")
        void textoVazioNaoEnvia() {
            handler.exposedSendPlain(CHAT_ID, null);
            handler.exposedSendPlain(CHAT_ID, "");
            handler.exposedSendPlain(CHAT_ID, "   ");

            verify(telegramFacade, never()).enviarMensagem(anyLong(), anyString());
        }

        @Test
        @DisplayName("ResourceAccessException no envio: loga e não propaga")
        void resourceErroNaoPropaga() {
            doThrow(new ResourceAccessException("Timeout"))
                    .when(telegramFacade)
                    .enviarMensagem(anyLong(), anyString());

            assertThatCode(() -> handler.exposedSendPlain(CHAT_ID, "Olá"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("sendWithButtons")
    class SendWithButtons {

        @Test
        @DisplayName("envia mensagem com botões")
        void envia() {
            InlineKeyboardMarkup markup = mock(InlineKeyboardMarkup.class);
            handler.exposedSendWithButtons(CHAT_ID, "Olá", markup);

            verify(telegramFacade).enviarComBotoesHtml(CHAT_ID, "Olá", markup);
        }

        @Test
        @DisplayName("exceção no envio: loga e não propaga")
        void erroNaoPropaga() {
            InlineKeyboardMarkup markup = mock(InlineKeyboardMarkup.class);
            doThrow(new RuntimeException("Boom"))
                    .when(telegramFacade)
                    .enviarComBotoesHtml(anyLong(), anyString(), any());

            assertThatCode(() -> handler.exposedSendWithButtons(CHAT_ID, "Olá", markup))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("sendPhoto")
    class SendPhoto {

        @Test
        @DisplayName("envia foto com legenda")
        void envia() {
            handler.exposedSendPhoto(CHAT_ID, "https://exemplo.com/foto.jpg", "legenda");

            verify(telegramFacade)
                    .enviarFotoHtml(CHAT_ID, "https://exemplo.com/foto.jpg", "legenda");
        }

        @Test
        @DisplayName("exceção no envio: loga e não propaga")
        void erroNaoPropaga() {
            doThrow(new RuntimeException("Boom"))
                    .when(telegramFacade)
                    .enviarFotoHtml(anyLong(), anyString(), anyString());

            assertThatCode(
                            () ->
                                    handler.exposedSendPhoto(
                                            CHAT_ID, "https://exemplo.com/foto.jpg", "legenda"))
                    .doesNotThrowAnyException();
        }
    }

    // =========================================================================
    // Helpers de extração
    // =========================================================================

    @Nested
    @DisplayName("Helpers de extração")
    class HelpersExtracao {

        @Test
        @DisplayName("chatId extrai o ID do chat")
        void chatId() {
            Update update = mockUpdate("teste");
            assertThat(handler.exposedChatId(update)).isEqualTo(CHAT_ID);
        }

        @Test
        @DisplayName("userId extrai o ID do usuário")
        void userId() {
            Update update = mockUpdate("teste");
            assertThat(handler.exposedUserId(update)).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("userName usa utils.buildFullName")
        void userName() {
            Update update = mockUpdate("teste");
            assertThat(handler.exposedUserName(update)).isEqualTo(USER_NAME);
        }

        @Test
        @DisplayName("isGroupChat: private → false")
        void isGroupChatPrivate() {
            Update update = mockUpdate("teste", Chat.Type.Private);
            assertThat(handler.exposedIsGroupChat(update)).isFalse();
        }

        @Test
        @DisplayName("isGroupChat: group → true")
        void isGroupChatGroup() {
            Update update = mockUpdate("teste", Chat.Type.group);
            assertThat(handler.exposedIsGroupChat(update)).isTrue();
        }

        @Test
        @DisplayName("isGroupChat: supergroup → true")
        void isGroupChatSupergroup() {
            Update update = mockUpdate("teste", Chat.Type.supergroup);
            assertThat(handler.exposedIsGroupChat(update)).isTrue();
        }

        @Test
        @DisplayName("textoBruto: extrai o texto")
        void textoBruto() {
            Update update = mockUpdate("T1000 Teste");
            assertThat(handler.exposedTextoBruto(update)).isEqualTo("T1000 Teste");
        }

        @Test
        @DisplayName("textoBruto: null retorna string vazia")
        void textoBrutoNull() {
            Update update = mockUpdate(null);
            assertThat(handler.exposedTextoBruto(update)).isEmpty();
        }
    }

    // =========================================================================
    // Helpers de data/hora
    // =========================================================================

    @Nested
    @DisplayName("Helpers de data/hora")
    class HelpersDataHora {

        @Test
        @DisplayName("hoje() retorna data atual em Brasília")
        void hoje() {
            assertThat(handler.exposedHoje())
                    .isEqualTo(LocalDate.now(java.time.ZoneId.of(BotMessages.BRAZIL_ZONE)));
        }

        @Test
        @DisplayName("agora() retorna hora atual em Brasília")
        void agora() {
            LocalTime agora = handler.exposedAgora();
            assertThat(agora).isNotNull();
            // Verifica que está no intervalo de ±5 segundos do agora real
            LocalTime real = LocalTime.now(java.time.ZoneId.of(BotMessages.BRAZIL_ZONE));
            long diff = Math.abs(agora.toSecondOfDay() - real.toSecondOfDay());
            assertThat(diff).isLessThanOrEqualTo(5);
        }
    }

    // =========================================================================
    // isValidUrl
    // =========================================================================

    @Nested
    @DisplayName("isValidUrl")
    class IsValidUrl {

        @Test
        @DisplayName("URLs HTTP/HTTPS válidas")
        void urlsValidas() {
            assertThat(handler.exposedIsValidUrl("http://exemplo.com")).isTrue();
            assertThat(handler.exposedIsValidUrl("https://exemplo.com/path?a=1")).isTrue();
        }

        @Test
        @DisplayName("URLs inválidas")
        void urlsInvalidas() {
            assertThat(handler.exposedIsValidUrl(null)).isFalse();
            assertThat(handler.exposedIsValidUrl("")).isFalse();
            assertThat(handler.exposedIsValidUrl("   ")).isFalse();
            assertThat(handler.exposedIsValidUrl("ftp://exemplo.com")).isFalse();
            assertThat(handler.exposedIsValidUrl("não é url")).isFalse();
            assertThat(handler.exposedIsValidUrl("http://")).isFalse();
            assertThat(handler.exposedIsValidUrl("http://host with spaces")).isFalse();
        }
    }

    // =========================================================================
    // Subclasse concreta para testes
    // =========================================================================

    /**
     * Subclasse concreta que expõe os métodos protegidos para teste e permite simular falhas em
     * {@code handleCommand}.
     */
    static class FakeHandler extends AbstractCommandHandler {

        int handleCommandCount = 0;
        String lastTextoNormalizado;
        RuntimeException failWith;
        String erroUsuario = BotMessages.ERRO_GENERICO;

        FakeHandler(
                TelegramFacade telegramFacade,
                GroupAuthorizationService authService,
                TelegramUtils utils) {
            super(telegramFacade, authService, utils);
        }

        @Override
        public String getPrefixo() {
            return "t1000 teste";
        }

        @Override
        protected void handleCommand(Update update, String textoNormalizado) {
            handleCommandCount++;
            lastTextoNormalizado = textoNormalizado;
            if (failWith != null) {
                throw failWith;
            }
        }

        @Override
        protected String mensagemErroUsuario() {
            return erroUsuario;
        }

        // ---- Exposição de métodos protegidos para testes ----

        void exposedExecutarComProtecao(Update update, String textoNormalizado) {
            executarComProtecao(update, textoNormalizado);
        }

        String exposedNormalizarTexto(String raw) {
            return normalizarTexto(raw);
        }

        String exposedArgumentoApos(String texto, String prefixo) {
            return argumentoApos(texto, prefixo);
        }

        void exposedSendHtml(long chatId, String texto) {
            sendHtml(chatId, texto);
        }

        void exposedSendPlain(long chatId, String texto) {
            sendPlain(chatId, texto);
        }

        void exposedSendWithButtons(long chatId, String texto, InlineKeyboardMarkup markup) {
            sendWithButtons(chatId, texto, markup);
        }

        void exposedSendPhoto(long chatId, String fotoUrl, String legenda) {
            sendPhoto(chatId, fotoUrl, legenda);
        }

        long exposedChatId(Update update) {
            return chatId(update);
        }

        long exposedUserId(Update update) {
            return userId(update);
        }

        String exposedUserName(Update update) {
            return userName(update);
        }

        boolean exposedIsGroupChat(Update update) {
            return isGroupChat(update);
        }

        String exposedTextoBruto(Update update) {
            return textoBruto(update);
        }

        LocalDate exposedHoje() {
            return hoje();
        }

        LocalTime exposedAgora() {
            return agora();
        }

        boolean exposedIsValidUrl(String url) {
            return isValidUrl(url);
        }
    }
}
