/* (c) 2026 | 25/05/2026 */
package net.ddns.adambravo79.tmill.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.client.TmdbClient;
import net.ddns.adambravo79.tmill.dto.TmdbDiscoverMovieResponse;
import net.ddns.adambravo79.tmill.dto.TmdbDiscoverTvResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReleasesService {

    private final TmdbClient tmdbClient;
    private static final int MAX_ITEMS_PER_DAY = 15; // limite por dia para não ficar gigante

    public String getWeeklyReleasesMessage() {
        try {
            LocalDate[] period = calculateThursdayPeriod();
            LocalDate start = period[0];
            LocalDate end = period[1];

            List<ReleaseItem> movies = fetchMovies(start, end);
            List<ReleaseItem> series = fetchSeries(start, end);

            List<ReleaseItem> all = new ArrayList<>();
            all.addAll(movies);
            all.addAll(series);

            // Filtra datas nulas e fora do período
            List<ReleaseItem> filtered =
                    all.stream()
                            .filter(item -> item.getReleaseDate() != null)
                            .filter(
                                    item ->
                                            !item.getReleaseDate().isBefore(start)
                                                    && !item.getReleaseDate().isAfter(end))
                            .collect(Collectors.toList());

            if (filtered.isEmpty()) {
                return "Nenhum lançamento encontrado para esta semana.";
            }

            // Ordena por data (e depois por título para consistência)
            filtered.sort(
                    Comparator.comparing(ReleaseItem::getReleaseDate)
                            .thenComparing(ReleaseItem::getTitle));

            // Agrupa por data
            Map<LocalDate, List<ReleaseItem>> groupedByDate =
                    filtered.stream()
                            .collect(
                                    Collectors.groupingBy(
                                            ReleaseItem::getReleaseDate,
                                            LinkedHashMap::new,
                                            Collectors.toList()));

            return formatMessage(start, end, groupedByDate);
        } catch (Exception e) {
            log.error("Erro ao obter estreias da semana", e);
            return "❌ Erro ao consultar lançamentos. Tente novamente mais tarde.";
        }
    }

    LocalDate[] calculateThursdayPeriod() {
        LocalDate now = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        LocalDate currentThursday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.THURSDAY));
        LocalDate nextThursday = currentThursday.plusDays(7);
        log.info("Período: {} a {}", currentThursday, nextThursday);
        return new LocalDate[] {currentThursday, nextThursday};
    }

    private List<ReleaseItem> fetchMovies(LocalDate start, LocalDate end) {
        TmdbDiscoverMovieResponse response =
                tmdbClient.discoverMoviesByDate(start.toString(), end.toString());
        if (response == null || response.results() == null) return List.of();
        return response.results().stream()
                .map(m -> new ReleaseItem(m.title(), m.release_date(), "movie"))
                .collect(Collectors.toList());
    }

    private List<ReleaseItem> fetchSeries(LocalDate start, LocalDate end) {
        TmdbDiscoverTvResponse response =
                tmdbClient.discoverTvByDate(start.toString(), end.toString());
        if (response == null || response.results() == null) return List.of();
        return response.results().stream()
                .map(t -> new ReleaseItem(t.name(), t.first_air_date(), "tv"))
                .collect(Collectors.toList());
    }

    private String formatMessage(
            LocalDate start, LocalDate end, Map<LocalDate, List<ReleaseItem>> groupedByDate) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM");
        DateTimeFormatter dayOfWeekFormatter =
                DateTimeFormatter.ofPattern("EEEE", new Locale("pt", "BR"));
        String period = start.format(dateFormatter) + " – " + end.format(dateFormatter);

        StringBuilder sb = new StringBuilder();
        sb.append("🎞️ | <b>Estreias da Semana</b>\n\n");
        sb.append("Confira os principais lançamentos entre ").append(period).append(".\n\n");

        for (Map.Entry<LocalDate, List<ReleaseItem>> entry : groupedByDate.entrySet()) {
            LocalDate date = entry.getKey();
            List<ReleaseItem> items = entry.getValue();
            String dayOfWeek = date.format(dayOfWeekFormatter);
            String formattedDate = date.format(dateFormatter);

            sb.append("🗓️ <b>")
                    .append(formattedDate)
                    .append(" (")
                    .append(dayOfWeek)
                    .append(")</b>\n");

            // Limita a quantidade por dia para não sobrecarregar
            List<ReleaseItem> toShow = items.stream().limit(MAX_ITEMS_PER_DAY).toList();
            for (ReleaseItem item : toShow) {
                sb.append("▪️ ").append(item.getTitle());
                if ("tv".equals(item.getType())) sb.append(" (série)");
                sb.append("\n");
            }
            if (items.size() > MAX_ITEMS_PER_DAY) {
                sb.append("▪️ ... e mais ")
                        .append(items.size() - MAX_ITEMS_PER_DAY)
                        .append(" títulos neste dia\n");
            }
            sb.append("\n");
        }

        sb.append(
                "📌 <i>Disponibilidade em streaming ainda não confirmada para lançamentos"
                        + " futuros.</i>\n");
        sb.append(
                "📅 Fonte: TMDB · Calendário completo no <a"
                        + " href=\"https://filmow.com/calendario/\">Filmow</a>");
        return sb.toString();
    }

    private static class ReleaseItem {
        private final String title;
        private final LocalDate releaseDate;
        private final String type;

        ReleaseItem(String title, String dateStr, String type) {
            this.title = title;
            this.releaseDate =
                    (dateStr != null && !dateStr.isBlank()) ? LocalDate.parse(dateStr) : null;
            this.type = type;
        }

        public String getTitle() {
            return title;
        }

        public LocalDate getReleaseDate() {
            return releaseDate;
        }

        public String getType() {
            return type;
        }
    }
}
