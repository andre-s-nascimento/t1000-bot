package net.ddns.adambravo79.tmill.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.model.FullRelease;

@Repository
@RequiredArgsConstructor
@Slf4j
public class ReleaseNotifiedRepository {

    private final JdbcTemplate jdbcTemplate;

    // Método que aceita LocalDate (converte para String internamente)
    public boolean isNotified(long tmdbId, String mediaType, LocalDate releaseDate) {
        String sql =
                "SELECT COUNT(*) FROM releases_notified WHERE tmdb_id = ? AND media_type = ? AND"
                        + " release_date = ?";
        Integer count =
                jdbcTemplate.queryForObject(
                        sql, Integer.class, tmdbId, mediaType, releaseDate.toString());
        return count != null && count > 0;
    }

    // Salva apenas o ID (versão simplificada, mantida para compatibilidade)
    public void saveNotified(long tmdbId, String mediaType, LocalDate releaseDate) {
        String sql =
                "INSERT INTO releases_notified (tmdb_id, media_type, release_date) VALUES (?, ?,"
                        + " ?)";
        jdbcTemplate.update(sql, tmdbId, mediaType, releaseDate.toString());
        log.debug(
                "Notificação registrada: tmdbId={}, type={}, date={}",
                tmdbId,
                mediaType,
                releaseDate);
    }

    // Salva os dados completos do lançamento (usado no DailyReleasesService)
    public void saveFullRelease(
            long tmdbId,
            String mediaType,
            LocalDate releaseDate,
            String title,
            String overview,
            Double rating,
            String providers,
            String posterPath) {
        String sql =
                """
            INSERT INTO releases_notified
            (tmdb_id, media_type, release_date, title, overview, rating, providers, poster_path)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;
        jdbcTemplate.update(
                sql,
                tmdbId,
                mediaType,
                releaseDate.toString(),
                title,
                overview,
                rating,
                providers,
                posterPath);
        log.debug("Lançamento completo salvo: tmdbId={}, title={}", tmdbId, title);
    }

    // Busca lançamentos completos entre duas datas
    public List<FullRelease> findFullReleasesBetween(LocalDate from, LocalDate to) {
        String sql =
                """
            SELECT tmdb_id, media_type, release_date, title, overview, rating, providers, poster_path
            FROM releases_notified
            WHERE date(notified_at) BETWEEN ? AND ?
            ORDER BY notified_at ASC
        """;
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) ->
                        new FullRelease(
                                rs.getLong("tmdb_id"),
                                rs.getString("media_type"),
                                LocalDate.parse(rs.getString("release_date")),
                                rs.getString("title"),
                                rs.getString("overview"),
                                rs.getDouble("rating"),
                                rs.getString("providers"),
                                rs.getString("poster_path")),
                from.toString(),
                to.toString());
    }

    public void clearAll() {
        jdbcTemplate.execute("DELETE FROM releases_notified");
        log.info("Tabela releases_notified limpa.");
    }

    public int deleteAll() {
        return jdbcTemplate.update("DELETE FROM releases_notified");
    }
}
