package net.ddns.adambravo79.tmill.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
                .saveIdea(
                        eq(USER_ID),
                        eq("Testador Silva"),
                        eq(CHAT_ID),
                        eq("Melhorar o bot"),
                        eq("privado"));
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
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), eq("Lista de estreias"));
    }

    @Test
    void deveResponderLancamentos() {
        when(message.text()).thenReturn("t1000 lancamentos");
        when(weeklyReleasesService.getWeeklyReleasesMessage()).thenReturn("Lista de lançamentos");
        commandHandler.handleTextUpdate(update);
        verify(weeklyReleasesService).getWeeklyReleasesMessage();
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), eq("Lista de lançamentos"));
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
        when(autoResponseService.getResponseRule(eq(USER_ID), eq("bom dia")))
                .thenReturn(Optional.of(response));

        commandHandler.handleTextUpdate(update);

        verify(autoResponseService).getResponseRule(eq(USER_ID), eq("bom dia"));
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("Bom dia para você!"));
        verify(telegramFacade, never()).enviarMidia(anyLong(), anyString(), anyString());
    }

    @Test
    void deveDispararAutoRespostaComMidiaValida() {
        when(message.text()).thenReturn("bom dia");
        AutoResponseOverride response =
                new AutoResponseOverride("Bom dia!", "https://exemplo.com/gif.gif");
        when(autoResponseService.getResponseRule(eq(USER_ID), eq("bom dia")))
                .thenReturn(Optional.of(response));

        commandHandler.handleTextUpdate(update);

        verify(telegramFacade)
                .enviarMidia(eq(CHAT_ID), eq("https://exemplo.com/gif.gif"), contains("Bom dia!"));
    }

    @Test
    void deveDispararAutoRespostaComMidiaInvalida_ENviaApenasTexto() {
        when(message.text()).thenReturn("bom dia");
        AutoResponseOverride response = new AutoResponseOverride("Bom dia!", "");
        when(autoResponseService.getResponseRule(eq(USER_ID), eq("bom dia")))
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
}
