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
            JsonNode searchResult = fetchSearchResult(tmdbId, type);
            if (searchResult == null) {
                return null;
            }

            long watchmodeId = extractWatchmodeId(searchResult);
            if (watchmodeId == -1) {
                return null;
            }

            JsonNode sources = fetchSources(watchmodeId);
            if (sources == null || !sources.isArray()) {
                return null;
            }

            List<String> providers = extractBrazilianProviders(sources);
            if (providers.isEmpty()) {
                return null;
            }

            String result = String.join(", ", providers);
            log.info("✅ Watchmode: {} ID {} -> {}", type, tmdbId, result);
            return result;

        } catch (Exception e) {
            log.warn("Erro ao consultar Watchmode para {} ID {}: {}", type, tmdbId, e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------------
    // Métodos privados auxiliares
    // ------------------------------------------------------------------------

    private JsonNode fetchSearchResult(long tmdbId, String type) {
        String searchField = "movie".equals(type) ? "tmdb_movie_id" : "tmdb_tv_id";
        String endpoint =
                String.format(
                        "/search/title/?search_field=%s&search_value=%d&apiKey=%s",
                        searchField, tmdbId, apiKey);

        log.debug("Consultando Watchmode: {}", endpoint);

        String response = restClient.get().uri(endpoint).retrieve().body(String.class);

        if (response == null) {
            return null;
        }

        try {
            JsonNode root = mapper.readTree(response);
            JsonNode results = root.path("title_results");
            return results.isEmpty() ? null : root;
        } catch (Exception e) {
            log.warn("Erro ao parsear resposta de busca: {}", e.getMessage());
            return null;
        }
    }

    private long extractWatchmodeId(JsonNode searchResult) {
        JsonNode results = searchResult.path("title_results");
        // results nunca é vazio (validado em fetchSearchResult)
        return results.get(0).path("id").asLong(-1);
    }

    private JsonNode fetchSources(long watchmodeId) {
        String endpoint = String.format("/title/%d/sources/?apiKey=%s", watchmodeId, apiKey);

        String response = restClient.get().uri(endpoint).retrieve().body(String.class);

        if (response == null) {
            return null;
        }

        try {
            return mapper.readTree(response);
        } catch (Exception e) {
            log.warn("Erro ao parsear fontes: {}", e.getMessage());
            return null;
        }
    }

    private List<String> extractBrazilianProviders(JsonNode sources) {
        List<String> brProviders = new ArrayList<>();
        for (JsonNode source : sources) {
            // Único if com todas as condições – sem continue (S135)
            if ("BR".equalsIgnoreCase(source.path("region").asText(""))
                    && !source.path("name").isNull()) {
                String name = source.path("name").asText();
                if (!name.isBlank()) {
                    brProviders.add(name);
                }
            }
        }
        return brProviders;
    }
}
