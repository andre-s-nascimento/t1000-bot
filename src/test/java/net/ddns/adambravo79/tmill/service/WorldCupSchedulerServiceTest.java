package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
        String resultado = ReflectionTestUtils.invokeMethod(service, "translateTeam", "brazil");
        assertThat(resultado).isEqualTo("Brasil");

        String resultado2 = ReflectionTestUtils.invokeMethod(service, "translateTeam", "england");
        assertThat(resultado2).isEqualTo("Inglaterra");

        String resultado3 = ReflectionTestUtils.invokeMethod(service, "translateTeam", "unknown");
        assertThat(resultado3).isEqualTo("unknown");
    }

    @Test
    void flagEmoji_deveRetornarBandeiraParaTimeConhecido() throws Exception {
        String resultado = ReflectionTestUtils.invokeMethod(service, "flagEmoji", "brazil");
        assertThat(resultado).isEqualTo("🇧🇷");

        String resultado2 = ReflectionTestUtils.invokeMethod(service, "flagEmoji", "france");
        assertThat(resultado2).isEqualTo("🇫🇷");

        String resultado3 = ReflectionTestUtils.invokeMethod(service, "flagEmoji", "unknown");
        assertThat(resultado3).isEqualTo("🏳️");
    }

    @Test
    void parseMinuteToInt_deveConverterCorretamente() throws Exception {
        java.lang.reflect.Method method =
                WorldCupSchedulerService.class.getDeclaredMethod("parseMinuteToInt", String.class);
        method.setAccessible(true);

        int resultado = (int) method.invoke(service, "45+3");
        assertThat(resultado).isEqualTo(48);

        int resultado2 = (int) method.invoke(service, "90+7");
        assertThat(resultado2).isEqualTo(97);

        int resultado3 = (int) method.invoke(service, "6");
        assertThat(resultado3).isEqualTo(6);

        int resultado4 = (int) method.invoke(service, (Object) null);
        assertThat(resultado4).isEqualTo(0);

        int resultado5 = (int) method.invoke(service, "abc");
        assertThat(resultado5).isEqualTo(0);
    }

    // =========================
    // TESTES DE ENVIO DE MENSAGENS
    // =========================

    @Test
    void sendMatchesMessage_deveEnviarJogosParaGrupos() throws Exception {
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatch(date, "Brazil", "Argentina", "12:00 UTC-3");
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of(match));

        ReflectionTestUtils.invokeMethod(service, "sendMatchesMessage", date, "Título");

        verify(telegramFacade).enviarMensagemHtml(eq(-100L), anyString());
    }

    @Test
    void sendMatchesMessage_deveIgnorarSeNaoHaJogos() throws Exception {
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of());

        ReflectionTestUtils.invokeMethod(service, "sendMatchesMessage", date, "Título");

        verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
    }

    @Test
    void sendMatchesMessageToChat_deveEnviarParaChatEspecifico() throws Exception {
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatch(date, "Brazil", "Argentina", "12:00 UTC-3");
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of(match));

        ReflectionTestUtils.invokeMethod(
                service, "sendMatchesMessageToChat", chatId, date, "Título");

        verify(telegramFacade).enviarMensagemHtml(eq(chatId), anyString());
    }

    @Test
    void sendMatchesToChat_deveAvisarSeSemJogos() throws Exception {
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of());

        service.sendMatchesToChat(chatId, date);

        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("Nenhum jogo programado"));
    }

    // =========================
    // TESTES DE RESULTADOS
    // =========================

    @Test
    void sendResultsToChat_deveEnviarPlacarComGols() throws Exception {
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatchComPlacar(date, "Brazil", "Argentina", 2, 1);
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of(match));

        service.sendResultsToChat(chatId, date);

        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("2 x 1"));
        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("⚽"));
    }

    @Test
    void sendResultsToChat_deveIncluirProrrogacaoEPenaltis() throws Exception {
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
    void sendResultsToChat_deveMostrarAguardandoQuandoSemPlacar() throws Exception {
        long chatId = 12345L;
        LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        WorldCupMatch match = criarMatchSemPlacar(date, "Brazil", "Argentina");
        when(worldCupService.getMatchesForDay(date)).thenReturn(List.of(match));

        service.sendResultsToChat(chatId, date);

        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("Aguardando resultado"));
    }

    @Test
    void cleanReminders_deveLimparSet() throws Exception {
        Set<String> reminders = new HashSet<>();
        reminders.add("key1");
        reminders.add("key2");
        ReflectionTestUtils.setField(service, "remindersSent", reminders);
        assertThat(reminders).hasSize(2);

        service.cleanReminders();

        assertThat(reminders).isEmpty();
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
