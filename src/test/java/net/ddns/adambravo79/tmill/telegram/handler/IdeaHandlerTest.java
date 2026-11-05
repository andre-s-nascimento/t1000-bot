/* (c) 2026 | 04/11/2026 */
package net.ddns.adambravo79.tmill.telegram.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;

import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.service.BotAnalyticsService;
import net.ddns.adambravo79.tmill.service.IdeasLoggerService;
import net.ddns.adambravo79.tmill.telegram.core.GroupAuthorizationService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;
import net.ddns.adambravo79.tmill.telegram.util.TelegramUtils;

/**
 * Testes do {@link IdeaHandler}.
 *
 * <p>Cobre:
 *
 * <ul>
 *   <li>Extração da ideia (com/sem dois-pontos ASCII/chinês, com/sem espaços)
 *   <li>Validação de ideia vazia
 *   <li>Persistência em arquivo + MongoDB (sucesso e falha parcial)
 *   <li>Notificação do owner (configurado e não configurado)
 *   <li>Confirmação ao usuário
 *   <li>Métricas
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdeaHandlerTest {

    @Mock private TelegramFacade telegramFacade;
    @Mock private GroupAuthorizationService authService;
    @Mock private TelegramUtils utils;
    @Mock private IdeasLoggerService ideasLogger;
    @Mock private BotAnalyticsService botAnalyticsService;
    @Mock private MetricsService metricsService;

    private IdeaHandler handler;

    private static final long CHAT_ID = 12345L;
    private static final long USER_ID = 999L;
    private static final long OWNER_ID = 500L;
    private static final String USER_NAME = "Testador Silva";
    private static final String CHAT_NAME = "privado";

    @BeforeEach
    void setUp() {
        handler =
                new IdeaHandler(
                        telegramFacade,
                        authService,
                        utils,
                        ideasLogger,
                        botAnalyticsService,
                        metricsService);

        ReflectionTestUtils.setField(handler, "ownerId", OWNER_ID);

        lenient().when(authService.isAuthorized(any(Update.class))).thenReturn(true);
        lenient().when(utils.buildFullName(any(User.class))).thenReturn(USER_NAME);
        lenient().when(utils.buildUserMention(any(User.class))).thenReturn("@Testador");
        lenient().when(utils.getChatName(any(Message.class))).thenReturn(CHAT_NAME);
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
    // getPrefixo
    // =========================================================================

    @Test
    @DisplayName("getPrefixo retorna 't1000 anotar ideia'")
    void getPrefixo() {
        assertThat(handler.getPrefixo()).isEqualTo("t1000 anotar ideia");
    }

    // =========================================================================
    // FLUXO FELIZ
    // =========================================================================

    @Nested
    @DisplayName("Fluxo feliz")
    class FluxoFeliz {

        @Test
        @DisplayName("ideia válida: salva em ambos destinos, notifica owner, confirma usuário")
        void ideiaValida() {
            Update update = mockUpdate("t1000 anotar ideia Fazer café");

            handler.processarComando(update);

            // 1. Salvamento em arquivo (todos args literais → sem eq())
            verify(ideasLogger).saveIdea(USER_ID, USER_NAME, CHAT_ID, "Fazer café", CHAT_NAME);

            // 2. Salvamento no MongoDB (mistura literal + matcher → eq() obrigatório)
            verify(botAnalyticsService)
                    .salvarIdeia(eq(USER_ID), eq("Fazer café"), any(), eq("BACKLOG_TECNICO"));

            // 3. Notificação ao owner (mistura literal + matcher)
            verify(telegramFacade).enviarMensagemHtml(eq(OWNER_ID), contains("Nova ideia"));

            // 4. Confirmação ao usuário (todos literais → sem eq())
            verify(telegramFacade).enviarMensagem(CHAT_ID, BotMessages.IDEIA_REGISTRADA);

            // 5. Métrica
            verify(metricsService).success("comando_anotar_ideia");
        }

        @ParameterizedTest(name = "[{index}] ''{0}'' → ''{1}''")
        @DisplayName("Variações de prefixo: dois-pontos, case, acentos")
        @CsvSource({
            // Dois-pontos ASCII e chinês
            "'t1000 anotar ideia: Fazer café', 'Fazer café'",
            "'t1000 anotar ideia：Fazer café', 'Fazer café'",
            // Preservação de maiúsculas e acentos
            "'t1000 anotar ideia Melhorar o BOT com AÇÃO', 'Melhorar o BOT com AÇÃO'",
            // Case-insensitive no prefixo
            "'T1000 ANOTAR IDEIA Teste', 'Teste'",
            "'t1000 Anotar Ideia Teste', 'Teste'",
            // Prefixo com espaços extras
            "'t1000   anotar   ideia   Teste', 'Teste'"
        })
        void variacoesDePrefixo(String input, String ideiaEsperada) {
            Update update = mockUpdate(input);

            handler.processarComando(update);

            verify(ideasLogger).saveIdea(USER_ID, USER_NAME, CHAT_ID, ideiaEsperada, CHAT_NAME);
        }
    }

    // =========================================================================
    // VALIDAÇÕES
    // =========================================================================

    @Nested
    @DisplayName("Validações")
    class Validacoes {

        @Test
        @DisplayName("só o comando sem ideia: pede a ideia")
        void semIdeiaPedeIdeia() {
            Update update = mockUpdate("t1000 anotar ideia");

            handler.processarComando(update);

            verify(telegramFacade).enviarMensagem(CHAT_ID, BotMessages.IDEIA_DIGITE_APOS_COMANDO);
            verify(ideasLogger, never())
                    .saveIdea(anyLong(), anyString(), anyLong(), anyString(), anyString());
            verify(metricsService).success("comando_anotar_ideia");
        }

        @ParameterizedTest(name = "[{index}] ''{0}'' → IDEIA_VAZIA")
        @ValueSource(
                strings = {
                    "t1000 anotar ideia :",
                    "t1000 anotar ideia ：",
                    "t1000 anotar ideia:  ",
                    "t1000 anotar ideia ：  "
                })
        @DisplayName("dois-pontos vazio: retorna IDEIA_VAZIA")
        void doisPontosVazio(String input) {
            Update update = mockUpdate(input);

            handler.processarComando(update);

            verify(telegramFacade).enviarMensagem(CHAT_ID, BotMessages.IDEIA_VAZIA);
            verify(ideasLogger, never())
                    .saveIdea(anyLong(), anyString(), anyLong(), anyString(), anyString());
        }
    }

    // =========================================================================
    // PERSISTÊNCIA — falhas parciais
    // =========================================================================

    @Nested
    @DisplayName("Falhas na persistência")
    class FalhasPersistencia {

        @Test
        @DisplayName("falha no arquivo: MongoDB ainda é tentado, usuário é confirmado")
        void falhaNoArquivo() {
            doThrow(new RuntimeException("Disco cheio"))
                    .when(ideasLogger)
                    .saveIdea(anyLong(), anyString(), anyLong(), anyString(), anyString());
            Update update = mockUpdate("t1000 anotar ideia Teste");

            assertThatCode(() -> handler.processarComando(update)).doesNotThrowAnyException();

            // MongoDB foi tentado
            verify(botAnalyticsService)
                    .salvarIdeia(eq(USER_ID), eq("Teste"), any(), eq("BACKLOG_TECNICO"));
            // Usuário foi confirmado
            verify(telegramFacade).enviarMensagem(CHAT_ID, BotMessages.IDEIA_REGISTRADA);
        }

        @Test
        @DisplayName("falha no MongoDB: arquivo ainda é salvo, usuário é confirmado")
        void falhaNoMongo() {
            doThrow(new RuntimeException("Mongo down"))
                    .when(botAnalyticsService)
                    .salvarIdeia(anyLong(), anyString(), any(), anyString());
            Update update = mockUpdate("t1000 anotar ideia Teste");

            assertThatCode(() -> handler.processarComando(update)).doesNotThrowAnyException();

            // Arquivo foi tentado
            verify(ideasLogger).saveIdea(USER_ID, USER_NAME, CHAT_ID, "Teste", CHAT_NAME);
            // Usuário foi confirmado
            verify(telegramFacade).enviarMensagem(CHAT_ID, BotMessages.IDEIA_REGISTRADA);
        }

        @Test
        @DisplayName("falha em ambos: usuário ainda é confirmado")
        void falhaEmAmbos() {
            doThrow(new RuntimeException("Disco cheio"))
                    .when(ideasLogger)
                    .saveIdea(anyLong(), anyString(), anyLong(), anyString(), anyString());
            doThrow(new RuntimeException("Mongo down"))
                    .when(botAnalyticsService)
                    .salvarIdeia(anyLong(), anyString(), any(), anyString());
            Update update = mockUpdate("t1000 anotar ideia Teste");

            assertThatCode(() -> handler.processarComando(update)).doesNotThrowAnyException();

            verify(telegramFacade).enviarMensagem(CHAT_ID, BotMessages.IDEIA_REGISTRADA);
        }
    }

    // =========================================================================
    // NOTIFICAÇÃO AO OWNER
    // =========================================================================

    @Nested
    @DisplayName("Notificação ao owner")
    class NotificacaoOwner {

        @Test
        @DisplayName("owner não configurado (0): não envia notificação")
        void ownerNaoConfigurado() {
            ReflectionTestUtils.setField(handler, "ownerId", 0L);
            Update update = mockUpdate("t1000 anotar ideia Teste");

            handler.processarComando(update);

            // Nunca envia notificação para owner=0 (o Telegram rejeitaria chatId 0)
            verify(telegramFacade, never()).enviarMensagemHtml(eq(0L), anyString());
            verify(telegramFacade, never()).enviarMensagem(eq(0L), anyString());
            // Mas a ideia ainda é salva
            verify(ideasLogger).saveIdea(USER_ID, USER_NAME, CHAT_ID, "Teste", CHAT_NAME);
        }

        @Test
        @DisplayName("notificação ao owner contém a ideia, usuário e local")
        void notificacaoContemDetalhes() {
            Update update = mockUpdate("t1000 anotar ideia Teste");

            handler.processarComando(update);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(telegramFacade).enviarMensagemHtml(eq(OWNER_ID), captor.capture());

            String msg = captor.getValue();
            assertThat(msg)
                    .contains("Nova ideia")
                    .contains("Teste")
                    .contains("@Testador")
                    .contains(CHAT_NAME);
        }

        @Test
        @DisplayName("ideia com caracteres HTML é escapada na notificação")
        void ideiaComHtmlEscapada() {
            Update update = mockUpdate("t1000 anotar ideia <script>alert</script>");

            handler.processarComando(update);

            // Verifica que o escapeHtml foi chamado com a ideia completa
            verify(utils).escapeHtml("<script>alert</script>");
        }
    }

    // =========================================================================
    // MÉTRICAS
    // =========================================================================

    @Nested
    @DisplayName("Métricas")
    class Metricas {

        @Test
        @DisplayName("ideia válida: success('comando_anotar_ideia')")
        void ideiaValidaRegistraSuccess() {
            Update update = mockUpdate("t1000 anotar ideia Teste");

            handler.processarComando(update);

            verify(metricsService).success("comando_anotar_ideia");
            verify(metricsService, never()).error(anyString());
        }

        @Test
        @DisplayName("ideia vazia: success (registra tentativa)")
        void ideiaVaziaRegistraSuccess() {
            Update update = mockUpdate("t1000 anotar ideia");

            handler.processarComando(update);

            verify(metricsService).success("comando_anotar_ideia");
        }

        @Test
        @DisplayName("3 ideias: 3 successes")
        void tresIdeias() {
            handler.processarComando(mockUpdate("t1000 anotar ideia A"));
            handler.processarComando(mockUpdate("t1000 anotar ideia B"));
            handler.processarComando(mockUpdate("t1000 anotar ideia C"));

            verify(metricsService, times(3)).success("comando_anotar_ideia");
        }
    }

    // =========================================================================
    // AUTORIZAÇÃO
    // =========================================================================

    @Nested
    @DisplayName("Autorização")
    class Autorizacao {

        @Test
        @DisplayName("não autorizado: não processa nada")
        void naoAutorizado() {
            when(authService.isAuthorized(any(Update.class))).thenReturn(false);
            Update update = mockUpdate("t1000 anotar ideia Teste");

            handler.processarComando(update);

            verifyNoInteractions(ideasLogger);
            verifyNoInteractions(botAnalyticsService);
            verify(telegramFacade, never()).enviarMensagem(anyLong(), anyString());
            verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
        }
    }
}
