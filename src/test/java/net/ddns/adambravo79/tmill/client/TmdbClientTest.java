/* (c) 2026 | 12/10/2026 */
package net.ddns.adambravo79.tmill.client;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import net.ddns.adambravo79.tmill.dto.TmdbDiscoverMovieResponse;
import net.ddns.adambravo79.tmill.dto.TmdbDiscoverTvResponse;
import net.ddns.adambravo79.tmill.model.*;
import net.ddns.adambravo79.tmill.service.config.JsonConfigLoader;

class TmdbClientTest {

    private RestClient restClient;
    private RestClient.RequestHeadersUriSpec<?> getSpec;
    private RestClient.RequestHeadersSpec<?> headersSpec;
    private RestClient.ResponseSpec responseSpec;
    private JsonConfigLoader jsonConfigLoader;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        restClient = mock(RestClient.class);
        getSpec = mock(RestClient.RequestHeadersUriSpec.class);
        headersSpec = mock(RestClient.RequestHeadersSpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);
        jsonConfigLoader = mock(JsonConfigLoader.class);

        doReturn(getSpec).when(restClient).get();
        doReturn(headersSpec).when(getSpec).uri(any(Function.class));
        doReturn(headersSpec).when(getSpec).uri(anyString(), anyLong());
        doReturn(responseSpec).when(headersSpec).retrieve();
    }

    // ===================== Construtor =====================

    @Test
    void construtorPrincipal_deveCriarInstancia() {
        TmdbClient client =
                new TmdbClient(
                        "fake-token",
                        "http://localhost",
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(30),
                        jsonConfigLoader);
        ReflectionTestUtils.setField(client, "restClient", restClient);
        assertThat(client).isNotNull();
    }

    // ===================== pesquisarFilme =====================

    @Test
    void pesquisarFilme_deveRetornarResultados() {
        TmdbClient client = new TmdbClient(restClient); // sem atalhos
        var filme =
                new MovieRecord(
                        1L,
                        "Duna",
                        "Dune",
                        "2021-10-01",
                        "overview",
                        99.0,
                        8.5,
                        "/poster.jpg",
                        List.of("US"));
        when(responseSpec.body(MovieSearchResponse.class))
                .thenReturn(new MovieSearchResponse(1, 1, 1, List.of(filme)));

        MovieSearchResponse result = client.pesquisarFilme("duna");

        assertThat(result.results()).hasSize(1);
        assertThat(result.results().get(0).title()).isEqualTo("Duna");
    }

    @Test
    void pesquisarFilme_deveAplicarAtalho() {
        // Stub do JSON de atalhos
        doReturn(Optional.of(Map.of("aliases", Map.of("duna", "Dune 2021"))))
                .when(jsonConfigLoader)
                .loadConfig(eq("tmdb-aliases.json"), eq(Map.class), any());

        TmdbClient client =
                new TmdbClient(
                        "fake-token",
                        "http://localhost",
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(30),
                        jsonConfigLoader);
        ReflectionTestUtils.setField(client, "restClient", restClient);
        ReflectionTestUtils.setField(client, "aliasesFile", "classpath:tmdb-aliases.json");

        // Força reload manualmente
        client.reloadAliases();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Function<UriBuilder, URI>> uriCaptor =
                ArgumentCaptor.forClass(Function.class);
        doReturn(headersSpec).when(getSpec).uri(uriCaptor.capture());
        when(responseSpec.body(MovieSearchResponse.class))
                .thenReturn(new MovieSearchResponse(0, 0, 0, List.of()));

        client.pesquisarFilme("duna");

        Function<UriBuilder, URI> func = uriCaptor.getValue();
        UriComponentsBuilder realBuilder = UriComponentsBuilder.fromUriString("http://localhost");
        URI uri = func.apply(realBuilder);
        assertThat(uri.toString()).contains("query=Dune%202021");
    }

    @Test
    void pesquisarFilme_deveRetornarVazioQuandoRespostaNula() {
        TmdbClient client = new TmdbClient(restClient);
        when(responseSpec.body(MovieSearchResponse.class)).thenReturn(null);

        MovieSearchResponse result = client.pesquisarFilme("qualquer");
        assertThat(result.results()).isEmpty();
        assertThat(result.totalResults()).isZero();
    }

    @Test
    void pesquisarFilme_deveRetornarVazioQuandoResultsNulo() {
        TmdbClient client = new TmdbClient(restClient);
        when(responseSpec.body(MovieSearchResponse.class))
                .thenReturn(new MovieSearchResponse(0, 0, 0, null));

        MovieSearchResponse result = client.pesquisarFilme("qualquer");
        assertThat(result.results()).isEmpty();
    }

    @Test
    void pesquisarFilme_devePropagarExcecao() {
        TmdbClient client = new TmdbClient(restClient);
        when(responseSpec.body(MovieSearchResponse.class))
                .thenThrow(new RuntimeException("Erro na API"));

        assertThatThrownBy(() -> client.pesquisarFilme("erro"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Erro na API");
    }

    // ===================== reloadAliases =====================

    @Test
    @DisplayName("reloadAliases: carrega atalhos do JSON")
    void reloadAliases_carregaAtalhos() {
        doReturn(
                        Optional.of(
                                Map.of(
                                        "aliases",
                                        Map.of("duna", "Dune 2021", "batman", "The Batman 2022"))))
                .when(jsonConfigLoader)
                .loadConfig(eq("tmdb-aliases.json"), eq(Map.class), any());

        TmdbClient client =
                new TmdbClient(
                        "fake-token",
                        "http://localhost",
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(30),
                        jsonConfigLoader);
        ReflectionTestUtils.setField(client, "restClient", restClient);
        ReflectionTestUtils.setField(client, "aliasesFile", "classpath:tmdb-aliases.json");

        client.reloadAliases();

        @SuppressWarnings("unchecked")
        Map<String, String> atalhos =
                (Map<String, String>) ReflectionTestUtils.getField(client, "atalhos");
        assertThat(atalhos)
                .containsEntry("duna", "Dune 2021")
                .containsEntry("batman", "The Batman 2022");
    }

    @Test
    @DisplayName("reloadAliases: JSON ausente mantém atalhos")
    void reloadAliases_jsonAusente_mantem() {
        doReturn(Optional.of(Map.of("aliases", Map.of("duna", "Dune 2021"))))
                .when(jsonConfigLoader)
                .loadConfig(eq("tmdb-aliases.json"), eq(Map.class), any());

        TmdbClient client =
                new TmdbClient(
                        "fake-token",
                        "http://localhost",
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(30),
                        jsonConfigLoader);
        ReflectionTestUtils.setField(client, "restClient", restClient);
        ReflectionTestUtils.setField(client, "aliasesFile", "classpath:tmdb-aliases.json");

        client.reloadAliases();

        doReturn(Optional.empty())
                .when(jsonConfigLoader)
                .loadConfig(eq("tmdb-aliases.json"), eq(Map.class), any());
        client.reloadAliases();

        @SuppressWarnings("unchecked")
        Map<String, String> atalhos =
                (Map<String, String>) ReflectionTestUtils.getField(client, "atalhos");
        assertThat(atalhos).containsEntry("duna", "Dune 2021");
    }

    @Test
    @DisplayName("reloadAliases: chave 'aliases' inválida mantém atalhos")
    void reloadAliases_chaveInvalida_mantem() {
        doReturn(Optional.of(Map.of("aliases", Map.of("duna", "Dune 2021"))))
                .when(jsonConfigLoader)
                .loadConfig(eq("tmdb-aliases.json"), eq(Map.class), any());

        TmdbClient client =
                new TmdbClient(
                        "fake-token",
                        "http://localhost",
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(30),
                        jsonConfigLoader);
        ReflectionTestUtils.setField(client, "restClient", restClient);
        ReflectionTestUtils.setField(client, "aliasesFile", "classpath:tmdb-aliases.json");

        client.reloadAliases();

        doReturn(Optional.of(Map.of("aliases", "não é mapa")))
                .when(jsonConfigLoader)
                .loadConfig(eq("tmdb-aliases.json"), eq(Map.class), any());
        client.reloadAliases();

        @SuppressWarnings("unchecked")
        Map<String, String> atalhos =
                (Map<String, String>) ReflectionTestUtils.getField(client, "atalhos");
        assertThat(atalhos).containsEntry("duna", "Dune 2021");
    }

    // ===================== buscarDetalhes =====================

    @Test
    void buscarDetalhes_deveRetornarFilme() {
        TmdbClient client = new TmdbClient(restClient);
        var filme =
                new MovieRecord(
                        2L,
                        "Batman",
                        "Batman",
                        "2022-03-01",
                        "overview",
                        88.0,
                        7.9,
                        "/poster.jpg",
                        List.of("US"));
        when(responseSpec.body(MovieRecord.class)).thenReturn(filme);

        MovieRecord result = client.buscarDetalhes(2L);
        assertThat(result.title()).isEqualTo("Batman");
    }

    @Test
    void buscarDetalhes_deveLancarExcecaoQuandoRespostaNula() {
        TmdbClient client = new TmdbClient(restClient);
        when(responseSpec.body(MovieRecord.class)).thenReturn(null);

        assertThatThrownBy(() -> client.buscarDetalhes(99L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Falha ao buscar detalhes");
    }

    // ===================== buscarElenco =====================

    @Test
    void buscarElenco_deveRetornarLista() {
        TmdbClient client = new TmdbClient(restClient);
        var cast = List.of(new CastRecord("Ator", "Personagem"));
        when(responseSpec.body(CreditsResponse.class))
                .thenReturn(new CreditsResponse(cast, List.of()));

        List<CastRecord> result = client.buscarElenco(1L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Ator");
    }

    @Test
    void buscarElenco_deveRetornarVazioQuandoRespostaNula() {
        TmdbClient client = new TmdbClient(restClient);
        when(responseSpec.body(CreditsResponse.class)).thenReturn(null);

        assertThat(client.buscarElenco(1L)).isEmpty();
    }

    @Test
    void buscarElenco_deveRetornarVazioQuandoCastNulo() {
        TmdbClient client = new TmdbClient(restClient);
        when(responseSpec.body(CreditsResponse.class))
                .thenReturn(new CreditsResponse(null, List.of()));

        assertThat(client.buscarElenco(1L)).isEmpty();
    }

    // ===================== buscarDiretor =====================

    @Test
    void buscarDiretor_deveRetornarNome() {
        TmdbClient client = new TmdbClient(restClient);
        var crew = List.of(new CrewRecord("Christopher Nolan", "Director"));
        when(responseSpec.body(CreditsResponse.class))
                .thenReturn(new CreditsResponse(List.of(), crew));

        assertThat(client.buscarDiretor(1L)).isEqualTo("Christopher Nolan");
    }

    @Test
    void buscarDiretor_deveRetornarNullQuandoNaoEncontrado() {
        TmdbClient client = new TmdbClient(restClient);
        var crew = List.of(new CrewRecord("Alguém", "Producer"));
        when(responseSpec.body(CreditsResponse.class))
                .thenReturn(new CreditsResponse(List.of(), crew));

        assertThat(client.buscarDiretor(1L)).isNull();
    }

    @Test
    void buscarDiretor_deveRetornarNullQuandoRespostaNula() {
        TmdbClient client = new TmdbClient(restClient);
        when(responseSpec.body(CreditsResponse.class)).thenReturn(null);

        assertThat(client.buscarDiretor(1L)).isNull();
    }

    // ===================== discoverMoviesByDate =====================

    @Test
    void discoverMoviesByDate_deveRetornarResposta() {
        TmdbClient client = new TmdbClient(restClient);
        var mockResponse = mock(TmdbDiscoverMovieResponse.class);
        when(responseSpec.body(TmdbDiscoverMovieResponse.class)).thenReturn(mockResponse);

        assertThat(client.discoverMoviesByDate("2026-07-01", "2026-07-31")).isSameAs(mockResponse);
    }

    // ===================== discoverTvByDate =====================

    @Test
    void discoverTvByDate_deveRetornarResposta() {
        TmdbClient client = new TmdbClient(restClient);
        var mockResponse = mock(TmdbDiscoverTvResponse.class);
        when(responseSpec.body(TmdbDiscoverTvResponse.class)).thenReturn(mockResponse);

        assertThat(client.discoverTvByDate("2026-07-01", "2026-07-31")).isSameAs(mockResponse);
    }

    // ===================== buscarOndeAssistirFilme =====================

    @Test
    void buscarOndeAssistirFilme_deveRetornarProvedores() {
        TmdbClient client = new TmdbClient(restClient);
        var response =
                new WatchProviderResponse(
                        Map.of(
                                "BR",
                                new WatchProviderResponse.CountryProviders(
                                        List.of(new Provider("Netflix", 1, "/logo.png")))));
        when(responseSpec.body(WatchProviderResponse.class)).thenReturn(response);

        assertThat(client.buscarOndeAssistirFilme(1L)).contains("Netflix");
    }

    @Test
    void buscarOndeAssistirFilme_deveRetornarIndisponivelQuandoNaoHaProvedor() {
        TmdbClient client = new TmdbClient(restClient);
        when(responseSpec.body(WatchProviderResponse.class))
                .thenReturn(new WatchProviderResponse(Map.of()));

        assertThat(client.buscarOndeAssistirFilme(1L)).contains("Indisponivel no momento");
    }

    @Test
    void buscarOndeAssistirFilme_deveRetornarIndisponivelQuandoResponseNulo() {
        TmdbClient client = new TmdbClient(restClient);
        when(responseSpec.body(WatchProviderResponse.class)).thenReturn(null);

        assertThat(client.buscarOndeAssistirFilme(1L)).contains("Indisponivel no momento");
    }

    @Test
    void buscarOndeAssistirFilme_deveRetornarIndisponivelQuandoResultsNulo() {
        TmdbClient client = new TmdbClient(restClient);
        when(responseSpec.body(WatchProviderResponse.class))
                .thenReturn(new WatchProviderResponse(null));

        assertThat(client.buscarOndeAssistirFilme(1L)).contains("Indisponivel no momento");
    }

    @Test
    void buscarOndeAssistirFilme_deveRetornarIndisponivelQuandoNaoTemBR() {
        TmdbClient client = new TmdbClient(restClient);
        var response =
                new WatchProviderResponse(
                        Map.of("US", new WatchProviderResponse.CountryProviders(List.of())));
        when(responseSpec.body(WatchProviderResponse.class)).thenReturn(response);

        assertThat(client.buscarOndeAssistirFilme(1L)).contains("Indisponivel no momento");
    }

    @Test
    void buscarOndeAssistirFilme_deveRetornarIndisponivelNo404() {
        TmdbClient client = new TmdbClient(restClient);
        when(responseSpec.body(WatchProviderResponse.class))
                .thenThrow(HttpClientErrorException.NotFound.class);

        assertThat(client.buscarOndeAssistirFilme(1L)).contains("Indisponivel no momento");
    }

    @Test
    void buscarOndeAssistirFilme_deveRetornarIndisponivelEmErroGenerico() {
        TmdbClient client = new TmdbClient(restClient);
        when(responseSpec.body(WatchProviderResponse.class))
                .thenThrow(new RuntimeException("Erro"));

        assertThat(client.buscarOndeAssistirFilme(1L)).contains("Indisponivel no momento");
    }

    @Test
    void buscarOndeAssistirSerie_deveRetornarProvedores() {
        TmdbClient client = new TmdbClient(restClient);
        var response =
                new WatchProviderResponse(
                        Map.of(
                                "BR",
                                new WatchProviderResponse.CountryProviders(
                                        List.of(new Provider("Disney+", 1, "/logo.png")))));
        when(responseSpec.body(WatchProviderResponse.class)).thenReturn(response);

        assertThat(client.buscarOndeAssistirSerie(1L)).contains("Disney+");
    }

    @Test
    void buscarOndeAssistirSerie_deveRetornarIndisponivelQuandoResponseNulo() {
        TmdbClient client = new TmdbClient(restClient);
        when(responseSpec.body(WatchProviderResponse.class)).thenReturn(null);

        assertThat(client.buscarOndeAssistirSerie(1L)).contains("Indisponivel no momento");
    }

    @Test
    void listarProvedoresFilmes_deveRetornarResposta() {
        TmdbClient client = new TmdbClient(restClient);
        var mockResponse = mock(WatchProvidersResponse.class);
        when(responseSpec.body(WatchProvidersResponse.class)).thenReturn(mockResponse);

        assertThat(client.listarProvedoresFilmes()).isSameAs(mockResponse);
    }

    @Test
    void buscarDetalhesSerie_deveRetornarSerie() {
        TmdbClient client = new TmdbClient(restClient);
        var serie = new TvRecord(1L, "Serie A", "Overview", 8.0, "2026-01-01", "/poster.jpg");
        when(responseSpec.body(TvRecord.class)).thenReturn(serie);

        assertThat(client.buscarDetalhesSerie(1L).name()).isEqualTo("Serie A");
    }

    @Test
    void buscarDetalhesSerie_deveLancarExcecaoQuandoRespostaNula() {
        TmdbClient client = new TmdbClient(restClient);
        when(responseSpec.body(TvRecord.class)).thenReturn(null);

        assertThatThrownBy(() -> client.buscarDetalhesSerie(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Falha ao buscar detalhes da serie");
    }

    @Test
    void buscarOndeAssistirFilme_deveRetornarStringVaziaQuandoTodosProvedoresNulos() {
        Provider provider1 = mock(Provider.class);
        when(provider1.name()).thenReturn(null);
        Provider provider2 = mock(Provider.class);
        when(provider2.name()).thenReturn(null);
        List<Provider> providers = List.of(provider1, provider2);

        WatchProviderResponse.CountryProviders brProviders =
                mock(WatchProviderResponse.CountryProviders.class);
        when(brProviders.flatrate()).thenReturn(providers);
        WatchProviderResponse response = mock(WatchProviderResponse.class);
        when(response.results()).thenReturn(Map.of("BR", brProviders));
        when(responseSpec.body(WatchProviderResponse.class)).thenReturn(response);

        TmdbClient client = new TmdbClient(restClient);
        String result = client.buscarOndeAssistirFilme(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void buscarOndeAssistirFilme_deveRetornarIndisponivelQuandoFlatRateNulo() {
        WatchProviderResponse.CountryProviders brProviders =
                mock(WatchProviderResponse.CountryProviders.class);
        when(brProviders.flatrate()).thenReturn(null);
        WatchProviderResponse response = mock(WatchProviderResponse.class);
        when(response.results()).thenReturn(Map.of("BR", brProviders));
        when(responseSpec.body(WatchProviderResponse.class)).thenReturn(response);

        TmdbClient client = new TmdbClient(restClient);
        String result = client.buscarOndeAssistirFilme(1L);

        assertThat(result).contains("Indisponivel no momento");
    }
}
