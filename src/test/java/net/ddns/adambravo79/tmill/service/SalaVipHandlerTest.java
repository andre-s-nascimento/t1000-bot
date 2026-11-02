package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import net.ddns.adambravo79.tmill.model.SalaVipMovie;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.TelegramUtils;

@ExtendWith(MockitoExtension.class)
class SalaVipHandlerTest {

    @Mock SalaVipService salaVipService;
    @Mock TelegramFacade telegramFacade;
    @Mock TelegramUtils utils;

    @InjectMocks SalaVipHandler handler;

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private SalaVipMovie movie(int numero, int episodio, String slug, String titulo, int ano) {
        return new SalaVipMovie(
                numero,
                episodio,
                slug,
                titulo,
                ano,
                "https://letterboxd.com/film/" + slug + "/",
                "nota");
    }

    private SalaVipMovie movieSemNumero(String slug, String titulo, int ano) {
        return new SalaVipMovie(
                null, 1, slug, titulo, ano, "https://letterboxd.com/film/" + slug + "/", "nota");
    }

    private void escapeHtmlIdentidade() {
        when(utils.escapeHtml(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ------------------------------------------------------------------
    // handle() — dispatch
    // ------------------------------------------------------------------

    @Test
    void handleRetornaFalseQuandoNaoReconhece() {
        assertThat(handler.handle(1L, "t1000 bom dia")).isFalse();
        assertThat(handler.handle(1L, "salavip 15")).isFalse();
        assertThat(handler.handle(1L, "t1000 salavip")).isFalse();
        verifyNoInteractions(salaVipService, telegramFacade, utils);
    }

    @Test
    void handleAceitaVariacoesDeNumero() {
        escapeHtmlIdentidade();
        var movie = movie(15, 15, "mad-max", "Mad Max", 1979);
        // O Handler agora usa buscarPrimeiroPorEpisodio
        when(salaVipService.buscarPrimeiroPorEpisodio(15)).thenReturn(Optional.of(movie));

        assertThat(handler.handle(1L, "t1000 salavip 15")).isTrue();
        assertThat(handler.handle(1L, "t1000 sala vip 15")).isTrue();
        assertThat(handler.handle(1L, "t1000 salavip numero 15")).isTrue();
        assertThat(handler.handle(1L, "t1000 salavip número 15")).isTrue();
        assertThat(handler.handle(1L, "t1000 salavip nº 15")).isTrue();
        assertThat(handler.handle(1L, "t1000 salavip n° 15")).isTrue();
        assertThat(handler.handle(1L, "t1000 salavip n 15")).isTrue();
        assertThat(handler.handle(1L, "t1000 salavip #15")).isTrue();
        assertThat(handler.handle(1L, "T1000 SALA VIP #15")).isTrue();

        verify(salaVipService, times(9)).buscarPrimeiroPorEpisodio(15);
    }

    @Test
    @DisplayName("handleNumeroZero: retorna erro e não chama o service")
    void handleNumeroZero() {
        assertThat(handler.handle(1L, "t1000 salavip 0")).isTrue();
        verify(telegramFacade).enviarMensagem(eq(1L), contains("maior que zero"));
        verifyNoInteractions(salaVipService);
    }

    @Test
    @DisplayName("handleNumeroNegativo: retorna erro e não chama o service")
    void handleNumeroNegativo() {
        assertThat(handler.handle(1L, "t1000 salavip -1")).isTrue();
        verify(telegramFacade).enviarMensagem(eq(1L), contains("maior que zero"));
        verifyNoInteractions(salaVipService);
    }

    // ------------------------------------------------------------------
    // buscarPorEpisodio (antigo buscarPorNumero no Handler)
    // ------------------------------------------------------------------

    @Test
    void handleNumeroEncontrado() {
        escapeHtmlIdentidade();
        var movie = movie(15, 15, "mad-max", "Mad Max", 1979);
        when(salaVipService.buscarPrimeiroPorEpisodio(15)).thenReturn(Optional.of(movie));

        assertThat(handler.handle(1L, "t1000 salavip numero 15")).isTrue();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramFacade).enviarMensagemHtml(eq(1L), captor.capture());
        String msg = captor.getValue();
        assertThat(msg)
                .contains("episódio número <b>15</b>")
                .contains("Mad Max")
                .contains("(1979)")
                .contains("https://letterboxd.com/film/mad-max/");
    }

    @Test
    void handleNumeroNaoEncontrado() {
        when(salaVipService.buscarPrimeiroPorEpisodio(99)).thenReturn(Optional.empty());

        assertThat(handler.handle(1L, "t1000 salavip numero 99")).isTrue();

        verify(telegramFacade).enviarMensagem(eq(1L), contains("#99"));
        verify(telegramFacade, never()).enviarMensagemHtml(eq(1L), anyString());
    }

    @Test
    @DisplayName("handleNumeroComoEpisodio: busca pelo episódio e mostra a nota")
    void handleNumeroComoEpisodio() {
        escapeHtmlIdentidade();
        var movie =
                new SalaVipMovie(
                        17,
                        16,
                        "hunger-games",
                        "The Hunger Games",
                        2023,
                        "https://letterboxd.com/film/hunger-games/",
                        "RapaduraCast [Sala VIP] 16 - Excelente!");

        when(salaVipService.buscarPrimeiroPorEpisodio(16)).thenReturn(Optional.of(movie));

        assertThat(handler.handle(1L, "t1000 salavip 16")).isTrue();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramFacade).enviarMensagemHtml(eq(1L), captor.capture());

        String msg = captor.getValue();
        assertThat(msg)
                .contains("é o episódio número <b>16</b>")
                .contains("The Hunger Games")
                .contains("RapaduraCast [Sala VIP] 16 - Excelente!");
    }

    // ------------------------------------------------------------------
    // buscarPorTitulo
    // ------------------------------------------------------------------

    @Test
    void handleTituloNaoEncontrado() {
        when(salaVipService.buscarPorTitulo("inexistente")).thenReturn(List.of());

        assertThat(handler.handle(1L, "t1000 salavip filme inexistente")).isTrue();

        verify(telegramFacade).enviarMensagem(eq(1L), contains("inexistente"));
        verify(telegramFacade, never()).enviarMensagemHtml(eq(1L), anyString());
    }

    @Test
    void handleTituloComUmResultado() {
        escapeHtmlIdentidade();
        var movie = movie(1, 1, "killers-of-the-flower-moon", "Killers of the Flower Moon", 2023);
        when(salaVipService.buscarPorTitulo("killers")).thenReturn(List.of(movie));

        assertThat(handler.handle(1L, "t1000 salavip tem filme killers")).isTrue();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramFacade).enviarMensagemHtml(eq(1L), captor.capture());
        assertThat(captor.getValue())
                .contains("Killers of the Flower Moon")
                .contains("(2023)")
                .contains("Sala VIP sob o número <b>#1</b>");
    }

