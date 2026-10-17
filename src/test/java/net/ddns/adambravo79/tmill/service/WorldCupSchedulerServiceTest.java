package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.SimpleTriggerContext;
import org.springframework.test.util.ReflectionTestUtils;

import net.ddns.adambravo79.tmill.model.Goal;
import net.ddns.adambravo79.tmill.model.Score;
import net.ddns.adambravo79.tmill.model.WorldCupMatch;
import net.ddns.adambravo79.tmill.service.config.JsonConfigLoader;
import net.ddns.adambravo79.tmill.service.config.WorldCupTeamsService;
import net.ddns.adambravo79.tmill.service.feature.FeatureFlagAdminService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;

@ExtendWith(MockitoExtension.class)
class WorldCupSchedulerServiceTest {

    @Mock private StaticWorldCupService worldCupService;
    @Mock private TelegramFacade telegramFacade;
    @Mock private WorldCupUpdaterService worldCupUpdaterService;
    @Mock private MetricsService metricsService;
    @Mock private FeatureFlagAdminService featureFlags;
    @Mock private JsonConfigLoader jsonConfigLoader;
    @Mock private WorldCupTeamsService teamsService;

    @InjectMocks private WorldCupSchedulerService service;

    @BeforeEach
    void setUp() {
        // Flag mestra da Copa habilitada por padrão
        lenient().when(featureFlags.isEnabled("worldcup.enabled")).thenReturn(true);
        // 🆕 Nova flag: reminders 30min habilitada por padrão
        lenient().when(featureFlags.isEnabled("worldcup.reminders30min.enabled")).thenReturn(true);

        // Como o WorldCupSchedulerService delega para WorldCupTeamsService,
        // configuramos respostas determinísticas para os times usados nos testes.
        lenient()
                .when(teamsService.translateTeam(anyString()))
                .thenAnswer(
                        i -> {
                            String team = i.getArgument(0);
                            if (team == null || team.isBlank()) return "?";
                            return switch (team.toLowerCase()) {
                                case "brazil" -> "Brasil";
                                case "argentina" -> "Argentina";
                                case "england" -> "Inglaterra";
                                case "united states", "usa" -> "Estados Unidos";
                                case "dr congo" -> "Republica Democratica do Congo";
                                default -> team;
                            };
                        });

        lenient()
                .when(teamsService.flagEmoji(anyString()))
                .thenAnswer(
                        i -> {
                            String team = i.getArgument(0);
                            if (team == null || team.isBlank()) return "🏳️";
                            return switch (team.toLowerCase()) {
                                case "brazil" -> "🇧🇷";
                                case "argentina" -> "🇦🇷";
                                case "france" -> "🇫🇷";
                                case "united states", "usa" -> "🇺🇸";
                                case "dr congo" -> "🇨🇩";
                                default -> "🏳️";
                            };
                        });

        Set<Long> mutableAllowedGroups = new HashSet<>();
        mutableAllowedGroups.add(-100L);
        ReflectionTestUtils.setField(service, "allowedGroups", mutableAllowedGroups);
        ReflectionTestUtils.setField(service, "allowedChatsStr", "");

        service.init();
    }

    // =========================================================================
    // 🆕 FEATURE FLAG — worldcup.reminders30min.enabled
    // =========================================================================

    @Test
    @DisplayName("checkThirtyMinutes: flag 'worldcup.reminders30min.enabled' off → pula")
    void checkThirtyMinutos_reminders30minOff_pula() {
        when(featureFlags.isEnabled("worldcup.reminders30min.enabled")).thenReturn(false);

        service.checkThirtyMinutesBeforeEachMatch();

        verifyNoInteractions(worldCupService, telegramFacade, metricsService);
    }

    @Test
    @DisplayName("checkThirtyMinutes: flag 'worldcup.enabled' off → pula mesmo com reminders on")
    void checkThirtyMinutos_worldcupOff_pula() {
        when(featureFlags.isEnabled("worldcup.enabled")).thenReturn(false);

        service.checkThirtyMinutesBeforeEachMatch();

        verifyNoInteractions(worldCupService, telegramFacade, metricsService);
        // worldcup.enabled foi consultada (pelo init + pelo método)
        verify(featureFlags, atLeastOnce()).isEnabled("worldcup.enabled");
        verify(featureFlags, never()).isEnabled("worldcup.reminders30min.enabled");
    }

