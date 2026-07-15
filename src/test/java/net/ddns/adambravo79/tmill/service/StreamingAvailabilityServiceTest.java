package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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
    @Mock private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock private RestClient.RequestHeadersSpec requestHeadersSpec;
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

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/movie/123")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        StreamingAvailabilityResponse response =
                criarRespostaComStreaming("Netflix", "Prime Video");
        when(responseSpec.body(StreamingAvailabilityResponse.class)).thenReturn(response);

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

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/tv/123")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(StreamingAvailabilityResponse.class)).thenReturn(null);

        List<String> result = service.getStreamingServicesForTitle(tmdbId, type);

        assertThat(result).isEmpty();
    }

    @Test
    void deveRetornarListaVaziaQuandoStreamingInfoNulo() {
        long tmdbId = 123L;
        String type = "movie";

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/movie/123")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        StreamingAvailabilityResponse response = new StreamingAvailabilityResponse(null);
        when(responseSpec.body(StreamingAvailabilityResponse.class)).thenReturn(response);

        List<String> result = service.getStreamingServicesForTitle(tmdbId, type);

        assertThat(result).isEmpty();
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoHaStreamingNoBrasil() {
        long tmdbId = 123L;
        String type = "movie";

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/movie/123")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        StreamingAvailabilityResponse response = new StreamingAvailabilityResponse(Map.of());
        when(responseSpec.body(StreamingAvailabilityResponse.class)).thenReturn(response);

        List<String> result = service.getStreamingServicesForTitle(tmdbId, type);

        assertThat(result).isEmpty();
    }

    @Test
    void deveRetornarListaVaziaQuandoListaBrVazia() {
        long tmdbId = 123L;
        String type = "movie";

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/movie/123")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        StreamingAvailabilityResponse.CountryStreamingInfo countryInfo =
                new StreamingAvailabilityResponse.CountryStreamingInfo(List.of());
        StreamingAvailabilityResponse response =
                new StreamingAvailabilityResponse(Map.of("br", countryInfo));
        when(responseSpec.body(StreamingAvailabilityResponse.class)).thenReturn(response);

        List<String> result = service.getStreamingServicesForTitle(tmdbId, type);

        assertThat(result).isEmpty();
    }

    @Test
    void deveRetornarListaVaziaQuandoOcorreExcecao() {
        long tmdbId = 123L;
        String type = "movie";

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/movie/123")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenThrow(new RuntimeException("Erro de rede"));

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
