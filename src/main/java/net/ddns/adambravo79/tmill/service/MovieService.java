package net.ddns.adambravo79.tmill.service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.client.TmdbClient;
import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.exception.MovieNotFoundException;
import net.ddns.adambravo79.tmill.model.CastRecord;
import net.ddns.adambravo79.tmill.model.MovieOrchestrationResponse;
import net.ddns.adambravo79.tmill.model.MovieRecord;
import net.ddns.adambravo79.tmill.model.MovieSearchResponse;

/**
 * Serviço responsável por buscar e formatar informações de filmes via API do TMDB.
 *
 * <p>Principais responsabilidades: - Pesquisar filmes por nome. - Buscar detalhes completos por ID.
 * - Montar resposta formatada com título, ano, nota, elenco, sinopse e provedores de streaming.
 */
@Slf4j
@Service
public class MovieService {

    private final TmdbClient tmdbClient;
    private final EasterEggService easterEggService;

    public MovieService(TmdbClient tmdbClient, EasterEggService easterEggService) {
        this.tmdbClient = tmdbClient;
        this.easterEggService = easterEggService;
    }

    /**
     * Realiza a busca de filmes por nome.
     *
     * @param nome título do filme.
     * @return {@link MovieSearchResponse} com os resultados encontrados.
     * @throws MovieNotFoundException se nenhum resultado for encontrado.
     */
    public MovieSearchResponse buscarFilme(String nome) {
        String sanitized = nome.trim().replaceAll("[^\\p{L}\\p{N}\\s]", "");
        if (sanitized.length() < 3) {
            throw new MovieNotFoundException("Termo de busca muito curto: " + nome);
        }
        if (sanitized.length() > 100) {
            throw new MovieNotFoundException("Termo de busca muito longo: " + nome);
        }

        var busca = tmdbClient.pesquisarFilme(sanitized);
        if (busca == null || busca.results() == null || busca.results().isEmpty()) {
            throw new MovieNotFoundException(BotMessages.FILME_NAO_ENCONTRADO + ": " + nome);
        }
        return busca;
    }

    /**
     * Busca detalhes completos de um filme diretamente pelo ID no TMDB.
     *
     * @param id identificador único do filme no TMDB.
     * @return {@link MovieOrchestrationResponse} com texto formatado e URL do poster.
     * @throws MovieNotFoundException se os detalhes não forem encontrados.
     */
    @Cacheable(value = "movieDetails", key = "#id", unless = "#result == null")
    public MovieOrchestrationResponse buscarPorId(long id) {
        // Busca detalhes, elenco, diretor e provedores em paralelo
        CompletableFuture<MovieRecord> detalhesFuture =
                CompletableFuture.supplyAsync(() -> tmdbClient.buscarDetalhes(id));
        CompletableFuture<List<CastRecord>> elencoFuture =
                CompletableFuture.supplyAsync(() -> tmdbClient.buscarElenco(id));
        CompletableFuture<String> diretorFuture =
                CompletableFuture.supplyAsync(() -> tmdbClient.buscarDiretor(id));
        CompletableFuture<String> streamingsFuture =
                CompletableFuture.supplyAsync(() -> tmdbClient.buscarOndeAssistirFilme(id));

        CompletableFuture.allOf(detalhesFuture, elencoFuture, diretorFuture, streamingsFuture)
                .join();

        MovieRecord detalhes = detalhesFuture.join();
        if (detalhes == null) {
            throw new MovieNotFoundException(
                    BotMessages.FALHA_BUSCAR_DETALHES_FILME + " para ID: " + id);
        }

        // Formatação dos dados em métodos auxiliares para reduzir complexidade
        String ano = formatYear(detalhes);
        String bandeiras = formatFlags(detalhes);
        String elenco = formatCast(elencoFuture.join());
        String diretor = diretorFuture.join();
        String streamings = streamingsFuture.join();
        String easterEgg = easterEggService.getEasterEgg(id).map(egg -> "\n\n" + egg).orElse("");

        String textoHtml =
                buildResponseText(detalhes, diretor, elenco, ano, bandeiras, streamings, easterEgg);
        String urlPoster = buildPosterUrl(detalhes);

        return new MovieOrchestrationResponse(textoHtml, urlPoster);
    }

    // ========================= MÉTODOS AUXILIARES PARA FORMATAÇÃO =========================

    private String formatYear(MovieRecord detalhes) {
        if (detalhes.releaseDate() != null && detalhes.releaseDate().length() >= 4) {
            return detalhes.releaseDate().substring(0, 4);
        }
        return BotMessages.TBA;
    }

    private String formatFlags(MovieRecord detalhes) {
        if (detalhes.originCountry() == null || detalhes.originCountry().isEmpty()) {
            return BotMessages.GLOBE_EMOJI;
        }
        StringBuilder sb = new StringBuilder();
        for (String code : detalhes.originCountry()) {
            getFlagEmoji(code).ifPresent(sb::append);
        }
        return sb.isEmpty() ? BotMessages.GLOBE_EMOJI : sb.toString();
    }

    private String formatCast(List<CastRecord> castList) {
        int totalCast = castList.size();
        String castNames =
                castList.stream()
                        .limit(5)
                        .map(c -> c.name() != null ? c.name() : "")
                        .collect(Collectors.joining(", "));
        if (totalCast > 5) {
            castNames += " e mais " + (totalCast - 5) + " atores";
        }
        return castNames;
    }

    private String buildResponseText(
            MovieRecord detalhes,
            String diretor,
            String elenco,
            String ano,
            String bandeiras,
            String streamings,
            String easterEgg) {
        String diretorFinal = (diretor != null && !diretor.isBlank()) ? diretor : BotMessages.N_A;
        String linkTmdb = "https://www.themoviedb.org/movie/" + detalhes.id();

        return String.format(
                """
                🎬 <b>%s</b>
                <i>%s</i>
                📅 Ano: %s %s
                ⭐ <b>Nota:</b> <a href="%s">%.1f/10</a>

                🎬 <b>Diretor:</b> %s

                👥 <b>Elenco:</b> %s

                📖 <b>Sinopse:</b> %s

                📺 <b>Onde assistir:</b> %s%s
                """,
                detalhes.title().toUpperCase(),
                escapeHtml(detalhes.originalTitle() != null ? detalhes.originalTitle() : ""),
                ano,
                bandeiras,
                linkTmdb,
                detalhes.voteAverage(),
                diretorFinal,
                elenco,
                escapeHtml(detalhes.overview()),
                streamings,
                easterEgg);
    }

    private String buildPosterUrl(MovieRecord detalhes) {
        if (detalhes.posterPath() != null && !detalhes.posterPath().isBlank()) {
            return "https://image.tmdb.org/t/p/w500" + detalhes.posterPath();
        }
        return "";
    }

    /** Converte código de país ISO em emoji de bandeira. */
    private Optional<String> getFlagEmoji(String countryCode) {
        if (countryCode == null || countryCode.length() != 2) return Optional.empty();
        int firstLetter = Character.codePointAt(countryCode.toUpperCase(), 0) - 0x41 + 0x1F1E6;
        int secondLetter = Character.codePointAt(countryCode.toUpperCase(), 1) - 0x41 + 0x1F1E6;
        return Optional.of(
                new String(Character.toChars(firstLetter))
                        + new String(Character.toChars(secondLetter)));
    }

    /** Escapa caracteres especiais para evitar conflitos com Markdown. */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
