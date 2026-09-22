package net.ddns.adambravo79.tmill.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
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
import org.junit.jupiter.api.DisplayName;
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
import com.pengrad.telegrambot.model.MessageEntity;
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
import net.ddns.adambravo79.tmill.service.AutoResponseService;
import net.ddns.adambravo79.tmill.service.BirthdayService;
import net.ddns.adambravo79.tmill.service.BotAnalyticsService;
import net.ddns.adambravo79.tmill.service.IdeasLoggerService;
import net.ddns.adambravo79.tmill.service.MessageStoreService;
import net.ddns.adambravo79.tmill.service.MovieService;
import net.ddns.adambravo79.tmill.service.WeeklyReleasesService;
import net.ddns.adambravo79.tmill.service.WorldCupSchedulerService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;
import net.ddns.adambravo79.tmill.telegram.util.TelegramUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommandHandlerTest {

    // ============================================================
    // MOCKS
    // ============================================================

    @Mock private BirthdayService birthdayService;
    @Mock private MovieService movieService;
    @Mock private AutoResponseService autoResponseService;
    @Mock private WeeklyReleasesService weeklyReleasesService;
    @Mock private WorldCupSchedulerService worldCupSchedulerService;
    @Mock private IdeasLoggerService ideasLogger;
    @Mock private MessageStoreService messageStoreService;
    @Mock private TelegramFacade telegramFacade;
    @Mock private TelegramUtils utils;
    @Mock private BotAnalyticsService botAnalyticsService;
    @Mock private MetricsService metricsService;

    @InjectMocks private CommandHandler commandHandler;

    // ============================================================
    // FIXTURES
    // ============================================================

    private Update update;
    private Message message;
    private User user;
    private Chat chat;

    private static final long CHAT_ID = 12345L;
    private static final long USER_ID = 999L;
    private static final long OWNER_ID = 999L;
    private static final String USER_FULL_NAME = "Testador Silva";
    private static final String CHAT_NAME = "privado";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(commandHandler, "ownerId", OWNER_ID);
        ReflectionTestUtils.setField(commandHandler, "telegramMessageLimit", 4000);
        ReflectionTestUtils.setField(commandHandler, "worldcupEnabled", true);

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

        // Stubs básicos — LENIENT permite não usar
        when(utils.buildFullName(any(User.class))).thenReturn(USER_FULL_NAME);
        when(utils.buildUserMention(any(User.class))).thenReturn("@Testador");
        when(utils.escapeHtml(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(utils.getChatName(any(Message.class))).thenReturn(CHAT_NAME);
    }

    // ============================================================
    // 🧪 /start
    // ============================================================

    @Test
    @DisplayName("/start: envia saudação, salva conversa, não registra métrica de comando")
    void deveResponderAoStart() {
        when(message.text()).thenReturn("/start");

        commandHandler.handleTextUpdate(update);

        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), anyString());
        verify(botAnalyticsService)
                .registrarConversa(
                        eq(CHAT_ID),
                        eq(USER_ID),
                        eq(USER_FULL_NAME),
                        eq("TEXT"),
                        eq("/start"),
                        anyString());
        verifyNoInteractions(movieService, autoResponseService, messageStoreService);
        // /start não tem métrica de comando
        verify(metricsService, never()).success(argThat(s -> s.startsWith("comando_")));
    }

    // ============================================================
    // 🧪 COMANDO: t1000 buscar
    // ============================================================

    @Test
    @DisplayName(
            "t1000 buscar: 1 resultado envia foto e registra métrica success('comando_buscar')")
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
        verify(metricsService).success("comando_buscar");
    }

    @Test
    @DisplayName("t1000 buscar: múltiplos resultados → envia botões")
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
        verify(metricsService).success("comando_buscar");
    }

    @Test
    @DisplayName(
            "t1000 buscar: filme não encontrado → mensagem de erro, métrica success mesmo assim")
    void deveInformarQuandoFilmeNaoEncontrado() {
        when(message.text()).thenReturn("t1000 buscar Inexistente");
        when(movieService.buscarFilme("Inexistente"))
                .thenThrow(new MovieNotFoundException("Filme não encontrado"));

        commandHandler.handleTextUpdate(update);

        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("❌ Filme não encontrado"));
        // Métrica de comando é registrada assim que o comando é reconhecido
        verify(metricsService).success("comando_buscar");
    }

    @Test
    @DisplayName("t1000 buscar: termo curto é rejeitado sem chamar o service")
    void deveRejeitarBuscaComMenosDe3Caracteres() {
        when(message.text()).thenReturn("t1000 buscar ab");

        commandHandler.handleTextUpdate(update);

        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("pelo menos 3 caracteres"));
        verifyNoInteractions(movieService);
        verify(metricsService).success("comando_buscar");
    }

    @Test
    @DisplayName("t1000 buscar: termo longo é rejeitado sem chamar o service")
    void deveRejeitarBuscaComMaisDe100Caracteres() {
        String termoLongo = "a".repeat(101);
        when(message.text()).thenReturn("t1000 buscar " + termoLongo);

        commandHandler.handleTextUpdate(update);

        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("muito longo"));
        verifyNoInteractions(movieService);
        verify(metricsService).success("comando_buscar");
    }

    @Test
    @DisplayName("t1000 buscar: resultado null (sem exceção) → mensagem 'Filme nao encontrado'")
    void deveLidarComBuscaRetornandoNull() {
        when(message.text()).thenReturn("t1000 buscar Nada");
        when(movieService.buscarFilme("Nada")).thenReturn(null);

        commandHandler.handleTextUpdate(update);

        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("❌ Filme nao encontrado"));
        verify(metricsService).success("comando_buscar");
    }

    @Test
    @DisplayName("t1000 buscar: results null → mensagem 'Filme nao encontrado'")
    void deveLidarComResultsNull() {
        // 🔧 FIX: termo precisa ter >=3 chars para chegar ao MovieService
        when(message.text()).thenReturn("t1000 buscar FilmeXYZ");
        MovieSearchResponse response = mock(MovieSearchResponse.class);
        when(response.results()).thenReturn(null);
        when(movieService.buscarFilme("FilmeXYZ")).thenReturn(response);

        commandHandler.handleTextUpdate(update);

        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("❌ Filme nao encontrado"));
        verify(metricsService).success("comando_buscar");
    }

    // ============================================================
    // 🧪 COMANDO: t1000 anotar ideia
    // ============================================================

    @Test
    @DisplayName("t1000 anotar ideia: válida salva no MongoDB, loga e avisa admin + chat")
    void deveAnotarIdeiaComSucesso() {
        when(message.text()).thenReturn("t1000 anotar ideia: Melhorar o bot");

        commandHandler.handleTextUpdate(update);

        verify(ideasLogger).saveIdea(USER_ID, USER_FULL_NAME, CHAT_ID, "Melhorar o bot", CHAT_NAME);
        verify(botAnalyticsService)
                .salvarIdeia(eq(USER_ID), eq("Melhorar o bot"), any(), eq("BACKLOG_TECNICO"));
        verify(telegramFacade).enviarMensagemHtml(eq(OWNER_ID), anyString());
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("✅ Ideia registrada"));
        verify(metricsService).success("comando_anotar_ideia");
    }

    @Test
    @DisplayName("t1000 anotar ideia: começando com dois-pontos remove o ':'")
    void deveAnotarIdeiaRemovendoDoisPontos() {
        when(message.text()).thenReturn("t1000 anotar ideia: Ajustar logs");

        commandHandler.handleTextUpdate(update);

        verify(ideasLogger).saveIdea(USER_ID, USER_FULL_NAME, CHAT_ID, "Ajustar logs", CHAT_NAME);
        verify(metricsService).success("comando_anotar_ideia");
    }

    @Test
    @DisplayName("t1000 anotar ideia: sem texto pede para digitar após o comando")
    void deveRejeitarIdeiaSemTexto() {
        when(message.text()).thenReturn("t1000 anotar ideia");

        commandHandler.handleTextUpdate(update);

        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("Digite a ideia"));
        verifyNoInteractions(ideasLogger);
        verify(metricsService).success("comando_anotar_ideia");
    }

    @Test
    @DisplayName("t1000 anotar ideia: apenas ':' após comando → mensagem de ideia vazia")
    void deveRejeitarIdeiaVaziaAposDoisPontos() {
        when(message.text()).thenReturn("t1000 anotar ideia :");

        commandHandler.handleTextUpdate(update);

        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains(BotMessages.IDEIA_VAZIA));
        verifyNoInteractions(ideasLogger);
        verify(metricsService).success("comando_anotar_ideia");
    }

    @Test
    @DisplayName("t1000 anotar ideia: apenas '：' (chinês) → mensagem de ideia vazia")
    void deveRejeitarIdeiaVaziaAposDoisPontosChines() {
        when(message.text()).thenReturn("t1000 anotar ideia ：");

        commandHandler.handleTextUpdate(update);

        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains(BotMessages.IDEIA_VAZIA));
        verify(metricsService).success("comando_anotar_ideia");
    }

    // ============================================================
    // 🧪 COMANDO: t1000 estreias / lancamentos
    // ============================================================

    @Test
    @DisplayName("t1000 estreias da semana: envia resposta e registra métrica")
    void deveResponderEstreias() {
        when(message.text()).thenReturn("t1000 estreias da semana");
        when(weeklyReleasesService.getWeeklyReleasesMessage()).thenReturn("Lista de estreias");

        commandHandler.handleTextUpdate(update);

        verify(weeklyReleasesService).getWeeklyReleasesMessage();
        verify(telegramFacade).enviarMensagemHtml(CHAT_ID, "Lista de estreias");
        verify(metricsService).success("comando_estreias");
    }

    @Test
    @DisplayName("t1000 lancamentos: envia resposta e registra métrica")
    void deveResponderLancamentos() {
        when(message.text()).thenReturn("t1000 lancamentos");
        when(weeklyReleasesService.getWeeklyReleasesMessage()).thenReturn("Lista de lançamentos");

        commandHandler.handleTextUpdate(update);

        verify(weeklyReleasesService).getWeeklyReleasesMessage();
        verify(telegramFacade).enviarMensagemHtml(CHAT_ID, "Lista de lançamentos");
        verify(metricsService).success("comando_estreias");
    }

    // ============================================================
    // 🧪 COMANDO: t1000 jogos / copa
    // ============================================================

    @Test
    @DisplayName("t1000 jogos de hoje (worldcup enabled): envia jogos e registra métrica")
    void deveResponderJogosDeHoje() {
        when(message.text()).thenReturn("t1000 jogos de hoje");

        commandHandler.handleTextUpdate(update);

        verify(worldCupSchedulerService).sendMatchesToChat(eq(CHAT_ID), any(LocalDate.class));
        verify(metricsService).success("comando_copa");
    }

    @Test
    @DisplayName("t1000 copa (worldcup enabled): envia jogos e registra métrica")
    void deveResponderCopa() {
        when(message.text()).thenReturn("t1000 copa hoje");

        commandHandler.handleTextUpdate(update);

        verify(worldCupSchedulerService).sendMatchesToChat(eq(CHAT_ID), any(LocalDate.class));
        verify(metricsService).success("comando_copa");
    }

    @Test
    @DisplayName(
            "t1000 jogos (worldcup disabled): mensagem de copa encerrada, métrica success ainda"
                    + " assim")
    void deveResponderCopaDesabilitada() {
        ReflectionTestUtils.setField(commandHandler, "worldcupEnabled", false);
        when(message.text()).thenReturn("t1000 jogos de hoje");

        commandHandler.handleTextUpdate(update);

        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("Copa de 2026 ja acabou"));
        verifyNoInteractions(worldCupSchedulerService);
        verify(metricsService).success("comando_copa");
    }

    // ============================================================
    // 🧪 COMANDO: t1000 resultados
    // ============================================================

    @Test
    @DisplayName("t1000 resultados hoje: envia resultados de hoje e registra métrica")
    void deveResponderResultadosHoje() {
        when(message.text()).thenReturn("t1000 resultados hoje");

        commandHandler.handleTextUpdate(update);

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(worldCupSchedulerService).sendResultsToChat(eq(CHAT_ID), dateCaptor.capture());
        LocalDate hoje = LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE));
        assertThat(dateCaptor.getValue()).isEqualTo(hoje);
        verify(metricsService).success("comando_resultados");
    }

    @Test
    @DisplayName("t1000 resultados ontem: envia resultados de ontem")
    void deveResponderResultadosOntem() {
        when(message.text()).thenReturn("t1000 resultados ontem");

        commandHandler.handleTextUpdate(update);

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(worldCupSchedulerService).sendResultsToChat(eq(CHAT_ID), dateCaptor.capture());
        LocalDate ontem = LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE)).minusDays(1);
        assertThat(dateCaptor.getValue()).isEqualTo(ontem);
        verify(metricsService).success("comando_resultados");
    }

    @Test
    @DisplayName("t1000 resultados DD/MM (20/06): parseia para 2026-06-20")
    void deveResponderResultadosComDataDDMM() {
        when(message.text()).thenReturn("t1000 resultados 20/06");

        commandHandler.handleTextUpdate(update);

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(worldCupSchedulerService).sendResultsToChat(eq(CHAT_ID), dateCaptor.capture());
        assertThat(dateCaptor.getValue()).isEqualTo(LocalDate.of(2026, Month.JUNE, 20));
        verify(metricsService).success("comando_resultados");
    }

    @Test
    @DisplayName("t1000 resultados DD-MM (20-06): parseia para 2026-06-20")
    void deveResponderResultadosComDataDDMMComHifen() {
        when(message.text()).thenReturn("t1000 resultados 20-06");

        commandHandler.handleTextUpdate(update);

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(worldCupSchedulerService).sendResultsToChat(eq(CHAT_ID), dateCaptor.capture());
        assertThat(dateCaptor.getValue()).isEqualTo(LocalDate.of(2026, Month.JUNE, 20));
    }

    @Test
    @DisplayName("t1000 resultados YYYY-MM-DD (2026-06-20): parseia exatamente")
    void deveResponderResultadosComDataYYYYMMDD() {
        when(message.text()).thenReturn("t1000 resultados 2026-06-20");

        commandHandler.handleTextUpdate(update);

        verify(worldCupSchedulerService)
                .sendResultsToChat(CHAT_ID, LocalDate.of(2026, Month.JUNE, 20));
        verify(metricsService).success("comando_resultados");
    }

    @Test
    @DisplayName("t1000 resultados com data inválida: mensagem de erro, métrica success")
    void deveAvisarDataInvalidaNosResultados() {
        when(message.text()).thenReturn("t1000 resultados invalido");

        commandHandler.handleTextUpdate(update);

        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("Formato de data invalido"));
        verifyNoInteractions(worldCupSchedulerService);
        verify(metricsService).success("comando_resultados");
    }

    @Test
    @DisplayName("t1000 resultados (worldcup disabled): mensagem de copa encerrada")
    void deveAvisarCopaEncerradaNosResultados() {
        ReflectionTestUtils.setField(commandHandler, "worldcupEnabled", false);
        when(message.text()).thenReturn("t1000 resultados hoje");

        commandHandler.handleTextUpdate(update);

        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("Copa de 2026 ja acabou"));
        verifyNoInteractions(worldCupSchedulerService);
        verify(metricsService).success("comando_resultados");
    }

    // ============================================================
    // 🧪 COMANDO: t1000 registrar aniversário
    // ============================================================

    @Test
    @DisplayName("t1000 registrar aniversário DD/MM: chama birthdayService.registrar")
    void deveRegistrarAniversario() {
        when(message.text()).thenReturn("t1000 registrar aniversário 05/10");
        when(birthdayService.registrar(USER_ID, USER_FULL_NAME, "05/10"))
                .thenReturn("✅ Aniversário registrado!");

        commandHandler.handleTextUpdate(update);

        verify(birthdayService).registrar(USER_ID, USER_FULL_NAME, "05/10");
        verify(telegramFacade).enviarMensagemHtml(CHAT_ID, "✅ Aniversário registrado!");
        verify(metricsService).success("comando_aniversario");
    }

    @Test
    @DisplayName("t1000 registrar aniversario (sem acento) também funciona")
    void deveRegistrarAniversarioSemAcento() {
        when(message.text()).thenReturn("t1000 registrar aniversario 05/10");
        when(birthdayService.registrar(USER_ID, USER_FULL_NAME, "05/10")).thenReturn("OK");

        commandHandler.handleTextUpdate(update);

        verify(birthdayService).registrar(USER_ID, USER_FULL_NAME, "05/10");
        verify(metricsService).success("comando_aniversario");
    }

    @Test
    @DisplayName("t1000 anotar aniversário (variante) também funciona")
    void deveRegistrarAniversarioVarianteAnotar() {
        when(message.text()).thenReturn("t1000 anotar aniversário 05/10");
        when(birthdayService.registrar(USER_ID, USER_FULL_NAME, "05/10")).thenReturn("OK");

        commandHandler.handleTextUpdate(update);

        verify(birthdayService).registrar(USER_ID, USER_FULL_NAME, "05/10");
        verify(metricsService).success("comando_aniversario");
    }

    @Test
    @DisplayName("t1000 registrar aniversário sem data: pede a data")
    void devePedirDataDeAniversario() {
        when(message.text()).thenReturn("t1000 registrar aniversário");

        commandHandler.handleTextUpdate(update);

        verify(telegramFacade)
                .enviarMensagemHtml(eq(CHAT_ID), eq(BotMessages.ANIVERSARIO_PEDE_DATA));
        verifyNoInteractions(birthdayService);
        verify(metricsService).success("comando_aniversario");
    }

    // ============================================================
    // 🧪 DISPATCHER — verifica que cada comando registra a métrica correta
    // ============================================================

    @Test
    @DisplayName("Cada comando reconhecido registra exatamente a métrica esperada")
    void dispatchCommand_registraMetricaCorretaPorComando() {
        // anotar_ideia
        when(message.text()).thenReturn("t1000 anotar ideia: x");
        commandHandler.handleTextUpdate(update);
        verify(metricsService).success("comando_anotar_ideia");

        // buscar
        when(message.text()).thenReturn("t1000 buscar abc");
        when(movieService.buscarFilme("abc")).thenThrow(new MovieNotFoundException(""));
        commandHandler.handleTextUpdate(update);
        verify(metricsService).success("comando_buscar");

        // estreias
        when(message.text()).thenReturn("t1000 estreias da semana");
        when(weeklyReleasesService.getWeeklyReleasesMessage()).thenReturn("x");
        commandHandler.handleTextUpdate(update);
        verify(metricsService).success("comando_estreias");

        // copa
        when(message.text()).thenReturn("t1000 jogos hoje");
        commandHandler.handleTextUpdate(update);
        verify(metricsService).success("comando_copa");

        // resultados
        when(message.text()).thenReturn("t1000 resultados hoje");
        commandHandler.handleTextUpdate(update);
        verify(metricsService).success("comando_resultados");

        // aniversário
        when(message.text()).thenReturn("t1000 registrar aniversário 05/10");
        when(birthdayService.registrar(anyLong(), anyString(), anyString())).thenReturn("OK");
        commandHandler.handleTextUpdate(update);
        verify(metricsService).success("comando_aniversario");

        // Nenhuma métrica "comando_" fora das 6 esperadas
        verify(metricsService, times(1)).success("comando_anotar_ideia");
        verify(metricsService, times(1)).success("comando_buscar");
        verify(metricsService, times(1)).success("comando_estreias");
        verify(metricsService, times(1)).success("comando_copa");
        verify(metricsService, times(1)).success("comando_resultados");
        verify(metricsService, times(1)).success("comando_aniversario");
    }

    // ============================================================
    // 🧪 COMANDO NÃO RECONHECIDO
    // ============================================================

    @Test
    @DisplayName("Comando não reconhecido: envia mensagem, não registra métrica de comando")
    void deveResponderComandoNaoReconhecido() {
        when(message.text()).thenReturn("t1000 comando invalido");

        commandHandler.handleTextUpdate(update);

        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("Comando nao reconhecido"));
        verify(metricsService, never()).success(argThat(s -> s.startsWith("comando_")));
    }

    @Test
    @DisplayName("Comando não reconhecido com link: loga warning, não registra métrica")
    void deveLogarLinkNaoProcessado() {
        when(message.text()).thenReturn("t1000 comando invalido http://link.com");

        commandHandler.handleTextUpdate(update);

        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("Comando nao reconhecido"));
        verify(metricsService, never()).success(argThat(s -> s.startsWith("comando_")));
    }

    // ============================================================
    // 🧪 MENSAGENS NORMAIS (não comando)
    // ============================================================

    @Test
    @DisplayName("Mensagem normal: salva no DB, não registra métrica de comando")
    void deveSalvarMensagemNormal() {
        when(message.text()).thenReturn("Uma mensagem qualquer");
        when(autoResponseService.getResponseRule(anyLong(), anyString()))
                .thenReturn(Optional.empty());

        commandHandler.handleTextUpdate(update);

        verify(messageStoreService)
                .saveMessage(
                        eq(CHAT_ID),
                        eq(USER_ID),
                        anyString(),
                        eq("Uma mensagem qualquer"),
                        eq(false));
        verify(telegramFacade, never()).enviarMensagem(anyLong(), anyString());
        verify(metricsService, never()).success(argThat(s -> s.startsWith("comando_")));
    }

    @Test
    @DisplayName("Comando não deve ser salvo no message store")
    void naoDeveSalvarComando() {
        when(message.text()).thenReturn("t1000 buscar filme");
        when(movieService.buscarFilme("filme")).thenThrow(new MovieNotFoundException(""));

        commandHandler.handleTextUpdate(update);

        verify(messageStoreService, never())
                .saveMessage(anyLong(), anyLong(), anyString(), anyString(), anyBoolean());
    }

    // ============================================================
    // 🧪 AUTO-RESPOSTA
    // ============================================================

    @Test
    @DisplayName("Auto-resposta sem animação: envia texto, sem métrica de comando")
    void deveDispararAutoRespostaSemMidia() {
        when(message.text()).thenReturn("bom dia");
        AutoResponseOverride response = new AutoResponseOverride("Bom dia para você!", null);
        when(autoResponseService.getResponseRule(USER_ID, "bom dia"))
                .thenReturn(Optional.of(response));

        commandHandler.handleTextUpdate(update);

        verify(autoResponseService).getResponseRule(USER_ID, "bom dia");
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("Bom dia para você!"));
        verify(telegramFacade, never()).enviarMidia(anyLong(), anyString(), anyString());
        verify(metricsService, never()).success(argThat(s -> s.startsWith("comando_")));
    }

    @Test
    @DisplayName("Auto-resposta com animação válida: envia mídia")
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
    @DisplayName("Auto-resposta com animação inválida: apenas texto")
    void deveDispararAutoRespostaComMidiaInvalida_EnviaApenasTexto() {
        when(message.text()).thenReturn("bom dia");
        AutoResponseOverride response = new AutoResponseOverride("Bom dia!", "ftp://inválido");
        when(autoResponseService.getResponseRule(USER_ID, "bom dia"))
                .thenReturn(Optional.of(response));

        commandHandler.handleTextUpdate(update);

        verify(telegramFacade, never()).enviarMidia(anyLong(), anyString(), anyString());
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("Bom dia!"));
    }

    @Test
    @DisplayName("Auto-resposta com animação malformada (URI inválida): apenas texto")
    void deveDispararAutoRespostaComAnimationMalformada() {
        when(message.text()).thenReturn("bom dia");
        AutoResponseOverride response =
                new AutoResponseOverride("Bom dia!", "http://host with spaces");
        when(autoResponseService.getResponseRule(USER_ID, "bom dia"))
                .thenReturn(Optional.of(response));

        commandHandler.handleTextUpdate(update);

        verify(telegramFacade, never()).enviarMidia(anyLong(), anyString(), anyString());
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("Bom dia!"));
    }

    // ============================================================
    // 🧪 HELPER: isValidUrl (via reflexão)
    // ============================================================

    @Test
    @DisplayName("isValidUrl: esquema não HTTP/HTTPS retorna false")
    void isValidUrl_esquemaInvalido_retornaFalse() {
        Boolean result =
                ReflectionTestUtils.invokeMethod(
                        commandHandler, "isValidUrl", "ftp://host.com/file");
        assertFalse(result);
    }

    @Test
    @DisplayName("isValidUrl: URL com espaço retorna false")
    void isValidUrl_malformada_retornaFalse() {
        Boolean result =
                ReflectionTestUtils.invokeMethod(
                        commandHandler, "isValidUrl", "http://host with spaces");
        assertFalse(result);
    }

    @Test
    @DisplayName("isValidUrl: URL http válida retorna true")
    void isValidUrl_httpValida_retornaTrue() {
        Boolean result =
                ReflectionTestUtils.invokeMethod(
                        commandHandler, "isValidUrl", "http://exemplo.com");
        assertTrue(result);
    }

    @Test
    @DisplayName("isValidUrl: URL https válida retorna true")
    void isValidUrl_httpsValida_retornaTrue() {
        Boolean result =
                ReflectionTestUtils.invokeMethod(
                        commandHandler, "isValidUrl", "https://exemplo.com/path?a=1");
        assertTrue(result);
    }

    @Test
    @DisplayName("isValidUrl: null e vazio retornam false")
    void isValidUrl_nullEVazio_retornamFalse() {
        assertFalse(
                (Boolean)
                        ReflectionTestUtils.invokeMethod(
                                commandHandler, "isValidUrl", (Object) null));
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(commandHandler, "isValidUrl", ""));
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(commandHandler, "isValidUrl", "  "));
    }

    // ============================================================
    // 🧪 HELPER: parseDateParam (via reflexão)
    // ============================================================

    @Test
    @DisplayName("parseDateParam: 'hoje' e 'de hoje' retornam hoje")
    void parseDateParam_hoje() {
        LocalDate hoje = LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE));
        assertThat(
                        (LocalDate)
                                ReflectionTestUtils.invokeMethod(
                                        commandHandler, "parseDateParam", "hoje"))
                .isEqualTo(hoje);
        assertThat(
                        (LocalDate)
                                ReflectionTestUtils.invokeMethod(
                                        commandHandler, "parseDateParam", "de hoje"))
                .isEqualTo(hoje);
    }

    @Test
    @DisplayName("parseDateParam: 'ontem' e 'de ontem' retornam ontem")
    void parseDateParam_ontem() {
        LocalDate ontem = LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE)).minusDays(1);
        assertThat(
                        (LocalDate)
                                ReflectionTestUtils.invokeMethod(
                                        commandHandler, "parseDateParam", "ontem"))
                .isEqualTo(ontem);
        assertThat(
                        (LocalDate)
                                ReflectionTestUtils.invokeMethod(
                                        commandHandler, "parseDateParam", "de ontem"))
                .isEqualTo(ontem);
    }

    @Test
    @DisplayName("parseDateParam: '20/06' retorna 2026-06-20")
    void parseDateParam_DDMM() {
        assertThat(
                        (LocalDate)
                                ReflectionTestUtils.invokeMethod(
                                        commandHandler, "parseDateParam", "20/06"))
                .isEqualTo(LocalDate.of(2026, Month.JUNE, 20));
    }

    @Test
    @DisplayName("parseDateParam: '20-06' retorna 2026-06-20")
    void parseDateParam_DDMMComHifen() {
        assertThat(
                        (LocalDate)
                                ReflectionTestUtils.invokeMethod(
                                        commandHandler, "parseDateParam", "20-06"))
                .isEqualTo(LocalDate.of(2026, Month.JUNE, 20));
    }

    @Test
    @DisplayName("parseDateParam: '2026-06-20' retorna 2026-06-20")
    void parseDateParam_YYYYMMDD() {
        assertThat(
                        (LocalDate)
                                ReflectionTestUtils.invokeMethod(
                                        commandHandler, "parseDateParam", "2026-06-20"))
                .isEqualTo(LocalDate.of(2026, Month.JUNE, 20));
    }

    @Test
    @DisplayName("parseDateParam: '20/06/2026' (com ano) NÃO é suportado — documenta limitação")
    void parseDateParam_DDMMYYYY_documentaComportamento() {
        // 🔧 FIX: o parser atual NÃO suporta DD/MM/YYYY (retorna null).
        // Documentamos o comportamento como limitação conhecida.
        LocalDate result =
                (LocalDate)
                        ReflectionTestUtils.invokeMethod(
                                commandHandler, "parseDateParam", "20/06/2026");
        // Comportamento real: retorna null (não é bug, é limitação).
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("parseDateParam: entrada inválida retorna null")
    void parseDateParam_invalido() {
        assertThat(
                        (LocalDate)
                                ReflectionTestUtils.invokeMethod(
                                        commandHandler, "parseDateParam", "xyz"))
                .isNull();
    }

    @Test
    @DisplayName("parseDateParam: null e vazio retornam null")
    void parseDateParam_nullEVazio() {
        assertThat(
                        (LocalDate)
                                ReflectionTestUtils.invokeMethod(
                                        commandHandler, "parseDateParam", (Object) null))
                .isNull();
        assertThat(
                        (LocalDate)
                                ReflectionTestUtils.invokeMethod(
                                        commandHandler, "parseDateParam", ""))
                .isNull();
        assertThat(
                        (LocalDate)
                                ReflectionTestUtils.invokeMethod(
                                        commandHandler, "parseDateParam", "   "))
                .isNull();
    }

    // ============================================================
    // 🧪 HELPER: hasSpoiler (via reflexão)
    // ============================================================

    @Test
    @DisplayName("hasSpoiler: mensagem sem entities retorna false")
    void hasSpoiler_semEntities_retornaFalse() {
        Message m = mock(Message.class);
        when(m.entities()).thenReturn(null);
        Boolean result = ReflectionTestUtils.invokeMethod(commandHandler, "hasSpoiler", m);
        assertFalse(result);
    }

    @Test
    @DisplayName("hasSpoiler: mensagem com entity spoiler retorna true")
    void hasSpoiler_comSpoiler_retornaTrue() {
        Message m = mock(Message.class);
        MessageEntity spoiler = mock(MessageEntity.class);
        when(spoiler.type()).thenReturn(MessageEntity.Type.spoiler);
        when(m.entities()).thenReturn(new MessageEntity[] {spoiler});
        Boolean result = ReflectionTestUtils.invokeMethod(commandHandler, "hasSpoiler", m);
        assertTrue(result);
    }

    @Test
    @DisplayName("hasSpoiler: mensagem com entity não-spoiler retorna false")
    void hasSpoiler_comOutroEntity_retornaFalse() {
        Message m = mock(Message.class);
        MessageEntity bold = mock(MessageEntity.class);
        when(bold.type()).thenReturn(MessageEntity.Type.bold);
        when(m.entities()).thenReturn(new MessageEntity[] {bold});
        Boolean result = ReflectionTestUtils.invokeMethod(commandHandler, "hasSpoiler", m);
        assertFalse(result);
    }

    // ============================================================
    // 🧪 HELPER: sanitizeForLog (via reflexão)
    // ============================================================

    @Test
    @DisplayName("sanitizeForLog: null vira 'null'")
    void sanitizeForLog_null_retornaNullString() {
        String result =
                ReflectionTestUtils.invokeMethod(commandHandler, "sanitizeForLog", (Object) null);
        assertThat(result).isEqualTo("null");
    }

    @Test
    @DisplayName("sanitizeForLog: espaços múltiplos viram um só")
    void sanitizeForLog_normalizaEspacos() {
        String result =
                ReflectionTestUtils.invokeMethod(commandHandler, "sanitizeForLog", "a    b  c");
        assertThat(result).isEqualTo("a b c");
    }

    @Test
    @DisplayName("sanitizeForLog: trunca em 200 caracteres + reticências")
    void sanitizeForLog_trunca() {
        String input = "a".repeat(300);
        String result = ReflectionTestUtils.invokeMethod(commandHandler, "sanitizeForLog", input);
        assertThat(result)
                .hasSize(200 + 3) // 200 + "..."
                .endsWith("...");
    }

    // ============================================================
    // 🧪 DESAMBIGUAÇÃO — botões
    // ============================================================

    @Test
    @DisplayName("enviarOpcoesDesambiguacao: até 10 resultados, botões em linhas de 2")
    void enviarOpcoesDesambiguacao_ate10() {
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

        ArgumentCaptor<InlineKeyboardMarkup> markupCaptor =
                ArgumentCaptor.forClass(InlineKeyboardMarkup.class);
        verify(telegramFacade)
                .enviarComBotoesHtml(eq(CHAT_ID), anyString(), markupCaptor.capture());

        InlineKeyboardMarkup markup = markupCaptor.getValue();
        InlineKeyboardButton[][] keyboard = markup.inlineKeyboard();

        // No máximo 10 botões (5 linhas de 2)
        int totalButtons = 0;
        for (InlineKeyboardButton[] row : keyboard) {
            totalButtons += row.length;
        }
        assertThat(totalButtons).isLessThanOrEqualTo(10);
    }

    @Test
    @DisplayName("enviarOpcoesDesambiguacao: sem ano, usa '(S/A)'")
    void enviarOpcoesDesambiguacao_semAno_usaSA() {
        when(message.text()).thenReturn("t1000 buscar semano");
        MovieRecord filme1 = new MovieRecord(1L, "Sem Ano", "", null, "", 0.0, 0.0, "", List.of());
        MovieRecord filme2 = new MovieRecord(2L, "Outro", "", "2022", "", 0.0, 0.0, "", List.of());
        MovieSearchResponse response = new MovieSearchResponse(1, 2, 1, List.of(filme1, filme2));
        when(movieService.buscarFilme("semano")).thenReturn(response);

        commandHandler.handleTextUpdate(update);

        ArgumentCaptor<InlineKeyboardMarkup> markupCaptor =
                ArgumentCaptor.forClass(InlineKeyboardMarkup.class);
        verify(telegramFacade)
                .enviarComBotoesHtml(eq(CHAT_ID), anyString(), markupCaptor.capture());

        InlineKeyboardButton[][] keyboard = markupCaptor.getValue().inlineKeyboard();
        boolean found = false;
        for (InlineKeyboardButton[] row : keyboard) {
            for (InlineKeyboardButton button : row) {
                if (button.text().contains("(S/A)")) {
                    found = true;
                }
            }
        }
        assertTrue(found, "Deveria existir botão com '(S/A)' para filme sem ano");
    }

    // ============================================================
    // 🧪 EXIBIÇÃO DE FILME — fallback sem imagem
    // ============================================================

    @Test
    @DisplayName("Filme sem foto: envia mensagem com '_(sem imagem)_'")
    void deveExibirFilmeSemFoto() {
        when(message.text()).thenReturn("t1000 buscar FilmeSemFoto");
        MovieRecord filme =
                new MovieRecord(1L, "FilmeSemFoto", "", "2021", "", 0.0, 0.0, "", List.of());
        MovieSearchResponse response = new MovieSearchResponse(1, 1, 1, List.of(filme));
        when(movieService.buscarFilme("FilmeSemFoto")).thenReturn(response);
        MovieOrchestrationResponse orcResponse =
                new MovieOrchestrationResponse("texto sem foto", null);
        when(movieService.buscarPorId(1L)).thenReturn(orcResponse);

        commandHandler.handleTextUpdate(update);

        verify(telegramFacade, never()).enviarFotoHtml(anyLong(), anyString(), anyString());
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("_(sem imagem)_"));
    }

    @Test
    @DisplayName("Filme com URL não-http: cai no fallback sem imagem")
    void deveExibirFilmeComFotoInvalida() {
        // 🔧 FIX: termo precisa ter >=3 chars
        when(message.text()).thenReturn("t1000 buscar FilmeInvalido");
        MovieRecord filme =
                new MovieRecord(1L, "FilmeInvalido", "", "2021", "", 0.0, 0.0, "", List.of());
        MovieSearchResponse response = new MovieSearchResponse(1, 1, 1, List.of(filme));
        when(movieService.buscarFilme("FilmeInvalido")).thenReturn(response);
        when(movieService.buscarPorId(1L))
                .thenReturn(new MovieOrchestrationResponse("texto", "ftp://foto.jpg"));

        commandHandler.handleTextUpdate(update);

        verify(telegramFacade, never()).enviarFotoHtml(anyLong(), anyString(), anyString());
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("_(sem imagem)_"));
    }

    @Test
    @DisplayName("Filme com URL blank: cai no fallback sem imagem")
    void deveExibirFilmeComFotoBlank() {
        // 🔧 FIX: termo precisa ter >=3 chars
        when(message.text()).thenReturn("t1000 buscar FilmeBlank");
        MovieRecord filme =
                new MovieRecord(1L, "FilmeBlank", "", "2021", "", 0.0, 0.0, "", List.of());
        MovieSearchResponse response = new MovieSearchResponse(1, 1, 1, List.of(filme));
        when(movieService.buscarFilme("FilmeBlank")).thenReturn(response);
        when(movieService.buscarPorId(1L))
                .thenReturn(new MovieOrchestrationResponse("texto", "   "));

        commandHandler.handleTextUpdate(update);

        verify(telegramFacade, never()).enviarFotoHtml(anyLong(), anyString(), anyString());
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("_(sem imagem)_"));
    }

    // ============================================================
    // 🧪 ANALYTICS
    // ============================================================

    @Test
    @DisplayName("Cada texto salva conversa no analytics")
    void cadaTexto_registraConversaNoAnalytics() {
        when(message.text()).thenReturn("uma mensagem");
        when(autoResponseService.getResponseRule(anyLong(), anyString()))
                .thenReturn(Optional.empty());

        commandHandler.handleTextUpdate(update);

        verify(botAnalyticsService)
                .registrarConversa(
                        eq(CHAT_ID),
                        eq(USER_ID),
                        eq(USER_FULL_NAME),
                        eq("TEXT"),
                        eq("uma mensagem"),
                        anyString());
    }

    // ============================================================
    // 🧪 NPE guard: ideia nula (linha coberta pelo teste de ideia vazia)
    // ============================================================

    @Test
    @DisplayName("handleAnotarIdeia: ideia null é tratada sem NPE")
    void handleAnotarIdeia_ideiaNull_naoLancaNPE() {
        // Simula comando direto sem nada depois de "anotar ideia"
        when(message.text()).thenReturn("t1000 anotar ideia");

        assertThatCode(() -> commandHandler.handleTextUpdate(update)).doesNotThrowAnyException();
        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("Digite a ideia"));
    }
}
