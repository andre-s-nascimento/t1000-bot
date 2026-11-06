/* (c) 2026 | 04/11/2026 */
package net.ddns.adambravo79.tmill.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.MessageEntity;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;

import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.model.AutoResponseOverride;
import net.ddns.adambravo79.tmill.service.AutoResponseService;
import net.ddns.adambravo79.tmill.service.BotAnalyticsService;
import net.ddns.adambravo79.tmill.service.MessageStoreService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.handler.BirthdayHandler;
import net.ddns.adambravo79.tmill.telegram.handler.HelpCommandHandler;
import net.ddns.adambravo79.tmill.telegram.handler.IdeaHandler;
import net.ddns.adambravo79.tmill.telegram.handler.MovieHandler;
import net.ddns.adambravo79.tmill.telegram.handler.SalaVipHandler;
import net.ddns.adambravo79.tmill.telegram.handler.TorrentHandler;
import net.ddns.adambravo79.tmill.telegram.handler.WorldCupHandler;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;
import net.ddns.adambravo79.tmill.telegram.util.TelegramUtils;

/**
 * Testes do {@link CommandHandler} (roteador).
 *
 * <p>Após a refatoração, este handler faz apenas:
 *
 * <ol>
 *   <li><b>Pré-processamento:</b> analytics, salvamento no DB (se não for comando), detecção de
 *       spoiler, auto-resposta.
 *   <li><b>Roteamento:</b> delega para o handler especializado correto.
 *   <li><b>Fallback:</b> se nenhum handler casar, envia "comando não reconhecido".
 * </ol>
 *
 * <p><b>O que NÃO testar aqui (já coberto em testes individuais):</b>
 *
 * <ul>
 *   <li>Lógica de busca de filme (coberta em {@code MovieHandlerTest})
 *   <li>Lógica da Copa (coberta em {@code WorldCupHandlerTest})
 *   <li>Lógica da Sala VIP (coberta em {@code SalaVipHandlerTest})
 *   <li>Lógica de aniversário (coberta em {@code BirthdayHandlerTest})
 *   <li>Lógica de ideia (coberta em {@code IdeaHandlerTest})
 *   <li>Lógica de help (coberta em {@code HelpCommandHandlerTest})
 * </ul>
 *
 * <p>Aqui, os handlers são <b>mocks</b> — verificamos apenas que o roteador os chama corretamente.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommandHandlerTest {

    @Mock private BotAnalyticsService botAnalyticsService;
    @Mock private MessageStoreService messageStoreService;
    @Mock private AutoResponseService autoResponseService;
    @Mock private TelegramFacade telegramFacade;
    @Mock private TelegramUtils utils;
    @Mock private MetricsService metricsService;

    // Handlers especializados (mocks — não testamos a lógica deles aqui)
    @Mock private BirthdayHandler birthdayHandler;
    @Mock private HelpCommandHandler helpCommandHandler;
    @Mock private IdeaHandler ideaHandler;
    @Mock private MovieHandler movieHandler;
    @Mock private SalaVipHandler salaVipHandler;
    @Mock private WorldCupHandler worldCupHandler;
    @Mock private TorrentHandler torrentHandler;

    private CommandHandler handler;

    private static final long CHAT_ID = 12345L;
    private static final long USER_ID = 999L;
    private static final String USER_NAME = "Testador Silva";

    @BeforeEach
    void setUp() {
        handler =
                new CommandHandler(
                        botAnalyticsService,
                        messageStoreService,
                        autoResponseService,
                        telegramFacade,
                        utils,
                        metricsService,
                        birthdayHandler,
                        helpCommandHandler,
                        ideaHandler,
                        movieHandler,
                        salaVipHandler,
                        worldCupHandler,
                        torrentHandler);

        lenient().when(utils.buildFullName(any(User.class))).thenReturn(USER_NAME);
        lenient().when(utils.buildUserMention(any(User.class))).thenReturn("@Testador");

        // Default: auto-resposta vazia (não casa)
        lenient()
                .when(autoResponseService.getResponseRule(anyLong(), anyString()))
                .thenReturn(Optional.empty());
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
        when(message.entities()).thenReturn(null);
        when(chat.id()).thenReturn(CHAT_ID);
        when(user.id()).thenReturn(USER_ID);
        when(user.firstName()).thenReturn("Testador");
        when(user.lastName()).thenReturn("Silva");

        return update;
    }

    // =========================================================================
    // ROTEAMENTO — COMANDOS EXPLÍCITOS
    // =========================================================================

    @Nested
    @DisplayName("Roteamento — comandos explícitos")
    class RoteamentoComandos {

        @ParameterizedTest(name = "[{index}] ''{0}'' → HelpCommandHandler")
        @ValueSource(strings = {"start", "START", "start "})
        @DisplayName("'start' roteia para HelpCommandHandler")
        void startRoteiaParaHelp(String input) {
            Update update = mockUpdate(input);

            handler.handleTextUpdate(update);

            verify(helpCommandHandler).processarComando(update);
        }

        @Test
        @DisplayName("'t1000 anotar ideia' roteia para IdeaHandler")
        void anotarIdeiaRoteia() {
            Update update = mockUpdate("t1000 anotar ideia: teste");

            handler.handleTextUpdate(update);

            verify(ideaHandler).processarComando(update);
        }

        @ParameterizedTest(name = "[{index}] ''{0}'' → MovieHandler")
        @ValueSource(
                strings = {"t1000 buscar duna", "t1000 estreias da semana", "t1000 lancamentos"})
        @DisplayName("Comandos de filme roteiam para MovieHandler")
        void comandosDeFilmeRoteiam(String input) {
            Update update = mockUpdate(input);

            handler.handleTextUpdate(update);

            verify(movieHandler).processarComando(update);
        }

        @ParameterizedTest(name = "[{index}] ''{0}'' → WorldCupHandler")
        @ValueSource(strings = {"t1000 jogos hoje", "t1000 copa hoje", "t1000 resultados ontem"})
        @DisplayName("Comandos da Copa roteiam para WorldCupHandler")
        void comandosDaCopaRoteiam(String input) {
            Update update = mockUpdate(input);

            handler.handleTextUpdate(update);

            verify(worldCupHandler).processarComando(update);
        }

        @ParameterizedTest(name = "[{index}] ''{0}'' → BirthdayHandler")
        @ValueSource(
                strings = {
                    "t1000 registrar aniversario 05/10",
                    "t1000 registrar aniversário 05/10",
                    "t1000 anotar aniversario 05/10",
                    "t1000 anotar aniversário 05/10",
                    "t1000 aniversario 05/10",
                    "t1000 aniversário 05/10"
                })
        @DisplayName("Variantes de aniversário roteiam para BirthdayHandler")
        void variantesAniversarioRoteiam(String input) {
            Update update = mockUpdate(input);

            handler.handleTextUpdate(update);

            verify(birthdayHandler).processarComando(update);
        }

        @ParameterizedTest(name = "[{index}] ''{0}'' → SalaVipHandler")
        @ValueSource(strings = {"t1000 salavip 15", "t1000 sala vip 15"})
        @DisplayName("Comandos da Sala VIP roteiam para SalaVipHandler")
        void comandosSalaVipRoteiam(String input) {
            Update update = mockUpdate(input);

            handler.handleTextUpdate(update);

            verify(salaVipHandler).processarComando(update);
        }

        @Test
        @DisplayName("'t-1000 buscar' (com hífen) normaliza e roteia")
        void t1000ComHifen() {
            Update update = mockUpdate("t-1000 buscar duna");

            handler.handleTextUpdate(update);

            verify(movieHandler).processarComando(update);
        }

        @Test
        @DisplayName("Comando com espaços duplos normaliza e roteia")
        void espacosDuplos() {
            Update update = mockUpdate("t1000   jogos   hoje");

            handler.handleTextUpdate(update);

            verify(worldCupHandler).processarComando(update);
        }
    }

    // =========================================================================
    // FALLBACK — COMANDO NÃO RECONHECIDO
    // =========================================================================

    @Nested
    @DisplayName("Fallback")
    class Fallback {

        @Test
        @DisplayName("'t1000 banana' (não roteado): envia COMANDO_NAO_RECONHECIDO")
        void comandoNaoReconhecido() {
            Update update = mockUpdate("t1000 banana");

            handler.handleTextUpdate(update);

            verify(telegramFacade).enviarMensagem(CHAT_ID, BotMessages.COMANDO_NAO_RECONHECIDO);
            verifyNoInteractions(
                    helpCommandHandler,
                    ideaHandler,
                    movieHandler,
                    worldCupHandler,
                    birthdayHandler,
                    salaVipHandler);
        }

        @Test
        @DisplayName("'t1000 com link' não roteado: loga warn (mas envia COMANDO_NAO_RECONHECIDO)")
        void comandoComLinkNaoRoteado() {
            Update update = mockUpdate("t1000 banana http://link.com");

            handler.handleTextUpdate(update);

            verify(telegramFacade).enviarMensagem(CHAT_ID, BotMessages.COMANDO_NAO_RECONHECIDO);
        }

        @Test
        @DisplayName("Texto vazio: não é comando, não roteia, não envia nada")
        void textoVazio() {
            Update update = mockUpdate("");

            handler.handleTextUpdate(update);

            verify(telegramFacade, never()).enviarMensagem(anyLong(), anyString());
        }

        @Test
        @DisplayName("Mensagem sem texto: ignorada (early return)")
        void mensagemSemTexto() {
            Update update = mock(Update.class);
            Message message = mock(Message.class);
            when(update.message()).thenReturn(message);
            when(message.text()).thenReturn(null);

            handler.handleTextUpdate(update);

            verifyNoInteractions(
                    botAnalyticsService,
                    messageStoreService,
                    autoResponseService,
                    helpCommandHandler,
                    ideaHandler,
                    movieHandler,
                    worldCupHandler,
                    birthdayHandler,
                    salaVipHandler);
        }
    }

    // =========================================================================
    // PRÉ-PROCESSAMENTO — mensagens que NÃO são comandos
    // =========================================================================

    @Nested
    @DisplayName("Pré-processamento (mensagens normais)")
    class PreProcessamento {

        @Test
        @DisplayName("Mensagem normal: salva no DB, verifica auto-resposta, não roteia")
        void mensagemNormalSalvaNoDb() {
            Update update = mockUpdate("uma mensagem qualquer");

            handler.handleTextUpdate(update);

            verify(messageStoreService)
                    .saveMessage(CHAT_ID, USER_ID, USER_NAME, "uma mensagem qualquer", false);
            verify(autoResponseService).getResponseRule(USER_ID, "uma mensagem qualquer");
            // Nenhum handler foi chamado
            verifyNoInteractions(
                    helpCommandHandler,
                    ideaHandler,
                    movieHandler,
                    worldCupHandler,
                    birthdayHandler,
                    salaVipHandler);
        }

        @Test
        @DisplayName("Mensagem com spoiler: salva com ignore_in_digest=true")
        void mensagemComSpoiler() {
            Update update = mockUpdate("spoiler do filme");
            Message message = update.message();
            MessageEntity spoiler = mock(MessageEntity.class);
            when(spoiler.type()).thenReturn(MessageEntity.Type.spoiler);
            when(message.entities()).thenReturn(new MessageEntity[] {spoiler});

            handler.handleTextUpdate(update);

            verify(messageStoreService)
                    .saveMessage(CHAT_ID, USER_ID, USER_NAME, "spoiler do filme", true);
        }

        @Test
        @DisplayName("Comando NÃO é salvo no DB")
        void comandoNaoSalvoNoDb() {
            Update update = mockUpdate("t1000 buscar duna");

            handler.handleTextUpdate(update);

            verify(messageStoreService, never())
                    .saveMessage(anyLong(), anyLong(), anyString(), anyString(), anyBoolean());
        }

        @Test
        @DisplayName("Analytics é registrado para TODA mensagem (comando ou não)")
        void analyticsSempreRegistrado() {
            handler.handleTextUpdate(mockUpdate("t1000 buscar duna"));
            handler.handleTextUpdate(mockUpdate("mensagem normal"));

            verify(botAnalyticsService, times(2))
                    .registrarConversa(
                            eq(CHAT_ID),
                            eq(USER_ID),
                            eq(USER_NAME),
                            eq("TEXT"),
                            anyString(),
                            anyString());
        }
    }

    // =========================================================================
    // AUTO-RESPOSTA
    // =========================================================================

    @Nested
    @DisplayName("Auto-resposta")
    class AutoResposta {

        @Test
        @DisplayName("Auto-resposta sem animação: envia texto, NÃO roteia")
        void autoRespostaSemAnimacao() {
            AutoResponseOverride response = new AutoResponseOverride("Olá!", null);
            when(autoResponseService.getResponseRule(USER_ID, "bom dia"))
                    .thenReturn(Optional.of(response));
            Update update = mockUpdate("bom dia");

            handler.handleTextUpdate(update);

            verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("Olá!"));
            verify(metricsService).success("auto_response_disparada");
            verifyNoInteractions(
                    helpCommandHandler,
                    ideaHandler,
                    movieHandler,
                    worldCupHandler,
                    birthdayHandler,
                    salaVipHandler);
        }

        @Test
        @DisplayName("Auto-resposta com animação válida: envia mídia")
        void autoRespostaComAnimacao() {
            AutoResponseOverride response =
                    new AutoResponseOverride("Bom dia!", "https://exemplo.com/gif.gif");
            when(autoResponseService.getResponseRule(USER_ID, "bom dia"))
                    .thenReturn(Optional.of(response));
            when(telegramFacade.enviarMidia(anyLong(), anyString(), anyString())).thenReturn(true);
            Update update = mockUpdate("bom dia");

            handler.handleTextUpdate(update);

            verify(telegramFacade)
                    .enviarMidia(eq(CHAT_ID), eq("https://exemplo.com/gif.gif"), anyString());
            verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
        }

        @Test
        @DisplayName("Auto-resposta com animação inválida: envia texto")
        void autoRespostaComAnimacaoInvalida() {
            AutoResponseOverride response = new AutoResponseOverride("Bom dia!", "nao-e-url");
            when(autoResponseService.getResponseRule(USER_ID, "bom dia"))
                    .thenReturn(Optional.of(response));
            Update update = mockUpdate("bom dia");

            handler.handleTextUpdate(update);

            verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("Bom dia!"));
            verify(telegramFacade, never()).enviarMidia(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("Auto-resposta com falha no envio de mídia: fallback para texto")
        void autoRespostaComFalhaNaMidia() {
            AutoResponseOverride response =
                    new AutoResponseOverride("Bom dia!", "https://exemplo.com/gif.gif");
            when(autoResponseService.getResponseRule(USER_ID, "bom dia"))
                    .thenReturn(Optional.of(response));
            when(telegramFacade.enviarMidia(anyLong(), anyString(), anyString())).thenReturn(false);
            Update update = mockUpdate("bom dia");

            handler.handleTextUpdate(update);

            verify(telegramFacade).enviarMidia(anyLong(), anyString(), anyString());
            verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), anyString());
        }

        @Test
        @DisplayName("Comandos NÃO passam por auto-resposta")
        void comandoNaoPassaPorAutoResposta() {
            // Mesmo que exista uma regra casando com "t1000 buscar", o comando não deve
            // ser interceptado pela auto-resposta (a checagem é só para não-comandos)
            Update update = mockUpdate("t1000 buscar duna");

            handler.handleTextUpdate(update);

            verify(autoResponseService, never()).getResponseRule(anyLong(), anyString());
            verify(movieHandler).processarComando(update);
        }
    }

    // =========================================================================
    // NORMALIZAÇÃO
    // =========================================================================

    @Nested
    @DisplayName("Normalização")
    class Normalizacao {

        @Test
        @DisplayName("'START' (uppercase) normaliza para 'start'")
        void startUppercase() {
            Update update = mockUpdate("START");

            handler.handleTextUpdate(update);

            verify(helpCommandHandler).processarComando(update);
        }

        @Test
        @DisplayName("'T1000 BUSCAR' normaliza para 't1000 buscar'")
        void t1000BuscarUppercase() {
            Update update = mockUpdate("T1000 BUSCAR DUNA");

            handler.handleTextUpdate(update);

            verify(movieHandler).processarComando(update);
        }

        @Test
        @DisplayName("'/start' (barra) normaliza para 'start'")
        void startComBarra() {
            Update update = mockUpdate("/start");

            handler.handleTextUpdate(update);

            verify(helpCommandHandler).processarComando(update);
        }
    }

    // =========================================================================
    // ISOLAMENTO — cada comando chama SÓ seu handler
    // =========================================================================

    @Nested
    @DisplayName("Isolamento entre handlers")
    class Isolamento {

        @Test
        @DisplayName("'t1000 buscar' chama só MovieHandler (não outros)")
        void buscarSoMovieHandler() {
            Update update = mockUpdate("t1000 buscar duna");

            handler.handleTextUpdate(update);

            verify(movieHandler).processarComando(update);
            verifyNoInteractions(
                    helpCommandHandler,
                    ideaHandler,
                    worldCupHandler,
                    birthdayHandler,
                    salaVipHandler);
        }

        @Test
        @DisplayName("'t1000 salavip' chama só SalaVipHandler")
        void salavipSoSalaVipHandler() {
            Update update = mockUpdate("t1000 salavip 15");

            handler.handleTextUpdate(update);

            verify(salaVipHandler).processarComando(update);
            verifyNoInteractions(
                    helpCommandHandler,
                    ideaHandler,
                    movieHandler,
                    worldCupHandler,
                    birthdayHandler);
        }

        @Test
        @DisplayName("'t1000 anotar ideia' chama só IdeaHandler")
        void ideiaSoIdeaHandler() {
            Update update = mockUpdate("t1000 anotar ideia: teste");

            handler.handleTextUpdate(update);

            verify(ideaHandler).processarComando(update);
            verifyNoInteractions(
                    helpCommandHandler,
                    movieHandler,
                    worldCupHandler,
                    birthdayHandler,
                    salaVipHandler);
        }
    }
}
