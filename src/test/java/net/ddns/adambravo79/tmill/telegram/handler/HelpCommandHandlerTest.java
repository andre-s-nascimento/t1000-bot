/* (c) 2026 | 04/11/2026 */
package net.ddns.adambravo79.tmill.telegram.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;

import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.telegram.core.GroupAuthorizationService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.TelegramUtils;

/**
 * Testes do {@link HelpCommandHandler}.
 *
 * <p>O handler implementa duas interfaces:
 *
 * <ol>
 *   <li>{@link io.ksilisk.telegrambot.core.handler.update.command.CommandUpdateHandler} — comandos
 *       com barra ({@code /help}, {@code /start}, {@code /ajuda}), roteados pelo ksilisk.
 *   <li>{@link AbstractCommandHandler} — texto sem barra ({@code start}, {@code help}), roteado
 *       pelo {@code CommandHandler}.
 * </ol>
 *
 * <p>Os testes cobrem ambos os caminhos e a autorização em cada um.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HelpCommandHandlerTest {

    @Mock private TelegramFacade telegramFacade;
    @Mock private GroupAuthorizationService authService;
    @Mock private TelegramUtils utils;

    private HelpCommandHandler handler;

    private static final long CHAT_ID = 12345L;
    private static final long USER_ID = 999L;
    private static final String USER_NAME = "Testador Silva";

    @BeforeEach
    void setUp() {
        handler = new HelpCommandHandler(telegramFacade, authService, utils);

        lenient().when(authService.isAuthorized(any(Update.class))).thenReturn(true);
        lenient().when(utils.buildFullName(any(User.class))).thenReturn(USER_NAME);
        lenient().when(utils.escapeHtml(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    // =========================================================================
    // FIXTURES
    // =========================================================================

    private Update mockUpdate(String texto) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.text()).thenReturn(texto);
        when(message.chat()).thenReturn(chat);
        when(message.from()).thenReturn(user);
        when(chat.id()).thenReturn(CHAT_ID);
        when(user.id()).thenReturn(USER_ID);
        when(user.firstName()).thenReturn("Testador");
        when(user.lastName()).thenReturn("Silva");

        return update;
    }

    // =========================================================================
    // CONTRATO ksilisk (CommandUpdateHandler)
    // =========================================================================

    @Nested
    @DisplayName("CommandUpdateHandler (ksilisk)")
    class KsiliskContract {

        @Test
        @DisplayName("commands() retorna /help, /ajuda, /start")
        void commandsRetornaComandos() {
            // ✅ S5853: asserções encadeadas em uma única chamada
            assertThat(handler.commands())
                    .hasSize(3)
                    .containsExactlyInAnyOrder("/help", "/ajuda", "/start");
        }

        @ParameterizedTest(name = "[{index}] ''{0}''")
        @ValueSource(strings = {"/help", "/ajuda"})
        @DisplayName("/help e /ajuda: envia HELP_TEXT")
        void helpEAjuda(String comando) {
            Update update = mockUpdate(comando);

            handler.handle(update);

            verify(telegramFacade).enviarMensagemHtml(CHAT_ID, BotMessages.HELP_TEXT);
        }

        @ParameterizedTest(name = "[{index}] ''{0}''")
        @ValueSource(strings = {"/start", "/START", "/Start"})
        @DisplayName("/start (case-insensitive): envia welcome com nome do usuário")
        void startCaseInsensitive(String comando) {
            Update update = mockUpdate(comando);

            handler.handle(update);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());

            assertThat(captor.getValue()).contains("Olá, <b>Testador</b>!").contains("Tmill Bot");
        }

        @Test
        @DisplayName("outro comando: envia HELP_TEXT (fallback)")
        void outroComandoEnviaHelp() {
            Update update = mockUpdate("/qualquer-coisa");

            handler.handle(update);

            verify(telegramFacade).enviarMensagemHtml(CHAT_ID, BotMessages.HELP_TEXT);
        }

        @Test
        @DisplayName("não autorizado: NÃO envia nada")
        void naoAutorizadoNoKsilisk() {
            when(authService.isAuthorized(any(Update.class))).thenReturn(false);
            Update update = mockUpdate("/help");

            handler.handle(update);

            verifyNoInteractions(telegramFacade);
        }
    }

    // =========================================================================
    // CONTRATO AbstractCommandHandler (texto sem barra)
    // =========================================================================

    @Nested
    @DisplayName("AbstractCommandHandler (texto sem barra)")
    class AbstractHandlerContract {

        @Test
        @DisplayName("getPrefixo retorna 'start'")
        void getPrefixo() {
            assertThat(handler.getPrefixo()).isEqualTo("start");
        }

        @ParameterizedTest(name = "[{index}] ''{0}''")
        @ValueSource(strings = {"start", "START", "start ", "  start  ", "Start"})
        @DisplayName("processarComando com 'start' (case/whitespace): envia welcome")
        void startSemBarra(String comando) {
            Update update = mockUpdate(comando);

            handler.processarComando(update);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());
            assertThat(captor.getValue()).contains("Tmill Bot");
        }

        @Test
        @DisplayName("processarComando com texto não-start: envia HELP_TEXT")
        void textoNaoStartEnviaHelp() {
            // 'start help-me' começa com 'start ' → welcome (é prefixo válido)
            // O teste de fallback para HELP_TEXT precisa de texto que NÃO comece com 'start'
            Update update = mockUpdate("help-me");

            handler.processarComando(update);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());
            // Neste caso, como não começa com 'start', cai no else → HELP_TEXT
            assertThat(captor.getValue()).isEqualTo(BotMessages.HELP_TEXT);
        }

        @Test
        @DisplayName("não autorizado no processarComando: NÃO envia nada")
        void naoAutorizadoNoAbstract() {
            when(authService.isAuthorized(any(Update.class))).thenReturn(false);
            Update update = mockUpdate("start");

            handler.processarComando(update);

            verifyNoInteractions(telegramFacade);
        }
    }

    // =========================================================================
    // MÉTODOS PÚBLICOS handleWelcome / handleHelp
    // =========================================================================

    @Nested
    @DisplayName("Métodos públicos handleWelcome / handleHelp")
    class MetodosPublicos {

        @Test
        @DisplayName("handleWelcome: envia welcome quando autorizado")
        void handleWelcomeAutorizado() {
            Update update = mockUpdate("/start");

            handler.handleWelcome(update);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());
            assertThat(captor.getValue()).contains("Tmill Bot");
        }

        @Test
        @DisplayName("handleWelcome: não envia quando não autorizado")
        void handleWelcomeNaoAutorizado() {
            when(authService.isAuthorized(any(Update.class))).thenReturn(false);
            Update update = mockUpdate("/start");

            handler.handleWelcome(update);

            verifyNoInteractions(telegramFacade);
        }

        @Test
        @DisplayName("handleHelp: envia HELP_TEXT quando autorizado")
        void handleHelpAutorizado() {
            Update update = mockUpdate("/help");

            handler.handleHelp(update);

            verify(telegramFacade).enviarMensagemHtml(CHAT_ID, BotMessages.HELP_TEXT);
        }

        @Test
        @DisplayName("handleHelp: não envia quando não autorizado")
        void handleHelpNaoAutorizado() {
            when(authService.isAuthorized(any(Update.class))).thenReturn(false);
            Update update = mockUpdate("/help");

            handler.handleHelp(update);

            verifyNoInteractions(telegramFacade);
        }
    }

    // =========================================================================
    // buildWelcomeMessage (package-private, testável diretamente)
    // =========================================================================

    @Nested
    @DisplayName("buildWelcomeMessage")
    class BuildWelcomeMessage {

        @Test
        @DisplayName("contém nome, comandos e assinatura")
        void contemTudo() {
            String msg = handler.buildWelcomeMessage("Fulano");

            assertThat(msg)
                    .contains("<b>Fulano</b>")
                    .contains("Tmill Bot")
                    .contains("t1000 buscar")
                    .contains("t1000 salavip")
                    .contains("t1000 anotar ideia")
                    .contains("t1000 anotar aniversário")
                    .contains("Java 21");
        }

        @Test
        @DisplayName("nome é escapado para HTML")
        void nomeEscapado() {
            handler.buildWelcomeMessage("<script>alert</script>");
            verify(utils).escapeHtml("<script>alert</script>");
        }

        @Test
        @DisplayName("fallback 'amigo(a)' quando firstName é nulo")
        void fallbackNomeNulo() {
            Update update = mockUpdate("/start");
            when(update.message().from().firstName()).thenReturn(null);

            handler.handle(update);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());
            assertThat(captor.getValue()).contains("amigo(a)");
        }

        @Test
        @DisplayName("fallback 'amigo(a)' quando firstName é vazio")
        void fallbackNomeVazio() {
            Update update = mockUpdate("/start");
            when(update.message().from().firstName()).thenReturn("   ");

            handler.handle(update);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());
            assertThat(captor.getValue()).contains("amigo(a)");
        }
    }

    // =========================================================================
    // INTEGRAÇÃO: os dois caminhos produzem o mesmo resultado
    // =========================================================================

    @Nested
    @DisplayName("Consistência entre os caminhos")
    class Consistencia {

        @Test
        @DisplayName(
                "/start via ksilisk e 'start' via processarComando produzem welcome equivalente")
        void startKsiliskVsAbstract() {
            Update updateKsilisk = mockUpdate("/start");
            Update updateAbstract = mockUpdate("start");

            handler.handle(updateKsilisk);
            handler.processarComando(updateAbstract);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(telegramFacade, times(2)).enviarMensagemHtml(eq(CHAT_ID), captor.capture());

            assertThat(captor.getAllValues().get(0)).isEqualTo(captor.getAllValues().get(1));
        }

        @Test
        @DisplayName("/help via ksilisk e 'help' via processarComando (sem /start) → HELP_TEXT")
        void helpKsiliskVsAbstract() {
            Update updateKsilisk = mockUpdate("/help");
            Update updateAbstract = mockUpdate("help");

            handler.handle(updateKsilisk);
            handler.processarComando(updateAbstract);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(telegramFacade, times(2)).enviarMensagemHtml(eq(CHAT_ID), captor.capture());

            assertThat(captor.getAllValues()).allMatch(msg -> msg.equals(BotMessages.HELP_TEXT));
        }
    }

    // =========================================================================
    // Sem métricas
    // =========================================================================

    @Nested
    @DisplayName("Sem métricas")
    class SemMetricas {

        @Test
        @DisplayName("handle() não interage com MetricsService (não há injeção)")
        void semMetricasNoHandle() {
            Update update = mockUpdate("/help");

            assertThatCode(() -> handler.handle(update)).doesNotThrowAnyException();
            verify(telegramFacade).enviarMensagemHtml(CHAT_ID, BotMessages.HELP_TEXT);
        }
    }
}