    @Test
    void handleTituloComUmResultadoSemNumero() {
        escapeHtmlIdentidade();
        var movie = movieSemNumero("sem-numero", "Filme Sem Número", 2020);
        when(salaVipService.buscarPorTitulo("sem")).thenReturn(List.of(movie));

        assertThat(handler.handle(1L, "t1000 salavip filme sem")).isTrue();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramFacade).enviarMensagemHtml(eq(1L), captor.capture());
        assertThat(captor.getValue()).contains("Sala VIP sob o número <b>#?</b>");
    }

    @Test
    void handleTituloComVariosResultadosLimitaCinco() {
        escapeHtmlIdentidade();
        var movies =
                List.of(
                        movie(10, 1, "a", "Alpha", 2020),
                        movie(11, 2, "b", "Beta", 2021),
                        movie(12, 3, "c", "Gamma", 2022),
                        movie(13, 4, "d", "Delta", 2023),
                        movie(14, 5, "e", "Epsilon", 2024),
                        movie(15, 6, "f", "Zeta", 2025));
        when(salaVipService.buscarPorTitulo("a")).thenReturn(movies);

        assertThat(handler.handle(1L, "t1000 salavip filme a")).isTrue();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramFacade).enviarMensagemHtml(eq(1L), captor.capture());
        String msg = captor.getValue();
        assertThat(msg)
                .contains("mais de um filme")
                .contains("Alpha")
                .contains("Beta")
                .contains("Gamma")
                .contains("Delta")
                .contains("Epsilon")
                .doesNotContain("Zeta")
                .contains("mostrando os 5 primeiros resultados");
    }

    @Test
    void handleTituloComVariosResultadosAteCinco() {
        escapeHtmlIdentidade();
        var movies = List.of(movie(10, 1, "a", "Alpha", 2020), movie(11, 2, "b", "Beta", 2021));
        when(salaVipService.buscarPorTitulo("a")).thenReturn(movies);

        assertThat(handler.handle(1L, "t1000 salavip filme a")).isTrue();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramFacade).enviarMensagemHtml(eq(1L), captor.capture());
        String msg = captor.getValue();
        assertThat(msg).contains("mais de um filme").doesNotContain("mostrando os 5 primeiros");
    }

    @Test
    @DisplayName("handleTituloEncontradoNaNota: adiciona aviso de que achou pela sinopse")
    void handleTituloEncontradoNaNota() {
        escapeHtmlIdentidade();
        var movie =
                new SalaVipMovie(
                        118,
                        117,
                        "signs",
                        "Signs",
                        2002,
                        "https://letterboxd.com/film/signs/",
                        "Um fazendeiro encontra sinais misteriosos em sua plantação.");
        when(salaVipService.buscarPorTitulo("sinais")).thenReturn(List.of(movie));

        assertThat(handler.handle(1L, "t1000 salavip filme sinais")).isTrue();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramFacade).enviarMensagemHtml(eq(1L), captor.capture());
        assertThat(captor.getValue())
                .contains("Signs")
                .contains("#118")
                .contains("Encontrado pela sinopse/nota");
    }

    // ------------------------------------------------------------------
    // buscarPorEpisodio (comando explícito "episodio")
    // ------------------------------------------------------------------

    @Test
    void handleEpisodioNaoEncontrado() {
        when(salaVipService.buscarPorEpisodio(99)).thenReturn(List.of());

        assertThat(handler.handle(1L, "t1000 salavip episodio 99")).isTrue();

        verify(telegramFacade).enviarMensagem(eq(1L), contains("#99"));
        verify(telegramFacade, never()).enviarMensagemHtml(eq(1L), anyString());
    }

    @Test
    void handleEpisodioComUmFilme() {
        escapeHtmlIdentidade();
        var movie = movie(15, 15, "mad-max", "Mad Max", 1979);
        when(salaVipService.buscarPorEpisodio(15)).thenReturn(List.of(movie));

        assertThat(handler.handle(1L, "t1000 salavip episodio 15")).isTrue();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramFacade).enviarMensagemHtml(eq(1L), captor.capture());
        assertThat(captor.getValue())
                .contains("episódio <b>#15</b>")
                .contains("Mad Max")
                .contains("(1979)");
    }

    @Test
    void handleEpisodioComDoisFilmes() {
        escapeHtmlIdentidade();
        var m1 = movie(15, 15, "mad-max", "Mad Max", 1979);
        var m2 = movie(16, 15, "mad-max-2", "Mad Max 2", 1981);
        when(salaVipService.buscarPorEpisodio(15)).thenReturn(List.of(m1, m2));

        assertThat(handler.handle(1L, "t1000 salavip episodio 15")).isTrue();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramFacade).enviarMensagemHtml(eq(1L), captor.capture());
        String msg = captor.getValue();
        assertThat(msg).contains("cobre 2 filmes").contains("Mad Max").contains("Mad Max 2");
    }

    @Test
    void handleEpisodioAceitaVariacoes() {
        escapeHtmlIdentidade();
        var movie = movie(15, 15, "mad-max", "Mad Max", 1979);
        when(salaVipService.buscarPorEpisodio(15)).thenReturn(List.of(movie));

        assertThat(handler.handle(1L, "t1000 salavip episodio 15")).isTrue();
        assertThat(handler.handle(1L, "t1000 salavip episódio 15")).isTrue();
        assertThat(handler.handle(1L, "t1000 salavip ep 15")).isTrue();

        verify(salaVipService, times(3)).buscarPorEpisodio(15);
    }
}
