/* (c) 2026 | 17/05/2026 */
package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import net.ddns.adambravo79.tmill.client.TmdbClient;
import net.ddns.adambravo79.tmill.exception.MovieNotFoundException;
import net.ddns.adambravo79.tmill.model.CastRecord;
import net.ddns.adambravo79.tmill.model.MovieOrchestrationResponse;
import net.ddns.adambravo79.tmill.model.MovieRecord;
import net.ddns.adambravo79.tmill.model.MovieSearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MovieServiceTest {

  @Mock private TmdbClient tmdbClient;
  @Mock private EasterEggService easterEggService;

  @InjectMocks private MovieService movieService;

  @BeforeEach
  void setUp() {
    // stubEmptyEasterEgg é chamado nos testes que necessitam
  }

  // =========================
  // VALIDAÇÕES DE ENTRADA (não usam easterEggService)
  // =========================
  @Test
  void deveLancarExcecaoQuandoBuscaMuitoCurta() {
    assertThatThrownBy(() -> movieService.buscarFilme("ab"))
        .isInstanceOf(MovieNotFoundException.class)
        .hasMessageContaining("Termo de busca muito curto");
  }

  @Test
  void deveLancarExcecaoQuandoBuscaMuitoLonga() {
    String termoLongo = "a".repeat(101);
    assertThatThrownBy(() -> movieService.buscarFilme(termoLongo))
        .isInstanceOf(MovieNotFoundException.class)
        .hasMessageContaining("Termo de busca muito longo");
  }

  @Test
  void deveSanitizarCaracteresEspeciais() {
    when(tmdbClient.pesquisarFilme("válidotermo"))
        .thenReturn(
            new MovieSearchResponse(
                1,
                1,
                1,
                List.of(
                    new MovieRecord(1L, "Filme", "Movie", "2020", "", 1.0, 1.0, "", List.of()))));

    movieService.buscarFilme("válido@termo#");
    verify(tmdbClient).pesquisarFilme("válidotermo");
  }

  @Test
  void deveLancarExcecaoQuandoFilmeNaoEncontrado() {
    when(tmdbClient.pesquisarFilme("xyz")).thenReturn(null);
    assertThatThrownBy(() -> movieService.buscarFilme("xyz"))
        .isInstanceOf(MovieNotFoundException.class)
        .hasMessageContaining("Filme nao encontrado");
  }

  @Test
  void deveLancarExcecaoQuandoListaVazia() {
    when(tmdbClient.pesquisarFilme("xyz")).thenReturn(new MovieSearchResponse(1, 0, 0, List.of()));
    assertThatThrownBy(() -> movieService.buscarFilme("xyz"))
        .isInstanceOf(MovieNotFoundException.class)
        .hasMessageContaining("Filme nao encontrado");
  }

  // =========================
  // TESTES QUE CHAMAM buscarPorId (usam easterEggService)
  // =========================
  private void stubEmptyEasterEgg() {
    when(easterEggService.getEasterEgg(anyLong())).thenReturn(Optional.empty());
  }

  @SuppressWarnings("removal")
  @Test
  void deveFormatarRespostaCompleta() {
    stubEmptyEasterEgg();
    Long id = 1L;

    when(tmdbClient.buscarDetalhes(id))
        .thenReturn(
            new MovieRecord(
                id,
                "O Agente Secreto",
                "The Secret Agent",
                "2026-01-01",
                "desc",
                10.0,
                8.5,
                "/img",
                List.of("BR")));

    when(tmdbClient.buscarElenco(id))
        .thenReturn(List.of(new CastRecord("Wagner Moura", "Marcelo")));

    when(tmdbClient.buscarDiretor(id)).thenReturn("Diretor Teste");
    when(tmdbClient.buscarOndeAssistirFilme(id)).thenReturn("Netflix");

    MovieOrchestrationResponse result = movieService.buscarPorId(id);

    assertThat(result.textoFormatado())
        .contains("O AGENTE SECRETO")
        .contains("2026")
        .contains("Netflix")
        .contains("Wagner")
        .matches("(?s).*🇧🇷.*");

    assertThat(result.urlFoto()).contains("image.tmdb.org");
  }

  @SuppressWarnings("removal")
  @Test
  void deveUsarGloboQuandoNaoHouverPais() {
    stubEmptyEasterEgg();
    var movie =
        new MovieRecord(
            1L,
            "O Agente Secreto",
            "The Secret Agent",
            "2025-09-10",
            "desc",
            10.0,
            8.5,
            "/img",
            List.of());
    when(tmdbClient.buscarDetalhes(1L)).thenReturn(movie);
    when(tmdbClient.buscarElenco(1L)).thenReturn(List.of());
    when(tmdbClient.buscarDiretor(1L)).thenReturn(null);
    when(tmdbClient.buscarOndeAssistirFilme(1L)).thenReturn("N/A");

    MovieOrchestrationResponse result = movieService.buscarPorId(1L);
    assertThat(result.textoFormatado()).contains("🌐");
  }

  @SuppressWarnings("removal")
  @Test
  void deveUsarTBAQuandoSemData() {
    stubEmptyEasterEgg();
    Long id = 1L;
    var movie = new MovieRecord(id, "Teste", "Test", null, "desc", 1.0, 1.0, "/img", List.of("US"));
    when(tmdbClient.buscarDetalhes(id)).thenReturn(movie);
    when(tmdbClient.buscarElenco(id)).thenReturn(List.of());
    when(tmdbClient.buscarDiretor(id)).thenReturn(null);
    when(tmdbClient.buscarOndeAssistirFilme(id)).thenReturn("N/A");

    MovieOrchestrationResponse result = movieService.buscarPorId(id);
    assertThat(result.textoFormatado()).contains("TBA");
  }

  @SuppressWarnings("removal")
  @Test
  void deveUsarGloboQuandoPaisInvalido() {
    stubEmptyEasterEgg();
    var movie =
        new MovieRecord(1L, "Teste", "Test", "2020", "desc", 1.0, 1.0, "/img", List.of("XXX"));
    when(tmdbClient.buscarDetalhes(1L)).thenReturn(movie);
    when(tmdbClient.buscarElenco(1L)).thenReturn(List.of());
    when(tmdbClient.buscarDiretor(1L)).thenReturn(null);
    when(tmdbClient.buscarOndeAssistirFilme(1L)).thenReturn("N/A");

    MovieOrchestrationResponse result = movieService.buscarPorId(1L);
    assertThat(result.textoFormatado()).contains("🌐");
  }

  @Test
  void deveLancarExcecaoQuandoDetalhesForemNull() {
    // NÃO chamar stubEmptyEasterEgg() — o método lança exceção antes de usar o easterEggService
    when(tmdbClient.buscarDetalhes(1L)).thenReturn(null);
    assertThatThrownBy(() -> movieService.buscarPorId(1L))
        .isInstanceOf(MovieNotFoundException.class)
        .hasMessageContaining("Falha ao buscar detalhes do filme para ID");
  }

  @Test
  void deveIncluirEasterEggQuandoPresente() {
    when(easterEggService.getEasterEgg(anyLong()))
        .thenReturn(Optional.of("🎬 Easter Egg especial!"));
    Long id = 42L;

    when(tmdbClient.buscarDetalhes(id))
        .thenReturn(
            new MovieRecord(
                id,
                "Filme Teste",
                "Test Movie",
                "2024",
                "desc",
                5.0,
                7.0,
                "/poster",
                List.of("US")));

    when(tmdbClient.buscarElenco(id)).thenReturn(List.of());
    when(tmdbClient.buscarDiretor(id)).thenReturn("Diretor");
    when(tmdbClient.buscarOndeAssistirFilme(id)).thenReturn("Prime Video");

    MovieOrchestrationResponse result = movieService.buscarPorId(id);

    assertThat(result.textoFormatado()).contains("Easter Egg especial!").contains("Prime Video");
  }
}
