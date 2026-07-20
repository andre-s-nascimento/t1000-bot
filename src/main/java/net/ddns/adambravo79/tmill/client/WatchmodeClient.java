package net.ddns.adambravo79.tmill.client;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WatchmodeClient {

    private final RestClient restClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;

    public WatchmodeClient(@Value("${watchmode.api.key}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.builder().baseUrl("https://api.watchmode.com/v1").build();
    }

    @Cacheable(
            value = "watchmode_providers",
            key = "#tmdbId + '_' + #type",
            unless = "#result == null")
    public String getProviders(long tmdbId, String type) {
        try {
            String searchField = "movie".equals(type) ? "tmdb_movie_id" : "tmdb_tv_id";
            String searchEndpoint =
                    "/search/title/?search_field="
                            + searchField
                            + "&search_value="
                            + tmdbId
                            + "&apiKey="
                            + apiKey;

            log.debug("Consultando Watchmode: {}", searchEndpoint);

            // Lê como String para evitar problemas de desserialização
            String searchResponse =
                    restClient.get().uri(searchEndpoint).retrieve().body(String.class);

            if (searchResponse == null) {
                return null;
            }

            JsonNode searchResult = mapper.readTree(searchResponse);
            if (searchResult.path("title_results").isEmpty()) {
                return null;
            }

            long watchmodeId = searchResult.path("title_results").get(0).path("id").asLong();

            String sourcesEndpoint = "/title/" + watchmodeId + "/sources/?apiKey=" + apiKey;
            String sourcesResponse =
                    restClient.get().uri(sourcesEndpoint).retrieve().body(String.class);

            if (sourcesResponse == null) {
                return null;
            }

            JsonNode sources = mapper.readTree(sourcesResponse);
            if (!sources.isArray()) {
                return null;
            }

            List<String> brProviders = new ArrayList<>();
            for (JsonNode source : sources) {
                String region = source.path("region").asText();
                if ("BR".equalsIgnoreCase(region)) {
                    String name = source.path("name").asText();
                    if (name != null && !name.isBlank()) {
                        brProviders.add(name);
                    }
                }
            }

            if (brProviders.isEmpty()) {
                return null;
            }

            String result = String.join(", ", brProviders);
            log.info("✅ Watchmode: {} ID {} -> {}", type, tmdbId, result);
            return result;

        } catch (Exception e) {
            log.warn("Erro ao consultar Watchmode para {} ID {}: {}", type, tmdbId, e.getMessage());
            return null;
        }
    }
}
