package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import net.ddns.adambravo79.tmill.client.TmdbClient;
import net.ddns.adambravo79.tmill.dto.MovieResult;
import net.ddns.adambravo79.tmill.dto.TmdbDiscoverMovieResponse;
import net.ddns.adambravo79.tmill.dto.TmdbDiscoverTvResponse;
import net.ddns.adambravo79.tmill.dto.TvResult;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;

@ExtendWith(MockitoExtension.class)
class WeeklyReleasesServiceTest {

    @Mock private TmdbClient tmdbClient;
    @Mock private MetricsService metricsService; // 👈 NOVO

    private WeeklyReleasesService service;

    private static final LocalDate THURSDAY = LocalDate.of(2026, Month.JULY, 9);
    private static final LocalDate NEXT_THURSDAY = THURSDAY.plusDays(7);

    @BeforeEach
    void setUp() {
        // Instancia manualmente para garantir 2 args (cobre o construtor @RequiredArgsConstructor)
        service = new WeeklyReleasesService(tmdbClient, metricsService);
    }

    // ============================================================
    // SUCESSO — métrica `estreias_enviadas`
    // ============================================================

    @Test
    @DisplayName("Com lançamentos: monta mensagem e registra success('estreias_enviadas')")
    void comLancamentos_registraMetricaSuccess() {
        WeeklyReleasesService spyService = spy(service);
        doReturn(new LocalDate[] {THURSDAY, NEXT_THURSDAY})
                .when(spyService)
                .calculateThursdayPeriod();

        MovieResult m1 = new MovieResult(1, "Filme A", "2026-07-09", 8.0);
        MovieResult m2 = new MovieResult(2, "Filme B", "2026-07-10", 7.5);
        TmdbDiscoverMovieResponse movieResponse =
                new TmdbDiscoverMovieResponse(1, List.of(m1, m2), 1, 2);

        TvResult t1 = new TvResult(3, "Série X", "2026-07-09", 9.0);
        TvResult t2 = new TvResult(4, "Série Y", "2026-07-11", 8.5);
        TmdbDiscoverTvResponse tvResponse = new TmdbDiscoverTvResponse(1, List.of(t1, t2), 1, 2);

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

        verify(metricsService).success("estreias_enviadas");
        verify(metricsService, never()).error(anyString());
    }

    // ============================================================
    // VAZIO — métrica `estreias_vazias`
    // ============================================================

    @Test
    @DisplayName("Sem lançamentos: mensagem amigável e registra error('estreias_vazias')")
    void semLancamentos_registraMetricaVazia() {
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
        verify(metricsService).error("estreias_vazias");
        verify(metricsService, never()).success("estreias_enviadas");
    }

    // ============================================================
    // ERRO — métrica `estreias_erro`
    // ============================================================

    @Test
    @DisplayName("Exceção na API: mensagem de erro e registra error('estreias_erro')")
    void excecaoApi_registraMetricaErro() {
        WeeklyReleasesService spyService = spy(service);
        doReturn(new LocalDate[] {THURSDAY, NEXT_THURSDAY})
                .when(spyService)
                .calculateThursdayPeriod();

        when(tmdbClient.discoverMoviesByDate(anyString(), anyString()))
                .thenThrow(new RuntimeException("Erro na API"));

        String result = spyService.getWeeklyReleasesMessage();

        assertThat(result).contains("❌ Erro ao consultar lançamentos");
        verify(metricsService).error("estreias_erro");
        verify(metricsService, never()).success("estreias_enviadas");
    }

    // ============================================================
    // LIMITE POR DIA
    // ============================================================

    @Test
    @DisplayName("Limite por dia: mostra '... e mais N títulos'")
    void limitePorDia() {
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

        verify(metricsService).success("estreias_enviadas");
    }

    // ============================================================
    // CASOS DE BORDA
    // ============================================================

