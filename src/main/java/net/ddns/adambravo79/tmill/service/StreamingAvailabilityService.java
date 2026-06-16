/* (c) 2026 | 25/05/2026 */
package net.ddns.adambravo79.tmill.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.dto.StreamingAvailabilityResponse;

@Slf4j
@Service
public class StreamingAvailabilityService {

    private final RestClient restClient;

    public StreamingAvailabilityService(
            @Value("${streaming-availability.api.host}") String apiHost,
            @Value("${streaming-availability.api.key}") String apiKey) {
        this.restClient =
                RestClient.builder()
                        .baseUrl("https://streaming-availability.p.rapidapi.com")
                        .defaultHeader("X-RapidAPI-Key", apiKey)
                        .defaultHeader("X-RapidAPI-Host", apiHost)
                        .build();
    }

    /**
     * Busca as informações de streaming para um filme ou série usando seu ID do TMDB.
     *
     * @param tmdbId O ID do título no TMDB.
     * @param type O tipo do título: "movie" ou "tv".
     * @return Uma lista com os nomes dos serviços de streaming disponíveis no Brasil.
     */
    public List<String> getStreamingServicesForTitle(long tmdbId, String type) {
        try {
            String endpoint = String.format("/%s/%d", type, tmdbId);
            StreamingAvailabilityResponse response =
                    this.restClient
                            .get()
                            .uri(endpoint)
                            .retrieve()
                            .body(StreamingAvailabilityResponse.class);

            if (response != null
                    && response.streamingInfo() != null
                    && response.streamingInfo().containsKey("br")) {
                // Acessa o objeto CountryStreamingInfo e depois sua lista 'br'
                return response.streamingInfo().get("br").br().stream()
                        .map(StreamingAvailabilityResponse.Service::serviceName)
                        .toList();
            }
        } catch (Exception e) {
            log.warn(
                    "Não foi possível obter dados de streaming para {} ID {}: {}",
                    type,
                    tmdbId,
                    e.getMessage());
        }
        return List.of();
    }
}
