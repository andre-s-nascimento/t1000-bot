package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
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

@ExtendWith(MockitoExtension.class)
class WorldCupSchedulerServiceTest {

    @Mock private StaticWorldCupService worldCupService;

    @Mock private TelegramFacade telegramFacade;

    @Mock private WorldCupUpdaterService worldCupUpdaterService;

    @InjectMocks private WorldCupSchedulerService service;

    @BeforeEach
    void setUp() {
        Set<Long> mutableAllowedGroups = new HashSet<>();
        mutableAllowedGroups.add(-100L);
        ReflectionTestUtils.setField(service, "allowedGroups", mutableAllowedGroups);
        ReflectionTestUtils.setField(service, "worldcupEnabled", true);
        ReflectionTestUtils.setField(service, "allowedChatsStr", "");
        service.init();
    }

    // =========================
    // TESTES DE TRADUÇÃO E BANDEIRAS
    // =========================

    @Test
    void translateTeam_deveTraduzirTimesConhecidos() throws Exception {
        Method method =
                WorldCupSchedulerService.class.getDeclaredMethod("translateTeam", String.class);
        method.setAccessible(true);
        assertThat(method.invoke(service, "brazil")).isEqualTo("Brasil");
        assertThat(method.invoke(service, "england")).isEqualTo("Inglaterra");
        assertThat(method.invoke(service, "unknown")).isEqualTo("unknown");
    }

    @Test
    void flagEmoji_deveRetornarBandeiraParaTimeConhecido() throws Exception {
        Method method = WorldCupSchedulerService.class.getDeclaredMethod("flagEmoji", String.class);
        method.setAccessible(true);
        assertThat(method.invoke(service, "brazil")).isEqualTo("🇧🇷");
        assertThat(method.invoke(service, "france")).isEqualTo("🇫🇷");
        assertThat(method.invoke(service, "unknown")).isEqualTo("🏳️");
    }

    @Test
    void parseMinuteToInt_deveConverterCorretamente() throws Exception {
        Method method =
                WorldCupSchedulerService.class.getDeclaredMethod("parseMinuteToInt", String.class);
        method.setAccessible(true);
        assertThat((int) method.invoke(service, "45+3")).isEqualTo(48);
        assertThat((int) method.invoke(service, "90+7")).isEqualTo(97);
        assertThat((int) method.invoke(service, "6")).isEqualTo(6);
        assertThat((int) method.invoke(service, (Object) null)).isZero();
        assertThat((int) method.invoke(service, "abc")).isZero();
    }

    // =========================
    // TESTES DE ENVIO DE MENSAGENS
    // =========================

    @Test
    void sendMatchesMessage_deveEnviarJogosParaGrupos() throws Exception {
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatch(date, "Brazil", "Argentina", "12:00 UTC-3");
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of(match));

        Method method =
                WorldCupSchedulerService.class.getDeclaredMethod(
                        "sendMatchesMessage", LocalDate.class, String.class);
        method.setAccessible(true);
        method.invoke(service, date, "Título");

