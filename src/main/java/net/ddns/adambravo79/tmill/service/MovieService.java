/* (c) 2026 | 06/05/2026 */
package net.ddns.adambravo79.tmill.service;

import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.client.TmdbClient;
import net.ddns.adambravo79.tmill.exception.MovieNotFoundException;
import net.ddns.adambravo79.tmill.model.CastRecord;
import net.ddns.adambravo79.tmill.model.MovieOrchestrationResponse;
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
        // NOVO: sanitização básica
        String sanitized = nome.trim().replaceAll("[^\\p{L}\\p{N}\\s]", "");
        if (sanitized.length() < 3) {
            throw new MovieNotFoundException("Termo de busca muito curto: " + nome);
        }
        if (sanitized.length() > 100) {
            throw new MovieNotFoundException("Termo de busca muito longo: " + nome);
        }

        var busca = tmdbClient.pesquisarFilme(sanitized);
        if (busca == null || busca.results() == null || busca.results().isEmpty()) {
            throw new MovieNotFoundException("Filme não encontrado: " + nome);
        }
        return busca;
    }

    /**
     * Executa a busca formatada aplicando lógica de desambiguação automática.
     *
     * @param nome título do filme.
     * @return {@link MovieOrchestrationResponse} com texto formatado e URL do poster.
     */
    public MovieOrchestrationResponse executarBuscaFormatada(String nome) {
        var busca = buscarFilme(nome);
        var basico = busca.results().get(0);
        return buscarPorId(basico.id());
    }

    /**
     * Busca detalhes completos de um filme diretamente pelo ID no TMDB.
     *
     * @param id identificador único do filme no TMDB.
     * @return {@link MovieOrchestrationResponse} com texto formatado e URL do poster.
     * @throws MovieNotFoundException se os detalhes não forem encontrados.
     */
    public MovieOrchestrationResponse buscarPorId(long id) {
        var detalhes = tmdbClient.buscarDetalhes(id);
        if (detalhes == null) {
            throw new MovieNotFoundException("Detalhes do filme não encontrados para ID: " + id);
        }

        // Elenco (top 5)
        var elenco =
                tmdbClient.buscarElenco(id).stream()
                        .limit(5)
                        .map(CastRecord::name)
                        .collect(Collectors.joining(", "));

        // Diretor (apenas nome, sem link)
        String diretor = tmdbClient.buscarDiretor(id);
        String directorLine =
                (diretor != null && !diretor.isBlank())
                        ? "🎬 *Diretor:* " + diretor + "\n"
                        : "\n"; // se não houver diretor, deixa uma linha em branco

        var streamings = tmdbClient.buscarOndeAssistir(id);

        String ano =
                (detalhes.releaseDate() != null && detalhes.releaseDate().length() >= 4)
                        ? detalhes.releaseDate().substring(0, 4)
                        : "TBA";

        String bandeiras = "🌐";
        if (detalhes.originCountry() != null && !detalhes.originCountry().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String code : detalhes.originCountry()) {
                getFlagEmoji(code).ifPresent(sb::append);
            }
            if (!sb.isEmpty()) bandeiras = sb.toString();
        }

        String linkTmdb = "https://www.themoviedb.org/movie/" + detalhes.id();

        String textoHtml =
                String.format(
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
                        escapeHtml(
                                detalhes.originalTitle() != null ? detalhes.originalTitle() : ""),
                        ano,
                        bandeiras,
                        linkTmdb,
                        detalhes.voteAverage(),
                        (diretor != null && !diretor.isBlank()) ? diretor : "N/A",
                        elenco,
                        escapeHtml(detalhes.overview()),
                        streamings,
                        easterEggService.getEasterEgg(id).map(egg -> "\n\n" + egg).orElse(""));

        String urlPoster =
                (detalhes.posterPath() != null && !detalhes.posterPath().isBlank())
                        ? "https://image.tmdb.org/t/p/w500" + detalhes.posterPath()
                        : "";

        return new MovieOrchestrationResponse(textoHtml, urlPoster);
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
