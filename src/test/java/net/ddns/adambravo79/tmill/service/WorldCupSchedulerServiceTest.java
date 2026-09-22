package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import net.ddns.adambravo79.tmill.model.Goal;
import net.ddns.adambravo79.tmill.model.Score;
import net.ddns.adambravo79.tmill.model.WorldCupMatch;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;

@ExtendWith(MockitoExtension.class)
class WorldCupSchedulerServiceTest {

    @Mock private StaticWorldCupService worldCupService;
    @Mock private TelegramFacade telegramFacade;
    @Mock private WorldCupUpdaterService worldCupUpdaterService;
    @Mock private MetricsService metricsService;

    @InjectMocks private WorldCupSchedulerService service;

    @BeforeEach
    void setUp() {
        // Clock default: system default zone (evita NPE no construtor)
        // Como usamos @Mock Clock, garantimos que não seja null.
        // Porém, os testes que precisam de tempo controlado sobrescrevem com Clock.fixed.

        Set<Long> mutableAllowedGroups = new HashSet<>();
        mutableAllowedGroups.add(-100L);
        ReflectionTestUtils.setField(service, "allowedGroups", mutableAllowedGroups);
        ReflectionTestUtils.setField(service, "worldcupEnabled", true);
        ReflectionTestUtils.setField(service, "allowedChatsStr", "");
        service.init();
    }

    // ============================================================
    // TRADUÇÃO E BANDEIRAS
    // ============================================================

    @Test
    @DisplayName("translateTeam: traduz times conhecidos")
    void translateTeam_conhecidos() throws Exception {
        Method m = WorldCupSchedulerService.class.getDeclaredMethod("translateTeam", String.class);
        m.setAccessible(true);
        assertThat(m.invoke(service, "brazil")).isEqualTo("Brasil");
        assertThat(m.invoke(service, "england")).isEqualTo("Inglaterra");
        assertThat(m.invoke(service, "unknown")).isEqualTo("unknown");
    }

    @ParameterizedTest
    @MethodSource("translateTeamProvider")
    void translateTeam_parametrizado(String input, String expected) throws Exception {
        Method m = WorldCupSchedulerService.class.getDeclaredMethod("translateTeam", String.class);
        m.setAccessible(true);
        assertThat(m.invoke(service, input)).isEqualTo(expected);
    }

    static Stream<Arguments> translateTeamProvider() {
        return Stream.of(
                Arguments.of(null, "?"),
                Arguments.of("", "?"),
                Arguments.of("brazil", "Brasil"),
                Arguments.of("United States of America", "Estados Unidos"),
                Arguments.of("DR Congo", "Republica Democratica do Congo"),
                Arguments.of("unknown", "unknown"));
    }

    @Test
    @DisplayName("flagEmoji: bandeira para time conhecido")
    void flagEmoji_conhecido() throws Exception {
        Method m = WorldCupSchedulerService.class.getDeclaredMethod("flagEmoji", String.class);
        m.setAccessible(true);
        assertThat(m.invoke(service, "brazil")).isEqualTo("🇧🇷");
        assertThat(m.invoke(service, "france")).isEqualTo("🇫🇷");
        assertThat(m.invoke(service, "unknown")).isEqualTo("🏳️");
    }

    @ParameterizedTest
    @MethodSource("flagEmojiProvider")
    void flagEmoji_parametrizado(String input, String expected) throws Exception {
        Method m = WorldCupSchedulerService.class.getDeclaredMethod("flagEmoji", String.class);
        m.setAccessible(true);
        assertThat(m.invoke(service, input)).isEqualTo(expected);
    }

    static Stream<Arguments> flagEmojiProvider() {
        return Stream.of(
                Arguments.of(null, "🏳️"),
                Arguments.of("", "🏳️"),
                Arguments.of("BRAZIL", "🇧🇷"),
                Arguments.of("xyzzz", "🏳️"),
                Arguments.of("United States of America", "🇺🇸"),
                Arguments.of("DR Congo", "🇨🇩"));
    }

