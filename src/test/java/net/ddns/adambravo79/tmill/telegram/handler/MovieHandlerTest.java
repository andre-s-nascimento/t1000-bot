/* (c) 2026 | 04/11/2026 */
package net.ddns.adambravo79.tmill.telegram.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
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

import java.util.ArrayList;
import java.util.List;

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
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;

import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.exception.MovieNotFoundException;
import net.ddns.adambravo79.tmill.model.MovieOrchestrationResponse;
import net.ddns.adambravo79.tmill.model.MovieRecord;
import net.ddns.adambravo79.tmill.model.MovieSearchResponse;
import net.ddns.adambravo79.tmill.service.MovieService;
import net.ddns.adambravo79.tmill.service.WeeklyReleasesService;
import net.ddns.adambravo79.tmill.telegram.core.GroupAuthorizationService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;
import net.ddns.adambravo79.tmill.telegram.util.TelegramUtils;

/**
 * Testes do {@link MovieHandler}.
 *
 * <p>Cobre:
 *
 * <ul>
 *   <li>{@code t1000 buscar}: validação de tamanho, 1 resultado, N resultados, 0 resultados
 *   <li>{@code t1000 estreias da semana} / {@code t1000 lancamentos}
 *   <li>Desambiguação (botões inline)
 *   <li>Envio de foto vs. texto (sem poster)
 *   <li>Métricas: {@code comando_buscar}, {@code comando_estreias}
 *   <li>Autorização
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MovieHandlerTest {

    @Mock private TelegramFacade telegramFacade;
    @Mock private GroupAuthorizationService authService;
    @Mock private TelegramUtils utils;
    @Mock private MovieService movieService;
    @Mock private WeeklyReleasesService weeklyReleasesService;
    @Mock private MetricsService metricsService;

    private MovieHandler handler;

    private static final long CHAT_ID = 12345L;
    private static final long USER_ID = 999L;
    private static final String USER_NAME = "Testador Silva";

    @BeforeEach
    void setUp() {
        handler =
                new MovieHandler(
                        telegramFacade,
                        authService,
                        utils,
                        movieService,
                        weeklyReleasesService,
                        metricsService);

        lenient().when(authService.isAuthorized(any(Update.class))).thenReturn(true);
        lenient().when(utils.buildFullName(any(User.class))).thenReturn(USER_NAME);
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

    private MovieRecord filme(long id, String title, String releaseDate) {
        return new MovieRecord(
                id, title, title, releaseDate, "overview", 10.0, 8.5, "/poster.jpg", List.of("US"));
    }

    // =========================================================================
    // getPrefixo
    // =========================================================================

    @Test
    @DisplayName("getPrefixo retorna 't1000 ' (prefixo raiz)")
    void getPrefixo() {
        assertThat(handler.getPrefixo()).isEqualTo("t1000 ");
    }

    // =========================================================================
    // t1000 buscar — VALIDAÇÕES
    // =========================================================================

    @Nested
    @DisplayName("t1000 buscar — validações")
    class ValidacoesBusca {

        @ParameterizedTest(name = "[{index}] ''{0}'' → curto")
        @ValueSource(strings = {"t1000 buscar", "t1000 buscar ab", "t1000 buscar a"})
        @DisplayName("termo < 3 chars: BUSCA_TERM_CURTO")
        void termoCurto(String input) {
            Update update = mockUpdate(input);

            handler.processarComando(update);

            verify(telegramFacade).enviarMensagem(CHAT_ID, BotMessages.BUSCA_TERM_CURTO);
            verifyNoInteractions(movieService);
            verify(metricsService).success("comando_buscar");
        }

        @Test
        @DisplayName("termo > 100 chars: BUSCA_TERM_LONGO")
        void termoLongo() {
            String termoLongo = "a".repeat(101);
            Update update = mockUpdate("t1000 buscar " + termoLongo);

            handler.processarComando(update);

            verify(telegramFacade).enviarMensagem(CHAT_ID, BotMessages.BUSCA_TERM_LONGO);
            verifyNoInteractions(movieService);
            verify(metricsService).success("comando_buscar");
        }

        @Test
        @DisplayName("termo exatamente 3 chars: aceito")
        void termoTresChars() {
            when(movieService.buscarFilme("abc"))
                    .thenThrow(new MovieNotFoundException("Não encontrado"));
            Update update = mockUpdate("t1000 buscar abc");

            handler.processarComando(update);

            verify(movieService).buscarFilme("abc");
        }

        @Test
        @DisplayName("termo exatamente 100 chars: aceito")
        void termoCemChars() {
            String termo = "a".repeat(100);
            when(movieService.buscarFilme(termo))
                    .thenThrow(new MovieNotFoundException("Não encontrado"));
            Update update = mockUpdate("t1000 buscar " + termo);

            handler.processarComando(update);

            verify(movieService).buscarFilme(termo);
        }
    }

    // =========================================================================
    // t1000 buscar — RESULTADOS
    // =========================================================================

    @Nested
    @DisplayName("t1000 buscar — resultados")
    class ResultadosBusca {

        @Test
        @DisplayName("MovieNotFoundException: envia ❌ + msg, success")
        void movieNotFound() {
            when(movieService.buscarFilme("xyz"))
                    .thenThrow(new MovieNotFoundException("Filme não encontrado"));
            Update update = mockUpdate("t1000 buscar xyz");

            handler.processarComando(update);

            verify(telegramFacade).enviarMensagem(CHAT_ID, "❌ Filme não encontrado");
            verify(metricsService).success("comando_buscar");
        }

        @Test
        @DisplayName("busca null: envia FILME_NAO_ENCONTRADO, success")
        void buscaNull() {
            when(movieService.buscarFilme("xyz")).thenReturn(null);
            Update update = mockUpdate("t1000 buscar xyz");

            handler.processarComando(update);

            verify(telegramFacade).enviarMensagem(CHAT_ID, "❌ " + BotMessages.FILME_NAO_ENCONTRADO);
            verify(metricsService).success("comando_buscar");
        }

        @Test
        @DisplayName("busca com results vazio: envia FILME_NAO_ENCONTRADO")
        void resultsVazio() {
            when(movieService.buscarFilme("xyz"))
                    .thenReturn(new MovieSearchResponse(1, 0, 1, List.of()));
            Update update = mockUpdate("t1000 buscar xyz");

            handler.processarComando(update);

            verify(telegramFacade).enviarMensagem(CHAT_ID, "❌ " + BotMessages.FILME_NAO_ENCONTRADO);
        }

        @Test
        @DisplayName("1 resultado: busca detalhes e envia foto + texto")
        void umResultado() {
            MovieRecord f = filme(1L, "Duna", "2021-10-01");
            when(movieService.buscarFilme("Duna"))
                    .thenReturn(new MovieSearchResponse(1, 1, 1, List.of(f)));
            when(movieService.buscarPorId(1L))
                    .thenReturn(
                            new MovieOrchestrationResponse(
                                    "Texto do filme", "https://exemplo.com/poster.jpg"));
            Update update = mockUpdate("t1000 buscar Duna");

            handler.processarComando(update);

            verify(movieService).buscarPorId(1L);
            // ✅ Sem eq(): todos os argumentos são literais
            verify(telegramFacade)
                    .enviarFotoHtml(CHAT_ID, "https://exemplo.com/poster.jpg", "Texto do filme");
            verify(metricsService).success("comando_buscar");
        }

        @Test
        @DisplayName("1 resultado sem poster: envia texto com '_(sem imagem)_'")
        void umResultadoSemPoster() {
            MovieRecord f = filme(1L, "Duna", "2021-10-01");
            when(movieService.buscarFilme("Duna"))
                    .thenReturn(new MovieSearchResponse(1, 1, 1, List.of(f)));
            when(movieService.buscarPorId(1L))
                    .thenReturn(new MovieOrchestrationResponse("Texto do filme", null));
            Update update = mockUpdate("t1000 buscar Duna");

            handler.processarComando(update);

            // ✅ eq(CHAT_ID) + contains(): mistura → eq() obrigatório
            verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("_(sem imagem)_"));
            verify(telegramFacade, never()).enviarFotoHtml(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("1 resultado com poster inválido (não-http): envia texto")
        void posterInvalido() {
            MovieRecord f = filme(1L, "Duna", "2021-10-01");
            when(movieService.buscarFilme("Duna"))
                    .thenReturn(new MovieSearchResponse(1, 1, 1, List.of(f)));
            when(movieService.buscarPorId(1L))
                    .thenReturn(new MovieOrchestrationResponse("Texto", "ftp://invalido"));
            Update update = mockUpdate("t1000 buscar Duna");

            handler.processarComando(update);

            verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("_(sem imagem)_"));
        }

        @Test
        @DisplayName("MovieNotFoundException ao buscar detalhes: envia ❌")
        void detalhesNotFound() {
            MovieRecord f = filme(1L, "Duna", "2021-10-01");
            when(movieService.buscarFilme("Duna"))
                    .thenReturn(new MovieSearchResponse(1, 1, 1, List.of(f)));
            when(movieService.buscarPorId(1L))
                    .thenThrow(new MovieNotFoundException("Detalhes indisponíveis"));
            Update update = mockUpdate("t1000 buscar Duna");

            handler.processarComando(update);

            verify(telegramFacade).enviarMensagem(CHAT_ID, "❌ Detalhes indisponíveis");
        }

        @Test
        @DisplayName("2+ resultados: envia botões de desambiguação")
        void multiplosResultados() {
            MovieRecord f1 = filme(1L, "Duna", "2021-10-01");
            MovieRecord f2 = filme(2L, "Duna 2", "2023-03-01");
            when(movieService.buscarFilme("Duna"))
                    .thenReturn(new MovieSearchResponse(1, 2, 1, List.of(f1, f2)));
            Update update = mockUpdate("t1000 buscar Duna");

            handler.processarComando(update);

            ArgumentCaptor<InlineKeyboardMarkup> markupCaptor =
                    ArgumentCaptor.forClass(InlineKeyboardMarkup.class);
            verify(telegramFacade)
                    .enviarComBotoesHtml(
                            eq(CHAT_ID),
                            contains("Encontrei vários resultados"),
                            markupCaptor.capture());

            InlineKeyboardMarkup markup = markupCaptor.getValue();
            InlineKeyboardButton[][] keyboard = markup.inlineKeyboard();
            // ✅ hasDimensions para arrays 2D (hasSize não existe em Object2DArrayAssert)
            assertThat(keyboard).hasDimensions(1, 2);
            assertThat(keyboard[0][0].text()).contains("Duna").contains("2021");
            assertThat(keyboard[0][1].text()).contains("Duna 2").contains("2023");
        }

        @Test
        @DisplayName("12 resultados: limita a 10 botões (5 linhas de 2)")
        void muitosResultadosLimita() {
            List<MovieRecord> resultados = new ArrayList<>();
            for (int i = 1; i <= 12; i++) {
                resultados.add(filme(i, "Filme " + i, "2020-01-01"));
            }
            when(movieService.buscarFilme("Filme"))
                    .thenReturn(new MovieSearchResponse(1, 12, 1, resultados));
            Update update = mockUpdate("t1000 buscar Filme");

            handler.processarComando(update);

            ArgumentCaptor<InlineKeyboardMarkup> captor =
                    ArgumentCaptor.forClass(InlineKeyboardMarkup.class);
            verify(telegramFacade).enviarComBotoesHtml(eq(CHAT_ID), anyString(), captor.capture());

            int totalBotoes = 0;
            for (InlineKeyboardButton[] row : captor.getValue().inlineKeyboard()) {
                totalBotoes += row.length;
            }
            assertThat(totalBotoes).isEqualTo(10);
        }

        @Test
        @DisplayName("resultado sem releaseDate: usa 'S/A' no botão")
        void resultadoSemData() {
            MovieRecord f1 = filme(1L, "Duna", null);
            MovieRecord f2 = filme(2L, "Duna 2", "2023-03-01");
            when(movieService.buscarFilme("Duna"))
                    .thenReturn(new MovieSearchResponse(1, 2, 1, List.of(f1, f2)));
            Update update = mockUpdate("t1000 buscar Duna");

            handler.processarComando(update);

            ArgumentCaptor<InlineKeyboardMarkup> captor =
                    ArgumentCaptor.forClass(InlineKeyboardMarkup.class);
            verify(telegramFacade).enviarComBotoesHtml(eq(CHAT_ID), anyString(), captor.capture());

            InlineKeyboardButton[][] kb = captor.getValue().inlineKeyboard();
            assertThat(kb[0][0].text()).contains("(S/A)");
        }

        @Test
        @DisplayName("resultado com releaseDate muito curto: usa 'S/A'")
        void resultadoComDataCurta() {
            MovieRecord f1 = filme(1L, "Duna", "20");
            MovieRecord f2 = filme(2L, "Duna 2", "2023-03-01");
            when(movieService.buscarFilme("Duna"))
                    .thenReturn(new MovieSearchResponse(1, 2, 1, List.of(f1, f2)));
            Update update = mockUpdate("t1000 buscar Duna");

            handler.processarComando(update);

            ArgumentCaptor<InlineKeyboardMarkup> captor =
                    ArgumentCaptor.forClass(InlineKeyboardMarkup.class);
            verify(telegramFacade).enviarComBotoesHtml(eq(CHAT_ID), anyString(), captor.capture());

            InlineKeyboardButton[][] kb = captor.getValue().inlineKeyboard();
            assertThat(kb[0][0].text()).contains("(S/A)");
        }
    }

    // =========================================================================
    // t1000 estreias / lancamentos
    // =========================================================================

    @Nested
    @DisplayName("t1000 estreias / lancamentos")
    class Estreias {

        @ParameterizedTest(name = "[{index}] ''{0}''")
        @ValueSource(
                strings = {
                    "t1000 estreias da semana",
                    "t1000 lancamentos",
                    "T1000 ESTREIAS DA SEMANA",
                    "t1000  estreias  da  semana", // será aceito após a correção
                    "t1000   estreias   da   semana", // 3 espaços
                    "  t1000 estreias da semana  " // espaços nas bordas
                })
        @DisplayName("Variações de prefixo chamam o WeeklyReleasesService")
        void variacoesDePrefixo(String input) {
            when(weeklyReleasesService.getWeeklyReleasesMessage()).thenReturn("Lista de estreias");
            Update update = mockUpdate(input);

            handler.processarComando(update);

            verify(weeklyReleasesService).getWeeklyReleasesMessage();
            verify(telegramFacade).enviarMensagemHtml(CHAT_ID, "Lista de estreias");
            verify(metricsService).success("comando_estreias");
        }

        @Test
        @DisplayName("Service lança exceção: envia ERRO_GENERICO, registra error")
        void excecaoNoService() {
            when(weeklyReleasesService.getWeeklyReleasesMessage())
                    .thenThrow(new RuntimeException("TMDB down"));
            Update update = mockUpdate("t1000 estreias da semana");

            assertThatCode(() -> handler.processarComando(update)).doesNotThrowAnyException();

            verify(telegramFacade).enviarMensagem(CHAT_ID, BotMessages.ERRO_GENERICO);
            verify(metricsService).error("comando_estreias");
            verify(metricsService, never()).success("comando_estreias");
        }
    }

    // =========================================================================
    // COMANDO DESCONHECIDO (passa pelo MovieHandler)
    // =========================================================================

    @Nested
    @DisplayName("Comando desconhecido")
    class ComandoDesconhecido {

        @Test
        @DisplayName(
                "texto com 't1000 ' mas sem 'buscar'/'estreias': envia COMANDO_NAO_RECONHECIDO")
        void comandoDesconhecido() {
            Update update = mockUpdate("t1000 banana");

            handler.processarComando(update);

            verify(telegramFacade).enviarMensagem(CHAT_ID, BotMessages.COMANDO_NAO_RECONHECIDO);
            verifyNoInteractions(movieService, weeklyReleasesService);
        }
    }

    // =========================================================================
    // MÉTRICAS
    // =========================================================================

    @Nested
    @DisplayName("Métricas")
    class Metricas {

        @Test
        @DisplayName("buscar: 3 chamadas = 3 successes")
        void buscarTresVezes() {
            when(movieService.buscarFilme(anyString()))
                    .thenThrow(new MovieNotFoundException("Não encontrado"));

            handler.processarComando(mockUpdate("t1000 buscar filme1"));
            handler.processarComando(mockUpdate("t1000 buscar filme2"));
            handler.processarComando(mockUpdate("t1000 buscar filme3"));

            verify(metricsService, times(3)).success("comando_buscar");
        }

        @Test
        @DisplayName("buscar válido: success (não error)")
        void buscarSucessoNaoRegistraError() {
            when(movieService.buscarFilme(anyString()))
                    .thenThrow(new MovieNotFoundException("Não encontrado"));
            Update update = mockUpdate("t1000 buscar xyz");

            handler.processarComando(update);

            verify(metricsService).success("comando_buscar");
            verify(metricsService, never()).error(anyString());
        }

        @Test
        @DisplayName("estreias válidas: success")
        void estreiasSucesso() {
            when(weeklyReleasesService.getWeeklyReleasesMessage()).thenReturn("Lista");

            handler.processarComando(mockUpdate("t1000 estreias da semana"));

            verify(metricsService).success("comando_estreias");
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
            Update update = mockUpdate("t1000 buscar Duna");

            handler.processarComando(update);

            verifyNoInteractions(movieService, weeklyReleasesService);
            verify(telegramFacade, never()).enviarMensagem(anyLong(), anyString());
            verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
            verify(telegramFacade, never()).enviarComBotoesHtml(anyLong(), anyString(), any());
        }
    }
}