        verify(telegramFacade).enviarMensagemHtml(eq(-100L), anyString());
    }

    @Test
    void sendMatchesMessage_deveIgnorarSeNaoHaJogos() throws Exception {
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of());

        Method method =
                WorldCupSchedulerService.class.getDeclaredMethod(
                        "sendMatchesMessage", LocalDate.class, String.class);
        method.setAccessible(true);
        method.invoke(service, date, "Título");

        verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
    }

    @Test
    void sendMatchesMessageToChat_deveEnviarParaChatEspecifico() throws Exception {
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatch(date, "Brazil", "Argentina", "12:00 UTC-3");
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of(match));

        Method method =
                WorldCupSchedulerService.class.getDeclaredMethod(
                        "sendMatchesMessageToChat", long.class, LocalDate.class, String.class);
        method.setAccessible(true);
        method.invoke(service, chatId, date, "Título");

        verify(telegramFacade).enviarMensagemHtml(eq(chatId), anyString());
    }

    @Test
    void sendMatchesToChat_deveAvisarSeSemJogos() {
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of());

        service.sendMatchesToChat(chatId, date);

        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("Nenhum jogo programado"));
    }

    @Test
    void sendMatchesToChat_quandoWorldcupDesabilitado_enviaMensagemDesativado() {
        ReflectionTestUtils.setField(service, "worldcupEnabled", false);
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        service.sendMatchesToChat(chatId, date);
        verify(telegramFacade)
                .enviarMensagemHtml(eq(chatId), contains("Servico de Copa desativado"));
    }

    // =========================
    // TESTES DE RESULTADOS
    // =========================

    @Test
    void sendResultsToChat_deveEnviarPlacarComGols() {
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatchComPlacar(date, "Brazil", "Argentina", 2, 1);
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of(match));

        service.sendResultsToChat(chatId, date);

        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("2 x 1"));
        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("⚽"));
    }

    @Test
    void sendResultsToChat_deveIncluirProrrogacaoEPenaltis() {
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        Score score = new Score(List.of(1, 1), null, List.of(2, 1), List.of(4, 3));
        WorldCupMatch match = criarMatchComScore(date, "Brazil", "Argentina", score);
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of(match));

        service.sendResultsToChat(chatId, date);

        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("(pro) 2-1"));
        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("(pen) 4-3"));
    }

    @Test
    void sendResultsToChat_deveMostrarAguardandoQuandoSemPlacar() {
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatchSemPlacar(date, "Brazil", "Argentina");
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of(match));

        service.sendResultsToChat(chatId, date);

        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("Aguardando resultado"));
    }

    @Test
    void sendResultsToChat_quandoNaoHaJogos_enviaMensagem() {
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of());
        service.sendResultsToChat(chatId, date);
        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("Nenhum jogo"));
    }

    @Test
    void sendResultsToChat_quandoWorldcupDesabilitado_enviaMensagemDesativado() {
        ReflectionTestUtils.setField(service, "worldcupEnabled", false);
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        service.sendResultsToChat(chatId, date);
        verify(telegramFacade)
                .enviarMensagemHtml(eq(chatId), contains("Servico de Copa desativado"));
    }

    @Test
    void sendResultsToChat_comGolsEmAmbosOsTimes_incluiGolsCorretamente() {
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        Score score = new Score(List.of(2, 1), null, null, null);
        Goal gol1 = new Goal("Neymar", "12", false, false);
        Goal gol2 = new Goal("Mbappe", "34", true, false);
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
                        List.of(gol1),
                        List.of(gol2));
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of(match));

        service.sendResultsToChat(chatId, date);

        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("Neymar 12"));
        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("Mbappe 34 (P)"));
    }

    @Test
    void sendResultsToChat_comGolContra_incluiGC() {
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        Score score = new Score(List.of(1, 0), null, null, null);
        Goal gol = new Goal("Jogador", "20", false, true);
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
                        List.of(gol),
                        List.of());
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of(match));

        service.sendResultsToChat(chatId, date);

        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("Jogador 20 (GC)"));
    }

    // =========================
    // TESTES DE LEMBRETES (30 MINUTOS)
    // =========================

    @Test
    void checkThirtyMinutesBeforeEachMatch_quandoLembreteJaEnviado_naoReenvia() {
        ZoneId zone = ZoneId.of("America/Sao_Paulo");
        LocalDate today = LocalDate.now(zone);
        WorldCupMatch match = criarMatch(today, "Brazil", "Argentina", "12:00 UTC-3");
        when(worldCupService.getMatchesForDay(today)).thenReturn(List.of(match));

        Set<String> remindersSent = new HashSet<>();
        String key = today + "_Brazil_Argentina";
        remindersSent.add(key);
        ReflectionTestUtils.setField(service, "remindersSent", remindersSent);

        service.checkThirtyMinutesBeforeEachMatch();
        verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
    }

    // =========================
    // TESTES DOS MÉTODOS AGENDADOS
    // =========================

    @Test
    void sendNoonMatches_quandoDesabilitado_naoFazNada() {
        ReflectionTestUtils.setField(service, "worldcupEnabled", false);
        service.sendNoonMatches();
        verifyNoInteractions(worldCupService, telegramFacade);
    }

    @Test
    void sendNoonMatches_quandoSemGrupos_naoFazNada() {
        ReflectionTestUtils.setField(service, "worldcupEnabled", true);
        Set<Long> empty = new HashSet<>();
        ReflectionTestUtils.setField(service, "allowedGroups", empty);
        service.sendNoonMatches();
        verifyNoInteractions(worldCupService, telegramFacade);
    }

    @Test
    void sendEveningMatches_quandoDesabilitado_naoFazNada() {
        ReflectionTestUtils.setField(service, "worldcupEnabled", false);
        service.sendEveningMatches();
        verifyNoInteractions(worldCupService, telegramFacade);
    }

    @Test
    void checkThirtyMinutesBeforeEachMatch_quandoDesabilitado_naoFazNada() {
        ReflectionTestUtils.setField(service, "worldcupEnabled", false);
        service.checkThirtyMinutesBeforeEachMatch();
        verifyNoInteractions(worldCupService, telegramFacade);
    }

    @Test
    void checkThirtyMinutesBeforeEachMatch_quandoSemGrupos_naoFazNada() {
        ReflectionTestUtils.setField(service, "worldcupEnabled", true);
        Set<Long> empty = new HashSet<>();
        ReflectionTestUtils.setField(service, "allowedGroups", empty);
        service.checkThirtyMinutesBeforeEachMatch();
        verifyNoInteractions(worldCupService, telegramFacade);
    }

    @Test
    void cleanReminders_deveLimparSet() {
        Set<String> reminders = new HashSet<>();
        reminders.add("key1");
        reminders.add("key2");
        ReflectionTestUtils.setField(service, "remindersSent", reminders);
        assertThat(reminders).hasSize(2);

        service.cleanReminders();

        assertThat(reminders).isEmpty();
    }

    // =========================
    // TESTES DOS MÉTODOS DE ENVIO PARA CHAT ESPECÍFICO
    // =========================

    @Test
    void sendNoonMatchesToChat_quandoWorldcupDesabilitado_enviaMensagemDesativado() {
        ReflectionTestUtils.setField(service, "worldcupEnabled", false);
        long chatId = 12345L;
        service.sendNoonMatchesToChat(chatId);
        verify(telegramFacade)
                .enviarMensagemHtml(eq(chatId), contains("Servico de Copa desativado"));
    }

    @Test
    void sendEveningMatchesToChat_quandoWorldcupDesabilitado_enviaMensagemDesativado() {
        ReflectionTestUtils.setField(service, "worldcupEnabled", false);
        long chatId = 12345L;
        service.sendEveningMatchesToChat(chatId);
        verify(telegramFacade)
                .enviarMensagemHtml(eq(chatId), contains("Servico de Copa desativado"));
    }

    @Test
    void sendManualTestToChat_quandoWorldcupDesabilitado_enviaMensagemDesativado() {
        ReflectionTestUtils.setField(service, "worldcupEnabled", false);
        long chatId = 12345L;
        service.sendManualTestToChat(chatId);
        verify(telegramFacade)
                .enviarMensagemHtml(eq(chatId), contains("Servico de Copa desativado"));
    }

    // =========================
    // TESTE DE sendManualTest
    // =========================

    @Test
    void sendManualTest_quandoWorldcupDesabilitado_logaWarn() {
        ReflectionTestUtils.setField(service, "worldcupEnabled", false);
        service.sendManualTest();
        verifyNoInteractions(telegramFacade);
    }

    @Test
    void sendManualTest_quandoSemGrupos_logaWarn() {
        ReflectionTestUtils.setField(service, "worldcupEnabled", true);
        Set<Long> empty = new HashSet<>();
        ReflectionTestUtils.setField(service, "allowedGroups", empty);
        service.sendManualTest();
        verifyNoInteractions(telegramFacade);
    }

    @Test
    void sendManualTest_quandoTudoOk_enviaMensagem() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatch(today, "Brazil", "Argentina", "12:00 UTC-3");
        when(worldCupService.getMatchesForDay(today)).thenReturn(List.of(match));
        service.sendManualTest();
        verify(telegramFacade).enviarMensagemHtml(eq(-100L), contains("TESTE MANUAL"));
    }

    // =========================
    // TESTE DE reloadWorldCup
    // =========================

    @Test
    void reloadWorldCup_deveChamarForceUpdate() {
        ResponseEntity<String> response = service.reloadWorldCup();
        verify(worldCupUpdaterService).forceUpdate();
        assertThat(response.getBody()).contains("recarregados");
    }

    // =========================
    // INICIALIZAÇÃO
    // =========================

    @Test
    void init_quandoWorldcupDesabilitado_naoConfiguraGrupos() {
        ReflectionTestUtils.setField(service, "worldcupEnabled", false);
        Set<Long> allowedGroups = new HashSet<>();
        ReflectionTestUtils.setField(service, "allowedGroups", allowedGroups);
        service.init();
        assertThat(allowedGroups).isEmpty();
        verifyNoInteractions(telegramFacade);
    }

    @Test
    void init_comIdsInvalidos_ignoraLog() {
        ReflectionTestUtils.setField(service, "worldcupEnabled", true);
        ReflectionTestUtils.setField(service, "allowedChatsStr", "-100,abc,123");
        Set<Long> allowedGroups = new HashSet<>();
        ReflectionTestUtils.setField(service, "allowedGroups", allowedGroups);
        service.init();
        assertThat(allowedGroups).containsExactly(-100L);
    }

    // =========================
    // TESTES DE flagEmoji (FALLBACK)
    // =========================

    @ParameterizedTest
    @MethodSource("flagEmojiProvider")
    void flagEmoji_deveRetornarBandeiraCorreta(String input, String expected) throws Exception {
        Method method = WorldCupSchedulerService.class.getDeclaredMethod("flagEmoji", String.class);
        method.setAccessible(true);
        assertThat(method.invoke(service, input)).isEqualTo(expected);
    }

    static Stream<Arguments> flagEmojiProvider() {
        return Stream.of(
                Arguments.of(null, "🏳️"),
                Arguments.of("", "🏳️"),
                Arguments.of("BRAZIL", "🇧🇷"),
                Arguments.of("xyzzz", "🏳️"));
    }

    // =========================
    // TESTES DE translateTeam (FALLBACK)
    // =========================

    @ParameterizedTest
    @MethodSource("translateTeamProvider")
    void translateTeam_deveTraduzirCorretamente(String input, String expected) throws Exception {
        Method method =
                WorldCupSchedulerService.class.getDeclaredMethod("translateTeam", String.class);
        method.setAccessible(true);
        assertThat(method.invoke(service, input)).isEqualTo(expected);
    }

    static Stream<Arguments> translateTeamProvider() {
        return Stream.of(
                Arguments.of(null, "?"),
                Arguments.of("", "?"),
                Arguments.of("brazil", "Brasil"),
                Arguments.of("unknown", "unknown"));
    }

    // =========================
    // HELPERS
    // =========================

    private WorldCupMatch criarMatch(LocalDate date, String team1, String team2, String time) {
        return new WorldCupMatch(
                "Round",
                date.toString(),
                time,
                team1,
                team2,
                "Group",
                "Stadium",
                null,
                List.of(),
                List.of());
    }

    private WorldCupMatch criarMatchComPlacar(
            LocalDate date, String team1, String team2, int g1, int g2) {
        Score score = new Score(List.of(g1, g2), null, null, null);
        return new WorldCupMatch(
                "Round",
                date.toString(),
                "12:00 UTC-3",
                team1,
                team2,
                "Group",
                "Stadium",
                score,
                List.of(new Goal("Jogador", "30", false, false)),
                List.of());
    }

    private WorldCupMatch criarMatchComScore(
            LocalDate date, String team1, String team2, Score score) {
        return new WorldCupMatch(
                "Round",
                date.toString(),
                "12:00 UTC-3",
                team1,
                team2,
                "Group",
                "Stadium",
                score,
                List.of(),
                List.of());
    }

    private WorldCupMatch criarMatchSemPlacar(LocalDate date, String team1, String team2) {
        return new WorldCupMatch(
                "Round",
                date.toString(),
                "12:00 UTC-3",
                team1,
                team2,
                "Group",
                "Stadium",
                null,
                List.of(),
                List.of());
    }
}