    // ============================================================
    // PARSE MINUTE
    // ============================================================

    @Test
    @DisplayName("parseMinuteToInt: converte corretamente")
    void parseMinuteToInt_ok() throws Exception {
        Method m =
                WorldCupSchedulerService.class.getDeclaredMethod("parseMinuteToInt", String.class);
        m.setAccessible(true);
        assertThat((int) m.invoke(service, "45+3")).isEqualTo(48);
        assertThat((int) m.invoke(service, "90+7")).isEqualTo(97);
        assertThat((int) m.invoke(service, "6")).isEqualTo(6);
        assertThat((int) m.invoke(service, (Object) null)).isZero();
        assertThat((int) m.invoke(service, "abc")).isZero();
        assertThat((int) m.invoke(service, "45+abc")).isZero();
    }

    // ============================================================
    // MÉTRICAS — sendMatchesMessage (privado, broadcast)
    // ============================================================

    @Test
    @DisplayName("sendMatchesMessage: envia jogos e registra success('worldcup_jogos_enviados')")
    void sendMatchesMessage_comJogos_registraSuccess() throws Exception {
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatch(date, "Brazil", "Argentina", "12:00 UTC-3");
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of(match));

        Method m =
                WorldCupSchedulerService.class.getDeclaredMethod(
                        "sendMatchesMessage", LocalDate.class, String.class);
        m.setAccessible(true);
        m.invoke(service, date, "Título");

