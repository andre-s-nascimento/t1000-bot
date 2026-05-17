/* (c) 2026 | 17/05/2026 */
package net.ddns.adambravo79.tmill.client;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.model.*;

@Slf4j
@Component
public class TmdbClient {

    private static final Map<String, String> ATALHOS =
            Map.of(
                    "duna", "Dune 2021",
                    "dune", "Dune 2021",
                    "batman", "The Batman 2022",
                    "o poderoso chefao", "The Godfather 1972");

    private final RestClient restClient;

    @Autowired
    public TmdbClient(
            @Value("${tmdb.token}") String tmdbToken,
            @Value("${tmdb.api.url}") String apiUrl,
            @Value("${tmdb.connect-timeout:5}") int connectTimeoutSeconds,
            @Value("${tmdb.read-timeout:10}") int readTimeoutSeconds) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

        this.restClient =
                RestClient.builder()
                        .baseUrl(apiUrl)
                        .defaultHeader("Authorization", "Bearer " + tmdbToken)
                        .defaultHeader("accept", "application/json")
                        .requestFactory(factory)
                        .build();
    }

    public TmdbClient(RestClient restClient) {
        this.restClient = restClient;
    }

    // ------------------------------------------------------------------------
    // Métodos de busca com retry
    // ------------------------------------------------------------------------

    @Retryable(
            includes = Exception.class,
            maxRetries = 2,
            delay = 1000,
            multiplier = 2,
            maxDelay = 5000)
    @Cacheable(
            value = "tmdb-search",
            key = "#query",
            unless = "#result == null or #result.results().isEmpty()")
    public MovieSearchResponse pesquisarFilme(String query) {
        String queryNormalizada = query.trim().toLowerCase();
        String queryFinal = ATALHOS.getOrDefault(queryNormalizada, query);

        if (!queryFinal.equals(query)) {
            log.info("🎬 TMDB: Atalho aplicado '{}' -> '{}'", query, queryFinal);
        }

        log.info("🔎 TMDB: Pesquisando filme query='{}'", queryFinal);

        try {
            MovieSearchResponse response =
                    restClient
                            .get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path("/search/movie")
                                                    .queryParam("query", queryFinal)
                                                    .queryParam("language", "pt-BR")
                                                    .queryParam("region", "BR")
                                                    .queryParam("include_adult", "false")
                                                    .build())
                            .retrieve()
                            .body(MovieSearchResponse.class);

            if (response == null || response.results() == null) {
                log.warn(
                        "⚠️ TMDB: resposta inválida para query='{}'. Retornando lista vazia.",
                        queryFinal);
                return new MovieSearchResponse(0, 0, 0, List.of());
            }

            log.info(
                    "✅ TMDB: Busca concluída query='{}' resultados={}",
                    queryFinal,
                    response.results().size());
            return response;
        } catch (Exception e) {
            log.error("❌ TMDB: erro na busca query='{}'", queryFinal, e);
            throw e;
        }
    }

    @Retryable(
            includes = Exception.class,
            maxRetries = 2,
            delay = 1000,
            multiplier = 2,
            maxDelay = 5000)
    @Cacheable(value = "tmdb-details", key = "#movieId", unless = "#result == null")
    public MovieRecord buscarDetalhes(Long movieId) {
        log.debug("TMDB: Buscando detalhes movieId={}", movieId);

        MovieRecord response =
                restClient
                        .get()
                        .uri(
                                uriBuilder ->
                                        uriBuilder
                                                .path("/movie/{id}")
                                                .queryParam("language", "pt-BR")
                                                .build(movieId))
                        .retrieve()
                        .body(MovieRecord.class);

        if (response == null) {
            log.error("❌ TMDB: resposta inválida ao buscar detalhes movieId={}", movieId);
            throw new IllegalStateException("Falha ao buscar detalhes do filme");
        }

        log.info("✅ TMDB: Detalhes obtidos movieId={} title={}", movieId, response.title());
        return response;
    }

    @Cacheable(value = "tmdb-credits", key = "#movieId", unless = "#result == null")
    @Retryable(includes = Exception.class, maxRetries = 1, delay = 500, multiplier = 2)
    public List<CastRecord> buscarElenco(Long movieId) {
        log.debug("TMDB: Buscando elenco movieId={}", movieId);

        CreditsResponse response =
                restClient
                        .get()
                        .uri("/movie/{id}/credits", movieId)
                        .retrieve()
                        .body(CreditsResponse.class);

        if (response == null || response.cast() == null) {
            log.warn("TMDB: Elenco não encontrado para movieId={}", movieId);
            return List.of();
        }

        log.info("✅ TMDB: Elenco obtido movieId={} castSize={}", movieId, response.cast().size());
        return response.cast();
    }

    @Retryable(includes = Exception.class, maxRetries = 1, delay = 500, multiplier = 2)
    @Cacheable(value = "tmdb-credits", key = "#movieId")
    public String buscarDiretor(Long movieId) {
        log.debug("TMDB: Buscando diretor para movieId={}", movieId);

        CreditsResponse response =
                restClient
                        .get()
                        .uri("/movie/{id}/credits", movieId)
                        .retrieve()
                        .body(CreditsResponse.class);

        if (response == null || response.crew() == null) {
            log.warn("TMDB: Créditos não encontrados para movieId={}", movieId);
            return null;
        }

        return response.crew().stream()
                .filter(member -> "Director".equals(member.job()))
                .map(CrewRecord::name)
                .findFirst()
                .orElse(null);
    }

    @Retryable(includes = Exception.class, maxRetries = 1, delay = 500, multiplier = 2)
    @Cacheable(
            value = "tmdb-providers",
            key = "#movieId",
            unless = "#result == null or #result == 'Indisponível no momento'")
    public String buscarOndeAssistir(Long movieId) {
        log.debug("TMDB: Verificando provedores movieId={}", movieId);

        WatchProviderResponse response =
                restClient
                        .get()
                        .uri("/movie/{id}/watch/providers", movieId)
                        .retrieve()
                        .body(WatchProviderResponse.class);

        if (response == null || response.results() == null) {
            log.warn("TMDB: Resposta inválida para provedores movieId={}", movieId);
            return "Indisponível no momento";
        }

        if (response.results().containsKey("BR")) {
            var brProviders = response.results().get("BR").flatrate();
            if (brProviders != null && !brProviders.isEmpty()) {
                String providers =
                        brProviders.stream()
                                .map(Provider::name)
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("");
                log.info(
                        "✅ TMDB: Provedores encontrados movieId={} providers={}",
                        movieId,
                        providers);
                return providers;
            }
        }

        log.warn("⚠️ TMDB: Nenhum provedor de streaming encontrado movieId={}", movieId);
        return "Disponível apenas para Aluguel/Compra (ou em um caminhão caído)";
    }
}
