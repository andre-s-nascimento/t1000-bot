/* (c) 2026 | 06/05/2026 */
package net.ddns.adambravo79.tmill.client;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import net.ddns.adambravo79.tmill.model.*;

class TmdbClientTest {

    private RestClient restClient;
    private RestClient.RequestHeadersUriSpec<?> getSpec;
    private RestClient.RequestHeadersSpec<?> headersSpec;
    private RestClient.ResponseSpec responseSpec;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        restClient = mock(RestClient.class);
        getSpec = mock(RestClient.RequestHeadersUriSpec.class);
        headersSpec = mock(RestClient.RequestHeadersSpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        doReturn(getSpec).when(restClient).get();
        lenient().doReturn(headersSpec).when(getSpec).uri(any(Function.class));
        lenient().doReturn(headersSpec).when(getSpec).uri(anyString(), anyLong());
        lenient().doReturn(responseSpec).when(headersSpec).retrieve();
    }

    // --- pesquisarFilme ---

    @Test
    void devePesquisarFilmeComSucesso() {
        var filme =
                new MovieRecord(
                        1L,
                        "Dune",
                        "2021-10-01",
                        "overview",
                        99.0,
                        8.5,
                        "/poster.jpg",
                        List.of("US"));
        when(responseSpec.body(MovieSearchResponse.class))
                .thenReturn(new MovieSearchResponse(1, 1, 1, List.of(filme)));

        MovieSearchResponse result = new TmdbClient(restClient).pesquisarFilme("duna");

        assertThat(result.results()).hasSize(1);
        assertThat(result.results().get(0).title()).isEqualTo("Dune");
    }

    // 🔥 ALTERADO: não lança exceção, retorna busca vazia
    @Test
    void deveRetornarBuscaVaziaQuandoPesquisaSemResultado() {
        when(responseSpec.body(MovieSearchResponse.class))
                .thenReturn(new MovieSearchResponse(0, 0, 0, List.of()));

        MovieSearchResponse result = new TmdbClient(restClient).pesquisarFilme("inexistente");
        assertThat(result.results()).isEmpty();
        assertThat(result.totalResults()).isZero();
    }

    // --- buscarDetalhes ---

    @Test
    void deveBuscarDetalhesComSucesso() {
        var movie =
                new MovieRecord(
                        2L,
                        "Batman",
                        "2022-03-01",
                        "overview",
                        88.0,
                        7.9,
                        "/poster.jpg",
                        List.of("US"));
        when(responseSpec.body(MovieRecord.class)).thenReturn(movie);

        assertThat(new TmdbClient(restClient).buscarDetalhes(2L).title()).isEqualTo("Batman");
    }

    @Test
    void deveFalharQuandoDetalhesInvalidos() {
        when(responseSpec.body(MovieRecord.class)).thenReturn(null);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> new TmdbClient(restClient).buscarDetalhes(99L))
                .withMessageContaining("Falha ao buscar detalhes");
    }

    // --- buscarElenco ---

    @Test
    void deveBuscarElencoComSucesso() {
        when(responseSpec.body(CreditsResponse.class))
                .thenReturn(
                        new CreditsResponse(
                                List.of(new CastRecord("Ator", "Personagem")), List.of()));

        List<CastRecord> elenco = new TmdbClient(restClient).buscarElenco(1L);

        assertThat(elenco).hasSize(1);
        assertThat(elenco.get(0).name()).isEqualTo("Ator");
    }

    // 🔥 ALTERADO: não lança exceção, retorna lista vazia
    @Test
    void deveRetornarListaVaziaQuandoElencoInvalido() {
        when(responseSpec.body(CreditsResponse.class)).thenReturn(null);

        List<CastRecord> elenco = new TmdbClient(restClient).buscarElenco(1L);
        assertThat(elenco).isEmpty();
    }

    // --- buscarOndeAssistir ---

    @Test
    void deveBuscarOndeAssistirComSucesso() {
        var response =
                new WatchProviderResponse(
                        Map.of(
                                "BR",
                                new CountryProviders(
                                        List.of(new Provider("Netflix", 1, "/logo.png")))));
        when(responseSpec.body(WatchProviderResponse.class)).thenReturn(response);

        assertThat(new TmdbClient(restClient).buscarOndeAssistir(1L)).contains("Netflix");
    }

    @Test
    void deveRetornarMensagemDefaultQuandoNaoHaStreaming() {
        when(responseSpec.body(WatchProviderResponse.class))
                .thenReturn(new WatchProviderResponse(Map.of()));

        assertThat(new TmdbClient(restClient).buscarOndeAssistir(1L))
                .contains("Disponível apenas para Aluguel/Compra");
    }
}