    @Test
    @DisplayName("Item com data null é ignorado")
    void itemComDataNull_ignorado() {
        WeeklyReleasesService spyService = spy(service);
        doReturn(new LocalDate[] {THURSDAY, NEXT_THURSDAY})
                .when(spyService)
                .calculateThursdayPeriod();

        MovieResult movieNull = new MovieResult(1, "Filme Null", null, 0.0);
        MovieResult movieValido = new MovieResult(2, "Filme Válido", "2026-07-09", 8.0);
        TmdbDiscoverMovieResponse movieResponse =
                new TmdbDiscoverMovieResponse(1, List.of(movieNull, movieValido), 1, 2);
        TmdbDiscoverTvResponse tvResponse = new TmdbDiscoverTvResponse(1, List.of(), 1, 0);

        when(tmdbClient.discoverMoviesByDate(THURSDAY.toString(), NEXT_THURSDAY.toString()))
                .thenReturn(movieResponse);
        when(tmdbClient.discoverTvByDate(THURSDAY.toString(), NEXT_THURSDAY.toString()))
                .thenReturn(tvResponse);

        String result = spyService.getWeeklyReleasesMessage();

        assertThat(result).contains("Filme Válido").doesNotContain("Filme Null");
        verify(metricsService).success("estreias_enviadas");
    }

    @Test
    @DisplayName("Resposta de filmes null: cai em 'estreias_vazias'")
    void movieResponseNull() {
        WeeklyReleasesService spyService = spy(service);
        doReturn(new LocalDate[] {THURSDAY, NEXT_THURSDAY})
                .when(spyService)
                .calculateThursdayPeriod();

        when(tmdbClient.discoverMoviesByDate(THURSDAY.toString(), NEXT_THURSDAY.toString()))
                .thenReturn(null);
        TmdbDiscoverTvResponse tvResponse = new TmdbDiscoverTvResponse(1, List.of(), 1, 0);
        when(tmdbClient.discoverTvByDate(THURSDAY.toString(), NEXT_THURSDAY.toString()))
                .thenReturn(tvResponse);

        String result = spyService.getWeeklyReleasesMessage();
        assertThat(result).isEqualTo("Nenhum lançamento encontrado para esta semana.");
        verify(metricsService).error("estreias_vazias");
    }

    @Test
    @DisplayName("Resposta de séries null: só filmes, ainda registra success")
    void tvResponseNull() {
        WeeklyReleasesService spyService = spy(service);
        doReturn(new LocalDate[] {THURSDAY, NEXT_THURSDAY})
                .when(spyService)
                .calculateThursdayPeriod();

        MovieResult movie = new MovieResult(1, "Filme Teste", "2026-07-09", 8.0);
        TmdbDiscoverMovieResponse movieResponse =
                new TmdbDiscoverMovieResponse(1, List.of(movie), 1, 1);
        when(tmdbClient.discoverMoviesByDate(THURSDAY.toString(), NEXT_THURSDAY.toString()))
                .thenReturn(movieResponse);
        when(tmdbClient.discoverTvByDate(THURSDAY.toString(), NEXT_THURSDAY.toString()))
                .thenReturn(null);

        String result = spyService.getWeeklyReleasesMessage();
        assertThat(result).contains("Filme Teste").doesNotContain("série");
        verify(metricsService).success("estreias_enviadas");
    }

    @Test
    @DisplayName("results() null do movie response: cai em 'estreias_vazias'")
    void movieResultsNull() {
        WeeklyReleasesService spyService = spy(service);
        doReturn(new LocalDate[] {THURSDAY, NEXT_THURSDAY})
                .when(spyService)
                .calculateThursdayPeriod();

        TmdbDiscoverMovieResponse movieResponse = mock(TmdbDiscoverMovieResponse.class);
        when(movieResponse.results()).thenReturn(null);
        TmdbDiscoverTvResponse tvResponse = new TmdbDiscoverTvResponse(1, List.of(), 1, 0);

        when(tmdbClient.discoverMoviesByDate(THURSDAY.toString(), NEXT_THURSDAY.toString()))
                .thenReturn(movieResponse);
        when(tmdbClient.discoverTvByDate(THURSDAY.toString(), NEXT_THURSDAY.toString()))
                .thenReturn(tvResponse);

        String result = spyService.getWeeklyReleasesMessage();
        assertThat(result).isEqualTo("Nenhum lançamento encontrado para esta semana.");
        verify(metricsService).error("estreias_vazias");
    }

