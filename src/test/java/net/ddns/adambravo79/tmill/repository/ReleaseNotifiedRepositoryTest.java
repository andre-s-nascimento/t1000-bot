package net.ddns.adambravo79.tmill.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import net.ddns.adambravo79.tmill.model.FullRelease;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked"}) // Necessário para mocks de RowMapper genérico
class ReleaseNotifiedRepositoryTest {

    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks private ReleaseNotifiedRepository repository;

    private static final long TMDB_ID = 123L;
    private static final String MEDIA_TYPE = "movie";
    private static final LocalDate RELEASE_DATE = LocalDate.of(2026, Month.JULY, 15);
    private static final String TITLE = "Filme Teste";
    private static final String OVERVIEW = "Sinopse";
    private static final Double RATING = 8.5;
    private static final String PROVIDERS = "Netflix, Prime";
    private static final String POSTER_PATH = "/poster.jpg";

    // ===================== isNotified =====================

    @Test
    void isNotified_quandoExiste_retornaTrue() {
        when(jdbcTemplate.queryForObject(
                        anyString(), eq(Integer.class), anyLong(), anyString(), anyString()))
                .thenReturn(1);

        boolean result = repository.isNotified(TMDB_ID, MEDIA_TYPE, RELEASE_DATE);

        assertThat(result).isTrue();
        verify(jdbcTemplate)
                .queryForObject(
                        contains("SELECT COUNT(*)"),
                        eq(Integer.class),
                        eq(TMDB_ID),
                        eq(MEDIA_TYPE),
                        eq(RELEASE_DATE.toString()));
    }

    @Test
    void isNotified_quandoNaoExiste_retornaFalse() {
        when(jdbcTemplate.queryForObject(
                        anyString(), eq(Integer.class), anyLong(), anyString(), anyString()))
                .thenReturn(0);

        boolean result = repository.isNotified(TMDB_ID, MEDIA_TYPE, RELEASE_DATE);

        assertThat(result).isFalse();
    }

    @Test
    void isNotified_quandoCountNulo_retornaFalse() {
        when(jdbcTemplate.queryForObject(
                        anyString(), eq(Integer.class), anyLong(), anyString(), anyString()))
                .thenReturn(null);

        boolean result = repository.isNotified(TMDB_ID, MEDIA_TYPE, RELEASE_DATE);

        assertThat(result).isFalse();
    }

    // ===================== saveNotified =====================

    @Test
    void saveNotified_deveInserirRegistro() {
        when(jdbcTemplate.update(anyString(), anyLong(), anyString(), anyString())).thenReturn(1);

        repository.saveNotified(TMDB_ID, MEDIA_TYPE, RELEASE_DATE);

        verify(jdbcTemplate)
                .update(
                        contains("INSERT INTO releases_notified"),
                        eq(TMDB_ID),
                        eq(MEDIA_TYPE),
                        eq(RELEASE_DATE.toString()));
    }

    // ===================== saveFullRelease =====================

