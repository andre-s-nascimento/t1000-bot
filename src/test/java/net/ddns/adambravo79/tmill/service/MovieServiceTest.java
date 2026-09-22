package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import net.ddns.adambravo79.tmill.client.TmdbClient;
import net.ddns.adambravo79.tmill.exception.MovieNotFoundException;
import net.ddns.adambravo79.tmill.model.CastRecord;
import net.ddns.adambravo79.tmill.model.MovieOrchestrationResponse;
import net.ddns.adambravo79.tmill.model.MovieRecord;
import net.ddns.adambravo79.tmill.model.MovieSearchResponse;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;

@ExtendWith(MockitoExtension.class)
class MovieServiceTest {

    @Mock private TmdbClient tmdbClient;
    @Mock private EasterEggService easterEggService;
    @Mock private MetricsService metricsService;

    @InjectMocks private MovieService movieService;

    // ============================================================
    // buscarFilme — validações de entrada (não devem registrar métrica)
    // ============================================================

    @Test
    @DisplayName("buscarFilme: termo curto lança exceção e não registra métrica")
    void buscarFilme_termoCurto_naoRegistraMetrica() {
        assertThatThrownBy(() -> movieService.buscarFilme("ab"))
                .isInstanceOf(MovieNotFoundException.class)
                .hasMessageContaining("Termo de busca muito curto");

        verifyNoInteractions(metricsService);
    }

    @Test
    @DisplayName("buscarFilme: termo longo lança exceção e não registra métrica")
    void buscarFilme_termoLongo_naoRegistraMetrica() {
        String termoLongo = "a".repeat(101);

        assertThatThrownBy(() -> movieService.buscarFilme(termoLongo))
                .isInstanceOf(MovieNotFoundException.class)
                .hasMessageContaining("Termo de busca muito longo");

        verifyNoInteractions(metricsService);
    }

    // ============================================================
    // buscarFilme — sucesso
    // ============================================================

    @Test
    @DisplayName("buscarFilme: sucesso registra métrica success")
    void buscarFilme_sucesso_registraMetricaSuccess() {
        MovieRecord filme =
                new MovieRecord(1L, "Filme", "Movie", "2020", "", 1.0, 1.0, "", List.of());

        // 🔧 FIX: o MovieService sanitiza removendo @ e # mas MANTÉM acentos.
        // Portanto o stub precisa ser "válidotermo" (com acento).
        when(tmdbClient.pesquisarFilme("válidotermo"))
                .thenReturn(new MovieSearchResponse(1, 1, 1, List.of(filme)));

        movieService.buscarFilme("válido@termo#");

        verify(tmdbClient).pesquisarFilme("válidotermo");
        verify(metricsService).success("tmdb_buscar_filme");
        verify(metricsService, never()).error("tmdb_buscar_filme");
    }

    // ============================================================
    // buscarFilme — falha
    // ============================================================

    @Test
    @DisplayName("buscarFilme: null retorna erro e registra métrica error")
    void buscarFilme_tmdbRetornaNull_registraMetricaError() {
        when(tmdbClient.pesquisarFilme("xyz")).thenReturn(null);

        assertThatThrownBy(() -> movieService.buscarFilme("xyz"))
                .isInstanceOf(MovieNotFoundException.class)
                .hasMessageContaining("Filme nao encontrado");

        verify(metricsService).error("tmdb_buscar_filme");
        verify(metricsService, never()).success("tmdb_buscar_filme");
    }

    @Test
    @DisplayName("buscarFilme: lista vazia retorna erro e registra métrica error")
    void buscarFilme_listaVazia_registraMetricaError() {
        when(tmdbClient.pesquisarFilme("xyz"))
                .thenReturn(new MovieSearchResponse(1, 0, 0, List.of()));

        assertThatThrownBy(() -> movieService.buscarFilme("xyz"))
                .isInstanceOf(MovieNotFoundException.class);

        verify(metricsService).error("tmdb_buscar_filme");
        verify(metricsService, never()).success("tmdb_buscar_filme");
    }

    // ============================================================
    // buscarPorId — sucesso
    // ============================================================

    @Test
    @DisplayName("buscarPorId: sucesso registra métrica success")
    void buscarPorId_sucesso_registraMetricaSuccess() {
        Long id = 1L;
        stubMovieWithFullDetails(id, "O Agente Secreto");

        MovieOrchestrationResponse result = movieService.buscarPorId(id);

        assertThat(result.textoFormatado()).contains("O AGENTE SECRETO");
        verify(metricsService).success("tmdb_buscar_detalhes");
        verify(metricsService, never()).error("tmdb_buscar_detalhes");
    }