    @Test
    @DisplayName("results() null do tv response: só filmes, registra success")
    void tvResultsNull() {
        WeeklyReleasesService spyService = spy(service);
        doReturn(new LocalDate[] {THURSDAY, NEXT_THURSDAY})
                .when(spyService)
                .calculateThursdayPeriod();

        MovieResult movie = new MovieResult(1, "Filme Teste", "2026-07-09", 8.0);
        TmdbDiscoverMovieResponse movieResponse =
                new TmdbDiscoverMovieResponse(1, List.of(movie), 1, 1);
        TmdbDiscoverTvResponse tvResponse = mock(TmdbDiscoverTvResponse.class);
        when(tvResponse.results()).thenReturn(null);

        when(tmdbClient.discoverMoviesByDate(THURSDAY.toString(), NEXT_THURSDAY.toString()))
                .thenReturn(movieResponse);
        when(tmdbClient.discoverTvByDate(THURSDAY.toString(), NEXT_THURSDAY.toString()))
                .thenReturn(tvResponse);

        String result = spyService.getWeeklyReleasesMessage();
        assertThat(result).contains("Filme Teste").doesNotContain("série");
        verify(metricsService).success("estreias_enviadas");
    }

    @Test
    @DisplayName("Item fora do período é filtrado")
    void itemForaDoPeriodo() {
        WeeklyReleasesService spyService = spy(service);
        doReturn(new LocalDate[] {THURSDAY, NEXT_THURSDAY})
                .when(spyService)
                .calculateThursdayPeriod();

        // Item com data muito antes do período
        MovieResult antes = new MovieResult(1, "Antes", "2026-06-01", 5.0);
        // Item com data dentro do período
        MovieResult dentro = new MovieResult(2, "Dentro", "2026-07-10", 8.0);
        // Item com data muito depois
        MovieResult depois = new MovieResult(3, "Depois", "2026-08-01", 7.0);

        TmdbDiscoverMovieResponse movieResponse =
                new TmdbDiscoverMovieResponse(1, List.of(antes, dentro, depois), 1, 3);
        TmdbDiscoverTvResponse tvResponse = new TmdbDiscoverTvResponse(1, List.of(), 1, 0);

        when(tmdbClient.discoverMoviesByDate(THURSDAY.toString(), NEXT_THURSDAY.toString()))
                .thenReturn(movieResponse);
        when(tmdbClient.discoverTvByDate(THURSDAY.toString(), NEXT_THURSDAY.toString()))
                .thenReturn(tvResponse);

        String result = spyService.getWeeklyReleasesMessage();

        assertThat(result).contains("Dentro").doesNotContain("Antes").doesNotContain("Depois");
        verify(metricsService).success("estreias_enviadas");
    }

    @Test
    @DisplayName("Todos os itens fora do período: cai em 'estreias_vazias'")
    void todosForaDoPeriodo() {
        WeeklyReleasesService spyService = spy(service);
        doReturn(new LocalDate[] {THURSDAY, NEXT_THURSDAY})
                .when(spyService)
                .calculateThursdayPeriod();

        MovieResult antes = new MovieResult(1, "Antes", "2026-06-01", 5.0);
        MovieResult depois = new MovieResult(2, "Depois", "2026-08-01", 7.0);
        TmdbDiscoverMovieResponse movieResponse =
                new TmdbDiscoverMovieResponse(1, List.of(antes, depois), 1, 2);
        TmdbDiscoverTvResponse tvResponse = new TmdbDiscoverTvResponse(1, List.of(), 1, 0);

        when(tmdbClient.discoverMoviesByDate(THURSDAY.toString(), NEXT_THURSDAY.toString()))
                .thenReturn(movieResponse);
        when(tmdbClient.discoverTvByDate(THURSDAY.toString(), NEXT_THURSDAY.toString()))
                .thenReturn(tvResponse);

        String result = spyService.getWeeklyReleasesMessage();
        assertThat(result).isEqualTo("Nenhum lançamento encontrado para esta semana.");
        verify(metricsService).error("estreias_vazias");
    }

    // ============================================================
    // MÉTRICAS — contagem em múltiplas chamadas
    // ============================================================

