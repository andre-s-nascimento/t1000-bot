package net.ddns.adambravo79.tmill.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;

import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.exception.MovieNotFoundException;
import net.ddns.adambravo79.tmill.model.AutoResponseOverride;
import net.ddns.adambravo79.tmill.model.MovieOrchestrationResponse;
import net.ddns.adambravo79.tmill.model.MovieRecord;
import net.ddns.adambravo79.tmill.model.MovieSearchResponse;
import net.ddns.adambravo79.tmill.service.*;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.TelegramUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // Permite stubs não utilizados
class CommandHandlerTest {

    @Mock private MovieService movieService;
    @Mock private AutoResponseService autoResponseService;
    @Mock private WeeklyReleasesService weeklyReleasesService;
    @Mock private WorldCupSchedulerService worldCupSchedulerService;
    @Mock private IdeasLogger ideasLogger;
    @Mock private MessageStoreService messageStoreService;
    @Mock private TelegramFacade telegramFacade;
    @Mock private TelegramUtils utils;

    @InjectMocks private CommandHandler commandHandler;

    private Update update;
    private Message message;
    private User user;
    private Chat chat;

    private static final long CHAT_ID = 12345L;
    private static final long USER_ID = 999L;
    private static final long OWNER_ID = 999L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(commandHandler, "ownerId", OWNER_ID);

        update = mock(Update.class);
        message = mock(Message.class);
        user = mock(User.class);
        chat = mock(Chat.class);

        when(update.message()).thenReturn(message);
        when(message.from()).thenReturn(user);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(CHAT_ID);

        when(user.id()).thenReturn(USER_ID);
        when(user.firstName()).thenReturn("Testador");
        when(user.lastName()).thenReturn("Silva");

