package net.ddns.adambravo79.tmill.repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import net.ddns.adambravo79.tmill.model.SalaVipMovie;
import net.ddns.adambravo79.tmill.model.SalaVipSnapshot;

@Repository
@RequiredArgsConstructor
public class SalaVipRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<SalaVipMovie> MOVIE_ROW_MAPPER =
            (rs, rowNum) ->
                    new SalaVipMovie(
                            rs.getLong("id"),
                            rs.getLong("snapshot_id"),
                            rs.getObject("sala_vip_number", Integer.class),
                            rs.getObject("sala_vip_episode", Integer.class),
                            rs.getString("letterboxd_slug"),
                            rs.getString("title"),
                            rs.getObject("year", Integer.class),
                            rs.getString("letterboxd_url"),
                            rs.getString("note_text"));

    /**
     * Salva um snapshot completo com seus filmes.
     * Retorna o Snapshot salvo com o ID gerado.
     */
    public SalaVipSnapshot saveSnapshot(SalaVipSnapshot snapshot) {
        String insertSnapshotSql =
                """
                INSERT INTO sala_vip_snapshot (captured_at, item_count)
                VALUES (?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    // Especifica que queremos apenas a coluna "id" de volta
                    PreparedStatement ps =
                            connection.prepareStatement(insertSnapshotSql, new String[] {"id"});
                    ps.setObject(1, snapshot.capturedAt());
                    ps.setInt(2, snapshot.itemCount());
                    return ps;
                },
                keyHolder);

        Long snapshotId = keyHolder.getKey().longValue();

        // Insere os filmes em batch
        String insertMovieSql =
                """
                INSERT INTO sala_vip_movie
                (snapshot_id, sala_vip_number, sala_vip_episode, letterboxd_slug,
                 title, year, letterboxd_url, note_text)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.batchUpdate(
                insertMovieSql,
                snapshot.movies(),
                snapshot.movies().size(),
                (ps, movie) -> {
                    ps.setLong(1, snapshotId);
                    ps.setObject(2, movie.salaVipNumber());
                    ps.setObject(3, movie.salaVipEpisode());
                    ps.setString(4, movie.letterboxdSlug());
                    ps.setString(5, movie.title());
                    ps.setObject(6, movie.year());
                    ps.setString(7, movie.letterboxdUrl());
                    ps.setString(8, movie.noteText());
                });

        return new SalaVipSnapshot(
                snapshotId, snapshot.capturedAt(), snapshot.itemCount(), snapshot.movies());
    }

    /**
     * Pega o ID do último snapshot salvo.
     */
    public Optional<Long> findLatestSnapshotId() {
        String sql = "SELECT id FROM sala_vip_snapshot ORDER BY captured_at DESC LIMIT 1";
        List<Long> ids = jdbcTemplate.queryForList(sql, Long.class);
        return ids.stream().findFirst();
    }

    /**
     * Busca todos os filmes de um snapshot específico, ordenados por número.
     * Usado pelo loader para comparar o JSON com o último snapshot.
     */
    public List<SalaVipMovie> findMoviesBySnapshotId(Long snapshotId) {
        String sql =
                """
                SELECT * FROM sala_vip_movie
                WHERE snapshot_id = ?
                ORDER BY sala_vip_number
                """;
        return jdbcTemplate.query(sql, MOVIE_ROW_MAPPER, snapshotId);
    }

    /**
     * Busca filme por número no último snapshot.
     */
    public Optional<SalaVipMovie> findMovieByNumberInLatestSnapshot(Integer salaVipNumber) {
        String sql =
                """
                SELECT m.* FROM sala_vip_movie m
                JOIN sala_vip_snapshot s ON m.snapshot_id = s.id
                WHERE m.sala_vip_number = ?
                  AND s.id = (SELECT id FROM sala_vip_snapshot ORDER BY captured_at DESC LIMIT 1)
                LIMIT 1
                """;
        List<SalaVipMovie> results = jdbcTemplate.query(sql, MOVIE_ROW_MAPPER, salaVipNumber);
        return results.stream().findFirst();
    }

    /**
     * Busca filmes por episódio do RapaduraCast no último snapshot.
     * Pode retornar 2+ filmes (ex: Mad Max 1 e 2 no episódio 15).
     */
    public List<SalaVipMovie> findMoviesByEpisodeInLatestSnapshot(Integer episode) {
        String sql =
                """
                SELECT m.* FROM sala_vip_movie m
                JOIN sala_vip_snapshot s ON m.snapshot_id = s.id
                WHERE m.sala_vip_episode = ?
                  AND s.id = (SELECT id FROM sala_vip_snapshot ORDER BY captured_at DESC LIMIT 1)
                ORDER BY m.sala_vip_number
                """;
        return jdbcTemplate.query(sql, MOVIE_ROW_MAPPER, episode);
    }

    /**
     * Busca filmes por trecho do título OU da nota (sinopse) no último snapshot.
     */
    public List<SalaVipMovie> findMoviesByTitleInLatestSnapshot(String titleFragment) {
        String sql =
                """
                SELECT m.* FROM sala_vip_movie m
                JOIN sala_vip_snapshot s ON m.snapshot_id = s.id
                WHERE (lower(m.title) LIKE lower(?) OR lower(m.note_text) LIKE lower(?))
                  AND s.id = (SELECT id FROM sala_vip_snapshot ORDER BY captured_at DESC LIMIT 1)
                """;
        String searchPattern = "%" + titleFragment + "%";
        return jdbcTemplate.query(sql, MOVIE_ROW_MAPPER, searchPattern, searchPattern);
    }
}