    @Test
    @DisplayName("Sucesso em 3 chamadas: 3 successes e 0 errors")
    void sucessoMultiplasVezes() {
        WeeklyReleasesService spyService = spy(service);
        doReturn(new LocalDate[] {THURSDAY, NEXT_THURSDAY})
                .when(spyService)
                .calculateThursdayPeriod();

        MovieResult m = new MovieResult(1, "Filme", "2026-07-09", 8.0);
        TmdbDiscoverMovieResponse movieResponse =
                new TmdbDiscoverMovieResponse(1, List.of(m), 1, 1);
        TmdbDiscoverTvResponse tvResponse = new TmdbDiscoverTvResponse(1, List.of(), 1, 0);

        when(tmdbClient.discoverMoviesByDate(anyString(), anyString())).thenReturn(movieResponse);
        when(tmdbClient.discoverTvByDate(anyString(), anyString())).thenReturn(tvResponse);

        spyService.getWeeklyReleasesMessage();
        spyService.getWeeklyReleasesMessage();
        spyService.getWeeklyReleasesMessage();

        verify(metricsService, times(3)).success("estreias_enviadas");
        verify(metricsService, never()).error(anyString());
    }

    @Test
    @DisplayName("Vazio em 2 chamadas: 2 errors 'estreias_vazias'")
    void vazioMultiplasVezes() {
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

        spyService.getWeeklyReleasesMessage();
        spyService.getWeeklyReleasesMessage();

        verify(metricsService, times(2)).error("estreias_vazias");
        verify(metricsService, never()).success("estreias_enviadas");
    }

    @Test
    @DisplayName("Erro do TMDB: 1 error 'estreias_erro' e nenhum success")
    void erroTmdb_registraMetricaEspecifica() {
        WeeklyReleasesService spyService = spy(service);
        doReturn(new LocalDate[] {THURSDAY, NEXT_THURSDAY})
                .when(spyService)
                .calculateThursdayPeriod();

        when(tmdbClient.discoverMoviesByDate(anyString(), anyString()))
                .thenThrow(new RuntimeException("TMDB down"));

        spyService.getWeeklyReleasesMessage();

        verify(metricsService).error("estreias_erro");
        verify(metricsService, never()).error("estreias_vazias");
        verify(metricsService, never()).success(anyString());
    }

    @Test
    @DisplayName("Exceção no calculateThursdayPeriod: registra 'estreias_erro'")
    void excecaoNoCalculate_registraErro() {
        WeeklyReleasesService spyService = spy(service);
        doReturn(null).when(spyService).calculateThursdayPeriod();

        String result = spyService.getWeeklyReleasesMessage();

        assertThat(result).contains("❌ Erro ao consultar lançamentos");
        verify(metricsService).error("estreias_erro");
    }

    // ============================================================
    // CÁLCULO DE PERÍODO
    // ============================================================

    @Test
    @DisplayName("calculateThursdayPeriod: retorna quinta e quinta seguinte")
    void calculateThursdayPeriod_retornaQuintaEProxima() {
        LocalDate[] period = service.calculateThursdayPeriod();
        assertThat(period).hasSize(2);
        assertThat(period[0].getDayOfWeek().getValue()).isEqualTo(DayOfWeek.THURSDAY.getValue());
        assertThat(period[1].minusDays(7)).isEqualTo(period[0]);
    }

    // ============================================================
    // MÉTRICAS — caminho que NÃO deve registrar
    // ============================================================

    @Test
    @DisplayName("Exceção no meio do processamento: nenhuma métrica de success")
    void nenhumSuccessEmExcecao() {
        WeeklyReleasesService spyService = spy(service);
        doReturn(new LocalDate[] {THURSDAY, NEXT_THURSDAY})
                .when(spyService)
                .calculateThursdayPeriod();

        // Falha na segunda chamada (depois de já ter buscado filmes)
        MovieResult m = new MovieResult(1, "Filme", "2026-07-09", 8.0);
        TmdbDiscoverMovieResponse movieResponse =
                new TmdbDiscoverMovieResponse(1, List.of(m), 1, 1);
        when(tmdbClient.discoverMoviesByDate(anyString(), anyString())).thenReturn(movieResponse);
        when(tmdbClient.discoverTvByDate(anyString(), anyString()))
                .thenThrow(new RuntimeException("boom"));

        spyService.getWeeklyReleasesMessage();

        verify(metricsService, never()).success(anyString());
        verify(metricsService).error("estreias_erro");
    }

    // ============================================================
    // VERIFICAÇÕES FINAIS — nenhuma interação inesperada
    // ============================================================

    @Test
    @DisplayName("calculateThursdayPeriod sozinho: não interage com MetricsService")
    void calculateThursdayPeriod_naoInterageComMetrics() {
        service.calculateThursdayPeriod();
        verifyNoInteractions(metricsService);
    }
}
