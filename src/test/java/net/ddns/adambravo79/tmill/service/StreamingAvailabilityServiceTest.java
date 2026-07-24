package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import net.ddns.adambravo79.tmill.dto.StreamingAvailabilityResponse;

@ExtendWith(MockitoExtension.class)
class StreamingAvailabilityServiceTest {

    @Mock private RestClient restClient;
    @Mock private RestClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;
    @Mock private RestClient.RequestHeadersSpec<?> requestHeadersSpec;
    @Mock private RestClient.ResponseSpec responseSpec;

    private StreamingAvailabilityService service;

    @BeforeEach
    void setUp() {
        service = new StreamingAvailabilityService("host", "key");
        ReflectionTestUtils.setField(service, "restClient", restClient);
    }

    @Test
    void deveRetornarListaDeServicosQuandoDisponiveis() {
        long tmdbId = 123L;
        String type = "movie";

        doReturn(requestHeadersUriSpec).when(restClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/movie/123");
        doReturn(responseSpec).when(requestHeadersSpec).retrieve();

        StreamingAvailabilityResponse response =
                criarRespostaComStreaming("Netflix", "Prime Video");
        doReturn(response).when(responseSpec).body(StreamingAvailabilityResponse.class);

        List<String> result = service.getStreamingServicesForTitle(tmdbId, type);

        assertThat(result).containsExactly("Netflix", "Prime Video");
        verify(restClient).get();
        verify(requestHeadersUriSpec).uri("/movie/123");
        verify(requestHeadersSpec).retrieve();
        verify(responseSpec).body(StreamingAvailabilityResponse.class);
    }

    @Test
    void deveRetornarListaVaziaQuandoRespostaNula() {
        long tmdbId = 123L;
        String type = "tv";

        doReturn(requestHeadersUriSpec).when(restClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/tv/123");
        doReturn(responseSpec).when(requestHeadersSpec).retrieve();
        doReturn(null).when(responseSpec).body(StreamingAvailabilityResponse.class);

        List<String> result = service.getStreamingServicesForTitle(tmdbId, type);

        assertThat(result).isEmpty();
    }

    @Test
    void deveRetornarListaVaziaQuandoStreamingInfoNulo() {
        long tmdbId = 123L;
        String type = "movie";

        doReturn(requestHeadersUriSpec).when(restClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/movie/123");
        doReturn(responseSpec).when(requestHeadersSpec).retrieve();

        StreamingAvailabilityResponse response = new StreamingAvailabilityResponse(null);
        doReturn(response).when(responseSpec).body(StreamingAvailabilityResponse.class);

        List<String> result = service.getStreamingServicesForTitle(tmdbId, type);

        assertThat(result).isEmpty();
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoHaStreamingNoBrasil() {
        long tmdbId = 123L;
        String type = "movie";

        doReturn(requestHeadersUriSpec).when(restClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/movie/123");
        doReturn(responseSpec).when(requestHeadersSpec).retrieve();

        StreamingAvailabilityResponse response = new StreamingAvailabilityResponse(Map.of());
        doReturn(response).when(responseSpec).body(StreamingAvailabilityResponse.class);

        List<String> result = service.getStreamingServicesForTitle(tmdbId, type);

        assertThat(result).isEmpty();
    }

    @Test
    void deveRetornarListaVaziaQuandoListaBrVazia() {
        long tmdbId = 123L;
        String type = "movie";

        doReturn(requestHeadersUriSpec).when(restClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/movie/123");
        doReturn(responseSpec).when(requestHeadersSpec).retrieve();

        StreamingAvailabilityResponse.CountryStreamingInfo countryInfo =
                new StreamingAvailabilityResponse.CountryStreamingInfo(List.of());
        StreamingAvailabilityResponse response =
                new StreamingAvailabilityResponse(Map.of("br", countryInfo));
        doReturn(response).when(responseSpec).body(StreamingAvailabilityResponse.class);

        List<String> result = service.getStreamingServicesForTitle(tmdbId, type);

        assertThat(result).isEmpty();
    }

    @Test
    void deveRetornarListaVaziaQuandoOcorreExcecao() {
        long tmdbId = 123L;
        String type = "movie";

        doReturn(requestHeadersUriSpec).when(restClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/movie/123");
        doThrow(new RuntimeException("Erro de rede")).when(requestHeadersSpec).retrieve();

        List<String> result = service.getStreamingServicesForTitle(tmdbId, type);

        assertThat(result).isEmpty();
    }

    private StreamingAvailabilityResponse criarRespostaComStreaming(String... services) {
        List<StreamingAvailabilityResponse.Service> serviceList =
                List.of(services).stream().map(StreamingAvailabilityResponse.Service::new).toList();
        StreamingAvailabilityResponse.CountryStreamingInfo countryInfo =
                new StreamingAvailabilityResponse.CountryStreamingInfo(serviceList);
        return new StreamingAvailabilityResponse(Map.of("br", countryInfo));
    }
}
