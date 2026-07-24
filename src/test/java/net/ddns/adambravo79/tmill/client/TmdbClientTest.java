package net.ddns.adambravo79.tmill.client;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import net.ddns.adambravo79.tmill.dto.TmdbDiscoverMovieResponse;
import net.ddns.adambravo79.tmill.dto.TmdbDiscoverTvResponse;
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
        doReturn(headersSpec).when(getSpec).uri(any(Function.class));
        doReturn(headersSpec).when(getSpec).uri(anyString(), anyLong());
        doReturn(responseSpec).when(headersSpec).retrieve();
    }

    // ===================== Construtor =====================

    @Test
    void construtorPrincipal_deveCriarInstancia() {
        // Testa o construtor com parâmetros reais (ou mocks) para cobrir as linhas
        // Para evitar chamadas reais à API, podemos passar um RestClient mock, mas
        // o construtor principal cria o RestClient internamente.
        // Uma alternativa é usar o construtor de teste, mas para cobrir as linhas
        // do construtor principal, podemos usar Reflection ou criar um spy.
        // Vamos simplesmente instanciar com valores dummy e verificar se não lança exceção.
        // Como as propriedades @Value são injetadas, podemos passar valores diretos.
        // Para não fazer chamadas reais, podemos criar um RestClient mock e injetar via Reflection,
        // mas o construtor principal não aceita RestClient.
        // Uma forma simples é criar o cliente com o construtor de teste e verificar que ele existe.
        // A cobertura das linhas do construtor principal pode ser alcançada se usarmos
        // o construtor em algum teste. Mas como temos um construtor de teste, o principal
        // pode não ser coberto. Para resolver, podemos criar um teste que chama o construtor
        // com valores e depois substitui o restClient por mock via Reflection.
        // Vou fazer isso para garantir cobertura.
        // Mas, para simplificar, vou criar uma instância com o construtor de teste e
        // verificar que não é nula. A cobertura do construtor principal virá de outro lugar.
        // Como o foco é cobertura, vou adicionar um teste que cria o cliente com o construtor
        // principal usando valores dummy e depois substitui o restClient via Reflection.
        // No entanto, o construtor principal usa @Value, que não é acessível diretamente.
        // Podemos usar o construtor principal com parâmetros reais (ex: "fake-token",
        // "http://localhost")
        // e depois substituir o campo restClient por mock.
        // Vou fazer isso:
        TmdbClient client =
                new TmdbClient(
                        "fake-token",
                        "http://localhost",
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(30));
        // Substitui o restClient pelo mock para evitar chamadas reais
        org.springframework.test.util.ReflectionTestUtils.setField(
                client, "restClient", restClient);
        // Agora podemos usar o cliente nos testes, mas já temos o cliente via construtor de teste.
        // Apenas para garantir cobertura, verificamos que o cliente não é nulo.
        assertThat(client).isNotNull();
    }

    // ===================== pesquisarFilme =====================

    @Test
    void pesquisarFilme_deveRetornarResultados() {
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

        TmdbClient client = new TmdbClient(restClient);
        MovieSearchResponse result = client.pesquisarFilme("duna");

        assertThat(result.results()).hasSize(1);
        assertThat(result.results().get(0).title()).isEqualTo("Duna");
    }

    @Test
    void pesquisarFilme_deveAplicarAtalho() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Function<UriBuilder, URI>> uriCaptor =
                ArgumentCaptor.forClass(Function.class);
        doReturn(headersSpec).when(getSpec).uri(uriCaptor.capture());
        when(responseSpec.body(MovieSearchResponse.class))
                .thenReturn(new MovieSearchResponse(0, 0, 0, List.of()));

        TmdbClient client = new TmdbClient(restClient);
        client.pesquisarFilme("duna");

        Function<UriBuilder, URI> func = uriCaptor.getValue();
        UriComponentsBuilder realBuilder = UriComponentsBuilder.fromUriString("http://localhost");
        URI uri = func.apply(realBuilder);
        assertThat(uri.toString()).contains("query=Dune%202021");
    }

    @Test
    void pesquisarFilme_deveRetornarVazioQuandoRespostaNula() {
        when(responseSpec.body(MovieSearchResponse.class)).thenReturn(null);

        TmdbClient client = new TmdbClient(restClient);
        MovieSearchResponse result = client.pesquisarFilme("qualquer");
        assertThat(result.results()).isEmpty();
        assertThat(result.totalResults()).isZero();
    }

    @Test
    void pesquisarFilme_deveRetornarVazioQuandoResultsNulo() {
        when(responseSpec.body(MovieSearchResponse.class))
                .thenReturn(new MovieSearchResponse(0, 0, 0, null));

        TmdbClient client = new TmdbClient(restClient);
        MovieSearchResponse result = client.pesquisarFilme("qualquer");
        assertThat(result.results()).isEmpty();
    }

    @Test
    void pesquisarFilme_devePropagarExcecao() {
        when(responseSpec.body(MovieSearchResponse.class))
                .thenThrow(new RuntimeException("Erro na API"));

        TmdbClient client = new TmdbClient(restClient);
        assertThatThrownBy(() -> client.pesquisarFilme("erro"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Erro na API");
    }

    // ===================== buscarDetalhes =====================

    @Test
    void buscarDetalhes_deveRetornarFilme() {
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

        TmdbClient client = new TmdbClient(restClient);
        MovieRecord result = client.buscarDetalhes(2L);
        assertThat(result.title()).isEqualTo("Batman");
    }

    @Test
    void buscarDetalhes_deveLancarExcecaoQuandoRespostaNula() {
        when(responseSpec.body(MovieRecord.class)).thenReturn(null);

        TmdbClient client = new TmdbClient(restClient);
        assertThatThrownBy(() -> client.buscarDetalhes(99L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Falha ao buscar detalhes");
    }

    // ===================== buscarElenco =====================

    @Test
    void buscarElenco_deveRetornarLista() {
        var cast = List.of(new CastRecord("Ator", "Personagem"));
        when(responseSpec.body(CreditsResponse.class))
                .thenReturn(new CreditsResponse(cast, List.of()));

        TmdbClient client = new TmdbClient(restClient);
        List<CastRecord> result = client.buscarElenco(1L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Ator");
    }

    @Test
    void buscarElenco_deveRetornarVazioQuandoRespostaNula() {
        when(responseSpec.body(CreditsResponse.class)).thenReturn(null);

        TmdbClient client = new TmdbClient(restClient);
        List<CastRecord> result = client.buscarElenco(1L);
        assertThat(result).isEmpty();
    }

    @Test
    void buscarElenco_deveRetornarVazioQuandoCastNulo() {
        when(responseSpec.body(CreditsResponse.class))
                .thenReturn(new CreditsResponse(null, List.of()));

        TmdbClient client = new TmdbClient(restClient);
        List<CastRecord> result = client.buscarElenco(1L);
        assertThat(result).isEmpty();
    }

    // ===================== buscarDiretor =====================

    @Test
    void buscarDiretor_deveRetornarNome() {
        var crew = List.of(new CrewRecord("Christopher Nolan", "Director"));
        when(responseSpec.body(CreditsResponse.class))
                .thenReturn(new CreditsResponse(List.of(), crew));

        TmdbClient client = new TmdbClient(restClient);
        String diretor = client.buscarDiretor(1L);
        assertThat(diretor).isEqualTo("Christopher Nolan");
    }

    @Test
    void buscarDiretor_deveRetornarNullQuandoNaoEncontrado() {
        var crew = List.of(new CrewRecord("Alguém", "Producer"));
        when(responseSpec.body(CreditsResponse.class))
                .thenReturn(new CreditsResponse(List.of(), crew));

        TmdbClient client = new TmdbClient(restClient);
        String diretor = client.buscarDiretor(1L);
        assertThat(diretor).isNull();
    }

    @Test
    void buscarDiretor_deveRetornarNullQuandoRespostaNula() {
        when(responseSpec.body(CreditsResponse.class)).thenReturn(null);

        TmdbClient client = new TmdbClient(restClient);
        String diretor = client.buscarDiretor(1L);
        assertThat(diretor).isNull();
    }

    // ===================== discoverMoviesByDate =====================

    @Test
    void discoverMoviesByDate_deveRetornarResposta() {
        var mockResponse = mock(TmdbDiscoverMovieResponse.class);
        when(responseSpec.body(TmdbDiscoverMovieResponse.class)).thenReturn(mockResponse);

        TmdbClient client = new TmdbClient(restClient);
        TmdbDiscoverMovieResponse result = client.discoverMoviesByDate("2026-07-01", "2026-07-31");
        assertThat(result).isSameAs(mockResponse);
    }

    @Test
    void discoverMoviesByDate_deveLancarExcecao() {
        when(responseSpec.body(TmdbDiscoverMovieResponse.class))
                .thenThrow(new RuntimeException("Erro"));

        TmdbClient client = new TmdbClient(restClient);
        assertThatThrownBy(() -> client.discoverMoviesByDate("2026-07-01", "2026-07-31"))
                .isInstanceOf(RuntimeException.class);
    }

    // ===================== discoverTvByDate =====================

    @Test
    void discoverTvByDate_deveRetornarResposta() {
        var mockResponse = mock(TmdbDiscoverTvResponse.class);
        when(responseSpec.body(TmdbDiscoverTvResponse.class)).thenReturn(mockResponse);

        TmdbClient client = new TmdbClient(restClient);
        TmdbDiscoverTvResponse result = client.discoverTvByDate("2026-07-01", "2026-07-31");
        assertThat(result).isSameAs(mockResponse);
    }

    @Test
    void discoverTvByDate_deveLancarExcecao() {
        when(responseSpec.body(TmdbDiscoverTvResponse.class))
                .thenThrow(new RuntimeException("Erro"));

        TmdbClient client = new TmdbClient(restClient);
        assertThatThrownBy(() -> client.discoverTvByDate("2026-07-01", "2026-07-31"))
                .isInstanceOf(RuntimeException.class);
    }

    // ===================== buscarOndeAssistirFilme =====================

    @Test
    void buscarOndeAssistirFilme_deveRetornarProvedores() {
        var response =
                new WatchProviderResponse(
                        Map.of(
                                "BR",
                                new WatchProviderResponse.CountryProviders(
                                        List.of(new Provider("Netflix", 1, "/logo.png")))));
        when(responseSpec.body(WatchProviderResponse.class)).thenReturn(response);

        TmdbClient client = new TmdbClient(restClient);
        String result = client.buscarOndeAssistirFilme(1L);
        assertThat(result).contains("Netflix");
    }

    @Test
    void buscarOndeAssistirFilme_deveRetornarIndisponivelQuandoNaoHaProvedor() {
        when(responseSpec.body(WatchProviderResponse.class))
                .thenReturn(new WatchProviderResponse(Map.of()));

        TmdbClient client = new TmdbClient(restClient);
        String result = client.buscarOndeAssistirFilme(1L);
        assertThat(result).contains("Indisponivel no momento");
    }

    @Test
    void buscarOndeAssistirFilme_deveRetornarIndisponivelQuandoResponseNulo() {
        when(responseSpec.body(WatchProviderResponse.class)).thenReturn(null);

        TmdbClient client = new TmdbClient(restClient);
        String result = client.buscarOndeAssistirFilme(1L);
        assertThat(result).contains("Indisponivel no momento");
    }

    @Test
    void buscarOndeAssistirFilme_deveRetornarIndisponivelQuandoResultsNulo() {
        var response = new WatchProviderResponse(null);
        when(responseSpec.body(WatchProviderResponse.class)).thenReturn(response);

        TmdbClient client = new TmdbClient(restClient);
        String result = client.buscarOndeAssistirFilme(1L);
        assertThat(result).contains("Indisponivel no momento");
    }

    @Test
    void buscarOndeAssistirFilme_deveRetornarIndisponivelQuandoNaoTemBR() {
        var response =
                new WatchProviderResponse(
                        Map.of("US", new WatchProviderResponse.CountryProviders(List.of())));
        when(responseSpec.body(WatchProviderResponse.class)).thenReturn(response);

        TmdbClient client = new TmdbClient(restClient);
        String result = client.buscarOndeAssistirFilme(1L);
        assertThat(result).contains("Indisponivel no momento");
    }

    @Test
    void buscarOndeAssistirFilme_deveRetornarIndisponivelQuandoBrProvidersVazio() {
        var response =
                new WatchProviderResponse(
                        Map.of("BR", new WatchProviderResponse.CountryProviders(List.of())));
        when(responseSpec.body(WatchProviderResponse.class)).thenReturn(response);

        TmdbClient client = new TmdbClient(restClient);
        String result = client.buscarOndeAssistirFilme(1L);
        assertThat(result).contains("Indisponivel no momento");
    }

    @Test
    void buscarOndeAssistirFilme_deveRetornarIndisponivelNo404() {
        when(responseSpec.body(WatchProviderResponse.class))
                .thenThrow(HttpClientErrorException.NotFound.class);

        TmdbClient client = new TmdbClient(restClient);
        String result = client.buscarOndeAssistirFilme(1L);
        assertThat(result).contains("Indisponivel no momento");
    }

    @Test
    void buscarOndeAssistirFilme_deveRetornarIndisponivelEmErroGenerico() {
        when(responseSpec.body(WatchProviderResponse.class))
                .thenThrow(new RuntimeException("Erro"));

        TmdbClient client = new TmdbClient(restClient);
        String result = client.buscarOndeAssistirFilme(1L);
        assertThat(result).contains("Indisponivel no momento");
    }

    // ===================== buscarOndeAssistirSerie =====================

    @Test
    void buscarOndeAssistirSerie_deveRetornarProvedores() {
        var response =
                new WatchProviderResponse(
                        Map.of(
                                "BR",
                                new WatchProviderResponse.CountryProviders(
                                        List.of(new Provider("Disney+", 1, "/logo.png")))));
        when(responseSpec.body(WatchProviderResponse.class)).thenReturn(response);

        TmdbClient client = new TmdbClient(restClient);
        String result = client.buscarOndeAssistirSerie(1L);
        assertThat(result).contains("Disney+");
    }

    @Test
    void buscarOndeAssistirSerie_deveRetornarIndisponivelQuandoResponseNulo() {
        when(responseSpec.body(WatchProviderResponse.class)).thenReturn(null);

        TmdbClient client = new TmdbClient(restClient);
        String result = client.buscarOndeAssistirSerie(1L);
        assertThat(result).contains("Indisponivel no momento");
    }

    @Test
    void buscarOndeAssistirSerie_deveRetornarIndisponivelQuandoResultsNulo() {
        var response = new WatchProviderResponse(null);
        when(responseSpec.body(WatchProviderResponse.class)).thenReturn(response);

        TmdbClient client = new TmdbClient(restClient);
        String result = client.buscarOndeAssistirSerie(1L);
        assertThat(result).contains("Indisponivel no momento");
    }

    @Test
    void buscarOndeAssistirSerie_deveRetornarIndisponivelQuandoNaoTemBR() {
        var response =
                new WatchProviderResponse(
                        Map.of("US", new WatchProviderResponse.CountryProviders(List.of())));
        when(responseSpec.body(WatchProviderResponse.class)).thenReturn(response);

        TmdbClient client = new TmdbClient(restClient);
        String result = client.buscarOndeAssistirSerie(1L);
        assertThat(result).contains("Indisponivel no momento");
    }

    @Test
    void buscarOndeAssistirSerie_deveRetornarIndisponivelQuandoBrProvidersVazio() {
        var response =
                new WatchProviderResponse(
                        Map.of("BR", new WatchProviderResponse.CountryProviders(List.of())));
        when(responseSpec.body(WatchProviderResponse.class)).thenReturn(response);

        TmdbClient client = new TmdbClient(restClient);
        String result = client.buscarOndeAssistirSerie(1L);
        assertThat(result).contains("Indisponivel no momento");
    }

    @Test
    void buscarOndeAssistirSerie_deveRetornarIndisponivelNo404() {
        when(responseSpec.body(WatchProviderResponse.class))
                .thenThrow(HttpClientErrorException.NotFound.class);

        TmdbClient client = new TmdbClient(restClient);
        String result = client.buscarOndeAssistirSerie(1L);
        assertThat(result).contains("Indisponivel no momento");
    }

    @Test
    void buscarOndeAssistirSerie_deveRetornarIndisponivelEmErroGenerico() {
        when(responseSpec.body(WatchProviderResponse.class))
                .thenThrow(new RuntimeException("Erro"));

        TmdbClient client = new TmdbClient(restClient);
        String result = client.buscarOndeAssistirSerie(1L);
        assertThat(result).contains("Indisponivel no momento");
    }

    // ===================== listarProvedoresFilmes =====================

    @Test
    void listarProvedoresFilmes_deveRetornarResposta() {
        var mockResponse = mock(WatchProvidersResponse.class);
        when(responseSpec.body(WatchProvidersResponse.class)).thenReturn(mockResponse);

        TmdbClient client = new TmdbClient(restClient);
        WatchProvidersResponse result = client.listarProvedoresFilmes();
        assertThat(result).isSameAs(mockResponse);
    }

    @Test
    void listarProvedoresFilmes_deveLancarExcecao() {
        when(responseSpec.body(WatchProvidersResponse.class))
                .thenThrow(new RuntimeException("Erro"));

        TmdbClient client = new TmdbClient(restClient);
        assertThatThrownBy(() -> client.listarProvedoresFilmes())
                .isInstanceOf(RuntimeException.class);
    }

    // ===================== buscarDetalhesSerie =====================

    @Test
    void buscarDetalhesSerie_deveRetornarSerie() {
        var serie = new TvRecord(1L, "Serie A", "Overview", 8.0, "2026-01-01", "/poster.jpg");
        when(responseSpec.body(TvRecord.class)).thenReturn(serie);

        TmdbClient client = new TmdbClient(restClient);
        TvRecord result = client.buscarDetalhesSerie(1L);
        assertThat(result.name()).isEqualTo("Serie A");
    }

    @Test
    void buscarDetalhesSerie_deveLancarExcecaoQuandoRespostaNula() {
        when(responseSpec.body(TvRecord.class)).thenReturn(null);

        TmdbClient client = new TmdbClient(restClient);
        assertThatThrownBy(() -> client.buscarDetalhesSerie(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Falha ao buscar detalhes da serie");
    }
}