    @Test
    @DisplayName("checkThirtyMinutes: flag 'worldcup.reminders30min.enabled' on → envia lembrete")
    void checkThirtyMinutos_reminders30minOn_envia() {
        ZoneId zone = ZoneId.of("America/Sao_Paulo");
        // 2026-07-01 11:30:01 BRT = 14:30:01 UTC
        Clock fixed = Clock.fixed(Instant.parse("2026-07-01T14:30:01Z"), zone);

        WorldCupSchedulerService serviceWithClock =
                new WorldCupSchedulerService(
                        worldCupService,
                        telegramFacade,
                        worldCupUpdaterService,
                        fixed,
                        metricsService,
                        featureFlags,
                        jsonConfigLoader,
                        teamsService);

        Set<Long> groups = new HashSet<>();
        groups.add(-100L);
        ReflectionTestUtils.setField(serviceWithClock, "allowedGroups", groups);
        ReflectionTestUtils.setField(serviceWithClock, "allowedChatsStr", "");

        LocalDate date = LocalDate.now(fixed);
        WorldCupMatch match = criarMatch(date, "Brazil", "Argentina", "12:00 UTC-3");
        when(worldCupService.getMatchesForDay(any(LocalDate.class))).thenReturn(List.of(match));

        serviceWithClock.checkThirtyMinutesBeforeEachMatch();

        verify(telegramFacade).enviarMensagemHtml(eq(-100L), contains("Faltam 30 minutos"));
        verify(metricsService).success("worldcup_lembrete_30min_enviado");
    }

    @Test
    @DisplayName("Reminders 30min desabilitada: noon/evening seguem funcionando")
    void reminders30minOff_noonEEveningFuncionam() {
        // NÃO stuba 'worldcup.reminders30min.enabled':
        // noon/evening só olham 'worldcup.enabled' (que está true no setUp)

        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatch(today, "Brazil", "Argentina", "12:00 UTC-3");
        when(worldCupService.getMatchesForDay(any(LocalDate.class))).thenReturn(List.of(match));

        service.sendNoonMatches();
        service.sendEveningMatches();

        verify(telegramFacade, times(2)).enviarMensagemHtml(eq(-100L), anyString());
        verify(metricsService, times(2)).success("worldcup_jogos_enviados");
        verify(metricsService, never()).success("worldcup_lembrete_30min_enviado");
        // Prova explícita: nenhuma das chamadas consultou a flag de reminders
        verify(featureFlags, never()).isEnabled("worldcup.reminders30min.enabled");
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
    // sendMatchesMessage (privado, broadcast)
    // ============================================================

    @Test
    @DisplayName("sendMatchesMessage: envia jogos e registra success('worldcup_jogos_enviados')")
    void sendMatchesMessage_comJogos_registraSuccess() throws Exception {
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatch(date, "Brazil", "Argentina", "12:00 UTC-3");
        when(worldCupService.getMatchesForDay(any(LocalDate.class))).thenReturn(List.of(match));

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
        when(worldCupService.getMatchesForDay(any(LocalDate.class))).thenReturn(List.of());

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
        when(worldCupService.getMatchesForDay(any(LocalDate.class))).thenReturn(List.of(match));

        Method m =
                WorldCupSchedulerService.class.getDeclaredMethod(
                        "sendMatchesMessage", LocalDate.class, String.class);
        m.setAccessible(true);
        m.invoke(service, date, "Título");

        verify(telegramFacade).enviarMensagemHtml(eq(-100L), contains("Brasil"));
        verify(metricsService).success("worldcup_jogos_enviados");
    }

    // ============================================================
    // sendThirtyMinuteReminder
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
    // sendResultsToChat
    // ============================================================

    @Test
    @DisplayName("sendResultsToChat: com placar registra success('worldcup_resultados_enviados')")
    void sendResultsToChat_comPlacar_registraSuccess() {
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatchComPlacar(date, "Brazil", "Argentina", 2, 1);
        when(worldCupService.getMatchesForDay(any(LocalDate.class))).thenReturn(List.of(match));

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
        when(worldCupService.getMatchesForDay(any(LocalDate.class))).thenReturn(List.of());

        service.sendResultsToChat(chatId, date);

        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("Nenhum jogo"));
        verify(metricsService).error("worldcup_sem_jogos");
        verify(metricsService, never()).success("worldcup_resultados_enviados");
    }

