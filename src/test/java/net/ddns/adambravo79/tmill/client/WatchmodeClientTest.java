package net.ddns.adambravo79.tmill.client;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec;
import org.springframework.web.client.RestClient.RequestHeadersUriSpec;
import org.springframework.web.client.RestClient.ResponseSpec;

class WatchmodeClientTest {

    private RestClient restClient;
    private RequestHeadersUriSpec<?> requestSpec;
    private RequestHeadersSpec<?> headersSpec;
    private ResponseSpec responseSpec;
    private WatchmodeClient watchmodeClient;

    @BeforeEach
    void setUp() {
        restClient = mock(RestClient.class);
        requestSpec = mock(RequestHeadersUriSpec.class);
        headersSpec = mock(RequestHeadersSpec.class);
        responseSpec = mock(ResponseSpec.class);

        doReturn(requestSpec).when(restClient).get();
        doReturn(headersSpec).when(requestSpec).uri(anyString());
        doReturn(responseSpec).when(headersSpec).retrieve();
        doReturn("{}").when(responseSpec).body(String.class);

        watchmodeClient = new WatchmodeClient("fake-api-key");
        ReflectionTestUtils.setField(watchmodeClient, "restClient", restClient);
    }

    // ===================== Sucesso =====================

    @Test
    void getProviders_comMovie_deveRetornarProvedores() {
        String searchJson =
                """
        {
          "title_results": [
            { "id": 12345 }
          ]
        }
        """;
        String sourcesJson =
                """
        [
          { "region": "BR", "name": "Netflix" },
          { "region": "US", "name": "HBO Max" },
          { "region": "BR", "name": "Prime Video" }
        ]
        """;
        doReturn(searchJson).doReturn(sourcesJson).when(responseSpec).body(String.class);

        String result = watchmodeClient.getProviders(1L, "movie");

        assertThat(result).isEqualTo("Netflix, Prime Video");
        verify(requestSpec)
                .uri(
                        "/search/title/?search_field=tmdb_movie_id&search_value=1&apiKey=fake-api-key");
        verify(requestSpec).uri("/title/12345/sources/?apiKey=fake-api-key");
    }

    @Test
    void getProviders_comTv_deveRetornarProvedores() {
        String searchJson =
                """
        {
          "title_results": [
            { "id": 67890 }
          ]
        }
        """;
        String sourcesJson =
                """
        [
          { "region": "BR", "name": "Disney+" },
          { "region": "BR", "name": "Star+" }
        ]
        """;
        doReturn(searchJson).doReturn(sourcesJson).when(responseSpec).body(String.class);

        String result = watchmodeClient.getProviders(2L, "tv");

        assertThat(result).isEqualTo("Disney+, Star+");
        verify(requestSpec)
                .uri("/search/title/?search_field=tmdb_tv_id&search_value=2&apiKey=fake-api-key");
        verify(requestSpec).uri("/title/67890/sources/?apiKey=fake-api-key");
    }

    // ===================== Testes para nome vazio ou nulo =====================

    @Test
    void getProviders_comMovie_ignoraProvedorComNomeVazio() {
        String searchJson =
                """
        {
          "title_results": [
            { "id": 12345 }
          ]
        }
        """;
        String sourcesJson =
                """
        [
          { "region": "BR", "name": "Netflix" },
          { "region": "BR", "name": "" },
          { "region": "BR", "name": "Prime Video" }
        ]
        """;
        doReturn(searchJson).doReturn(sourcesJson).when(responseSpec).body(String.class);

        String result = watchmodeClient.getProviders(1L, "movie");

        assertThat(result).isEqualTo("Netflix, Prime Video");
    }

    @Test
    void getProviders_comMovie_ignoraProvedorComNomeNull() {
        String searchJson =
                """
        {
          "title_results": [
            { "id": 12345 }
          ]
        }
        """;
        String sourcesJson =
                """
        [
          { "region": "BR", "name": null },
          { "region": "BR", "name": "Prime Video" }
        ]
        """;
        doReturn(searchJson).doReturn(sourcesJson).when(responseSpec).body(String.class);

        String result = watchmodeClient.getProviders(1L, "movie");

        assertThat(result).isEqualTo("Prime Video");
    }

    // ===================== Sem provedores BR =====================

