package net.ddns.adambravo79.tmill.model;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import net.ddns.adambravo79.tmill.dto.AudioRequest;

class ModelTest {

    // ===================== CHAT COMPLETION =====================

    @Test
    void deveCriarChatCompletionResponse() {
        Message msg = new Message("user", "conteudo");
        Choice choice = new Choice(msg);
        ChatCompletionResponse resp = new ChatCompletionResponse(List.of(choice));

        assertThat(resp.choices()).hasSize(1);
        assertThat(resp.choices().get(0).message().content()).isEqualTo("conteudo");
    }

    @Test
    void deveCriarChoice() {
        Message msg = new Message("system", "ola");
        Choice choice = new Choice(msg);

        assertThat(choice.message().role()).isEqualTo("system");
        assertThat(choice.message().content()).isEqualTo("ola");
    }

    @Test
    void deveCriarMessage() {
        Message msg = new Message("assistant", "resposta");
        assertThat(msg.role()).isEqualTo("assistant");
        assertThat(msg.content()).isEqualTo("resposta");
    }

    // ===================== CREDITS =====================

    @Test
    void deveCriarCreditsResponse() {
        CastRecord cast = new CastRecord("Ator", "Personagem");
        CreditsResponse resp = new CreditsResponse(List.of(cast), List.of());

        assertThat(resp.cast()).hasSize(1);
        assertThat(resp.cast().get(0).character()).isEqualTo("Personagem");
    }

    @Test
    void deveCriarCastRecord() {
        CastRecord cast = new CastRecord("Leonardo DiCaprio", "Cobb");
        assertThat(cast.name()).isEqualTo("Leonardo DiCaprio");
        assertThat(cast.character()).isEqualTo("Cobb");
    }

    @Test
    void deveCriarCrewRecord() {
        CrewRecord crew = new CrewRecord("Christopher Nolan", "Director");
        assertThat(crew.name()).isEqualTo("Christopher Nolan");
        assertThat(crew.job()).isEqualTo("Director");
    }

    // ===================== MOVIES =====================

    @Test
    void deveCriarMovieRecord() {
        MovieRecord movie =
                new MovieRecord(
                        1L,
                        "Titulo",
                        "Title",
                        "2020",
                        "overview",
                        10.0,
                        8.0,
                        "/poster",
                        List.of("US"));
        assertThat(movie.id()).isEqualTo(1L);
        assertThat(movie.title()).isEqualTo("Titulo");
        assertThat(movie.originalTitle()).isEqualTo("Title");
        assertThat(movie.releaseDate()).isEqualTo("2020");
        assertThat(movie.overview()).isEqualTo("overview");
        assertThat(movie.popularity()).isEqualTo(10.0);
        assertThat(movie.voteAverage()).isEqualTo(8.0);
        assertThat(movie.posterPath()).isEqualTo("/poster");
        assertThat(movie.originCountry()).containsExactly("US");
    }

    @Test
    void deveCriarMovieResponse() {
        MovieRecord movie =
                new MovieRecord(
                        1L,
                        "Titulo",
                        "Title",
                        "2020",
                        "overview",
                        10.0,
                        8.0,
                        "/poster",
                        List.of("US"));
        MovieResponse resp = new MovieResponse(List.of(movie));

        assertThat(resp.results()).hasSize(1);
        assertThat(resp.results().get(0).title()).isEqualTo("Titulo");
    }

    @Test
    void deveCriarMovieSearchResponse() {
        MovieRecord movie =
                new MovieRecord(
                        1L,
                        "Titulo",
                        "Title",
                        "2020",
                        "overview",
                        10.0,
                        8.0,
                        "/poster",
                        List.of("US"));
        MovieSearchResponse resp = new MovieSearchResponse(1, 1, 1, List.of(movie));

        assertThat(resp.page()).isEqualTo(1);
        assertThat(resp.totalPages()).isEqualTo(1);
        assertThat(resp.totalResults()).isEqualTo(1);
        assertThat(resp.results()).hasSize(1);
    }

    @Test
    void deveCriarMovieOrchestrationResponse() {
        MovieOrchestrationResponse resp = new MovieOrchestrationResponse("Título", "Sinopse");
        assertThat(resp.textoFormatado()).isEqualTo("Título");
        assertThat(resp.urlFoto()).isEqualTo("Sinopse");
    }

    @Test
    void deveCriarDirectorRecord() {
        DirectorRecord director = new DirectorRecord("Christopher Nolan", 123L);
        assertThat(director.name()).isEqualTo("Christopher Nolan");
        assertThat(director.id()).isEqualTo(123L);
    }

