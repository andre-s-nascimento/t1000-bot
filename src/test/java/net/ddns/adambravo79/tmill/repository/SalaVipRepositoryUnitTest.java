package net.ddns.adambravo79.tmill.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import net.ddns.adambravo79.tmill.model.SalaVipMovie;

@SuppressWarnings("unchecked") // Mockito + varargs genéricos do JdbcTemplate
@ExtendWith(MockitoExtension.class)
class SalaVipRepositoryUnitTest {

    @Mock JdbcTemplate jdbcTemplate;
    @InjectMocks SalaVipRepository repository;

    @Test
    void findMoviesByTitleInLatestSnapshot_deveBuscarPorTituloENota() {
        List<SalaVipMovie> esperado =
                List.of(
                        new SalaVipMovie(
                                1L,
                                1L,
                                118,
                                117,
                                "signs",
                                "Signs",
                                2002,
                                "https://letterboxd.com/film/signs/",
                                "Um fazendeiro encontra sinais misteriosos."));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any()))
                .thenReturn(esperado);

        List<SalaVipMovie> resultado = repository.findMoviesByTitleInLatestSnapshot("sinais");

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).title()).isEqualTo("Signs");

        verify(jdbcTemplate)
                .query(
                        contains("OR lower(m.note_text)"),
                        any(RowMapper.class),
                        eq("%sinais%"),
                        eq("%sinais%"));
    }

    @Test
    void findMovieByNumberInLatestSnapshot_deveRetornarVazioSeNaoEncontrar() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(99))).thenReturn(List.of());

        assertThat(repository.findMovieByNumberInLatestSnapshot(99)).isEmpty();
    }

    @Test
    void findMoviesByEpisodeInLatestSnapshot_deveRetornarLista() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(15))).thenReturn(List.of());

        assertThat(repository.findMoviesByEpisodeInLatestSnapshot(15)).isEmpty();
    }

    @Test
    void findMoviesByEpisodeInLatestSnapshot_deveBuscarPorEpisodio() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(16))).thenReturn(List.of());
        repository.findMoviesByEpisodeInLatestSnapshot(16);
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), eq(16));
    }
}