    @Test
    void saveFullRelease_deveInserirDadosCompletos() {
        when(jdbcTemplate.update(
                        anyString(),
                        anyLong(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyDouble(),
                        anyString(),
                        anyString()))
                .thenReturn(1);

        repository.saveFullRelease(
                TMDB_ID, MEDIA_TYPE, RELEASE_DATE, TITLE, OVERVIEW, RATING, PROVIDERS, POSTER_PATH);

        verify(jdbcTemplate)
                .update(
                        contains("INSERT INTO releases_notified"),
                        eq(TMDB_ID),
                        eq(MEDIA_TYPE),
                        eq(RELEASE_DATE.toString()),
                        eq(TITLE),
                        eq(OVERVIEW),
                        eq(RATING),
                        eq(PROVIDERS),
                        eq(POSTER_PATH));
    }

    @Test
    void saveFullRelease_comRatingNulo_deveInserirComNull() {
        when(jdbcTemplate.update(
                        anyString(),
                        anyLong(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        isNull(),
                        anyString(),
                        anyString()))
                .thenReturn(1);

        repository.saveFullRelease(
                TMDB_ID, MEDIA_TYPE, RELEASE_DATE, TITLE, OVERVIEW, null, PROVIDERS, POSTER_PATH);

        verify(jdbcTemplate)
                .update(
                        contains("INSERT INTO releases_notified"),
                        eq(TMDB_ID),
                        eq(MEDIA_TYPE),
                        eq(RELEASE_DATE.toString()),
                        eq(TITLE),
                        eq(OVERVIEW),
                        isNull(),
                        eq(PROVIDERS),
                        eq(POSTER_PATH));
    }

    // ===================== findFullReleasesBetween =====================

    @Test
    void findFullReleasesBetween_deveRetornarLista() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("tmdb_id")).thenReturn(TMDB_ID);
        when(rs.getString("media_type")).thenReturn(MEDIA_TYPE);
        when(rs.getString("release_date")).thenReturn(RELEASE_DATE.toString());
        when(rs.getString("title")).thenReturn(TITLE);
        when(rs.getString("overview")).thenReturn(OVERVIEW);
        when(rs.getDouble("rating")).thenReturn(RATING);
        when(rs.getString("providers")).thenReturn(PROVIDERS);
        when(rs.getString("poster_path")).thenReturn(POSTER_PATH);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString(), anyString()))
                .thenAnswer(
                        invocation -> {
                            RowMapper<FullRelease> mapper = invocation.getArgument(1);
                            return List.of(mapper.mapRow(rs, 1));
                        });

        LocalDate from = RELEASE_DATE.minusDays(1);
        LocalDate to = RELEASE_DATE.plusDays(1);

        List<FullRelease> results = repository.findFullReleasesBetween(from, to);

        assertThat(results).hasSize(1);
        FullRelease release = results.get(0);
        assertThat(release.tmdbId()).isEqualTo(TMDB_ID);
        assertThat(release.mediaType()).isEqualTo(MEDIA_TYPE);
        assertThat(release.releaseDate()).isEqualTo(RELEASE_DATE);
        assertThat(release.title()).isEqualTo(TITLE);
        assertThat(release.overview()).isEqualTo(OVERVIEW);
        assertThat(release.rating()).isEqualTo(RATING);
        assertThat(release.providers()).isEqualTo(PROVIDERS);
        assertThat(release.posterPath()).isEqualTo(POSTER_PATH);

        verify(jdbcTemplate)
                .query(
                        contains("SELECT tmdb_id"),
                        any(RowMapper.class),
                        eq(from.toString()),
                        eq(to.toString()));
    }

    @Test
    void findFullReleasesBetween_quandoNenhumResultado_retornaListaVazia() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString(), anyString()))
                .thenReturn(List.of());

        List<FullRelease> results =
                repository.findFullReleasesBetween(
                        RELEASE_DATE.minusDays(1), RELEASE_DATE.plusDays(1));

        assertThat(results).isEmpty();
    }

    // ===================== clearAll =====================

    @Test
    void clearAll_deveExecutarDelete() {
        repository.clearAll();
        verify(jdbcTemplate).execute("DELETE FROM releases_notified");
    }

    // ===================== deleteAll =====================

    @Test
    void deleteAll_deveRetornarNumeroDeLinhasDeletadas() {
        when(jdbcTemplate.update("DELETE FROM releases_notified")).thenReturn(5);

        int deleted = repository.deleteAll();

        assertThat(deleted).isEqualTo(5);
        verify(jdbcTemplate).update("DELETE FROM releases_notified");
    }

    @Test
    void deleteAll_quandoNenhumRegistro_retornaZero() {
        when(jdbcTemplate.update("DELETE FROM releases_notified")).thenReturn(0);

        int deleted = repository.deleteAll();

        assertThat(deleted).isZero();
        verify(jdbcTemplate).update("DELETE FROM releases_notified");
    }
}