    @Test
    @DisplayName("buscarPorId: resposta formatada inclui bandeira, elenco, diretor e streaming")
    void buscarPorId_formataTodosOsCampos() {
        Long id = 42L;
        stubMovieWithFullDetails(id, "Filme Teste");

        MovieOrchestrationResponse result = movieService.buscarPorId(id);

        assertThat(result.textoFormatado())
                .contains("FILME TESTE")
                .contains("Netflix")
                .contains("Diretor Teste")
                .contains("Wagner")
                .matches("(?s).*🇧🇷.*");
        assertThat(result.urlFoto()).contains("image.tmdb.org");
    }

    @Test
    @DisplayName("buscarPorId: país inválido usa globo como fallback")
    void buscarPorId_paisInvalido_usaGlobo() {
        Long id = 1L;
        MovieRecord movie =
                new MovieRecord(
                        id, "Teste", "Test", "2020", "desc", 1.0, 1.0, "/img", List.of("XXX"));
        when(tmdbClient.buscarDetalhes(id)).thenReturn(movie);
        when(tmdbClient.buscarElenco(id)).thenReturn(List.of());
        when(tmdbClient.buscarDiretor(id)).thenReturn(null);
        when(tmdbClient.buscarOndeAssistirFilme(id)).thenReturn("N/A");
        when(easterEggService.getEasterEgg(id)).thenReturn(Optional.empty());

        MovieOrchestrationResponse result = movieService.buscarPorId(id);

        assertThat(result.textoFormatado()).contains("🌐");
        verify(metricsService).success("tmdb_buscar_detalhes");
    }

    @Test
    @DisplayName("buscarPorId: ano ausente usa TBA")
    void buscarPorId_semAno_usaTBA() {
        Long id = 1L;
        MovieRecord movie =
                new MovieRecord(id, "Teste", "Test", null, "desc", 1.0, 1.0, "/img", List.of("US"));
        when(tmdbClient.buscarDetalhes(id)).thenReturn(movie);
        when(tmdbClient.buscarElenco(id)).thenReturn(List.of());
        when(tmdbClient.buscarDiretor(id)).thenReturn(null);
        when(tmdbClient.buscarOndeAssistirFilme(id)).thenReturn("N/A");
        when(easterEggService.getEasterEgg(id)).thenReturn(Optional.empty());

        MovieOrchestrationResponse result = movieService.buscarPorId(id);

        assertThat(result.textoFormatado()).contains("TBA");
    }

    @Test
    @DisplayName("buscarPorId: diretor nulo usa N/A")
    void buscarPorId_semDiretor_usaNA() {
        Long id = 1L;
        MovieRecord movie =
                new MovieRecord(
                        id, "Teste", "Test", "2020", "desc", 1.0, 1.0, "/img", List.of("US"));
        when(tmdbClient.buscarDetalhes(id)).thenReturn(movie);
        when(tmdbClient.buscarElenco(id)).thenReturn(List.of());
        when(tmdbClient.buscarDiretor(id)).thenReturn(null);
        when(tmdbClient.buscarOndeAssistirFilme(id)).thenReturn("N/A");
        when(easterEggService.getEasterEgg(id)).thenReturn(Optional.empty());

        MovieOrchestrationResponse result = movieService.buscarPorId(id);

        assertThat(result.textoFormatado()).contains("N/A");
    }

    @Test
    @DisplayName("buscarPorId: easter egg presente é concatenado à resposta")
    void buscarPorId_comEasterEgg_incluiNaResposta() {
        Long id = 42L;
        MovieRecord movie =
                new MovieRecord(
                        id, "Filme", "Movie", "2024", "desc", 5.0, 7.0, "/p", List.of("US"));
        when(tmdbClient.buscarDetalhes(id)).thenReturn(movie);
        when(tmdbClient.buscarElenco(id)).thenReturn(List.of());
        when(tmdbClient.buscarDiretor(id)).thenReturn("Diretor");
        when(tmdbClient.buscarOndeAssistirFilme(id)).thenReturn("Prime");
        when(easterEggService.getEasterEgg(id)).thenReturn(Optional.of("🎬 Easter Egg especial!"));

        MovieOrchestrationResponse result = movieService.buscarPorId(id);

        assertThat(result.textoFormatado()).contains("Easter Egg especial!");
    }