    // ===================== TV =====================

    @Test
    void deveCriarTvRecord() {
        TvRecord tv = new TvRecord(1L, "Serie Teste", "Overview", 8.5, "/poster.jpg", "2026-01-01");
        assertThat(tv.id()).isEqualTo(1L);
        assertThat(tv.name()).isEqualTo("Serie Teste");
        assertThat(tv.overview()).isEqualTo("Overview");
        assertThat(tv.voteAverage()).isEqualTo(8.5);
        assertThat(tv.firstAirDate()).isEqualTo("2026-01-01");
        assertThat(tv.posterPath()).isEqualTo("/poster.jpg");
    }

    // ===================== PROVIDERS =====================

    @Test
    void deveCriarProvider() {
        Provider provider = new Provider("Prime", 2, "/logo2.png");
        assertThat(provider.name()).isEqualTo("Prime");
        assertThat(provider.id()).isEqualTo(2);
        assertThat(provider.logoPath()).isEqualTo("/logo2.png");
    }

    @Test
    void deveCriarCountryProviders() {
        Provider provider = new Provider("Netflix", 1, "/logo.png");
        CountryProviders cp = new CountryProviders(List.of(provider));

        assertThat(cp.flatrate()).hasSize(1);
        assertThat(cp.flatrate().get(0).name()).isEqualTo("Netflix");
    }

    @Test
    void deveCriarWatchProviderResponse() {
        Provider provider = new Provider("Disney+", 3, "/logo3.png");
        WatchProviderResponse.CountryProviders cp =
                new WatchProviderResponse.CountryProviders(List.of(provider));
        WatchProviderResponse resp = new WatchProviderResponse(Map.of("BR", cp));

        assertThat(resp.results()).containsKey("BR");
        assertThat(resp.results().get("BR").flatrate().get(0).name()).isEqualTo("Disney+");
    }

    @Test
    void deveCriarWatchProvidersResponse() {
        WatchProvidersResponse.ProviderDetails details =
                new WatchProvidersResponse.ProviderDetails("Netflix", 1);
        WatchProvidersResponse response = new WatchProvidersResponse(Map.of("BR", details));
        assertThat(response.results()).containsKey("BR");
        assertThat(response.results().get("BR").provider_name()).isEqualTo("Netflix");
        assertThat(response.results().get("BR").provider_id()).isEqualTo(1);
    }

    // ===================== TRANSCRIPTION =====================

    @Test
    void deveCriarTranscriptionResponse() {
        TranscriptionResponse resp = new TranscriptionResponse("texto transcrito");
        assertThat(resp.text()).isEqualTo("texto transcrito");
    }

    @Test
    void deveCriarTranscriptionCacheEntry() {
        TranscriptionCacheEntry entry =
                new TranscriptionCacheEntry("bruto", "refinado", System.currentTimeMillis());
        assertThat(entry.textoBruto()).isEqualTo("bruto");
        assertThat(entry.textoRefinado()).isEqualTo("refinado");
        assertThat(entry.timestamp()).isPositive();
    }

    // ===================== RELEASES =====================

    @Test
    void deveCriarReleaseNotified() {
        ReleaseNotified release = new ReleaseNotified(1L, "movie", "2026-07-15");
        assertThat(release.tmdbId()).isEqualTo(1L);
        assertThat(release.mediaType()).isEqualTo("movie");
        assertThat(release.releaseDate()).isEqualTo("2026-07-15");
    }

    @Test
    void deveCriarFullRelease() {
        LocalDate date = LocalDate.of(2026, Month.JULY, 15);
        FullRelease release =
                new FullRelease(
                        1L,
                        "movie",
                        date,
                        "Filme Teste",
                        "Sinopse do filme",
                        8.0,
                        "Netflix, Prime",
                        "/poster.jpg");
        assertThat(release.tmdbId()).isEqualTo(1L);
        assertThat(release.title()).isEqualTo("Filme Teste");
        assertThat(release.releaseDate()).isEqualTo(date);
        assertThat(release.overview()).isEqualTo("Sinopse do filme");
        assertThat(release.mediaType()).isEqualTo("movie");
        assertThat(release.rating()).isEqualTo(8.0);
        assertThat(release.providers()).isEqualTo("Netflix, Prime");
        assertThat(release.posterPath()).isEqualTo("/poster.jpg");
    }

    // ===================== AUTO RESPONSE =====================

    @Test
    void deveCriarAutoResponseOverride() {
        AutoResponseOverride override =
                new AutoResponseOverride("Resposta automática", "https://example.com/anim.gif");
        assertThat(override.response()).isEqualTo("Resposta automática");
        assertThat(override.animation()).isEqualTo("https://example.com/anim.gif");
    }