    @Test
    void getProviders_semProvedorBR_retornaNull() {
        String searchJson =
                """
        {
          "title_results": [
            { "id": 999 }
          ]
        }
        """;
        String sourcesJson =
                """
        [
          { "region": "US", "name": "HBO Max" },
          { "region": "UK", "name": "Sky" }
        ]
        """;
        doReturn(searchJson).doReturn(sourcesJson).when(responseSpec).body(String.class);

        String result = watchmodeClient.getProviders(1L, "movie");

        assertThat(result).isNull();
    }

    // ===================== Busca sem resultados =====================

    @Test
    void getProviders_buscaSemResultado_retornaNull() {
        String searchJson = """
        {
          "title_results": []
        }
        """;
        doReturn(searchJson).when(responseSpec).body(String.class);

        String result = watchmodeClient.getProviders(1L, "movie");

        assertThat(result).isNull();
    }

    // ===================== Resposta de busca nula =====================

    @Test
    void getProviders_buscaResponseNula_retornaNull() {
        doReturn(null).when(responseSpec).body(String.class);

        String result = watchmodeClient.getProviders(1L, "movie");

        assertThat(result).isNull();
    }

    // ===================== Sources response nula =====================

    @Test
    void getProviders_sourcesResponseNula_retornaNull() {
        String searchJson =
                """
        {
          "title_results": [
            { "id": 123 }
          ]
        }
        """;
        doReturn(searchJson).doReturn(null).when(responseSpec).body(String.class);

        String result = watchmodeClient.getProviders(1L, "movie");

        assertThat(result).isNull();
    }

    // ===================== Sources não é array =====================

    @Test
    void getProviders_sourcesNaoArray_retornaNull() {
        String searchJson =
                """
        {
          "title_results": [
            { "id": 123 }
          ]
        }
        """;
        String sourcesJson = """
        { "invalid": "object" }
        """;
        doReturn(searchJson).doReturn(sourcesJson).when(responseSpec).body(String.class);

        String result = watchmodeClient.getProviders(1L, "movie");

        assertThat(result).isNull();
    }

    // ===================== Sources vazio =====================

    @Test
    void getProviders_sourcesVazio_retornaNull() {
        String searchJson =
                """
        {
          "title_results": [
            { "id": 123 }
          ]
        }
        """;
        String sourcesJson = "[]";
        doReturn(searchJson).doReturn(sourcesJson).when(responseSpec).body(String.class);

        String result = watchmodeClient.getProviders(1L, "movie");

        assertThat(result).isNull();
    }

    // ===================== Novo teste: JSON inválido para sources =====================

    @Test
    void getProviders_sourcesJsonInvalido_retornaNull() {
        String searchJson =
                """
        {
          "title_results": [
            { "id": 123 }
          ]
        }
        """;
        String sourcesJson = "{ invalid json }";
        doReturn(searchJson).doReturn(sourcesJson).when(responseSpec).body(String.class);

        String result = watchmodeClient.getProviders(1L, "movie");

        assertThat(result).isNull();
    }

    // ===================== Exceção genérica (erro na API) =====================

    @Test
    void getProviders_excecaoDuranteChamada_retornaNull() {
        doThrow(new RuntimeException("Erro de rede")).when(responseSpec).body(String.class);

        String result = watchmodeClient.getProviders(1L, "movie");

        assertThat(result).isNull();
    }

    // ===================== Exceção no parse JSON (busca) =====================

    @Test
    void getProviders_jsonInvalido_retornaNull() {
        doReturn("{ invalid json }").when(responseSpec).body(String.class);

        String result = watchmodeClient.getProviders(1L, "movie");

        assertThat(result).isNull();
    }

    // ===================== Construtor =====================

    @Test
    void construtorPadrao_deveFuncionar() {
        WatchmodeClient client = new WatchmodeClient("chave-teste");
        assertThat(client).isNotNull();
    }

    @Test
    void getProviders_comMovie_semId_retornaNull() {
        String searchJson =
                """
        {
          "title_results": [
            { }
          ]
        }
        """;
        // Não precisa de sourcesJson porque vai retornar null antes
        doReturn(searchJson).when(responseSpec).body(String.class);

        String result = watchmodeClient.getProviders(1L, "movie");

        assertThat(result).isNull();
        verify(requestSpec)
                .uri(
                        "/search/title/?search_field=tmdb_movie_id&search_value=1&apiKey=fake-api-key");
        verify(requestSpec, never()).uri(contains("/sources/"));
    }
}