    @Test
    @DisplayName("buscarPorId: elenco >5 usa 'e mais N atores'")
    void buscarPorId_elencoGrande_usaEmais() {
        Long id = 1L;
        MovieRecord movie =
                new MovieRecord(id, "T", "T", "2020", "d", 1.0, 1.0, "/i", List.of("US"));
        when(tmdbClient.buscarDetalhes(id)).thenReturn(movie);
        when(tmdbClient.buscarElenco(id))
                .thenReturn(
                        List.of(
                                new CastRecord("A", "1"),
                                new CastRecord("B", "2"),
                                new CastRecord("C", "3"),
                                new CastRecord("D", "4"),
                                new CastRecord("E", "5"),
                                new CastRecord("F", "6"),
                                new CastRecord("G", "7")));
        when(tmdbClient.buscarDiretor(id)).thenReturn("D");
        when(tmdbClient.buscarOndeAssistirFilme(id)).thenReturn("N/A");
        when(easterEggService.getEasterEgg(id)).thenReturn(Optional.empty());

        MovieOrchestrationResponse result = movieService.buscarPorId(id);

        assertThat(result.textoFormatado()).contains("e mais 2 atores");
    }

    @Test
    @DisplayName("buscarPorId: sem posterPath retorna URL vazia")
    void buscarPorId_semPoster_retornaUrlVazia() {
        Long id = 1L;
        MovieRecord movie =
                new MovieRecord(id, "T", "T", "2020", "d", 1.0, 1.0, null, List.of("US"));
        when(tmdbClient.buscarDetalhes(id)).thenReturn(movie);
        when(tmdbClient.buscarElenco(id)).thenReturn(List.of());
        when(tmdbClient.buscarDiretor(id)).thenReturn(null);
        when(tmdbClient.buscarOndeAssistirFilme(id)).thenReturn("N/A");
        when(easterEggService.getEasterEgg(id)).thenReturn(Optional.empty());

        MovieOrchestrationResponse result = movieService.buscarPorId(id);

        assertThat(result.urlFoto()).isEmpty();
    }

    // ============================================================
    // buscarPorId — falha
    // ============================================================

    @Test
    @DisplayName("buscarPorId: detalhes null lança exceção e registra métrica error")
    void buscarPorId_detalhesNull_registraMetricaError() {
        when(tmdbClient.buscarDetalhes(1L)).thenReturn(null);

        assertThatThrownBy(() -> movieService.buscarPorId(1L))
                .isInstanceOf(MovieNotFoundException.class)
                .hasMessageContaining("Falha ao buscar detalhes do filme para ID");

        verify(metricsService).error("tmdb_buscar_detalhes");
        verify(metricsService, never()).success("tmdb_buscar_detalhes");
    }

    @Test
    @DisplayName("buscarPorId: exceção genérica de cliente registra métrica error")
    void buscarPorId_excecaoGenerica_registraMetricaError() {
        when(tmdbClient.buscarDetalhes(1L)).thenThrow(new RuntimeException("TMDB down"));

        assertThatThrownBy(() -> movieService.buscarPorId(1L)).isInstanceOf(RuntimeException.class);

        verify(metricsService).error("tmdb_buscar_detalhes");
        verify(metricsService, never()).success("tmdb_buscar_detalhes");
    }

    // ============================================================
    // Isolamento: cada chamada bem-sucedida registra exatamente 1 métrica
    // ============================================================

    @Test
    @DisplayName("buscarPorId: cada execução registra exatamente 1 success")
    void buscarPorId_registraExatamenteUmSuccess() {
        Long id = 1L;
        stubMovieWithFullDetails(id, "X");

        movieService.buscarPorId(id);

        verify(metricsService, times(1)).success("tmdb_buscar_detalhes");
        verify(metricsService, never()).error("tmdb_buscar_detalhes");
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private void stubMovieWithFullDetails(Long id, String title) {
        MovieRecord movie =
                new MovieRecord(
                        id,
                        title,
                        "Original",
                        "2026-01-01",
                        "desc",
                        10.0,
                        8.5,
                        "/img",
                        List.of("BR"));
        when(tmdbClient.buscarDetalhes(id)).thenReturn(movie);
        when(tmdbClient.buscarElenco(id))
                .thenReturn(List.of(new CastRecord("Wagner Moura", "Marcelo")));
        when(tmdbClient.buscarDiretor(id)).thenReturn("Diretor Teste");
        when(tmdbClient.buscarOndeAssistirFilme(id)).thenReturn("Netflix");
        when(easterEggService.getEasterEgg(anyLong())).thenReturn(Optional.empty());
    }
}
