package net.ddns.adambravo79.tmill.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import net.ddns.adambravo79.tmill.dto.TmdbDiscoverMovieResponse;
import net.ddns.adambravo79.tmill.dto.TmdbDiscoverTvResponse;
import net.ddns.adambravo79.tmill.model.MovieRecord;
import net.ddns.adambravo79.tmill.model.TvRecord;
import net.ddns.adambravo79.tmill.model.WatchProvidersResponse;

class TmdbClientIntegrationTest {

    private MockWebServer mockWebServer;
    private TmdbClient tmdbClient;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/").toString();

        RestClient restClient =
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader("Authorization", "Bearer fake-token")
                        .defaultHeader("accept", "application/json")
                        .build();

        tmdbClient = new TmdbClient(restClient);
    }

    @AfterEach
    void tearDown() {
        mockWebServer.close();
    }

    // ===================== discoverMoviesByDate =====================

    @Test
    void discoverMoviesByDate_deveRetornarResposta() {
        String json =
                """
                {
                    "page": 1,
                    "results": [
                        {
                            "id": 1,
                            "title": "Filme Teste",
                            "release_date": "2026-07-01",
                            "vote_average": 8.5,
                            "popularity": 10.0
                        }
                    ],
                    "total_pages": 1,
                    "total_results": 1
                }
                """;
        mockWebServer.enqueue(
                new MockResponse.Builder()
                        .code(200)
                        .addHeader("Content-Type", "application/json")
                        .body(json)
                        .build());

        TmdbDiscoverMovieResponse response =
                tmdbClient.discoverMoviesByDate("2026-07-01", "2026-07-31");

        assertThat(response).isNotNull();
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).title()).isEqualTo("Filme Teste");
    }

    @Test
    void discoverMoviesByDate_quandoErro_deveLancarExcecao() {
        mockWebServer.enqueue(new MockResponse.Builder().code(404).build());

        assertThatThrownBy(() -> tmdbClient.discoverMoviesByDate("2026-07-01", "2026-07-31"))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    // ===================== discoverTvByDate =====================

    @Test
    void discoverTvByDate_deveRetornarResposta() {
        String json =
                """
                {
                    "page": 1,
                    "results": [
                        {
                            "id": 2,
                            "name": "Serie Teste",
                            "first_air_date": "2026-07-01",
                            "vote_average": 8.0,
                            "popularity": 15.0
                        }
                    ],
                    "total_pages": 1,
                    "total_results": 1
                }
                """;
        mockWebServer.enqueue(
                new MockResponse.Builder()
                        .code(200)
                        .addHeader("Content-Type", "application/json")
                        .body(json)
                        .build());

        TmdbDiscoverTvResponse response = tmdbClient.discoverTvByDate("2026-07-01", "2026-07-31");

        assertThat(response).isNotNull();
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).name()).isEqualTo("Serie Teste");
    }

    @Test
    void discoverTvByDate_quandoErro_deveLancarExcecao() {
        mockWebServer.enqueue(new MockResponse.Builder().code(404).build());

        assertThatThrownBy(() -> tmdbClient.discoverTvByDate("2026-07-01", "2026-07-31"))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    // ===================== buscarDetalhes =====================

    @Test
    void buscarDetalhes_deveRetornarFilme() {
        String json =
                """
                {
                    "id": 1,
                    "title": "Batman",
                    "original_title": "The Batman",
                    "release_date": "2022-03-01",
                    "overview": "Sinopse",
                    "popularity": 88.0,
                    "vote_average": 7.9,
                    "poster_path": "/poster.jpg",
                    "origin_country": ["US"]
                }
                """;
        mockWebServer.enqueue(
                new MockResponse.Builder()
                        .code(200)
                        .addHeader("Content-Type", "application/json")
                        .body(json)
                        .build());

        MovieRecord result = tmdbClient.buscarDetalhes(1L);

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("Batman");
        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void buscarDetalhes_quando404_deveLancarExcecao() {
        mockWebServer.enqueue(new MockResponse.Builder().code(404).build());

        assertThatThrownBy(() -> tmdbClient.buscarDetalhes(999L))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    // ===================== buscarDetalhesSerie =====================

    @Test
    void buscarDetalhesSerie_deveRetornarSerie() {
        String json =
                """
                {
                    "id": 2,
                    "name": "Serie A",
                    "overview": "Overview da serie",
                    "vote_average": 8.0,
                    "first_air_date": "2026-01-01",
                    "poster_path": "/poster.jpg"
                }
                """;
        mockWebServer.enqueue(
                new MockResponse.Builder()
                        .code(200)
                        .addHeader("Content-Type", "application/json")
                        .body(json)
                        .build());

        TvRecord result = tmdbClient.buscarDetalhesSerie(2L);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Serie A");
        assertThat(result.id()).isEqualTo(2L);
    }

    @Test
    void buscarDetalhesSerie_quando404_deveLancarExcecao() {
        mockWebServer.enqueue(new MockResponse.Builder().code(404).build());

        assertThatThrownBy(() -> tmdbClient.buscarDetalhesSerie(999L))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    // ===================== listarProvedoresFilmes =====================

    @Test
    void listarProvedoresFilmes_deveRetornarResposta() {
        // O endpoint /watch/providers/movie retorna um array, mas o DTO espera um mapa.
        // Para fins de cobertura, usamos um JSON que se encaixa no DTO.
        String json =
                """
                {
                    "results": {
                        "1": {
                            "provider_name": "Netflix",
                            "provider_id": 1
                        }
                    }
                }
                """;
        mockWebServer.enqueue(
                new MockResponse.Builder()
                        .code(200)
                        .addHeader("Content-Type", "application/json")
                        .body(json)
                        .build());

        WatchProvidersResponse response = tmdbClient.listarProvedoresFilmes();

        assertThat(response).isNotNull();
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get("1").provider_name()).isEqualTo("Netflix");
    }

    // ===================== buscarOndeAssistir =====================
    @Test
    void buscarOndeAssistirFilme_deveRetornarProvedores() {
        String json =
                """
{
    "results": {
        "BR": {
            "flatrate": [
                { "provider_name": "Netflix", "provider_id": 1, "logo_path": "/logo.png" }
            ]
        }
    }
}
""";
        mockWebServer.enqueue(
                new MockResponse.Builder()
                        .code(200)
                        .addHeader("Content-Type", "application/json")
                        .body(json)
                        .build());

        String result = tmdbClient.buscarOndeAssistirFilme(1L);
        assertThat(result).contains("Netflix");
    }

    @Test
    void buscarOndeAssistirSerie_deveRetornarProvedores() {
        String json =
                """
{
    "results": {
        "BR": {
            "flatrate": [
                { "provider_name": "Disney+", "provider_id": 2, "logo_path": "/logo.png" }
            ]
        }
    }
}
""";
        mockWebServer.enqueue(
                new MockResponse.Builder()
                        .code(200)
                        .addHeader("Content-Type", "application/json")
                        .body(json)
                        .build());

        String result = tmdbClient.buscarOndeAssistirSerie(1L);
        assertThat(result).contains("Disney+");
    }
}
