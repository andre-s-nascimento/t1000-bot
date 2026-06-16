/* (c) 2026 | 25/05/2026 */
package net.ddns.adambravo79.tmill.client;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.dto.TmdbDiscoverMovieResponse;
import net.ddns.adambravo79.tmill.dto.TmdbDiscoverTvResponse;
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
            @Value("${groq.connect-timeout:5s}") Duration connectTimeout,
            @Value("${groq.read-timeout:30s}") Duration readTimeout) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        this.restClient =
                RestClient.builder()
                        .baseUrl(apiUrl)
                        .defaultHeader("Authorization", "Bearer " + tmdbToken)
                        .defaultHeader("accept", "application/json")
                        .requestFactory(factory)
                        .build();
    }

    // Construtor para testes
    public TmdbClient(RestClient restClient) {
        this.restClient = restClient;
    }

    // ------------------------------------------------------------------------
    // Métodos de busca com retry (já existentes)
    // ------------------------------------------------------------------------

    @Retryable(
            includes = Exception.class,
            maxRetries = 2,
            delay = 1000,
            multiplier = 2,
            maxDelay = 5000)
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

    // ------------------------------------------------------------------------
    // NOVOS MÉTODOS PARA DESCOBERTA DE LANÇAMENTOS (sem filtro de provedores)
    // Conforme API Reference: /discover/movie e /discover/tv
    // ------------------------------------------------------------------------

    /**
     * Busca filmes que estreiam entre as datas informadas (sem filtrar por provedor).
     *
     * @param gte data inicial (YYYY-MM-DD)
     * @param lte data final (YYYY-MM-DD)
     * @return resposta contendo lista de filmes
     */
    public TmdbDiscoverMovieResponse discoverMoviesByDate(String gte, String lte) {
        log.info("TMDB: Buscando filmes entre {} e {} (sem filtro de provedor)", gte, lte);
        return restClient
                .get()
                .uri(
                        uriBuilder ->
                                uriBuilder
                                        .path("/discover/movie")
                                        .queryParam("language", "pt-BR")
                                        .queryParam("region", "BR")
                                        .queryParam("release_date.gte", gte)
                                        .queryParam("release_date.lte", lte)
                                        .queryParam("sort_by", "release_date.asc")
                                        .queryParam("page", 1)
                                        .build())
                .retrieve()
                .body(TmdbDiscoverMovieResponse.class);
    }

    /**
     * Busca séries com primeira exibição entre as datas informadas (sem filtrar por provedor).
     *
     * @param gte data inicial (YYYY-MM-DD)
     * @param lte data final (YYYY-MM-DD)
     * @return resposta contendo lista de séries
     */
    public TmdbDiscoverTvResponse discoverTvByDate(String gte, String lte) {
        log.info("TMDB: Buscando séries entre {} e {} (sem filtro de provedor)", gte, lte);
        return restClient
                .get()
                .uri(
                        uriBuilder ->
                                uriBuilder
                                        .path("/discover/tv")
                                        .queryParam("language", "pt-BR")
                                        .queryParam("first_air_date.gte", gte)
                                        .queryParam("first_air_date.lte", lte)
                                        .queryParam("sort_by", "first_air_date.asc")
                                        .queryParam("page", 1)
                                        .build())
                .retrieve()
                .body(TmdbDiscoverTvResponse.class);
    }

    // ------------------------------------------------------------------------
    // MÉTODOS PARA OBTER WATCH PROVIDERS (com tratamento de 404)
    // Conforme API Reference: /movie/{id}/watch/providers e /tv/{id}/watch/providers
    // ------------------------------------------------------------------------

    /**
     * Retorna string com nomes dos provedores de streaming para o filme no Brasil. Exemplo de
     * retorno: "Netflix, Prime Video" ou "Indisponível no momento".
     */
    public String buscarOndeAssistirFilme(Long movieId) {
        return buscarWatchProvider("/movie/{id}/watch/providers", movieId);
    }

    /**
     * Retorna string com nomes dos provedores de streaming para a série no Brasil. Exemplo de
     * retorno: "Disney+, Star+" ou "Indisponível no momento".
     */
    public String buscarOndeAssistirSerie(Long tvId) {
        return buscarWatchProvider("/tv/{id}/watch/providers", tvId);
    }

    private String buscarWatchProvider(String path, Long id) {
        log.debug("TMDB: Verificando provedores {} id={}", path, id);
        try {
            WatchProviderResponse response =
                    restClient.get().uri(path, id).retrieve().body(WatchProviderResponse.class);

            if (response == null || response.results() == null) {
                log.warn("Resposta inválida para {} id={}", path, id);
                return "Indisponível no momento";
            }

            // Verifica se existe entry para o Brasil
            if (response.results().containsKey("BR")) {
                var brProviders = response.results().get("BR").flatrate();
                if (brProviders != null && !brProviders.isEmpty()) {
                    String providers =
                            brProviders.stream()
                                    .map(Provider::name)
                                    .reduce((a, b) -> a + ", " + b)
                                    .orElse("");
                    log.info("✅ Provedores encontrados id={} providers={}", id, providers);
                    return providers;
                }
            }
            log.warn("⚠️ Nenhum provedor de streaming encontrado para {} id={}", path, id);
            return "Indisponível no momento";
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Watch providers não encontrados para {} id={} (404)", path, id);
            return "Indisponível no momento";
        } catch (Exception e) {
            log.error("Erro ao buscar provedores para {} id={}", path, id, e);
            return "Indisponível no momento";
        }
    }

    // ------------------------------------------------------------------------
    // MÉTODO LEGADO (para compatibilidade)
    // ------------------------------------------------------------------------
    public String buscarOndeAssistir(Long movieId) {
        return buscarOndeAssistirFilme(movieId);
    }

    // ------------------------------------------------------------------------
    // MÉTODO OPCIONAL: LISTAR PROVEDORES DISPONÍVEIS NO BRASIL
    // Útil para debug ou para obter IDs atualizados
    // ------------------------------------------------------------------------
    public WatchProvidersResponse listarProvedoresFilmes() {
        return restClient
                .get()
                .uri(
                        uriBuilder ->
                                uriBuilder
                                        .path("/watch/providers/movie")
                                        .queryParam("language", "pt-BR")
                                        .queryParam("watch_region", "BR")
                                        .build())
                .retrieve()
                .body(WatchProvidersResponse.class);
    }
}
