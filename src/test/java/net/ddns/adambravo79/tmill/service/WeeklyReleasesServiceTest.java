package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import net.ddns.adambravo79.tmill.client.TmdbClient;
import net.ddns.adambravo79.tmill.dto.MovieResult;
import net.ddns.adambravo79.tmill.dto.TmdbDiscoverMovieResponse;
import net.ddns.adambravo79.tmill.dto.TmdbDiscoverTvResponse;
import net.ddns.adambravo79.tmill.dto.TvResult;

@ExtendWith(MockitoExtension.class)
class WeeklyReleasesServiceTest {

    @Mock private TmdbClient tmdbClient;
    @InjectMocks private WeeklyReleasesService service;

    private static final LocalDate THURSDAY = LocalDate.of(2026, Month.JULY, 9);
    private static final LocalDate NEXT_THURSDAY = THURSDAY.plusDays(7);

    @Test
    void getWeeklyReleasesMessage_deveRetornarMensagemComLançamentos() {
        WeeklyReleasesService spyService = spy(service);
        doReturn(new LocalDate[] {THURSDAY, NEXT_THURSDAY})
                .when(spyService)
                .calculateThursdayPeriod();

        // Objetos reais (records)
        MovieResult movie1 = new MovieResult(1, "Filme A", "2026-07-09", 8.0);
        MovieResult movie2 = new MovieResult(2, "Filme B", "2026-07-10", 7.5);
        TmdbDiscoverMovieResponse movieResponse =
                new TmdbDiscoverMovieResponse(1, List.of(movie1, movie2), 1, 2);

        TvResult tv1 = new TvResult(3, "Série X", "2026-07-09", 9.0);
        TvResult tv2 = new TvResult(4, "Série Y", "2026-07-11", 8.5);
        TmdbDiscoverTvResponse tvResponse = new TmdbDiscoverTvResponse(1, List.of(tv1, tv2), 1, 2);

        when(tmdbClient.discoverMoviesByDate(THURSDAY.toString(), NEXT_THURSDAY.toString()))
                .thenReturn(movieResponse);
        when(tmdbClient.discoverTvByDate(THURSDAY.toString(), NEXT_THURSDAY.toString()))
                .thenReturn(tvResponse);

        String result = spyService.getWeeklyReleasesMessage();

        assertThat(result)
                .contains("Estreias da Semana")
                .contains("Filme A")
                .contains("Filme B")
                .contains("Série X (série)")
                .contains("Série Y (série)")
                .contains("09/07")
                .contains("10/07")
                .contains("11/07");
    }

    @Test
    void getWeeklyReleasesMessage_deveRetornarMensagemSemLançamentos() {
        WeeklyReleasesService spyService = spy(service);
        doReturn(new LocalDate[] {THURSDAY, NEXT_THURSDAY})
                .when(spyService)
                .calculateThursdayPeriod();

        TmdbDiscoverMovieResponse movieResponse = mock(TmdbDiscoverMovieResponse.class);
        when(movieResponse.results()).thenReturn(List.of());

        TmdbDiscoverTvResponse tvResponse = mock(TmdbDiscoverTvResponse.class);
        when(tvResponse.results()).thenReturn(List.of());

        when(tmdbClient.discoverMoviesByDate(anyString(), anyString())).thenReturn(movieResponse);
        when(tmdbClient.discoverTvByDate(anyString(), anyString())).thenReturn(tvResponse);

        String result = spyService.getWeeklyReleasesMessage();
        assertThat(result).isEqualTo("Nenhum lançamento encontrado para esta semana.");
    }

    @Test
    void getWeeklyReleasesMessage_deveTratarExcecao() {
        WeeklyReleasesService spyService = spy(service);
        doReturn(new LocalDate[] {THURSDAY, NEXT_THURSDAY})
                .when(spyService)
                .calculateThursdayPeriod();

        when(tmdbClient.discoverMoviesByDate(anyString(), anyString()))
                .thenThrow(new RuntimeException("Erro na API"));

        String result = spyService.getWeeklyReleasesMessage();
        assertThat(result).contains("❌ Erro ao consultar lançamentos");
    }

    @Test
    void getWeeklyReleasesMessage_deveLimitarItensPorDia() {
        WeeklyReleasesService spyService = spy(service);
        doReturn(new LocalDate[] {THURSDAY, NEXT_THURSDAY})
                .when(spyService)
                .calculateThursdayPeriod();

        List<MovieResult> movies =
                IntStream.rangeClosed(1, 16)
                        .mapToObj(
                                i -> {
                                    MovieResult m = mock(MovieResult.class);
                                    when(m.title()).thenReturn("Filme " + i);
                                    when(m.release_date()).thenReturn("2026-07-09");
                                    return m;
                                })
                        .toList();

        TmdbDiscoverMovieResponse movieResponse = mock(TmdbDiscoverMovieResponse.class);
        when(movieResponse.results()).thenReturn(movies);

        TmdbDiscoverTvResponse tvResponse = mock(TmdbDiscoverTvResponse.class);
        when(tvResponse.results()).thenReturn(List.of());

        when(tmdbClient.discoverMoviesByDate(anyString(), anyString())).thenReturn(movieResponse);
        when(tmdbClient.discoverTvByDate(anyString(), anyString())).thenReturn(tvResponse);

        String result = spyService.getWeeklyReleasesMessage();

        assertThat(result).doesNotContain("Filme 9").contains("... e mais 1 títulos neste dia");
    }
}
