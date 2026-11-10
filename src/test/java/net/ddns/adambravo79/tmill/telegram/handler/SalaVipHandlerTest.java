/* (c) 2026 | 04/11/2026 */
package net.ddns.adambravo79.tmill.telegram.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

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

import net.ddns.adambravo79.tmill.model.SalaVipMovie;
import net.ddns.adambravo79.tmill.service.SalaVipService;
import net.ddns.adambravo79.tmill.telegram.core.GroupAuthorizationService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.TelegramUtils;

/**
 * Testes do {@link SalaVipHandler}.
 *
 * <p>Cobre:
 *
 * <ul>
 *   <li>Comando de busca por número/episódio ({@code t1000 salavip 15})
 *   <li>Comando de busca por episódio explícito ({@code t1000 salavip episodio 15})
 *   <li>Comando de busca por título ({@code t1000 salavip filme matrix})
 *   <li>Variações de prefixo ({@code salavip} vs. {@code sala vip})
 *   <li>Validação de número negativo/zero
 *   <li>Múltiplos resultados (até 5 exibidos)
 *   <li>Match por nota (sinopse) além do título
 *   <li>Autorização
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SalaVipHandlerTest {

    @Mock private TelegramFacade telegramFacade;
    @Mock private GroupAuthorizationService authService;
    @Mock private TelegramUtils utils;
    @Mock private SalaVipService salaVipService;

    private SalaVipHandler handler;

    private static final long CHAT_ID = 12345L;
    private static final long USER_ID = 999L;
    private static final String USER_NAME = "Testador Silva";

    @BeforeEach
    void setUp() {
        handler = new SalaVipHandler(telegramFacade, authService, utils, salaVipService);

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

    private SalaVipMovie movie(
            Integer numero, Integer episodio, String slug, String titulo, int ano, String nota) {
        return new SalaVipMovie(
                numero,
                episodio,
                slug,
                titulo,
                ano,
                "https://letterboxd.com/film/" + slug + "/",
                nota);
    }

    // =========================================================================
    // getPrefixo
    // =========================================================================

    @Test
    @DisplayName("getPrefixo retorna 't1000 salavip'")
    void getPrefixo() {
        assertThat(handler.getPrefixo()).isEqualTo("t1000 salavip");
    }

    @Test
    @DisplayName("getPrefixoAlternativo retorna 't1000 sala vip'")
    void getPrefixoAlternativo() {
        assertThat(handler.getPrefixoAlternativo()).isEqualTo("t1000 sala vip");
    }

    // =========================================================================
    // BUSCA POR NÚMERO/EPISÓDIO (padrão)
    // =========================================================================

    @Nested
    @DisplayName("Busca por número (padrão)")
    class BuscaPorNumero {

        @ParameterizedTest(name = "[{index}] ''{0}''")
        @ValueSource(
                strings = {
                    "t1000 salavip 15",
                    "t1000 sala vip 15",
                    "t1000 salavip numero 15",
                    "t1000 salavip número 15",
                    "t1000 salavip nº 15",
                    "t1000 salavip n° 15",
                    "t1000 salavip n 15",
                    "t1000 salavip #15",
                    "T1000 SALAVIP #15"
                })
        @DisplayName("Variações de prefixo chamam buscarPrimeiroPorEpisodio")
        void variacoesDePrefixo(String input) {
            when(salaVipService.buscarPrimeiroPorEpisodio(15))
                    .thenReturn(Optional.of(movie(15, 15, "mad-max", "Mad Max", 1979, null)));
            Update update = mockUpdate(input);

            handler.processarComando(update);

            verify(salaVipService).buscarPrimeiroPorEpisodio(15);
            verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("Mad Max"));
        }

        @Test
        @DisplayName("Número 0 ou negativo: envia erro, NÃO chama o service")
        void numeroInvalido() {
            Update update = mockUpdate("t1000 salavip 0");

            handler.processarComando(update);

            verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("maior que zero"));
            verifyNoInteractions(salaVipService);
        }

        @Test
        @DisplayName("Número negativo: envia erro")
        void numeroNegativo() {
            Update update = mockUpdate("t1000 salavip -1");

            handler.processarComando(update);

            verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("maior que zero"));
            verifyNoInteractions(salaVipService);
        }

        @Test
        @DisplayName("Filme encontrado: envia detalhes com nota")
        void encontradoComNota() {
            SalaVipMovie m =
                    movie(15, 15, "mad-max", "Mad Max", 1979, "Um clássico pós-apocalíptico.");
            when(salaVipService.buscarPrimeiroPorEpisodio(15)).thenReturn(Optional.of(m));
            Update update = mockUpdate("t1000 salavip 15");

            handler.processarComando(update);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());

            String msg = captor.getValue();
            assertThat(msg)
                    .contains("Mad Max")
                    .contains("1979")
                    .contains("episódio número <b>15</b>")
                    .contains("Um clássico pós-apocalíptico.")
                    .contains("letterboxd.com/film/mad-max");
        }

        @Test
        @DisplayName("Filme encontrado sem nota: envia detalhes sem nota")
        void encontradoSemNota() {
            when(salaVipService.buscarPrimeiroPorEpisodio(15))
                    .thenReturn(Optional.of(movie(15, 15, "mad-max", "Mad Max", 1979, null)));
            Update update = mockUpdate("t1000 salavip 15");

            handler.processarComando(update);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());

            assertThat(captor.getValue()).contains("Mad Max").doesNotContain("\n\n");
        }

        @Test
        @DisplayName("Filme não encontrado: envia mensagem de erro")
        void naoEncontrado() {
            when(salaVipService.buscarPrimeiroPorEpisodio(99)).thenReturn(Optional.empty());
            Update update = mockUpdate("t1000 salavip 99");

            handler.processarComando(update);

            verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("episódio #99"));
        }

        @Test
        @DisplayName("Escapa HTML no título e na nota")
        void escapaHtml() {
            SalaVipMovie m = movie(1, 1, "test", "<script>alert</script>", 2020, "<b>nota</b>");
            when(salaVipService.buscarPrimeiroPorEpisodio(1)).thenReturn(Optional.of(m));
            Update update = mockUpdate("t1000 salavip 1");

            handler.processarComando(update);

            // Verifica que escapeHtml foi chamado para título e nota
            verify(utils).escapeHtml("<script>alert</script>");
            verify(utils).escapeHtml("<b>nota</b>");
        }
    }

    // =========================================================================
    // BUSCA POR EPISÓDIO (comando explícito)
    // =========================================================================

    @Nested
    @DisplayName("Busca por episódio (comando explícito)")
    class BuscaPorEpisodio {

        @ParameterizedTest(name = "[{index}] ''{0}''")
        @ValueSource(
                strings = {
                    "t1000 salavip episodio 15",
                    "t1000 salavip episódio 15",
                    "t1000 salavip ep 15",
                    "t1000 salavip ep15"
                })
        @DisplayName("Variações chamam buscarPorEpisodio")
        void variacoes(String input) {
            when(salaVipService.buscarPorEpisodio(15))
                    .thenReturn(List.of(movie(15, 15, "mad-max", "Mad Max", 1979, null)));
            Update update = mockUpdate(input);

            handler.processarComando(update);

            verify(salaVipService).buscarPorEpisodio(15);
            verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("Mad Max"));
        }

        @Test
        @DisplayName("Episódio com 1 filme: envia detalhes")
        void umFilme() {
            when(salaVipService.buscarPorEpisodio(15))
                    .thenReturn(List.of(movie(15, 15, "mad-max", "Mad Max", 1979, null)));
            Update update = mockUpdate("t1000 salavip episodio 15");

            handler.processarComando(update);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());

            assertThat(captor.getValue()).contains("episódio <b>#15</b>").contains("Mad Max");
        }

        @Test
        @DisplayName("Episódio com 2 filmes: envia lista")
        void doisFilmes() {
            when(salaVipService.buscarPorEpisodio(15))
                    .thenReturn(
                            List.of(
                                    movie(15, 15, "mad-max", "Mad Max", 1979, null),
                                    movie(16, 15, "mad-max-2", "Mad Max 2", 1981, null)));
            Update update = mockUpdate("t1000 salavip episodio 15");

            handler.processarComando(update);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());

            String msg = captor.getValue();
            assertThat(msg).contains("cobre 2 filmes").contains("Mad Max").contains("Mad Max 2");
        }

        @Test
        @DisplayName("Episódio não encontrado: envia erro")
        void naoEncontrado() {
            when(salaVipService.buscarPorEpisodio(99)).thenReturn(List.of());
            Update update = mockUpdate("t1000 salavip episodio 99");

            handler.processarComando(update);

            verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("episódio #99"));
        }
    }

    // =========================================================================
    // BUSCA POR TÍTULO
    // =========================================================================

    @Nested
    @DisplayName("Busca por título")
    class BuscaPorTitulo {

        @ParameterizedTest(name = "[{index}] ''{0}''")
        @ValueSource(
                strings = {
                    "t1000 salavip filme matrix",
                    "t1000 salavip tem filme matrix",
                    "T1000 SALAVIP FILME MATRIX"
                })
        @DisplayName("Variações chamam buscarPorTitulo (case-insensitive)")
        void variacoes(String input) {
            when(salaVipService.buscarPorTitulo(anyString()))
                    .thenReturn(List.of(movie(1, 1, "matrix", "The Matrix", 1999, null)));
            Update update = mockUpdate(input);

            handler.processarComando(update);

            // O handler passa o termo com o case original (ex.: "MATRIX" para input uppercase).
            // O SalaVipService faz busca case-insensitive (lower(title) LIKE lower(?)),
            // então validamos que o termo passado é "matrix" ignorando case.
            verify(salaVipService).buscarPorTitulo(argThat(t -> t.equalsIgnoreCase("matrix")));
            verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("The Matrix"));
        }

        @Test
        @DisplayName("Título encontrado: envia detalhes")
        void encontrado() {
            when(salaVipService.buscarPorTitulo("matrix"))
                    .thenReturn(List.of(movie(1, 1, "matrix", "The Matrix", 1999, null)));
            Update update = mockUpdate("t1000 salavip filme matrix");

            handler.processarComando(update);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());

            assertThat(captor.getValue())
                    .contains("The Matrix")
                    .contains("#1")
                    .contains("letterboxd.com/film/matrix");
        }

        @Test
        @DisplayName("Título não encontrado: envia erro")
        void naoEncontrado() {
            when(salaVipService.buscarPorTitulo("inexistente")).thenReturn(List.of());
            Update update = mockUpdate("t1000 salavip filme inexistente");

            handler.processarComando(update);

            verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("inexistente"));
        }

        @Test
        @DisplayName("Múltiplos resultados: lista até 5")
        void multiplosResultados() {
            when(salaVipService.buscarPorTitulo("a"))
                    .thenReturn(
                            List.of(
                                    movie(1, 1, "a", "Alpha", 2020, null),
                                    movie(2, 2, "b", "Beta", 2021, null),
                                    movie(3, 3, "c", "Gamma", 2022, null),
                                    movie(4, 4, "d", "Delta", 2023, null),
                                    movie(5, 5, "e", "Epsilon", 2024, null),
                                    movie(6, 6, "f", "Zeta", 2025, null)));
            Update update = mockUpdate("t1000 salavip filme a");

            handler.processarComando(update);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());

            String msg = captor.getValue();
            assertThat(msg)
                    .contains("mais de um filme")
                    .contains("Alpha")
                    .contains("Epsilon")
                    .doesNotContain("Zeta")
                    .contains("mostrando os 5 primeiros");
        }

        @Test
        @DisplayName("Match na nota: adiciona aviso '(Encontrado pela sinopse/nota)'")
        void matchNaNota() {
            SalaVipMovie m =
                    movie(
                            1,
                            1,
                            "signs",
                            "Signs",
                            2002,
                            "Um fazendeiro encontra sinais misteriosos.");
            when(salaVipService.buscarPorTitulo("sinais")).thenReturn(List.of(m));
            Update update = mockUpdate("t1000 salavip filme sinais");

            handler.processarComando(update);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());

            assertThat(captor.getValue())
                    .contains("Encontrado pela sinopse/nota")
                    .contains("Signs");
        }

        @Test
        @DisplayName("Match no título: NÃO adiciona aviso de nota")
        void matchNoTituloSemAviso() {
            when(salaVipService.buscarPorTitulo("matrix"))
                    .thenReturn(List.of(movie(1, 1, "matrix", "The Matrix", 1999, "nota")));
            Update update = mockUpdate("t1000 salavip filme matrix");

            handler.processarComando(update);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());

            assertThat(captor.getValue()).doesNotContain("Encontrado pela sinopse/nota");
        }
    }

    // =========================================================================
    // FILME SEM NÚMERO DE SALA VIP
    // =========================================================================

    @Nested
    @DisplayName("Filme sem número de Sala VIP")
    class FilmeSemNumero {

        @Test
        @DisplayName("Episode null: usa '?'")
        void numberNull() {
            // ✅ CORREÇÃO: Passando null no episódio (segundo parâmetro) e não no primeiro.
            // A assinatura do construtor auxiliar é: (salaVipNumber, salaVipEpisode, slug, title,
            // year, url, note)
            SalaVipMovie m = new SalaVipMovie(null, null, "test", "Test", 2020, "url", null);

            when(salaVipService.buscarPorEpisodio(1)).thenReturn(List.of(m));
            Update update = mockUpdate("t1000 salavip episodio 1");

            handler.processarComando(update);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());

            // Agora a saída será "... Sala VIP #?."
            assertThat(captor.getValue()).contains("#?");
        }
    }

    // =========================================================================
    // COMANDO DESCONHECIDO
    // =========================================================================

    @Nested
    @DisplayName("Comando desconhecido")
    class ComandoDesconhecido {

        @Test
        @DisplayName("Texto que não casa com nenhum padrão: loga e retorna")
        void naoReconhecido() {
            Update update = mockUpdate("t1000 salavip xyz");

            assertThatCode(() -> handler.processarComando(update)).doesNotThrowAnyException();

            verifyNoInteractions(salaVipService);
            verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
            verify(telegramFacade, never()).enviarMensagem(anyLong(), anyString());
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
            Update update = mockUpdate("t1000 salavip 15");

            handler.processarComando(update);

            verifyNoInteractions(salaVipService);
            verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
            verify(telegramFacade, never()).enviarMensagem(anyLong(), anyString());
        }
    }
}
