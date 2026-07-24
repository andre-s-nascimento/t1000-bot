package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.github.benmanes.caffeine.cache.Cache;

import net.ddns.adambravo79.tmill.client.TmdbClient;
import net.ddns.adambravo79.tmill.client.WatchmodeClient;
import net.ddns.adambravo79.tmill.dto.MovieResult;
import net.ddns.adambravo79.tmill.dto.TmdbDiscoverMovieResponse;
import net.ddns.adambravo79.tmill.dto.TmdbDiscoverTvResponse;
import net.ddns.adambravo79.tmill.dto.TvResult;
import net.ddns.adambravo79.tmill.model.FullRelease;
import net.ddns.adambravo79.tmill.model.MovieRecord;
import net.ddns.adambravo79.tmill.repository.ReleaseNotifiedRepository;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;

@ExtendWith(MockitoExtension.class)
class DailyReleasesServiceTest {

    @Mock private TmdbClient tmdbClient;
    @Mock private WatchmodeClient watchmodeClient;
    @Mock private TelegramFacade telegramFacade;
    @Mock private ReleaseNotifiedRepository releaseRepository;
    @Mock private Cache<String, String> providerCache;

    @InjectMocks private DailyReleasesService service;

    private static final String CHAT_IDS = "123,456";
    private static final ZoneId BRAZIL_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final String MOVIE_TYPE = "movie";
    private static final String TV_TYPE = "tv";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "chatIdsStr", CHAT_IDS);
        ReflectionTestUtils.setField(service, "providerCache", providerCache);
    }

    // ===================== sendHourlyReleases =====================

    @Test
    void sendHourlyReleases_quandoChatIdsVazio_naoFazNada() {
        ReflectionTestUtils.setField(service, "chatIdsStr", "");
        service.sendHourlyReleases();
        verifyNoInteractions(tmdbClient, watchmodeClient, telegramFacade, releaseRepository);
    }

    @Test
    void sendHourlyReleases_quandoChatIdsNulo_naoFazNada() {
        ReflectionTestUtils.setField(service, "chatIdsStr", null);
        service.sendHourlyReleases();
        verifyNoInteractions(tmdbClient, watchmodeClient, telegramFacade, releaseRepository);
    }

    @Test
    void sendHourlyReleases_quandoNenhumLancamento_naoEnvia() {
        TmdbDiscoverMovieResponse movieResponse = mock(TmdbDiscoverMovieResponse.class);
        when(movieResponse.results()).thenReturn(List.of());
        when(tmdbClient.discoverMoviesByDate(anyString(), anyString())).thenReturn(movieResponse);

        TmdbDiscoverTvResponse tvResponse = mock(TmdbDiscoverTvResponse.class);
        when(tvResponse.results()).thenReturn(List.of());
        when(tmdbClient.discoverTvByDate(anyString(), anyString())).thenReturn(tvResponse);

        service.sendHourlyReleases();

        verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
        verify(releaseRepository, never())
                .saveFullRelease(
                        anyLong(),
                        anyString(),
                        any(),
                        anyString(),
                        anyString(),
                        anyDouble(),
                        anyString(),
                        anyString());
    }

    @Test
    void sendHourlyReleases_comLancamentoSemProvedor_ignora() {
        // Mock para retornar um filme
        MovieResult movieResult =
                new MovieResult(1L, "Filme Teste", LocalDate.now().toString(), 7.5);
        TmdbDiscoverMovieResponse movieResponse = mock(TmdbDiscoverMovieResponse.class);
        when(movieResponse.results()).thenReturn(List.of(movieResult));
        when(tmdbClient.discoverMoviesByDate(anyString(), anyString())).thenReturn(movieResponse);
        // Sem séries
        TmdbDiscoverTvResponse tvResponse = mock(TmdbDiscoverTvResponse.class);
        when(tvResponse.results()).thenReturn(List.of());
        when(tmdbClient.discoverTvByDate(anyString(), anyString())).thenReturn(tvResponse);

        LocalDate today = LocalDate.now(BRAZIL_ZONE);
        when(releaseRepository.isNotified(eq(1L), eq(MOVIE_TYPE), eq(today))).thenReturn(false);

        // Cache retorna null (provedor indisponível)
        when(providerCache.get(eq("movie_1"), any())).thenReturn(null);

        service.sendHourlyReleases();

        verify(tmdbClient, never()).buscarDetalhes(anyLong());
        verify(releaseRepository, never())
                .saveFullRelease(
                        anyLong(),
                        anyString(),
                        any(),
                        anyString(),
                        anyString(),
                        anyDouble(),
                        anyString(),
                        anyString());
        verify(telegramFacade, never()).enviarFotoHtml(anyLong(), anyString(), anyString());
        verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
    }

    @Test
    void sendHourlyReleases_comLancamentoComProvedor_enviaESalva() {
        // Mock de filme
        MovieResult movieResult =
                new MovieResult(1L, "Filme Teste", LocalDate.now().toString(), 7.5);
        TmdbDiscoverMovieResponse movieResponse = mock(TmdbDiscoverMovieResponse.class);
        when(movieResponse.results()).thenReturn(List.of(movieResult));
        when(tmdbClient.discoverMoviesByDate(anyString(), anyString())).thenReturn(movieResponse);

        // Sem séries
        TmdbDiscoverTvResponse tvResponse = mock(TmdbDiscoverTvResponse.class);
        when(tvResponse.results()).thenReturn(List.of());
        when(tmdbClient.discoverTvByDate(anyString(), anyString())).thenReturn(tvResponse);

        LocalDate today = LocalDate.now(BRAZIL_ZONE);
        when(releaseRepository.isNotified(eq(1L), eq(MOVIE_TYPE), eq(today))).thenReturn(false);

        // Cache retorna provedores
        when(providerCache.get(eq("movie_1"), any())).thenReturn("Netflix, Prime Video");

        MovieRecord details = mock(MovieRecord.class);
        when(details.overview()).thenReturn("Sinopse do filme");
        when(details.voteAverage()).thenReturn(8.5);
        when(details.posterPath()).thenReturn("/poster.jpg");
        when(tmdbClient.buscarDetalhes(1L)).thenReturn(details);

        service.sendHourlyReleases();

        verify(releaseRepository, times(1))
                .saveFullRelease(
                        eq(1L),
                        eq(MOVIE_TYPE),
                        eq(today),
                        eq("Filme Teste"),
                        eq("Sinopse do filme"),
                        eq(8.5),
                        eq("Netflix, Prime Video"),
                        eq("/poster.jpg"));

        verify(telegramFacade, times(1))
                .enviarFotoHtml(eq(123L), anyString(), contains("Filme Teste"));
        verify(telegramFacade, times(1))
                .enviarFotoHtml(eq(456L), anyString(), contains("Filme Teste"));
        verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
    }

    @Test
    void sendHourlyReleases_comLancamentoSemPoster_enviaMensagemTexto() {
        MovieResult movieResult =
                new MovieResult(1L, "Filme Sem Poster", LocalDate.now().toString(), 6.0);
        TmdbDiscoverMovieResponse movieResponse = mock(TmdbDiscoverMovieResponse.class);
        when(movieResponse.results()).thenReturn(List.of(movieResult));
        when(tmdbClient.discoverMoviesByDate(anyString(), anyString())).thenReturn(movieResponse);

        TmdbDiscoverTvResponse tvResponse = mock(TmdbDiscoverTvResponse.class);
        when(tvResponse.results()).thenReturn(List.of());
        when(tmdbClient.discoverTvByDate(anyString(), anyString())).thenReturn(tvResponse);

        LocalDate today = LocalDate.now(BRAZIL_ZONE);
        when(releaseRepository.isNotified(eq(1L), eq(MOVIE_TYPE), eq(today))).thenReturn(false);
        when(providerCache.get(eq("movie_1"), any())).thenReturn("Netflix");

        MovieRecord details = mock(MovieRecord.class);
        when(details.overview()).thenReturn("Sinopse");
        when(details.voteAverage()).thenReturn(6.0);
        when(details.posterPath()).thenReturn(null);
        when(tmdbClient.buscarDetalhes(1L)).thenReturn(details);

        service.sendHourlyReleases();

        verify(telegramFacade, times(1)).enviarMensagemHtml(eq(123L), contains("Filme Sem Poster"));
        verify(telegramFacade, times(1)).enviarMensagemHtml(eq(456L), contains("Filme Sem Poster"));
        verify(telegramFacade, never()).enviarFotoHtml(anyLong(), anyString(), anyString());
    }

    @Test
    void sendHourlyReleases_comErroAoBuscarFilmes_continua() {
        when(tmdbClient.discoverMoviesByDate(anyString(), anyString()))
                .thenThrow(new RuntimeException("Erro na API"));

        TmdbDiscoverTvResponse tvResponse = mock(TmdbDiscoverTvResponse.class);
        when(tvResponse.results()).thenReturn(List.of());
        when(tmdbClient.discoverTvByDate(anyString(), anyString())).thenReturn(tvResponse);

        service.sendHourlyReleases();

        verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
        verify(releaseRepository, never())
                .saveFullRelease(
                        anyLong(),
                        anyString(),
                        any(),
                        anyString(),
                        anyString(),
                        anyDouble(),
                        anyString(),
                        anyString());
    }

    @Test
    void sendHourlyReleases_comChatIdInvalido_ignoraELoga() {
        ReflectionTestUtils.setField(service, "chatIdsStr", "123,abc,456");

        MovieResult movieResult =
                new MovieResult(1L, "Filme Teste", LocalDate.now().toString(), 7.5);
        TmdbDiscoverMovieResponse movieResponse = mock(TmdbDiscoverMovieResponse.class);
        when(movieResponse.results()).thenReturn(List.of(movieResult));
        when(tmdbClient.discoverMoviesByDate(anyString(), anyString())).thenReturn(movieResponse);

        TmdbDiscoverTvResponse tvResponse = mock(TmdbDiscoverTvResponse.class);
        when(tvResponse.results()).thenReturn(List.of());
        when(tmdbClient.discoverTvByDate(anyString(), anyString())).thenReturn(tvResponse);

        LocalDate today = LocalDate.now(BRAZIL_ZONE);
        when(releaseRepository.isNotified(eq(1L), eq(MOVIE_TYPE), eq(today))).thenReturn(false);
        when(providerCache.get(eq("movie_1"), any())).thenReturn("Netflix");

        MovieRecord details = mock(MovieRecord.class);
        when(details.overview()).thenReturn("Sinopse");
        when(details.voteAverage()).thenReturn(8.0);
        when(details.posterPath()).thenReturn("/poster.jpg");
        when(tmdbClient.buscarDetalhes(1L)).thenReturn(details);

        service.sendHourlyReleases();

        // Deve enviar para 123 e 456, mas não para 'abc'
        verify(telegramFacade, times(2)).enviarFotoHtml(anyLong(), anyString(), anyString());
        // Nenhuma chamada com ID inválido (0L)
        verify(telegramFacade, never()).enviarFotoHtml(eq(0L), anyString(), anyString());
    }

    // ===================== sendWeeklyDigest =====================

    @Test
    void sendWeeklyDigest_quandoChatIdsVazio_naoFazNada() {
        ReflectionTestUtils.setField(service, "chatIdsStr", "");
        service.sendWeeklyDigest();
        verifyNoInteractions(releaseRepository, telegramFacade);
    }

    @Test
    void sendWeeklyDigest_quandoSemReleases_enviaMensagemVazia() {
        when(releaseRepository.findFullReleasesBetween(any(), any())).thenReturn(List.of());

        service.sendWeeklyDigest();

        verify(telegramFacade, times(1))
                .enviarMensagemHtml(eq(123L), contains("Nenhum lançamento"));
        verify(telegramFacade, times(1))
                .enviarMensagemHtml(eq(456L), contains("Nenhum lançamento"));
    }

    @Test
    void sendWeeklyDigest_comReleases_enviaDigest() {
        FullRelease release =
                new FullRelease(
                        1L,
                        "Filme Teste",
                        LocalDate.now(BRAZIL_ZONE),
                        "Sinopse do filme",
                        MOVIE_TYPE,
                        8.0,
                        "Netflix",
                        "/poster.jpg");
        when(releaseRepository.findFullReleasesBetween(any(), any())).thenReturn(List.of(release));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        service.sendWeeklyDigest();

        verify(telegramFacade, times(2)).enviarMensagemHtml(anyLong(), captor.capture());

        String mensagemEnviada = captor.getValue();
        // O código atual de produção usa overview como título no digest
        assertThat(mensagemEnviada).contains("Sinopse do filme");
        assertThat(mensagemEnviada).contains("GIRO DOS STREAMINGS");
        assertThat(mensagemEnviada).contains("Netflix");
        assertThat(mensagemEnviada).contains("8,0/10");
    }

    @Test
    void sendWeeklyDigest_comChatIdInvalido_ignoraELoga() {
        ReflectionTestUtils.setField(service, "chatIdsStr", "123,abc,456");

        FullRelease release =
                new FullRelease(
                        1L,
                        "Filme Teste",
                        LocalDate.now(BRAZIL_ZONE),
                        "Sinopse",
                        MOVIE_TYPE,
                        8.0,
                        "Netflix",
                        "/poster.jpg");
        when(releaseRepository.findFullReleasesBetween(any(), any())).thenReturn(List.of(release));

        service.sendWeeklyDigest();

        // Deve enviar para 123 e 456
        verify(telegramFacade, times(2))
                .enviarMensagemHtml(anyLong(), contains("GIRO DOS STREAMINGS"));
        // Nenhuma chamada com ID 0
        verify(telegramFacade, never()).enviarMensagemHtml(eq(0L), anyString());
    }

    // ===================== Testes para séries (cobertura adicional) =====================

    @Test
    void sendHourlyReleases_comSerieComProvedor_enviaESalva() {
        // Sem filmes
        TmdbDiscoverMovieResponse movieResponse = mock(TmdbDiscoverMovieResponse.class);
        when(movieResponse.results()).thenReturn(List.of());
        when(tmdbClient.discoverMoviesByDate(anyString(), anyString())).thenReturn(movieResponse);

        // Uma série
        TvResult tvResult = new TvResult(2L, "Serie Teste", LocalDate.now().toString(), 8.0);
        TmdbDiscoverTvResponse tvResponse = mock(TmdbDiscoverTvResponse.class);
        when(tvResponse.results()).thenReturn(List.of(tvResult));
        when(tmdbClient.discoverTvByDate(anyString(), anyString())).thenReturn(tvResponse);

        LocalDate today = LocalDate.now(BRAZIL_ZONE);
        when(releaseRepository.isNotified(eq(2L), eq(TV_TYPE), eq(today))).thenReturn(false);
        when(providerCache.get(eq("tv_2"), any())).thenReturn("Disney+");

        service.sendHourlyReleases();

        verify(releaseRepository, times(1))
                .saveFullRelease(
                        eq(2L),
                        eq(TV_TYPE),
                        eq(today),
                        eq("Serie Teste"),
                        eq(""), // overview vazio
                        eq(0.0), // rating 0 para séries
                        eq("Disney+"),
                        isNull());

        verify(telegramFacade, times(1)).enviarMensagemHtml(eq(123L), contains("Serie Teste"));
        verify(telegramFacade, times(1)).enviarMensagemHtml(eq(456L), contains("Serie Teste"));
    }

    @Test
    void sendHourlyReleases_comSerieJaNotificada_ignora() {
        TmdbDiscoverMovieResponse movieResponse = mock(TmdbDiscoverMovieResponse.class);
        when(movieResponse.results()).thenReturn(List.of());
        when(tmdbClient.discoverMoviesByDate(anyString(), anyString())).thenReturn(movieResponse);

        TvResult tvResult = new TvResult(2L, "Serie Teste", LocalDate.now().toString(), 8.0);
        TmdbDiscoverTvResponse tvResponse = mock(TmdbDiscoverTvResponse.class);
        when(tvResponse.results()).thenReturn(List.of(tvResult));
        when(tmdbClient.discoverTvByDate(anyString(), anyString())).thenReturn(tvResponse);

        LocalDate today = LocalDate.now(BRAZIL_ZONE);
        when(releaseRepository.isNotified(eq(2L), eq(TV_TYPE), eq(today))).thenReturn(true);

        service.sendHourlyReleases();

        verify(providerCache, never()).get(anyString(), any());
        verify(releaseRepository, never())
                .saveFullRelease(
                        anyLong(),
                        anyString(),
                        any(),
                        anyString(),
                        anyString(),
                        anyDouble(),
                        anyString(),
                        anyString());
        verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
    }

    @Test
    void sendHourlyReleases_comErroAoBuscarSeries_continua() {
        TmdbDiscoverMovieResponse movieResponse = mock(TmdbDiscoverMovieResponse.class);
        when(movieResponse.results()).thenReturn(List.of());
        when(tmdbClient.discoverMoviesByDate(anyString(), anyString())).thenReturn(movieResponse);

        when(tmdbClient.discoverTvByDate(anyString(), anyString()))
                .thenThrow(new RuntimeException("Erro na API"));

        service.sendHourlyReleases();

        verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
        verify(releaseRepository, never())
                .saveFullRelease(
                        anyLong(),
                        anyString(),
                        any(),
                        anyString(),
                        anyString(),
                        anyDouble(),
                        anyString(),
                        anyString());
    }
}