        verify(telegramFacade).enviarMensagemHtml(eq(-100L), anyString());
        verify(metricsService).success("worldcup_jogos_enviados");
        verify(metricsService, never()).error("worldcup_sem_jogos");
    }

    @Test
    @DisplayName("sendMatchesMessage: sem jogos registra error('worldcup_sem_jogos')")
    void sendMatchesMessage_semJogos_registraError() throws Exception {
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of());

        Method m =
                WorldCupSchedulerService.class.getDeclaredMethod(
                        "sendMatchesMessage", LocalDate.class, String.class);
        m.setAccessible(true);
        m.invoke(service, date, "Título");

        verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
        verify(metricsService).error("worldcup_sem_jogos");
        verify(metricsService, never()).success("worldcup_jogos_enviados");
    }

    @Test
    @DisplayName("sendMatchesMessage: estádio vazio não adiciona estádio")
    void sendMatchesMessage_estadioVazio() throws Exception {
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match =
                new WorldCupMatch(
                        "Round",
                        date.toString(),
                        "12:00 UTC-3",
                        "Brazil",
                        "Argentina",
                        "Group",
                        "",
                        null,
                        List.of(),
                        List.of());
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of(match));

        Method m =
                WorldCupSchedulerService.class.getDeclaredMethod(
                        "sendMatchesMessage", LocalDate.class, String.class);
        m.setAccessible(true);
        m.invoke(service, date, "Título");

        verify(telegramFacade).enviarMensagemHtml(eq(-100L), contains("Brasil"));
        verify(metricsService).success("worldcup_jogos_enviados");
    }

    // ============================================================
    // MÉTRICAS — sendThirtyMinuteReminder
    // ============================================================

    @Test
    @DisplayName("sendThirtyMinuteReminder: registra success('worldcup_lembrete_30min_enviado')")
    void sendThirtyMinuteReminder_registraSuccess() throws Exception {
        Method m =
                WorldCupSchedulerService.class.getDeclaredMethod(
                        "sendThirtyMinuteReminder", WorldCupMatch.class);
        m.setAccessible(true);

        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatch(date, "Brazil", "Argentina", "12:00 UTC-3");
        m.invoke(service, match);

        verify(telegramFacade).enviarMensagemHtml(eq(-100L), contains("Faltam 30 minutos"));
        verify(metricsService).success("worldcup_lembrete_30min_enviado");
    }

    // ============================================================
    // MÉTRICAS — sendResultsToChat
    // ============================================================

    @Test
    @DisplayName("sendResultsToChat: com placar registra success('worldcup_resultados_enviados')")
    void sendResultsToChat_comPlacar_registraSuccess() {
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatchComPlacar(date, "Brazil", "Argentina", 2, 1);
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of(match));

        service.sendResultsToChat(chatId, date);

        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("2 x 1"));
        verify(metricsService).success("worldcup_resultados_enviados");
        verify(metricsService, never()).error("worldcup_sem_jogos");
    }

    @Test
    @DisplayName("sendResultsToChat: sem jogos registra error('worldcup_sem_jogos')")
    void sendResultsToChat_semJogos_registraError() {
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of());

        service.sendResultsToChat(chatId, date);

        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("Nenhum jogo"));
        verify(metricsService).error("worldcup_sem_jogos");
        verify(metricsService, never()).success("worldcup_resultados_enviados");
    }

    @Test
    @DisplayName("sendResultsToChat: disabled registra error('worldcup_desabilitado')")
    void sendResultsToChat_disabled_registraError() {
        ReflectionTestUtils.setField(service, "worldcupEnabled", false);
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

        service.sendResultsToChat(chatId, date);

        verify(telegramFacade)
                .enviarMensagemHtml(eq(chatId), contains("Servico de Copa desativado"));
        verify(metricsService).error("worldcup_desabilitado");
        verify(metricsService, never()).success("worldcup_resultados_enviados");
    }

    @Test
    @DisplayName("sendResultsToChat: prorrogação e pênaltis aparecem")
    void sendResultsToChat_proEPen() {
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        Score score = new Score(List.of(1, 1), null, List.of(2, 1), List.of(4, 3));
        WorldCupMatch match = criarMatchComScore(date, "Brazil", "Argentina", score);
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of(match));

        service.sendResultsToChat(chatId, date);

        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("(pro) 2-1"));
        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("(pen) 4-3"));
        verify(metricsService).success("worldcup_resultados_enviados");
    }

    @Test
    @DisplayName("sendResultsToChat: sem placar → 'Aguardando resultado'")
    void sendResultsToChat_aguardando() {
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatchSemPlacar(date, "Brazil", "Argentina");
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of(match));

        service.sendResultsToChat(chatId, date);

        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("Aguardando resultado"));
        verify(metricsService).success("worldcup_resultados_enviados");
    }

    @Test
    @DisplayName("sendResultsToChat: gols em ambos os times")
    void sendResultsToChat_golsAmbosTimes() {
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        Score score = new Score(List.of(2, 1), null, null, null);
        Goal g1 = new Goal("Neymar", "12", false, false);
        Goal g2 = new Goal("Mbappe", "34", true, false);
        WorldCupMatch match =
                new WorldCupMatch(
                        "Round",
                        date.toString(),
                        "12:00 UTC-3",
                        "Brazil",
                        "France",
                        "Group",
                        "Stadium",
                        score,
                        List.of(g1),
                        List.of(g2));
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of(match));

        service.sendResultsToChat(chatId, date);

        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("Neymar 12"));
        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("Mbappe 34 (P)"));
    }

    @Test
    @DisplayName("sendResultsToChat: gol contra tem (GC)")
    void sendResultsToChat_golContra() {
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        Score score = new Score(List.of(1, 0), null, null, null);
        Goal g = new Goal("Jogador", "20", false, true);
        WorldCupMatch match =
                new WorldCupMatch(
                        "Round",
                        date.toString(),
                        "12:00 UTC-3",
                        "Brazil",
                        "Argentina",
                        "Group",
                        "Stadium",
                        score,
                        List.of(g),
                        List.of());
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of(match));

        service.sendResultsToChat(chatId, date);

        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("Jogador 20 (GC)"));
    }

    // ============================================================
    // MÉTRICAS — sendMatchesToChat (público)
    // ============================================================

    @Test
    @DisplayName("sendMatchesToChat: com jogos registra success")
    void sendMatchesToChat_comJogos() {
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatch(date, "Brazil", "Argentina", "12:00 UTC-3");
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of(match));

        service.sendMatchesToChat(chatId, date);

        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("JOGOS DE HOJE"));
        verify(metricsService).success("worldcup_jogos_enviados");
    }

    @Test
    @DisplayName("sendMatchesToChat: sem jogos registra error")
    void sendMatchesToChat_semJogos() {
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of());

        service.sendMatchesToChat(chatId, date);

        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("Nenhum jogo programado"));
        verify(metricsService).error("worldcup_sem_jogos");
    }

    @Test
    @DisplayName("sendMatchesToChat: disabled registra error")
    void sendMatchesToChat_disabled() {
        ReflectionTestUtils.setField(service, "worldcupEnabled", false);
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

        service.sendMatchesToChat(chatId, date);

        verify(telegramFacade)
                .enviarMensagemHtml(eq(chatId), contains("Servico de Copa desativado"));
        verify(metricsService).error("worldcup_desabilitado");
    }

    // ============================================================
    // MÉTRICAS — sendMatchesMessageToChat (privado)
    // ============================================================

    @Test
    @DisplayName("sendMatchesMessageToChat: com jogos registra success")
    void sendMatchesMessageToChat_comJogos() throws Exception {
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatch(date, "Brazil", "Argentina", "12:00 UTC-3");
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of(match));

        Method m =
                WorldCupSchedulerService.class.getDeclaredMethod(
                        "sendMatchesMessageToChat", long.class, LocalDate.class, String.class);
        m.setAccessible(true);
        m.invoke(service, chatId, date, "Título");

        verify(telegramFacade).enviarMensagemHtml(eq(chatId), anyString());
        verify(metricsService).success("worldcup_jogos_enviados");
    }

    @Test
    @DisplayName("sendMatchesMessageToChat: sem jogos registra error")
    void sendMatchesMessageToChat_semJogos() throws Exception {
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of());

        Method m =
                WorldCupSchedulerService.class.getDeclaredMethod(
                        "sendMatchesMessageToChat", long.class, LocalDate.class, String.class);
        m.setAccessible(true);
        m.invoke(service, chatId, date, "Título");

        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("Nenhum jogo"));
        verify(metricsService).error("worldcup_sem_jogos");
    }

    @Test
    @DisplayName("sendMatchesMessageToChat: estádio vazio")
    void sendMatchesMessageToChat_estadioVazio() throws Exception {
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match =
                new WorldCupMatch(
                        "Round",
                        date.toString(),
                        "12:00 UTC-3",
                        "Brazil",
                        "Argentina",
                        "Group",
                        null,
                        null,
                        List.of(),
                        List.of());
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of(match));

        Method m =
                WorldCupSchedulerService.class.getDeclaredMethod(
                        "sendMatchesMessageToChat", long.class, LocalDate.class, String.class);
        m.setAccessible(true);
        m.invoke(service, chatId, date, "Título");

        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("Brasil"));
        verify(metricsService).success("worldcup_jogos_enviados");
    }

    // ============================================================
    // MÉTRICAS — métodos *ToChat (noon/evening/manual)
    // ============================================================

    @Test
    @DisplayName("sendNoonMatchesToChat: com jogos registra success")
    void sendNoonMatchesToChat_comJogos() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatch(today, "Brazil", "Argentina", "12:00 UTC-3");
        when(worldCupService.getMatchesForDay(today)).thenReturn(List.of(match));

        service.sendNoonMatchesToChat(123L);

        verify(telegramFacade).enviarMensagemHtml(eq(123L), contains("JOGOS DE HOJE (meio-dia)"));
        verify(metricsService).success("worldcup_jogos_enviados");
    }

    @Test
    @DisplayName("sendNoonMatchesToChat: disabled registra error")
    void sendNoonMatchesToChat_disabled() {
        ReflectionTestUtils.setField(service, "worldcupEnabled", false);
        service.sendNoonMatchesToChat(123L);
        verify(telegramFacade).enviarMensagemHtml(eq(123L), contains("Servico de Copa desativado"));
        verify(metricsService).error("worldcup_desabilitado");
    }

    @Test
    @DisplayName("sendEveningMatchesToChat: com jogos registra success")
    void sendEveningMatchesToChat_comJogos() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatch(today, "Brazil", "Argentina", "18:30 UTC-3");
        when(worldCupService.getMatchesForDay(today)).thenReturn(List.of(match));

        service.sendEveningMatchesToChat(123L);

        verify(telegramFacade).enviarMensagemHtml(eq(123L), contains("RESUMO DOS JOGOS DE HOJE"));
        verify(metricsService).success("worldcup_jogos_enviados");
    }

    @Test
    @DisplayName("sendEveningMatchesToChat: disabled registra error")
    void sendEveningMatchesToChat_disabled() {
        ReflectionTestUtils.setField(service, "worldcupEnabled", false);
        service.sendEveningMatchesToChat(123L);
        verify(telegramFacade).enviarMensagemHtml(eq(123L), contains("Servico de Copa desativado"));
        verify(metricsService).error("worldcup_desabilitado");
    }

    @Test
    @DisplayName("sendManualTestToChat: com jogos registra success")
    void sendManualTestToChat_comJogos() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatch(today, "Brazil", "Argentina", "12:00 UTC-3");
        when(worldCupService.getMatchesForDay(today)).thenReturn(List.of(match));

        service.sendManualTestToChat(123L);

        verify(telegramFacade).enviarMensagemHtml(eq(123L), contains("TESTE MANUAL"));
        verify(metricsService).success("worldcup_jogos_enviados");
    }

    @Test
    @DisplayName("sendManualTestToChat: disabled registra error")
    void sendManualTestToChat_disabled() {
        ReflectionTestUtils.setField(service, "worldcupEnabled", false);
        service.sendManualTestToChat(123L);
        verify(telegramFacade).enviarMensagemHtml(eq(123L), contains("Servico de Copa desativado"));
        verify(metricsService).error("worldcup_desabilitado");
    }

    // ============================================================
    // MÉTRICAS — sendManualTest (broadcast)
    // ============================================================

    @Test
    @DisplayName("sendManualTest: disabled registra error('worldcup_desabilitado')")
    void sendManualTest_disabled() {
        ReflectionTestUtils.setField(service, "worldcupEnabled", false);
        service.sendManualTest();
        verifyNoInteractions(telegramFacade);
        verify(metricsService).error("worldcup_desabilitado");
    }

    @Test
    @DisplayName("sendManualTest: sem grupos registra error")
    void sendManualTest_semGrupos() {
        ReflectionTestUtils.setField(service, "allowedGroups", new HashSet<>());
        service.sendManualTest();
        verifyNoInteractions(telegramFacade);
        verify(metricsService).error("worldcup_desabilitado");
    }

    @Test
    @DisplayName("sendManualTest: com jogos registra success")
    void sendManualTest_comJogos() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatch(today, "Brazil", "Argentina", "12:00 UTC-3");
        when(worldCupService.getMatchesForDay(today)).thenReturn(List.of(match));

        service.sendManualTest();

        verify(telegramFacade).enviarMensagemHtml(eq(-100L), contains("TESTE MANUAL"));
        verify(metricsService).success("worldcup_jogos_enviados");
    }

    // ============================================================
    // LEMBRETES 30 MIN — checkThirtyMinutesBeforeEachMatch
    // ============================================================

    @Test
    @DisplayName("checkThirtyMinutesBeforeEachMatch: 11:30 dispara lembrete e registra métrica")
    void checkThirtyMinutos_disparaRegistraMetrica() {
        Clock fixed =
                Clock.fixed(
                        Instant.parse("2026-07-01T11:30:01-03:00"), ZoneId.of("America/Sao_Paulo"));
        ReflectionTestUtils.setField(service, "clock", fixed);

        LocalDate date = LocalDate.now(fixed);
        WorldCupMatch match = criarMatch(date, "Brazil", "Argentina", "12:00 UTC-3");
        when(worldCupService.getMatchesForDay(any())).thenReturn(List.of(match));

        service.checkThirtyMinutesBeforeEachMatch();

        verify(telegramFacade).enviarMensagemHtml(eq(-100L), contains("Faltam 30 minutos"));
        verify(metricsService).success("worldcup_lembrete_30min_enviado");
    }

    @Test
    @DisplayName("checkThirtyMinutes: lembrete duplicado não reenvia")
    void checkThirtyMinutos_duplicadoNaoEnvia() {
        ZoneId zone = ZoneId.of("America/Sao_Paulo");
        LocalDate today = LocalDate.now(zone);
        WorldCupMatch match = criarMatch(today, "Brazil", "Argentina", "12:00 UTC-3");
        when(worldCupService.getMatchesForDay(today)).thenReturn(List.of(match));

        Set<String> sent = new HashSet<>();
        sent.add(today + "_Brazil_Argentina");
        ReflectionTestUtils.setField(service, "remindersSent", sent);

        service.checkThirtyMinutesBeforeEachMatch();
        verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
        verify(metricsService, never()).success("worldcup_lembrete_30min_enviado");
    }

    @Test
    @DisplayName("checkThirtyMinutes: disabled não faz nada")
    void checkThirtyMinutos_disabled() {
        ReflectionTestUtils.setField(service, "worldcupEnabled", false);
        service.checkThirtyMinutesBeforeEachMatch();
        verifyNoInteractions(worldCupService, telegramFacade, metricsService);
    }

    @Test
    @DisplayName("checkThirtyMinutes: sem grupos não faz nada")
    void checkThirtyMinutos_semGrupos() {
        ReflectionTestUtils.setField(service, "allowedGroups", new HashSet<>());
        service.checkThirtyMinutesBeforeEachMatch();
        verifyNoInteractions(worldCupService, telegramFacade, metricsService);
    }

    // ============================================================
    // AGENDADOS
    // ============================================================

    @Test
    @DisplayName("sendNoonMatches: disabled não faz nada")
    void sendNoonMatches_disabled() {
        ReflectionTestUtils.setField(service, "worldcupEnabled", false);
        service.sendNoonMatches();
        verifyNoInteractions(worldCupService, telegramFacade, metricsService);
    }

    @Test
    @DisplayName("sendNoonMatches: sem grupos não faz nada")
    void sendNoonMatches_semGrupos() {
        ReflectionTestUtils.setField(service, "allowedGroups", new HashSet<>());
        service.sendNoonMatches();
        verifyNoInteractions(worldCupService, telegramFacade, metricsService);
    }

    @Test
    @DisplayName("sendNoonMatches: com jogos registra success")
    void sendNoonMatches_comJogos() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatch(today, "Brazil", "Argentina", "12:00 UTC-3");
        when(worldCupService.getMatchesForDay(today)).thenReturn(List.of(match));

        service.sendNoonMatches();

        verify(telegramFacade).enviarMensagemHtml(eq(-100L), contains("JOGOS DE HOJE (meio-dia)"));
        verify(metricsService).success("worldcup_jogos_enviados");
    }

    @Test
    @DisplayName("sendEveningMatches: com jogos registra success")
    void sendEveningMatches_comJogos() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatch(today, "Brazil", "Argentina", "18:30 UTC-3");
        when(worldCupService.getMatchesForDay(today)).thenReturn(List.of(match));

        service.sendEveningMatches();

        verify(telegramFacade).enviarMensagemHtml(eq(-100L), contains("RESUMO DOS JOGOS DE HOJE"));
        verify(metricsService).success("worldcup_jogos_enviados");
    }

    // ============================================================
    // CLEAN REMINDERS
    // ============================================================

    @Test
    @DisplayName("cleanReminders: limpa o set")
    void cleanReminders_limpa() {
        Set<String> reminders = new HashSet<>();
        reminders.add("k1");
        reminders.add("k2");
        ReflectionTestUtils.setField(service, "remindersSent", reminders);

        service.cleanReminders();

        assertThat(reminders).isEmpty();
        verifyNoInteractions(metricsService);
    }

    // ============================================================
    // RELOAD
    // ============================================================

    @Test
    @DisplayName("reloadWorldCup: chama forceUpdate")
    void reloadWorldCup_chamaForceUpdate() {
        ResponseEntity<String> response = service.reloadWorldCup();
        verify(worldCupUpdaterService).forceUpdate();
        assertThat(response.getBody()).contains("recarregados");
        verifyNoInteractions(metricsService);
    }

    // ============================================================
    // INIT
    // ============================================================

    @Test
    @DisplayName("init: disabled não configura grupos")
    void init_disabled() {
        ReflectionTestUtils.setField(service, "worldcupEnabled", false);
        Set<Long> groups = new HashSet<>();
        ReflectionTestUtils.setField(service, "allowedGroups", groups);
        service.init();
        assertThat(groups).isEmpty();
    }

    @Test
    @DisplayName("init: ignora IDs inválidos e positivos")
    void init_idsInvalidos() {
        ReflectionTestUtils.setField(service, "worldcupEnabled", true);
        ReflectionTestUtils.setField(service, "allowedChatsStr", "-100,abc,123,-200");
        Set<Long> groups = new HashSet<>();
        ReflectionTestUtils.setField(service, "allowedGroups", groups);
        service.init();
        assertThat(groups).containsExactlyInAnyOrder(-100L, -200L);
    }

    // ============================================================
    // CONTAGEM DE MÉTRICAS EM FLUXO COMPLEXO
    // ============================================================

    @Test
    @DisplayName("Fluxo complexo: múltiplas chamadas somam métricas corretamente")
    void fluxoComplexo() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatch(today, "Brazil", "Argentina", "12:00 UTC-3");
        when(worldCupService.getMatchesForDay(today)).thenReturn(List.of(match));

        service.sendNoonMatches();
        service.sendEveningMatches();
        service.sendManualTest();

        verify(metricsService, times(3)).success("worldcup_jogos_enviados");
        verify(metricsService, never()).error(anyString());
    }

    @Test
    @DisplayName("Fluxo misto: sucessos e erros são contabilizados separadamente")
    void fluxoMisto() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

        // 1) Sem jogos
        when(worldCupService.getMatchesForDay(today)).thenReturn(List.of());
        service.sendNoonMatches();
        verify(metricsService).error("worldcup_sem_jogos");

        // 2) Com jogos
        WorldCupMatch match = criarMatch(today, "Brazil", "Argentina", "12:00 UTC-3");
        when(worldCupService.getMatchesForDay(today)).thenReturn(List.of(match));
        service.sendEveningMatches();

        verify(metricsService, times(1)).error("worldcup_sem_jogos");
        verify(metricsService, times(1)).success("worldcup_jogos_enviados");
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private WorldCupMatch criarMatch(LocalDate date, String t1, String t2, String time) {
        return new WorldCupMatch(
                "Round",
                date.toString(),
                time,
                t1,
                t2,
                "Group",
                "Stadium",
                null,
                List.of(),
                List.of());
    }

    private WorldCupMatch criarMatchComPlacar(
            LocalDate date, String t1, String t2, int g1, int g2) {
        Score score = new Score(List.of(g1, g2), null, null, null);
        return new WorldCupMatch(
                "Round",
                date.toString(),
                "12:00 UTC-3",
                t1,
                t2,
                "Group",
                "Stadium",
                score,
                List.of(new Goal("Jogador", "30", false, false)),
                List.of());
    }

    private WorldCupMatch criarMatchComScore(LocalDate date, String t1, String t2, Score score) {
        return new WorldCupMatch(
                "Round",
                date.toString(),
                "12:00 UTC-3",
                t1,
                t2,
                "Group",
                "Stadium",
                score,
                List.of(),
                List.of());
    }

    private WorldCupMatch criarMatchSemPlacar(LocalDate date, String t1, String t2) {
        return new WorldCupMatch(
                "Round",
                date.toString(),
                "12:00 UTC-3",
                t1,
                t2,
                "Group",
                "Stadium",
                null,
                List.of(),
                List.of());
    }
}