    @Test
    @DisplayName("sendResultsToChat: disabled registra error('worldcup_desabilitado')")
    void sendResultsToChat_disabled_registraError() {
        when(featureFlags.isEnabled("worldcup.enabled")).thenReturn(false);
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
        when(worldCupService.getMatchesForDay(any(LocalDate.class))).thenReturn(List.of(match));

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
        when(worldCupService.getMatchesForDay(any(LocalDate.class))).thenReturn(List.of(match));

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
        when(worldCupService.getMatchesForDay(any(LocalDate.class))).thenReturn(List.of(match));

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
        when(worldCupService.getMatchesForDay(any(LocalDate.class))).thenReturn(List.of(match));

        service.sendResultsToChat(chatId, date);

        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("Jogador 20 (GC)"));
    }

    // ============================================================
    // sendMatchesToChat
    // ============================================================

    @Test
    @DisplayName("sendMatchesToChat: com jogos registra success")
    void sendMatchesToChat_comJogos() {
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatch(date, "Brazil", "Argentina", "12:00 UTC-3");
        when(worldCupService.getMatchesForDay(any(LocalDate.class))).thenReturn(List.of(match));

        service.sendMatchesToChat(chatId, date);

        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("JOGOS DE HOJE"));
        verify(metricsService).success("worldcup_jogos_enviados");
    }

    @Test
    @DisplayName("sendMatchesToChat: sem jogos registra error")
    void sendMatchesToChat_semJogos() {
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        when(worldCupService.getMatchesForDay(any(LocalDate.class))).thenReturn(List.of());

        service.sendMatchesToChat(chatId, date);

        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("Nenhum jogo programado"));
        verify(metricsService).error("worldcup_sem_jogos");
    }

    @Test
    @DisplayName("sendMatchesToChat: disabled registra error")
    void sendMatchesToChat_disabled() {
        when(featureFlags.isEnabled("worldcup.enabled")).thenReturn(false);
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

        service.sendMatchesToChat(chatId, date);

        verify(telegramFacade)
                .enviarMensagemHtml(eq(chatId), contains("Servico de Copa desativado"));
        verify(metricsService).error("worldcup_desabilitado");
    }

    // ============================================================
    // sendMatchesMessageToChat
    // ============================================================

    @Test
    @DisplayName("sendMatchesMessageToChat: com jogos registra success")
    void sendMatchesMessageToChat_comJogos() throws Exception {
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatch(date, "Brazil", "Argentina", "12:00 UTC-3");
        when(worldCupService.getMatchesForDay(any(LocalDate.class))).thenReturn(List.of(match));

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
        when(worldCupService.getMatchesForDay(any(LocalDate.class))).thenReturn(List.of());

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
        when(worldCupService.getMatchesForDay(any(LocalDate.class))).thenReturn(List.of(match));

        Method m =
                WorldCupSchedulerService.class.getDeclaredMethod(
                        "sendMatchesMessageToChat", long.class, LocalDate.class, String.class);
        m.setAccessible(true);
        m.invoke(service, chatId, date, "Título");

        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("Brasil"));
        verify(metricsService).success("worldcup_jogos_enviados");
    }

    // ============================================================
    // *ToChat (noon/evening/manual)
    // ============================================================

    @Test
    @DisplayName("sendNoonMatchesToChat: com jogos registra success")
    void sendNoonMatchesToChat_comJogos() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatch(today, "Brazil", "Argentina", "12:00 UTC-3");
        when(worldCupService.getMatchesForDay(any(LocalDate.class))).thenReturn(List.of(match));

        service.sendNoonMatchesToChat(123L);

        verify(telegramFacade).enviarMensagemHtml(eq(123L), contains("JOGOS DE HOJE (meio-dia)"));
        verify(metricsService).success("worldcup_jogos_enviados");
    }

    @Test
    @DisplayName("sendNoonMatchesToChat: disabled registra error")
    void sendNoonMatchesToChat_disabled() {
        when(featureFlags.isEnabled("worldcup.enabled")).thenReturn(false);
        service.sendNoonMatchesToChat(123L);
        verify(telegramFacade).enviarMensagemHtml(eq(123L), contains("Servico de Copa desativado"));
        verify(metricsService).error("worldcup_desabilitado");
    }

    @Test
    @DisplayName("sendEveningMatchesToChat: com jogos registra success")
    void sendEveningMatchesToChat_comJogos() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatch(today, "Brazil", "Argentina", "18:30 UTC-3");
        when(worldCupService.getMatchesForDay(any(LocalDate.class))).thenReturn(List.of(match));

        service.sendEveningMatchesToChat(123L);

        verify(telegramFacade).enviarMensagemHtml(eq(123L), contains("RESUMO DOS JOGOS DE HOJE"));
        verify(metricsService).success("worldcup_jogos_enviados");
    }

    @Test
    @DisplayName("sendEveningMatchesToChat: disabled registra error")
    void sendEveningMatchesToChat_disabled() {
        when(featureFlags.isEnabled("worldcup.enabled")).thenReturn(false);
        service.sendEveningMatchesToChat(123L);
        verify(telegramFacade).enviarMensagemHtml(eq(123L), contains("Servico de Copa desativado"));
        verify(metricsService).error("worldcup_desabilitado");
    }

    @Test
    @DisplayName("sendManualTestToChat: com jogos registra success")
    void sendManualTestToChat_comJogos() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatch(today, "Brazil", "Argentina", "12:00 UTC-3");
        when(worldCupService.getMatchesForDay(any(LocalDate.class))).thenReturn(List.of(match));

        service.sendManualTestToChat(123L);

        verify(telegramFacade).enviarMensagemHtml(eq(123L), contains("TESTE MANUAL"));
        verify(metricsService).success("worldcup_jogos_enviados");
    }

    @Test
    @DisplayName("sendManualTestToChat: disabled registra error")
    void sendManualTestToChat_disabled() {
        when(featureFlags.isEnabled("worldcup.enabled")).thenReturn(false);
        service.sendManualTestToChat(123L);
        verify(telegramFacade).enviarMensagemHtml(eq(123L), contains("Servico de Copa desativado"));
        verify(metricsService).error("worldcup_desabilitado");
    }

    // ============================================================
    // sendManualTest (broadcast)
    // ============================================================

    @Test
    @DisplayName("sendManualTest: disabled registra error('worldcup_desabilitado')")
    void sendManualTest_disabled() {
        when(featureFlags.isEnabled("worldcup.enabled")).thenReturn(false);
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
        when(worldCupService.getMatchesForDay(any(LocalDate.class))).thenReturn(List.of(match));

        service.sendManualTest();

        verify(telegramFacade).enviarMensagemHtml(eq(-100L), contains("TESTE MANUAL"));
        verify(metricsService).success("worldcup_jogos_enviados");
    }

    // ============================================================
    // LEMBRETES 30 MIN (com Clock fixo)
    // ============================================================

    @Test
    @DisplayName("checkThirtyMinutes: lembrete duplicado não reenvia")
    void checkThirtyMinutos_duplicadoNaoEnvia() {
        ZoneId zone = ZoneId.of("America/Sao_Paulo");
        LocalDate today = LocalDate.now(zone);
        WorldCupMatch match = criarMatch(today, "Brazil", "Argentina", "12:00 UTC-3");
        when(worldCupService.getMatchesForDay(any(LocalDate.class))).thenReturn(List.of(match));

        Set<String> sent = new HashSet<>();
        sent.add(today + "_Brazil_Argentina");
        ReflectionTestUtils.setField(service, "remindersSent", sent);

        service.checkThirtyMinutesBeforeEachMatch();
        verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
        verify(metricsService, never()).success("worldcup_lembrete_30min_enviado");
    }

    @Test
    @DisplayName("checkThirtyMinutes: worldcup desabilitado → pula")
    void checkThirtyMinutos_worldcupDisabled() {
        when(featureFlags.isEnabled("worldcup.enabled")).thenReturn(false);
        service.checkThirtyMinutesBeforeEachMatch();
        verifyNoInteractions(worldCupService, telegramFacade, metricsService);
    }

    @Test
    @DisplayName("checkThirtyMinutes: sem grupos → pula")
    void checkThirtyMinutos_semGrupos() {
        ReflectionTestUtils.setField(service, "allowedGroups", new HashSet<>());
        service.checkThirtyMinutesBeforeEachMatch();
        verifyNoInteractions(worldCupService, telegramFacade, metricsService);
    }

    // ============================================================
    // sendNoonMatches / sendEveningMatches (broadcast)
    // ============================================================

    @Test
    @DisplayName("sendNoonMatches: disabled não faz nada")
    void sendNoonMatches_disabled() {
        when(featureFlags.isEnabled("worldcup.enabled")).thenReturn(false);
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
        when(worldCupService.getMatchesForDay(any(LocalDate.class))).thenReturn(List.of(match));

        service.sendNoonMatches();

        verify(telegramFacade).enviarMensagemHtml(eq(-100L), contains("JOGOS DE HOJE (meio-dia)"));
        verify(metricsService).success("worldcup_jogos_enviados");
    }

    @Test
    @DisplayName("sendEveningMatches: com jogos registra success")
    void sendEveningMatches_comJogos() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatch(today, "Brazil", "Argentina", "18:30 UTC-3");
        when(worldCupService.getMatchesForDay(any(LocalDate.class))).thenReturn(List.of(match));

        service.sendEveningMatches();

        verify(telegramFacade).enviarMensagemHtml(eq(-100L), contains("RESUMO DOS JOGOS DE HOJE"));
        verify(metricsService).success("worldcup_jogos_enviados");
    }

    // ============================================================
    // cleanReminders
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
    // reloadWorldCup
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
    // init
    // ============================================================

    @Test
    @DisplayName("init: disabled não configura grupos")
    void init_disabled() {
        when(featureFlags.isEnabled("worldcup.enabled")).thenReturn(false);
        Set<Long> groups = new HashSet<>();
        ReflectionTestUtils.setField(service, "allowedGroups", groups);
        service.init();
        assertThat(groups).isEmpty();
    }

    @Test
    @DisplayName("init: ignora IDs inválidos e positivos")
    void init_idsInvalidos() {
        ReflectionTestUtils.setField(service, "allowedChatsStr", "-100,abc,123,-200");
        Set<Long> groups = new HashSet<>();
        ReflectionTestUtils.setField(service, "allowedGroups", groups);
        service.init();
        assertThat(groups).containsExactlyInAnyOrder(-100L, -200L);
    }

    // ============================================================
    // Fluxos complexos
    // ============================================================

    @Test
    @DisplayName("Fluxo complexo: múltiplas chamadas somam métricas corretamente")
    void fluxoComplexo() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatch(today, "Brazil", "Argentina", "12:00 UTC-3");
        when(worldCupService.getMatchesForDay(any(LocalDate.class))).thenReturn(List.of(match));

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
        when(worldCupService.getMatchesForDay(any(LocalDate.class))).thenReturn(List.of());
        service.sendNoonMatches();
        verify(metricsService).error("worldcup_sem_jogos");

        // 2) Com jogos
        WorldCupMatch match = criarMatch(today, "Brazil", "Argentina", "12:00 UTC-3");
        when(worldCupService.getMatchesForDay(any(LocalDate.class))).thenReturn(List.of(match));
        service.sendEveningMatches();

        verify(metricsService, times(1)).error("worldcup_sem_jogos");
        verify(metricsService, times(1)).success("worldcup_jogos_enviados");
    }

    // ============================================================
    // configureTasks
    // ============================================================

    @Test
    @DisplayName("configureTasks: deve registrar as tasks e invocar getCron cobrindo a lambda")
    void configureTasks_deveRegistrarAsTasksECobrirLambda() {
        lenient()
                .when(jsonConfigLoader.loadConfig(anyString(), eq(Map.class), anyString()))
                .thenReturn(
                        Optional.of(
                                Map.of(
                                        "cron", "0 0 12 * * *",
                                        "updateCron", "0 0 12 * * *",
                                        "hourlyCron", "0 0 12 * * *",
                                        "weeklyCron", "0 0 12 * * *",
                                        "morningCron", "0 0 12 * * *",
                                        "eveningCron", "0 0 12 * * *",
                                        "noonCron", "0 0 12 * * *",
                                        "checkCron", "0 0 12 * * *",
                                        "cleanCron", "0 0 12 * * *")));

        ScheduledTaskRegistrar taskRegistrar = mock(ScheduledTaskRegistrar.class);

        service.configureTasks(taskRegistrar);

        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(taskRegistrar, atLeastOnce())
                .addTriggerTask(any(Runnable.class), triggerCaptor.capture());

        SimpleTriggerContext ctx = new SimpleTriggerContext();
        for (Trigger trigger : triggerCaptor.getAllValues()) {
            trigger.nextExecution(ctx);
        }
    }

    @Test
    @DisplayName("init: lê o worldcup-config.json exatamente uma vez")
    void init_leConfigUmaVez() {
        // setUp já chamou service.init() no @BeforeEach
        // O loadCrons() usa 1 chamada para ler o JSON (não 5)

        verify(jsonConfigLoader, times(1))
                .loadConfig(
                        "config/worldcup-config.json", Map.class, "config/worldcup-config.json");
    }

    @Test
    @DisplayName("configureTasks: não lê o JSON ao calcular a próxima execução")
    void configureTasks_naoLerJsonAoCriarTrigger() {
        clearInvocations(jsonConfigLoader); // zera o contador do init()

        ScheduledTaskRegistrar taskRegistrar = mock(ScheduledTaskRegistrar.class);
        service.configureTasks(taskRegistrar);

        // Captura todas as triggers registradas
        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(taskRegistrar, atLeastOnce())
                .addTriggerTask(any(Runnable.class), triggerCaptor.capture());

        // Invoca nextExecution várias vezes para simular o scheduler rodando
        SimpleTriggerContext ctx = new SimpleTriggerContext();
        for (int i = 0; i < 10; i++) {
            for (Trigger trigger : triggerCaptor.getAllValues()) {
                trigger.nextExecution(ctx);
            }
        }

        // ✅ Nenhuma leitura de JSON deve ter acontecido durante o cálculo das triggers
        verify(jsonConfigLoader, never()).loadConfig(anyString(), eq(Map.class), anyString());
    }

    @Test
    @DisplayName("init: chave 'noonCron' ausente → usa default")
    void init_noonCronAusente_usaDefault() {
        // Stub do JSON sem "noonCron"
        var configSemNoonCron =
                Map.of(
                        "eveningCron", "0 30 18 * * *",
                        "checkCron", "0 * * * * *",
                        "cleanCron", "0 5 0 * * *");

        doReturn(Optional.of(configSemNoonCron))
                .when(jsonConfigLoader)
                .loadConfig(
                        "config/worldcup-config.json", Map.class, "config/worldcup-config.json");

        // Cria um Clock fixo para o novo serviço
        ZoneId zone = ZoneId.of("America/Sao_Paulo");
        Clock fixedClock = Clock.fixed(Instant.parse("2026-07-01T14:30:01Z"), zone);

        // Recria o serviço para o init() rodar com o novo stub
        service =
                new WorldCupSchedulerService(
                        worldCupService,
                        telegramFacade,
                        worldCupUpdaterService,
                        fixedClock,
                        metricsService,
                        featureFlags,
                        jsonConfigLoader,
                        teamsService);
        service.init();

        // Verifica que o default foi aplicado
        String noonCronValue = (String) ReflectionTestUtils.getField(service, "noonCron");
        assertThat(noonCronValue).isEqualTo("0 0 12 * * *");
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
