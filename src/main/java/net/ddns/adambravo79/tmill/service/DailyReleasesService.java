package net.ddns.adambravo79.tmill.service;

import static net.ddns.adambravo79.tmill.constant.BotMessages.CHAT_ID_INVALIDO;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.client.TmdbClient;
import net.ddns.adambravo79.tmill.client.WatchmodeClient;
import net.ddns.adambravo79.tmill.dto.MovieResult;
import net.ddns.adambravo79.tmill.dto.TvResult;
import net.ddns.adambravo79.tmill.model.FullRelease;
import net.ddns.adambravo79.tmill.model.MovieRecord;
import net.ddns.adambravo79.tmill.repository.ReleaseNotifiedRepository;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyReleasesService {

    private final TmdbClient tmdbClient;
    private final WatchmodeClient watchmodeClient;
    private final TelegramFacade telegramFacade;
    private final ReleaseNotifiedRepository releaseRepository;

    @Value("${digest.chat-ids:}")
    private String chatIdsStr;

    private static final ZoneId BRAZIL_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final int MAX_RESULTS_PER_HOUR = 15;
    private static final String MEDIA_TYPE_MOVIE = "movie";
    private static final String MEDIA_TYPE_TV = "tv";

    // Cache local para provedores (evita chamadas repetidas na mesma execução)
    private final Cache<String, String> providerCache =
            Caffeine.newBuilder().expireAfterWrite(24, TimeUnit.HOURS).maximumSize(500).build();

    // =================== NOTIFICAÇÃO A CADA 6 HORAS ===================
    @Scheduled(cron = "0 0 */6 * * *") // a cada 6 horas no minuto 0
    public void sendHourlyReleases() {
        log.info("⏰ Executando verificação de lançamentos (6 em 6 horas)...");
        if (chatIdsStr == null || chatIdsStr.isBlank()) {
            log.warn("Nenhum chat configurado para lançamentos.");
            return;
        }

        LocalDate today = LocalDate.now(BRAZIL_ZONE);
        List<ReleaseItem> newReleases = fetchNewReleasesForDate(today);

        if (newReleases.isEmpty()) {
            log.info("Nenhum novo lançamento encontrado nesta rodada.");
            return;
        }

        for (ReleaseItem item : newReleases) {
            // Só salva se tiver provedor válido
            if (item.providers != null
                    && !item.providers.isBlank()
                    && !"Indisponível".equalsIgnoreCase(item.providers.trim())) {
                releaseRepository.saveFullRelease(
                        item.id,
                        item.type,
                        today,
                        item.title,
                        item.overview,
                        item.rating,
                        item.providers,
                        item.posterPath);
                sendReleaseToAllChats(item);
            } else {
                log.debug("Lançamento sem provedor válido, ignorado: {}", item.title);
            }
        }
        log.info("✅ {} lançamentos notificados nesta rodada.", newReleases.size());
    }

    // =================== GIRO SEMANAL ===================
    @Scheduled(cron = "0 30 18 * * 4")
    public void sendWeeklyDigest() {
        log.info("📅 Gerando giro semanal dos streamings...");
        if (chatIdsStr == null || chatIdsStr.isBlank()) return;

        LocalDate today = LocalDate.now(BRAZIL_ZONE);
        LocalDate lastThursday = today.minusWeeks(1).with(java.time.DayOfWeek.THURSDAY);

        List<FullRelease> releases = releaseRepository.findFullReleasesBetween(lastThursday, today);

        if (releases.isEmpty()) {
            String msg = "📅 Nenhum lançamento novo nos streamings nesta semana.";
            for (String idStr : chatIdsStr.split(",")) {
                try {
                    long chatId = Long.parseLong(idStr.trim());
                    telegramFacade.enviarMensagemHtml(chatId, msg);
                } catch (NumberFormatException e) {
                    log.warn(CHAT_ID_INVALIDO, idStr);
                }
            }
            return;
        }

        List<ReleaseItem> items =
                releases.stream()
                        .map(
                                r ->
                                        new ReleaseItem(
                                                r.tmdbId(),
                                                r.title(),
                                                r.overview(),
                                                r.rating(),
                                                r.providers(),
                                                r.mediaType(),
                                                r.posterPath()))
                        .toList();

        String digest = buildWeeklyDigest(items, lastThursday, today);
        for (String idStr : chatIdsStr.split(",")) {
            try {
                long chatId = Long.parseLong(idStr.trim());
                telegramFacade.enviarMensagemHtml(chatId, digest);
            } catch (NumberFormatException e) {
                log.warn("Chat ID inválido: {}", idStr);
            }
        }
        log.info("✅ Giro semanal enviado com {} lançamentos.", items.size());
    }

    // =================== MÉTODOS PRIVADOS ===================

    private List<ReleaseItem> fetchNewReleasesForDate(LocalDate date) {
        List<ReleaseItem> all = new ArrayList<>();
        all.addAll(fetchNewMovies(date));
        all.addAll(fetchNewTvShows(date));
        return all.stream().limit(MAX_RESULTS_PER_HOUR).toList();
    }

    private List<ReleaseItem> fetchNewMovies(LocalDate date) {
        List<ReleaseItem> result = new ArrayList<>();
        try {
            var response = tmdbClient.discoverMoviesByDate(date.toString(), date.toString());
            if (response != null && response.results() != null) {
                for (MovieResult mr : response.results()) {
                    if (!releaseRepository.isNotified(mr.id(), MEDIA_TYPE_MOVIE, date)) {
                        String providers = getProvidersWithCache(mr.id(), MEDIA_TYPE_MOVIE);
                        if (providers != null
                                && StringUtils.hasText(providers)
                                && !providers.isBlank()) {
                            MovieRecord details = tmdbClient.buscarDetalhes(mr.id());
                            result.add(buildMovieItem(mr, details, providers));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Erro ao buscar filmes do dia", e);
        }
        return result;
    }

    private List<ReleaseItem> fetchNewTvShows(LocalDate date) {
        List<ReleaseItem> result = new ArrayList<>();
        try {
            var response = tmdbClient.discoverTvByDate(date.toString(), date.toString());
            if (response != null && response.results() != null) {
                for (TvResult tv : response.results()) {
                    if (!releaseRepository.isNotified(tv.id(), MEDIA_TYPE_TV, date)) {
                        String providers = getProvidersWithCache(tv.id(), MEDIA_TYPE_TV);
                        if (providers != null
                                && StringUtils.hasText(providers)
                                && !providers.isBlank()) {
                            result.add(
                                    new ReleaseItem(
                                            tv.id(),
                                            tv.name(),
                                            "",
                                            0.0,
                                            providers,
                                            MEDIA_TYPE_TV,
                                            null));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Erro ao buscar séries do dia", e);
        }
        return result;
    }

    /** Obtém provedores do Watchmode com cache de 24 horas. */
    private String getProvidersWithCache(long tmdbId, String type) {
        String key = type + "_" + tmdbId;
        return providerCache.get(
                key,
                k -> {
                    String providers = watchmodeClient.getProviders(tmdbId, type);
                    return providers != null ? providers : "Indisponível";
                });
    }

    private String buildWeeklyDigest(List<ReleaseItem> items, LocalDate from, LocalDate to) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>📅 GIRO DOS STREAMINGS</b>\n");
        sb.append("Lançamentos da semana de ")
                .append(from.format(DateTimeFormatter.ofPattern("dd/MM")))
                .append(" a ")
                .append(to.format(DateTimeFormatter.ofPattern("dd/MM")))
                .append("\n\n");

        for (ReleaseItem item : items) {
            String tipo = MEDIA_TYPE_MOVIE.equals(item.type) ? "🎬 Filme" : "📺 Série";
            sb.append("▪️ <b>").append(item.title).append("</b>\n");
            sb.append("   ").append(tipo).append("\n");
            sb.append("   📺 ").append(item.providers).append("\n");
            if (item.rating > 0) {
                sb.append("   ⭐ ").append(String.format("%.1f/10", item.rating)).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private ReleaseItem buildMovieItem(MovieResult mr, MovieRecord details, String providers) {
        String overview = details.overview() != null ? details.overview() : "";
        if (overview.length() > 300) {
            overview = overview.substring(0, 300) + "...";
        }
        return new ReleaseItem(
                mr.id(),
                mr.title(),
                overview,
                details.voteAverage() != null ? details.voteAverage() : 0.0,
                providers,
                MEDIA_TYPE_MOVIE,
                details.posterPath());
    }

    private void sendReleaseToAllChats(ReleaseItem item) {
        String caption = buildCaption(item);
        String posterUrl =
                item.posterPath != null
                        ? "https://image.tmdb.org/t/p/w500" + item.posterPath
                        : null;

        for (String idStr : chatIdsStr.split(",")) {
            try {
                long chatId = Long.parseLong(idStr.trim());
                if (posterUrl != null && !posterUrl.isBlank()) {
                    telegramFacade.enviarFotoHtml(chatId, posterUrl, caption);
                } else {
                    telegramFacade.enviarMensagemHtml(chatId, caption);
                }
            } catch (NumberFormatException e) {
                log.warn("Chat ID inválido: {}", idStr);
            }
        }
    }

    private String buildCaption(ReleaseItem item) {
        String nota = item.rating > 0 ? String.format("%.1f/10", item.rating) : "N/A";
        return String.format(
                """
        <b>%s</b>

        <b>JÁ DISPONÍVEL</b>

        ⭐ %s
        👩‍🎓 %s

        ⭐ <b>Nota:</b> %s

        📺 <b>Onde assistir:</b> %s
        """,
                item.title,
                item.title,
                item.overview != null && !item.overview.isBlank()
                        ? item.overview
                        : "Sinopse indisponível",
                nota,
                item.providers);
    }

    private record ReleaseItem(
            long id,
            String title,
            String overview,
            double rating,
            String providers,
            String type,
            String posterPath) {}
}