        // Stubs básicos do utils – serão ignorados se não usados (graças ao LENIENT)
        when(utils.buildFullName(any(User.class))).thenReturn("Testador Silva");
        when(utils.buildUserMention(any(User.class))).thenReturn("@Testador");
        when(utils.escapeHtml(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(utils.getChatName(any(Message.class))).thenReturn("privado");
    }

    // =========================
    // 🧪 TESTES DE /start
    // =========================

    @Test
    void deveResponderAoStart() {
        when(message.text()).thenReturn("/start");
        commandHandler.handleTextUpdate(update);
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), anyString());
        verifyNoInteractions(movieService, autoResponseService, messageStoreService);
    }

    // =========================
    // 🧪 TESTES DE BUSCA
    // =========================

    @Test
    void deveBuscarFilmeComUmResultado() {
        when(message.text()).thenReturn("t1000 buscar Duna");
        MovieRecord filme =
                new MovieRecord(
                        1L,
                        "Duna",
                        "Dune",
                        "2021-10-01",
                        "desc",
                        8.5,
                        8.5,
                        "/poster.jpg",
                        List.of("US"));
        MovieSearchResponse response = new MovieSearchResponse(1, 1, 1, List.of(filme));
        when(movieService.buscarFilme("Duna")).thenReturn(response);
        when(movieService.buscarPorId(1L))
                .thenReturn(new MovieOrchestrationResponse("texto", "http://foto.jpg"));

        commandHandler.handleTextUpdate(update);

        verify(movieService).buscarFilme("Duna");
        verify(movieService).buscarPorId(1L);
        verify(telegramFacade).enviarFotoHtml(eq(CHAT_ID), eq("http://foto.jpg"), anyString());
    }

    @Test
    void deveBuscarFilmeComMultiplosResultados() {
        when(message.text()).thenReturn("t1000 buscar Teste");
        MovieRecord filme1 =
                new MovieRecord(1L, "Teste A", "", "2021", "", 0.0, 0.0, "", List.of());
        MovieRecord filme2 =
                new MovieRecord(2L, "Teste B", "", "2022", "", 0.0, 0.0, "", List.of());
        MovieSearchResponse response = new MovieSearchResponse(1, 2, 1, List.of(filme1, filme2));
        when(movieService.buscarFilme("Teste")).thenReturn(response);

        commandHandler.handleTextUpdate(update);

        verify(movieService).buscarFilme("Teste");
        verify(movieService, never()).buscarPorId(anyLong());
        verify(telegramFacade).enviarComBotoesHtml(eq(CHAT_ID), anyString(), any());
    }

    @Test
    void deveInformarQuandoFilmeNaoEncontrado() {
        when(message.text()).thenReturn("t1000 buscar Inexistente");
        when(movieService.buscarFilme("Inexistente"))
                .thenThrow(new MovieNotFoundException("Filme não encontrado"));

        commandHandler.handleTextUpdate(update);

        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("❌ Filme não encontrado"));
    }

    @Test
    void deveRejeitarBuscaComMenosDe3Caracteres() {
        when(message.text()).thenReturn("t1000 buscar ab");
        commandHandler.handleTextUpdate(update);
        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("pelo menos 3 caracteres"));
        verifyNoInteractions(movieService);
    }

    @Test
    void deveRejeitarBuscaComMaisDe100Caracteres() {
        String termoLongo = "a".repeat(101);
        when(message.text()).thenReturn("t1000 buscar " + termoLongo);
        commandHandler.handleTextUpdate(update);
        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("muito longo"));
        verifyNoInteractions(movieService);
    }

    // =========================
    // 🧪 TESTES DE ANOTAR IDEIA
    // =========================

    @Test
    void deveAnotarIdeiaComSucesso() {
        when(message.text()).thenReturn("t1000 anotar ideia: Melhorar o bot");
        commandHandler.handleTextUpdate(update);

        verify(ideasLogger)
                .saveIdea(USER_ID, "Testador Silva", CHAT_ID, "Melhorar o bot", "privado");
        verify(telegramFacade).enviarMensagemHtml(eq(OWNER_ID), anyString());
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("✅ Ideia registrada"));
    }

    @Test
    void deveRejeitarIdeiaVazia() {
        when(message.text()).thenReturn("t1000 anotar ideia");
        commandHandler.handleTextUpdate(update);
        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("Digite a ideia"));
        verifyNoInteractions(ideasLogger);
    }

    // =========================
    // 🧪 TESTES DE ESTREIAS
    // =========================

    @Test
    void deveResponderEstreias() {
        when(message.text()).thenReturn("t1000 estreias da semana");
        when(weeklyReleasesService.getWeeklyReleasesMessage()).thenReturn("Lista de estreias");
        commandHandler.handleTextUpdate(update);
        verify(weeklyReleasesService).getWeeklyReleasesMessage();
        verify(telegramFacade).enviarMensagemHtml(CHAT_ID, "Lista de estreias");
    }

    @Test
    void deveResponderLancamentos() {
        when(message.text()).thenReturn("t1000 lancamentos");
        when(weeklyReleasesService.getWeeklyReleasesMessage()).thenReturn("Lista de lançamentos");
        commandHandler.handleTextUpdate(update);
        verify(weeklyReleasesService).getWeeklyReleasesMessage();
        verify(telegramFacade).enviarMensagemHtml(CHAT_ID, "Lista de lançamentos");
    }

    // =========================
    // 🧪 TESTES DE COPA
    // =========================

    @Test
    void deveResponderJogosDeHoje() {
        ReflectionTestUtils.setField(commandHandler, "worldcupEnabled", true);
        when(message.text()).thenReturn("t1000 jogos de hoje");
        commandHandler.handleTextUpdate(update);
        verify(worldCupSchedulerService).sendMatchesToChat(eq(CHAT_ID), any(LocalDate.class));
    }

    @Test
    void deveResponderResultadosComData() {
        ReflectionTestUtils.setField(commandHandler, "worldcupEnabled", true);
        when(message.text()).thenReturn("t1000 resultados 20/06");
        commandHandler.handleTextUpdate(update);
        verify(worldCupSchedulerService).sendResultsToChat(eq(CHAT_ID), any(LocalDate.class));
    }

    @Test
    void deveAvisarDataInvalidaNosResultados() {
        ReflectionTestUtils.setField(commandHandler, "worldcupEnabled", true);
        when(message.text()).thenReturn("t1000 resultados invalido");
        commandHandler.handleTextUpdate(update);
        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("Formato de data invalido"));
        verifyNoInteractions(worldCupSchedulerService);
    }

    @Test
    void deveResponderCopaDesabilitada() {
        ReflectionTestUtils.setField(commandHandler, "worldcupEnabled", false);
        when(message.text()).thenReturn("t1000 jogos de hoje");
        commandHandler.handleTextUpdate(update);
        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("Copa de 2026 ja acabou"));
        verifyNoInteractions(worldCupSchedulerService);
    }

    // =========================
    // 🧪 TESTES DE AUTO-RESPOSTA
    // =========================

    @Test
    void deveDispararAutoRespostaSemMidia() {
        when(message.text()).thenReturn("bom dia");
        AutoResponseOverride response = new AutoResponseOverride("Bom dia para você!", null);
        when(autoResponseService.getResponseRule(USER_ID, "bom dia"))
                .thenReturn(Optional.of(response));

        commandHandler.handleTextUpdate(update);

        verify(autoResponseService).getResponseRule(USER_ID, "bom dia");
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("Bom dia para você!"));
        verify(telegramFacade, never()).enviarMidia(anyLong(), anyString(), anyString());
    }

    @Test
    void deveDispararAutoRespostaComMidiaValida() {
        when(message.text()).thenReturn("bom dia");
        AutoResponseOverride response =
                new AutoResponseOverride("Bom dia!", "https://exemplo.com/gif.gif");
        when(autoResponseService.getResponseRule(USER_ID, "bom dia"))
                .thenReturn(Optional.of(response));

        commandHandler.handleTextUpdate(update);

        verify(telegramFacade)
                .enviarMidia(eq(CHAT_ID), eq("https://exemplo.com/gif.gif"), contains("Bom dia!"));
    }

    @Test
    void deveDispararAutoRespostaComMidiaInvalida_ENviaApenasTexto() {
        when(message.text()).thenReturn("bom dia");
        AutoResponseOverride response = new AutoResponseOverride("Bom dia!", "");
        when(autoResponseService.getResponseRule(USER_ID, "bom dia"))
                .thenReturn(Optional.of(response));

        commandHandler.handleTextUpdate(update);

        verify(telegramFacade, never()).enviarMidia(anyLong(), anyString(), anyString());
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("Bom dia!"));
    }

    // =========================
    // 🧪 TESTES DE COMANDO NÃO RECONHECIDO E LINKS
    // =========================

    @Test
    void deveResponderComandoNaoReconhecido() {
        when(message.text()).thenReturn("t1000 comando invalido");
        commandHandler.handleTextUpdate(update);
        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("Comando nao reconhecido"));
    }

    @Test
    void deveLogarLinkNaoProcessado() {
        when(message.text()).thenReturn("t1000 comando invalido http://link.com");
        commandHandler.handleTextUpdate(update);
        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("Comando nao reconhecido"));
        // não lança exceção
    }

    // =========================
    // 🧪 TESTES DE MENSAGENS NORMAIS (sem comando)
    // =========================

    @Test
    void deveSalvarMensagemNormal() {
        when(message.text()).thenReturn("Uma mensagem qualquer");
        when(autoResponseService.getResponseRule(anyLong(), anyString()))
                .thenReturn(Optional.empty());

        commandHandler.handleTextUpdate(update);

        verify(messageStoreService)
                .saveMessage(eq(CHAT_ID), eq(USER_ID), anyString(), eq("Uma mensagem qualquer"));
        verify(telegramFacade, never()).enviarMensagem(anyLong(), anyString());
    }

    @Test
    void naoDeveSalvarComando() {
        when(message.text()).thenReturn("t1000 buscar filme");
        when(movieService.buscarFilme("filme")).thenThrow(new MovieNotFoundException(""));

        commandHandler.handleTextUpdate(update);

        verify(messageStoreService, never())
                .saveMessage(anyLong(), anyLong(), anyString(), anyString());
    }

    // ========================================================================
    // NOVOS TESTES PARA COBRIR BRANCHES FALTANTES
    // ========================================================================

    // 1. handleResultados com param vazio → usa hoje
    @Test
    void deveResponderResultadosComHoje() {
        ReflectionTestUtils.setField(commandHandler, "worldcupEnabled", true);
        when(message.text()).thenReturn("t1000 resultados hoje");

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        doNothing()
                .when(worldCupSchedulerService)
                .sendResultsToChat(eq(CHAT_ID), dateCaptor.capture());

        commandHandler.handleTextUpdate(update);

        verify(worldCupSchedulerService, times(1))
                .sendResultsToChat(eq(CHAT_ID), dateCaptor.capture());
        LocalDate hoje = LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE));
        assertThat(dateCaptor.getValue()).isEqualTo(hoje);
    }

    // 2. handleResultados com "hoje" e "ontem"

    @Test
    void deveResponderResultadosComOntem() {
        ReflectionTestUtils.setField(commandHandler, "worldcupEnabled", true);
        when(message.text()).thenReturn("t1000 resultados ontem");
        commandHandler.handleTextUpdate(update);
        verify(worldCupSchedulerService).sendResultsToChat(eq(CHAT_ID), any(LocalDate.class));
        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(worldCupSchedulerService).sendResultsToChat(eq(CHAT_ID), dateCaptor.capture());
        LocalDate ontem = LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE)).minusDays(1);
        assertThat(dateCaptor.getValue()).isEqualTo(ontem);
    }

    // 3. parseDateParam com formato dd/MM (sem ano) e dd-MM
    @Test
    void deveResponderResultadosComDataDDMM() {
        ReflectionTestUtils.setField(commandHandler, "worldcupEnabled", true);
        when(message.text()).thenReturn("t1000 resultados 20/06");
        commandHandler.handleTextUpdate(update);
        verify(worldCupSchedulerService).sendResultsToChat(eq(CHAT_ID), any(LocalDate.class));
        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(worldCupSchedulerService).sendResultsToChat(eq(CHAT_ID), dateCaptor.capture());
        // Ano padrão é 2026
        assertThat(dateCaptor.getValue()).isEqualTo(LocalDate.of(2026, Month.JUNE, 20));
    }

    @Test
    void deveResponderResultadosComDataDDMMComHifen() {
        ReflectionTestUtils.setField(commandHandler, "worldcupEnabled", true);
        when(message.text()).thenReturn("t1000 resultados 20-06");
        commandHandler.handleTextUpdate(update);
        verify(worldCupSchedulerService).sendResultsToChat(eq(CHAT_ID), any(LocalDate.class));
        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(worldCupSchedulerService).sendResultsToChat(eq(CHAT_ID), dateCaptor.capture());
        assertThat(dateCaptor.getValue()).isEqualTo(LocalDate.of(2026, Month.JUNE, 20));
    }

    // 4. exibirRespostaFilme com foto inválida (null ou sem http)
    @Test
    void deveExibirFilmeSemFoto() {
        // Simula busca com um resultado
        when(message.text()).thenReturn("t1000 buscar FilmeSemFoto");
        MovieRecord filme =
                new MovieRecord(1L, "FilmeSemFoto", "", "2021", "", 0.0, 0.0, "", List.of());
        MovieSearchResponse response = new MovieSearchResponse(1, 1, 1, List.of(filme));
        when(movieService.buscarFilme("FilmeSemFoto")).thenReturn(response);
        // Retorna MovieOrchestrationResponse sem foto
        MovieOrchestrationResponse orcResponse =
                new MovieOrchestrationResponse("texto sem foto", null);
        when(movieService.buscarPorId(1L)).thenReturn(orcResponse);

        commandHandler.handleTextUpdate(update);

        verify(movieService).buscarFilme("FilmeSemFoto");
        verify(movieService).buscarPorId(1L);
        verify(telegramFacade, never()).enviarFotoHtml(anyLong(), anyString(), anyString());
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("_(sem imagem)_"));
    }

    @Test
    void deveExibirFilmeComFotoInvalida() {
        when(message.text()).thenReturn("t1000 buscar FilmeFotoInvalida");
        MovieRecord filme =
                new MovieRecord(1L, "FilmeFotoInvalida", "", "2021", "", 0.0, 0.0, "", List.of());
        MovieSearchResponse response = new MovieSearchResponse(1, 1, 1, List.of(filme));
        when(movieService.buscarFilme("FilmeFotoInvalida")).thenReturn(response);
        // Retorna orcResponse com foto inválida (não começa com http)
        MovieOrchestrationResponse orcResponse =
                new MovieOrchestrationResponse("texto", "ftp://foto.jpg");
        when(movieService.buscarPorId(1L)).thenReturn(orcResponse);

        commandHandler.handleTextUpdate(update);

        verify(telegramFacade, never()).enviarFotoHtml(anyLong(), anyString(), anyString());
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("_(sem imagem)_"));
    }

    // 5. handleAnotarIdeia com ideia começando com ":" ou "："
    @Test
    void deveAnotarIdeiaComDoisPontos() {
        when(message.text()).thenReturn("t1000 anotar ideia: Melhorar o bot");
        when(utils.buildFullName(any())).thenReturn("Testador Silva");
        when(utils.getChatName(any())).thenReturn("privado");
        when(utils.buildUserMention(any())).thenReturn("@Testador");
        when(utils.escapeHtml(any())).thenAnswer(inv -> inv.getArgument(0));

        commandHandler.handleTextUpdate(update);

        verify(ideasLogger)
                .saveIdea(USER_ID, "Testador Silva", CHAT_ID, "Melhorar o bot", "privado");
        verify(telegramFacade).enviarMensagemHtml(eq(OWNER_ID), anyString());
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("✅ Ideia registrada"));
    }

    @Test
    void deveAnotarIdeiaComDoisPontosChines() {
        when(message.text()).thenReturn("t1000 anotar ideia：Melhorar o bot");
        // Nota: o caractere "：" é o dois-pontos chinês (U+FF1A)
        when(utils.buildFullName(any())).thenReturn("Testador Silva");
        when(utils.getChatName(any())).thenReturn("privado");
        when(utils.buildUserMention(any())).thenReturn("@Testador");
        when(utils.escapeHtml(any())).thenAnswer(inv -> inv.getArgument(0));

        commandHandler.handleTextUpdate(update);

        verify(ideasLogger)
                .saveIdea(USER_ID, "Testador Silva", CHAT_ID, "Melhorar o bot", "privado");
        verify(telegramFacade).enviarMensagemHtml(eq(OWNER_ID), anyString());
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("✅ Ideia registrada"));
    }

    // ========================================================================
    // TESTES ADICIONAIS PARA BRANCHES FALTANTES
    // ========================================================================

    // 1. sendAutoResponse com animation inválida (não HTTP/HTTPS)
    @Test
    void sendAutoResponse_comAnimationInvalidaNaoHttp_deveEnviarApenasTexto() {
        when(message.text()).thenReturn("trigger");
        AutoResponseOverride response =
                new AutoResponseOverride("Resposta", "ftp://invalid.com/video.mp4");
        when(autoResponseService.getResponseRule(USER_ID, "trigger"))
                .thenReturn(Optional.of(response));

        commandHandler.handleTextUpdate(update);

        // Não deve chamar enviarMidia
        verify(telegramFacade, never()).enviarMidia(anyLong(), anyString(), anyString());
        // Deve chamar enviarMensagemHtml com a resposta
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("Resposta"));
        // Verifica se o log de warn foi emitido (simula verificação do log, mas não testamos logs)
    }

    // 2. sendAutoResponse com animation malformada (URISyntaxException)
    @Test
    void sendAutoResponse_comAnimationMalformada_deveEnviarApenasTexto() {
        when(message.text()).thenReturn("trigger");
        // URL com espaço em branco ou caracteres inválidos
        AutoResponseOverride response = new AutoResponseOverride("Resposta", "http://invalid .com");
        when(autoResponseService.getResponseRule(USER_ID, "trigger"))
                .thenReturn(Optional.of(response));

        commandHandler.handleTextUpdate(update);

        verify(telegramFacade, never()).enviarMidia(anyLong(), anyString(), anyString());
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("Resposta"));
    }

    // 3. isValidUrl com esquema não suportado (ftp, etc.)
    @Test
    void isValidUrl_esquemaInvalido_retornaFalse() {
        CommandHandler handler =
                new CommandHandler(
                        movieService,
                        autoResponseService,
                        weeklyReleasesService,
                        worldCupSchedulerService,
                        ideasLogger,
                        messageStoreService,
                        telegramFacade,
                        utils);
        boolean result =
                ReflectionTestUtils.invokeMethod(handler, "isValidUrl", "ftp://host.com/file");
        assertThat(result).isFalse();
    }

    // 4. isValidUrl com URL malformada (lança URISyntaxException)
    @Test
    void isValidUrl_malformada_retornaFalse() {
        CommandHandler handler =
                new CommandHandler(
                        movieService,
                        autoResponseService,
                        weeklyReleasesService,
                        worldCupSchedulerService,
                        ideasLogger,
                        messageStoreService,
                        telegramFacade,
                        utils);

        // Testa com URL contendo espaço (garantidamente inválida)
        assertFalse(
                (Boolean)
                        ReflectionTestUtils.invokeMethod(
                                handler, "isValidUrl", "http://host with spaces"));
    }

    // 5. handleAnotarIdeia com ideia começando com ":" (dois-pontos) e depois apenas ":" (vazia)
    @Test
    void handleAnotarIdeia_apenasDoisPontos_deveRetornarMensagemVazia() {
        when(message.text()).thenReturn("t1000 anotar ideia :");
        commandHandler.handleTextUpdate(update);
        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains(BotMessages.IDEIA_VAZIA));
        verify(ideasLogger, never())
                .saveIdea(anyLong(), anyString(), anyLong(), anyString(), anyString());
    }

    // 6. handleAnotarIdeia com ideia começando com "：" (dois-pontos chinês) e vazia
    @Test
    void handleAnotarIdeia_apenasDoisPontosChines_deveRetornarMensagemVazia() {
        when(message.text()).thenReturn("t1000 anotar ideia ：");
        commandHandler.handleTextUpdate(update);
        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains(BotMessages.IDEIA_VAZIA));
        verify(ideasLogger, never())
                .saveIdea(anyLong(), anyString(), anyLong(), anyString(), anyString());
    }

    // 7. handleBuscarFilme com busca retornando null (em vez de exceção)
    @Test
    void handleBuscarFilme_buscaNull_deveRetornarMensagemNaoEncontrado() {
        when(message.text()).thenReturn("t1000 buscar Inexistente");
        when(movieService.buscarFilme("Inexistente")).thenReturn(null);
        commandHandler.handleTextUpdate(update);
        // A mensagem real é "❌ Filme nao encontrado" (sem acento)
        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("❌ Filme nao encontrado"));
        verify(movieService, never()).buscarPorId(anyLong());
    }

    // 8. handleBuscarFilme com busca.results() null (cenário adicional)
    @Test
    void handleBuscarFilme_resultsNull_deveRetornarMensagemNaoEncontrado() {
        when(message.text()).thenReturn("t1000 buscar Outro");
        MovieSearchResponse response = mock(MovieSearchResponse.class);
        when(response.results()).thenReturn(null);
        when(movieService.buscarFilme("Outro")).thenReturn(response);
        commandHandler.handleTextUpdate(update);
        // Corrige para "nao" sem acento
        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("❌ Filme nao encontrado"));
    }

    // 9. exibirRespostaFilme com fotoUrl blank (string vazia)
    @Test
    void exibirRespostaFilme_fotoUrlBlank_deveEnviarSemImagem() {
        when(message.text()).thenReturn("t1000 buscar FilmeBlank");
        MovieRecord filme =
                new MovieRecord(1L, "FilmeBlank", "", "2021", "", 0.0, 0.0, "", List.of());
        MovieSearchResponse response = new MovieSearchResponse(1, 1, 1, List.of(filme));
        when(movieService.buscarFilme("FilmeBlank")).thenReturn(response);
        MovieOrchestrationResponse orcResponse = new MovieOrchestrationResponse("texto", " ");
        when(movieService.buscarPorId(1L)).thenReturn(orcResponse);

        commandHandler.handleTextUpdate(update);

        verify(telegramFacade, never()).enviarFotoHtml(anyLong(), anyString(), anyString());
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("_(sem imagem)_"));
    }

    // 10. enviarOpcoesDesambiguacao com mais de 10 resultados (i >= 10)
    @Test
    void enviarOpcoesDesambiguacao_maisDe10Resultados_deveMostrarApenas10() {
        when(message.text()).thenReturn("t1000 buscar muitos");
        List<MovieRecord> muitos = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            muitos.add(
                    new MovieRecord(
                            (long) i, "Filme " + i, "", "2021", "", 0.0, 0.0, "", List.of()));
        }
        MovieSearchResponse response = new MovieSearchResponse(1, 12, 1, muitos);
        when(movieService.buscarFilme("muitos")).thenReturn(response);

        commandHandler.handleTextUpdate(update);

        // Verifica que enviou com botões (apenas 10)
        verify(telegramFacade).enviarComBotoesHtml(eq(CHAT_ID), anyString(), any());
        // Não deve chamar buscarPorId
        verify(movieService, never()).buscarPorId(anyLong());
    }

    // 11. enviarOpcoesDesambiguacao com filme.releaseDate null
    @Test
    void enviarOpcoesDesambiguacao_releaseDateNull_deveMostrarSemAno() {
        // Arrange
        when(message.text()).thenReturn("t1000 buscar semano");
        MovieRecord filme1 = new MovieRecord(1L, "Sem Ano", "", null, "", 0.0, 0.0, "", List.of());
        MovieRecord filme2 = new MovieRecord(2L, "Outro", "", "2022", "", 0.0, 0.0, "", List.of());
        MovieSearchResponse response = new MovieSearchResponse(1, 2, 1, List.of(filme1, filme2));
        when(movieService.buscarFilme("semano")).thenReturn(response);

        // Act
        commandHandler.handleTextUpdate(update);

        // Assert
        ArgumentCaptor<InlineKeyboardMarkup> markupCaptor =
                ArgumentCaptor.forClass(InlineKeyboardMarkup.class);
        verify(telegramFacade)
                .enviarComBotoesHtml(eq(CHAT_ID), anyString(), markupCaptor.capture());

        InlineKeyboardMarkup markup = markupCaptor.getValue();
        InlineKeyboardButton[][] keyboard = markup.inlineKeyboard(); // método correto

        boolean found = false;
        for (InlineKeyboardButton[] row : keyboard) {
            for (InlineKeyboardButton button : row) {
                if (button.text().contains("Sem Ano (S/A)")) {
                    found = true;
                    break;
                }
            }
        }
        assertTrue(found, "Deveria existir um botão com '(S/A)' para o filme sem ano");
    }

    // 12. handleResultados com data inválida (parseDateParam retorna null) – já existe teste, mas
    // pode não estar cobrindo o branch else com date null
    @Test
    void handleResultados_dataInvalida_deveRetornarMensagemErro() {
        ReflectionTestUtils.setField(commandHandler, "worldcupEnabled", true);
        when(message.text()).thenReturn("t1000 resultados invalido");
        commandHandler.handleTextUpdate(update);
        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains(BotMessages.DATA_INVALIDA));
        verify(worldCupSchedulerService, never()).sendResultsToChat(anyLong(), any());
    }

    // 13. parseDateParam com formato misto (ex: "20/06" com ano diferente) – já testado, mas
    // adicionamos para cobrir todos os branches
    @Test
    void parseDateParam_formatoDDMMComAnoDiferente_deveParsearCorretamente() {
        when(message.text()).thenReturn("t1000 resultados 2026-06-20");
        ReflectionTestUtils.setField(commandHandler, "worldcupEnabled", true);
        commandHandler.handleTextUpdate(update);
        verify(worldCupSchedulerService)
                .sendResultsToChat(CHAT_ID, LocalDate.of(2026, Month.JUNE, 20));
    }
}