    @Test
    void deveCriarUserOverride() {
        UserOverride override =
                new UserOverride("Resposta automática", "https://example.com/animation.gif");
        assertThat(override.response()).isEqualTo("Resposta automática");
        assertThat(override.animation()).isEqualTo("https://example.com/animation.gif");
    }

    @Test
    void deveCriarAutoResponseRule() {
        AutoResponseRule rule =
                new AutoResponseRule(
                        "Oi!",
                        "https://example.com/anim.gif",
                        LocalTime.of(8, 0),
                        LocalTime.of(18, 0),
                        Map.of(
                                "1",
                                new AutoResponseOverride(
                                        "Resposta 1", "https://example.com/anim1.gif")));
        assertThat(rule.response()).isEqualTo("Oi!");
        assertThat(rule.animation()).isEqualTo("https://example.com/anim.gif");
        assertThat(rule.startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(rule.endTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(rule.userOverrides()).hasSize(1);
        assertThat(rule.userOverrides().get("1").response()).isEqualTo("Resposta 1");
        assertThat(rule.userOverrides().get("1").animation())
                .isEqualTo("https://example.com/anim1.gif");
    }

    // ===================== WORLD CUP =====================

    @Test
    void deveCriarGoal() {
        Goal goal = new Goal("Neymar", "45+2", false, true);
        assertThat(goal.name()).isEqualTo("Neymar");
        assertThat(goal.minute()).isEqualTo("45+2");
        assertThat(goal.penalty()).isFalse();
        assertThat(goal.owngoal()).isTrue();
    }

    @Test
    void deveCriarScore() {
        Score score = new Score(List.of(2, 1), List.of(1, 0), List.of(), List.of(4, 3));
        assertThat(score.ft()).containsExactly(2, 1);
        assertThat(score.ht()).containsExactly(1, 0);
        assertThat(score.et()).isEmpty();
        assertThat(score.p()).containsExactly(4, 3);
    }

    // ===================== WORLD CUP MATCH =====================

    @Test
    void deveCriarWorldCupMatch() {
        Score score = new Score(List.of(2, 1), List.of(1, 0), List.of(), List.of());
        Goal gol1 = new Goal("Neymar", "12", false, false);
        Goal gol2 = new Goal("Mbappe", "34", true, false);
        WorldCupMatch match =
                new WorldCupMatch(
                        "Final",
                        "2026-07-15",
                        "20:00 UTC-3",
                        "Brasil",
                        "França",
                        "Grupo A",
                        "Maracanã",
                        score,
                        List.of(gol1),
                        List.of(gol2));

        assertThat(match.round()).isEqualTo("Final");
        assertThat(match.date()).isEqualTo("2026-07-15");
        assertThat(match.time()).isEqualTo("20:00 UTC-3");
        assertThat(match.homeTeam()).isEqualTo("Brasil");
        assertThat(match.awayTeam()).isEqualTo("França");
        assertThat(match.group()).isEqualTo("Grupo A");
        assertThat(match.stadium()).isEqualTo("Maracanã");
        assertThat(match.score()).isEqualTo(score);
        assertThat(match.goals1()).containsExactly(gol1);
        assertThat(match.goals2()).containsExactly(gol2);

        assertThat(match.getLocalDate()).isEqualTo(LocalDate.of(2026, Month.JULY, 15));
        assertThat(match.hasScore()).isTrue();
    }

    @Test
    void getLocalDate_deveParsearDataCorretamente() {
        WorldCupMatch match =
                new WorldCupMatch(
                        "Final",
                        "2026-07-15",
                        "20:00 UTC-3",
                        "Brasil",
                        "França",
                        null,
                        null,
                        null,
                        List.of(),
                        List.of());
        assertThat(match.getLocalDate()).isEqualTo(LocalDate.of(2026, Month.JULY, 15));
    }

    @Test
    void getLocalDate_quandoDataInvalida_deveLancarExcecao() {
        WorldCupMatch match =
                new WorldCupMatch(
                        "Final",
                        "2026-15-07",
                        "20:00 UTC-3",
                        "Brasil",
                        "França",
                        null,
                        null,
                        null,
                        List.of(),
                        List.of());
        assertThatThrownBy(match::getLocalDate)
                .isInstanceOf(java.time.format.DateTimeParseException.class);
    }

    @ParameterizedTest
    @MethodSource("provideMatchDateTimeTestCases")
    void getMatchDateTime_deveConverterCorretamente(
            String time, String targetZone, String expectedTime) {
        WorldCupMatch match =
                new WorldCupMatch(
                        "Final",
                        "2026-07-15",
                        time,
                        "Brasil",
                        "França",
                        null,
                        null,
                        null,
                        List.of(),
                        List.of());
        ZoneId target = ZoneId.of(targetZone);
        ZonedDateTime result = match.getMatchDateTime(target);
        assertThat(result.getZone()).isEqualTo(target);
        assertThat(result.toLocalDate()).isEqualTo(LocalDate.of(2026, Month.JULY, 15));
        assertThat(result.toLocalTime()).hasToString(expectedTime);
    }

    private static Stream<Arguments> provideMatchDateTimeTestCases() {
        return Stream.of(
                Arguments.of("20:00 UTC-3", "America/Sao_Paulo", "20:00"),
                Arguments.of("20:00 UTC+0", "UTC", "20:00"),
                Arguments.of("20:00 UTC+1", "America/Sao_Paulo", "16:00"));
    }

    @Test
    void getMatchDateTime_quandoTimeInvalido_deveLancarExcecao() {
        WorldCupMatch match =
                new WorldCupMatch(
                        "Final",
                        "2026-07-15",
                        "20:00",
                        "Brasil",
                        "França",
                        null,
                        null,
                        null,
                        List.of(),
                        List.of());
        ZoneId targetZone = ZoneId.of("UTC");
        assertThatThrownBy(() -> match.getMatchDateTime(targetZone))
                .isInstanceOf(ArrayIndexOutOfBoundsException.class);
    }

    @Test
    void getMatchDateTime_comOffsetNaoNumerico_deveLancarNumberFormatException() {
        WorldCupMatch match =
                new WorldCupMatch(
                        "Final",
                        "2026-07-15",
                        "20:00 UTC+5:30",
                        "Brasil",
                        "França",
                        null,
                        null,
                        null,
                        List.of(),
                        List.of());
        ZoneId targetZone = ZoneId.of("UTC");
        assertThatThrownBy(() -> match.getMatchDateTime(targetZone))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void hasScore_quandoScoreNulo_retornaFalse() {
        WorldCupMatch match =
                new WorldCupMatch(
                        "Final",
                        "2026-07-15",
                        "20:00 UTC-3",
                        "Brasil",
                        "França",
                        null,
                        null,
                        null,
                        List.of(),
                        List.of());
        assertThat(match.hasScore()).isFalse();
    }

    @Test
    void hasScore_quandoScoreFtPreenchido_retornaTrue() {
        Score score = new Score(List.of(2, 1), List.of(), List.of(), List.of());
        WorldCupMatch match =
                new WorldCupMatch(
                        "Final",
                        "2026-07-15",
                        "20:00 UTC-3",
                        "Brasil",
                        "França",
                        null,
                        null,
                        score,
                        List.of(),
                        List.of());
        assertThat(match.hasScore()).isTrue();
    }

    // ===================== AUDIO REQUEST (DTO) =====================

    @Test
    void deveCriarAudioRequest() {
        AudioRequest request =
                new AudioRequest("file-id", 123L, System.currentTimeMillis(), 456L, "Usuario");
        assertThat(request.fileId()).isEqualTo("file-id");
        assertThat(request.groupId()).isEqualTo(123L);
        assertThat(request.senderId()).isEqualTo(456L);
        assertThat(request.senderName()).isEqualTo("Usuario");
    }

    // ===================== WORLD CUP MATCH – COBERTURA ADICIONAL =====================

    @Test
    void hasScore_quandoScoreFtNulo_retornaFalse() {
        // Cria um Score com ft = null (pode acontecer se o JSON não tiver o campo)
        Score score = new Score(null, List.of(), List.of(), List.of());
        WorldCupMatch match =
                new WorldCupMatch(
                        "Final",
                        "2026-07-15",
                        "20:00 UTC-3",
                        "Brasil",
                        "França",
                        null,
                        null,
                        score,
                        List.of(),
                        List.of());
        assertThat(match.hasScore()).isFalse();
    }

    @Test
    void getMatchDateTime_comHoraInvalida_deveLancarDateTimeParseException() {
        WorldCupMatch match =
                new WorldCupMatch(
                        "Final",
                        "2026-07-15",
                        "25:00 UTC-3", // hora inválida
                        "Brasil",
                        "França",
                        null,
                        null,
                        null,
                        List.of(),
                        List.of());
        assertThatThrownBy(() -> match.getMatchDateTime(ZoneId.of("UTC")))
                .isInstanceOf(java.time.format.DateTimeParseException.class);
    }
}
