/* (c) 2026 | 11/06/2026 */

package net.ddns.adambravo79.tmill.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.model.Goal;
import net.ddns.adambravo79.tmill.model.Score;
import net.ddns.adambravo79.tmill.model.WorldCupMatch;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;

@Slf4j
@Service
public class WorldCupSchedulerService {

    private static final String POSICAO_PLACAR = "%s (%s) x (%s) %s - %s";
    private static final String FECHA_BOLD = "</b>\n\n";
    private final StaticWorldCupService worldCupService;
    private final TelegramFacade telegramFacade;
    private final Set<Long> allowedGroups = new HashSet<>();
    private final Set<String> remindersSent = ConcurrentHashMap.newKeySet();
    private final WorldCupUpdaterService worldCupUpdaterService;
    private final Clock clock;

    @Value(BotMessages.DEFAULT_WORLDCUP_ENABLED)
    private boolean worldcupEnabled;

    @Value(BotMessages.DEFAULT_BOT_ALLOWED_CHATS)
    private String allowedChatsStr;

    public WorldCupSchedulerService(
            StaticWorldCupService worldCupService,
            TelegramFacade telegramFacade,
            WorldCupUpdaterService worldCupUpdaterService,
            Clock clock) {
        this.worldCupService = worldCupService;
        this.telegramFacade = telegramFacade;
        this.worldCupUpdaterService = worldCupUpdaterService;
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
    }

    @PostConstruct
    public void init() {
        if (!worldcupEnabled) return;
        if (allowedChatsStr != null && !allowedChatsStr.isBlank()) {
            for (String s : allowedChatsStr.split(",")) {
                try {
                    long id = Long.parseLong(s.trim());
                    if (id < 0) allowedGroups.add(id);
                } catch (NumberFormatException e) {
                    log.warn("ID invalido para Copa: {}", s);
                }
            }
        }
        log.info("🏆 Servico de Copa ativo para grupos: {}", allowedGroups);
    }

    @Scheduled(cron = "0 0 12 * * *", zone = BotMessages.BRAZIL_ZONE)
    public void sendNoonMatches() {
        if (!worldcupEnabled || allowedGroups.isEmpty()) return;
        LocalDate today = LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE));
        sendMatchesMessage(today, "🏆 JOGOS DE HOJE (meio-dia)");
    }

    @Scheduled(cron = "0 30 18 * * *", zone = BotMessages.BRAZIL_ZONE)
    public void sendEveningMatches() {
        if (!worldcupEnabled || allowedGroups.isEmpty()) return;
        LocalDate today = LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE));
        sendMatchesMessage(today, "🏆 RESUMO DOS JOGOS DE HOJE");
    }

    @Scheduled(cron = "0 * * * * *", zone = BotMessages.BRAZIL_ZONE)
    public void checkThirtyMinutesBeforeEachMatch() {
        if (!worldcupEnabled || allowedGroups.isEmpty()) return;
        LocalDate today = LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE));
        LocalDateTime now = LocalDateTime.now(clock);

        List<WorldCupMatch> matches = worldCupService.getMatchesForDay(today);
        for (WorldCupMatch match : matches) {
            ZonedDateTime matchZdt = match.getMatchDateTime(ZoneId.of(BotMessages.BRAZIL_ZONE));
            LocalDateTime matchTime = matchZdt.toLocalDateTime();
            LocalDateTime reminderTime = matchTime.minusMinutes(30);
            String reminderKey = today + "_" + match.homeTeam() + "_" + match.awayTeam();

            if (now.isAfter(reminderTime)
                    && now.isBefore(matchTime)
                    && remindersSent.add(reminderKey)) {
                sendThirtyMinuteReminder(match);
                log.info(
                        "⏰ Aviso enviado para jogo: {} vs {} as {}",
                        match.homeTeam(),
                        match.awayTeam(),
                        matchTime);
            }
        }
    }

    @Scheduled(cron = "0 5 0 * * *", zone = BotMessages.BRAZIL_ZONE)
    public void cleanReminders() {
        remindersSent.clear();
        log.info("🧹 Avisos de jogos limpos para um novo dia");
    }

    private void sendMatchesMessage(LocalDate date, String title) {
        List<WorldCupMatch> matches = worldCupService.getMatchesForDay(date);
        if (matches.isEmpty()) {
            log.info("Nenhum jogo na data {}", date);
            return;
        }

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(BotMessages.FMT_HH_MM);
        StringBuilder sb = new StringBuilder();
        sb.append("<b>").append(title).append(FECHA_BOLD);
        for (WorldCupMatch m : matches) {
            ZonedDateTime localTime = m.getMatchDateTime(ZoneId.of(BotMessages.BRAZIL_ZONE));
            String line =
                    String.format(
                            POSICAO_PLACAR,
                            translateTeam(m.homeTeam()),
                            flagEmoji(m.homeTeam()),
                            flagEmoji(m.awayTeam()),
                            translateTeam(m.awayTeam()),
                            localTime.format(timeFormatter));
            if (m.stadium() != null && !m.stadium().isBlank()) {
                line += " - " + m.stadium();
            }
            sb.append(line).append("\n");
        }
        String message = sb.toString();
        for (Long groupId : allowedGroups) {
            telegramFacade.enviarMensagemHtml(groupId, message);
        }
    }

    private void sendThirtyMinuteReminder(WorldCupMatch match) {
        ZonedDateTime localTime = match.getMatchDateTime(ZoneId.of(BotMessages.BRAZIL_ZONE));
        // Use a text block for the multi-line message
        String message =
                """
        <b>⏰ Faltam 30 minutos para o inicio do jogo!</b>

        ⚽ %s (%s) x (%s) %s - %s
        """
                        .formatted(
                                translateTeam(match.homeTeam()),
                                flagEmoji(match.homeTeam()),
                                flagEmoji(match.awayTeam()),
                                translateTeam(match.awayTeam()),
                                localTime.format(
                                        DateTimeFormatter.ofPattern(BotMessages.FMT_HH_MM)));
        for (Long groupId : allowedGroups) {
            telegramFacade.enviarMensagemHtml(groupId, message);
        }
    }

    public void sendResultsToChat(long chatId, LocalDate date) {
        if (!worldcupEnabled) {
            telegramFacade.enviarMensagemHtml(chatId, BotMessages.WORLD_CUP_DISABLED);
            return;
        }

        List<WorldCupMatch> matches = worldCupService.getMatchesForDay(date);
        if (matches.isEmpty()) {
            telegramFacade.enviarMensagemHtml(
                    chatId,
                    BotMessages.WORLD_CUP_NO_MATCHES_DATE
                            + date.format(DateTimeFormatter.ofPattern(BotMessages.FMT_DD_MM_YYYY)));
            return;
        }

        String resultsMessage = buildResultsMessage(date, matches);
        telegramFacade.enviarMensagemHtml(chatId, resultsMessage);
    }

    private String buildResultsMessage(LocalDate date, List<WorldCupMatch> matches) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(BotMessages.FMT_DD_MM_YYYY);
        StringBuilder sb = new StringBuilder();
        sb.append("<b>📊 RESULTADOS - ").append(date.format(dateFormatter)).append(FECHA_BOLD);

        for (WorldCupMatch match : matches) {
            sb.append(formatMatchResult(match));
            sb.append("\n");
        }
        return sb.toString();
    }

    private String formatMatchResult(WorldCupMatch match) {
        StringBuilder sb = new StringBuilder();
        if (!match.hasScore()) {
            sb.append("⏳ ")
                    .append(translateTeam(match.homeTeam()))
                    .append(" (")
                    .append(flagEmoji(match.homeTeam()))
                    .append(") x (")
                    .append(flagEmoji(match.awayTeam()))
                    .append(") ")
                    .append(translateTeam(match.awayTeam()))
                    .append(" - Aguardando resultado\n\n");
            return sb.toString();
        }

        // Build the header with scores and possible extra time/penalties
        sb.append(formatScoreHeader(match));

        // Append goals
        List<Goal> allGoals = collectAllGoals(match);
        allGoals.sort(Comparator.comparingInt(g -> parseMinuteToInt(g.minute())));

        for (Goal goal : allGoals) {
            sb.append(formatGoal(goal, match));
        }
        sb.append("\n");
        return sb.toString();
    }

    private String formatScoreHeader(WorldCupMatch match) {
        Score score = match.score();
        List<Integer> ft = score.ft();
        int homeGoals = ft.get(0);
        int awayGoals = ft.get(1);

        StringBuilder header = new StringBuilder();
        header.append(flagEmoji(match.homeTeam()))
                .append(" ")
                .append(translateTeam(match.homeTeam()))
                .append(" ")
                .append(homeGoals)
                .append(" x ")
                .append(awayGoals)
                .append(" ")
                .append(translateTeam(match.awayTeam()))
                .append(" ")
                .append(flagEmoji(match.awayTeam()));

        if (score.et() != null && score.et().size() == 2) {
            int etHome = score.et().get(0);
            int etAway = score.et().get(1);
            header.append(" (pro) ").append(etHome).append("-").append(etAway);
        }

        if (score.p() != null && score.p().size() == 2) {
            int pHome = score.p().get(0);
            int pAway = score.p().get(1);
            header.append(" (pen) ").append(pHome).append("-").append(pAway);
        }

        return header.append("\n").toString();
    }

    private List<Goal> collectAllGoals(WorldCupMatch match) {
        List<Goal> allGoals = new ArrayList<>();
        if (match.goals1() != null) {
            allGoals.addAll(match.goals1());
        }
        if (match.goals2() != null) {
            allGoals.addAll(match.goals2());
        }
        return allGoals;
    }

    private String formatGoal(Goal goal, WorldCupMatch match) {
        String team = determineGoalTeam(goal, match);
        StringBuilder line =
                new StringBuilder("  ⚽ ")
                        .append(flagEmoji(team))
                        .append(" ")
                        .append(goal.name())
                        .append(" ")
                        .append(goal.minute());
        if (Boolean.TRUE.equals(goal.penalty())) line.append(" (P)");
        if (Boolean.TRUE.equals(goal.owngoal())) line.append(" (GC)");
        line.append("\n");
        return line.toString();
    }

    private String determineGoalTeam(Goal goal, WorldCupMatch match) {
        // Assuming Goal objects do not contain team info; we need to check which list contains the
        // goal.
        // Since we don't have a direct link, we can use a simple heuristic: if the goal appears in
        // home
        // team's list, it's home.
        if (match.goals1() != null && match.goals1().contains(goal)) {
            return match.homeTeam();
        } else if (match.goals2() != null && match.goals2().contains(goal)) {
            return match.awayTeam();
        }
        return "?";
    }

    private int parseMinuteToInt(String minute) {
        if (minute == null || minute.isBlank()) return 0;
        minute = minute.trim();
        if (minute.contains("+")) {
            String[] parts = minute.split("\\+");
            if (parts.length < 2) return 0; // ← adicione esta linha
            try {
                int base = Integer.parseInt(parts[0]);
                int extra = Integer.parseInt(parts[1]);
                return base + extra;
            } catch (NumberFormatException e) {
                return 0;
            }
        } else {
            try {
                return Integer.parseInt(minute);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    @PostMapping("/reload-worldcup")
    public ResponseEntity<String> reloadWorldCup() {
        worldCupUpdaterService.forceUpdate();
        return ResponseEntity.ok("Dados da Copa recarregados com sucesso!");
    }

    public void sendNoonMatchesToChat(long chatId) {
        if (!worldcupEnabled) {
            telegramFacade.enviarMensagemHtml(chatId, BotMessages.WORLD_CUP_DISABLED);
            return;
        }
        LocalDate today = LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE));
        sendMatchesMessageToChat(chatId, today, "🏆 JOGOS DE HOJE (meio-dia)");
    }

    public void sendEveningMatchesToChat(long chatId) {
        if (!worldcupEnabled) {
            telegramFacade.enviarMensagemHtml(chatId, BotMessages.WORLD_CUP_DISABLED);
            return;
        }
        LocalDate today = LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE));
        sendMatchesMessageToChat(chatId, today, "🏆 RESUMO DOS JOGOS DE HOJE");
    }

    public void sendManualTestToChat(long chatId) {
        if (!worldcupEnabled) {
            telegramFacade.enviarMensagemHtml(chatId, BotMessages.WORLD_CUP_DISABLED);
            return;
        }
        LocalDate today = LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE));
        sendMatchesMessageToChat(chatId, today, "🧪 TESTE MANUAL - Copa 2026");
    }

    private void sendMatchesMessageToChat(long chatId, LocalDate date, String title) {
        List<WorldCupMatch> matches = worldCupService.getMatchesForDay(date);
        if (matches.isEmpty()) {
            telegramFacade.enviarMensagemHtml(
                    chatId, "📭 Nenhum jogo programado para " + date + ".");
            return;
        }
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(BotMessages.FMT_HH_MM);
        StringBuilder sb = new StringBuilder();
        sb.append("<b>").append(title).append(FECHA_BOLD);
        for (WorldCupMatch m : matches) {
            ZonedDateTime localTime = m.getMatchDateTime(ZoneId.of(BotMessages.BRAZIL_ZONE));
            sb.append(
                    String.format(
                            POSICAO_PLACAR,
                            translateTeam(m.homeTeam()),
                            flagEmoji(m.homeTeam()),
                            flagEmoji(m.awayTeam()),
                            translateTeam(m.awayTeam()),
                            localTime.format(timeFormatter)));
            if (m.stadium() != null && !m.stadium().isBlank()) {
                sb.append(" - ").append(m.stadium());
            }
            sb.append("\n");
        }
        telegramFacade.enviarMensagemHtml(chatId, sb.toString());
    }

    public void sendManualTest() {
        if (!worldcupEnabled || allowedGroups.isEmpty()) {
            log.warn("Teste manual ignorado: servico desabilitado ou sem grupos");
            return;
        }
        LocalDate today = LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE));
        sendMatchesMessage(today, "🧪 TESTE MANUAL - Copa 2026");
    }

    private String flagEmoji(String team) {
        if (team == null || team.isBlank()) return "🏳️";
        String normalized = team.toLowerCase().trim();
        String flag = FLAG_MAP.get(normalized);
        if (flag != null) return flag;
        for (var entry : FLAG_MAP.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        log.warn("Bandeira nao encontrada para: {}", team);
        return "🏳️";
    }

    public void sendMatchesToChat(long chatId, LocalDate date) {
        if (!worldcupEnabled) {
            telegramFacade.enviarMensagemHtml(chatId, BotMessages.WORLD_CUP_DISABLED);
            return;
        }
        List<WorldCupMatch> matches = worldCupService.getMatchesForDay(date);
        if (matches.isEmpty()) {
            telegramFacade.enviarMensagemHtml(chatId, BotMessages.WORLD_CUP_NO_MATCHES_TODAY);
            return;
        }

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(BotMessages.FMT_HH_MM);
        StringBuilder sb = new StringBuilder();
        sb.append("<b>🏆 JOGOS DE HOJE</b>\n\n");
        for (WorldCupMatch m : matches) {
            ZonedDateTime localTime = m.getMatchDateTime(ZoneId.of(BotMessages.BRAZIL_ZONE));
            sb.append(
                    String.format(
                            POSICAO_PLACAR,
                            translateTeam(m.homeTeam()),
                            flagEmoji(m.homeTeam()),
                            flagEmoji(m.awayTeam()),
                            translateTeam(m.awayTeam()),
                            localTime.format(timeFormatter)));
            if (m.stadium() != null && !m.stadium().isBlank()) {
                sb.append(" - ").append(m.stadium());
            }
            sb.append("\n");
        }
        telegramFacade.enviarMensagemHtml(chatId, sb.toString());
    }

    private static final Map<String, String> TEAM_NAME_PT =
            Map.ofEntries(
                    // CONMEBOL
                    Map.entry("brazil", "Brasil"),
                    Map.entry("argentina", "Argentina"),
                    Map.entry("uruguay", "Uruguai"),
                    Map.entry("colombia", "Colombia"),
                    Map.entry("ecuador", "Equador"),
                    Map.entry("paraguay", "Paraguai"),
                    Map.entry("peru", "Peru"),
                    Map.entry("chile", "Chile"),
                    Map.entry("bolivia", "Bolivia"),
                    Map.entry("venezuela", "Venezuela"),
                    // UEFA
                    Map.entry("germany", "Alemanha"),
                    Map.entry("france", "Franca"),
                    Map.entry("spain", "Espanha"),
                    Map.entry("england", "Inglaterra"),
                    Map.entry("italy", "Italia"),
                    Map.entry("netherlands", "Holanda"),
                    Map.entry("portugal", "Portugal"),
                    Map.entry("belgium", "Belgica"),
                    Map.entry("croatia", "Croacia"),
                    Map.entry("switzerland", "Suica"),
                    Map.entry("denmark", "Dinamarca"),
                    Map.entry("sweden", "Suecia"),
                    Map.entry("poland", "Polonia"),
                    Map.entry("serbia", "Servia"),
                    Map.entry("turkey", "Turquia"),
                    Map.entry("ukraine", "Ucrania"),
                    Map.entry("austria", "Austria"),
                    Map.entry("czech republic", "Republica Tcheca"),
                    Map.entry("bosnia & herzegovina", "Bosnia"),
                    Map.entry("norway", "Noruega"),
                    Map.entry("scotland", "Escocia"),
                    // CONCACAF
                    Map.entry("mexico", "Mexico"),
                    Map.entry("united states", "Estados Unidos"),
                    Map.entry("usa", "Estados Unidos"),
                    Map.entry("canada", "Canada"),
                    Map.entry("panama", "Panama"),
                    Map.entry("costa rica", "Costa Rica"),
                    Map.entry("honduras", "Honduras"),
                    Map.entry("jamaica", "Jamaica"),
                    Map.entry("el salvador", "El Salvador"),
                    Map.entry("haiti", "Haiti"),
                    Map.entry("curacao", "Curacao"),
                    // CAF
                    Map.entry("morocco", "Marrocos"),
                    Map.entry("senegal", "Senegal"),
                    Map.entry("tunisia", "Tunisia"),
                    Map.entry("algeria", "Argelia"),
                    Map.entry("nigeria", "Nigeria"),
                    Map.entry("cameroon", "Camaroes"),
                    Map.entry("ivory coast", "Costa do Marfim"),
                    Map.entry("ghana", "Gana"),
                    Map.entry("egypt", "Egito"),
                    Map.entry("mali", "Mali"),
                    Map.entry("burkina faso", "Burkina Faso"),
                    Map.entry("dr congo", "Republica Democratica do Congo"),
                    Map.entry("south africa", "Africa do Sul"),
                    Map.entry("cape verde", "Cabo Verde"),
                    // AFC
                    Map.entry("japan", "Japao"),
                    Map.entry("south korea", "Coreia do Sul"),
                    Map.entry("australia", "Australia"),
                    Map.entry("saudi arabia", "Arabia Saudita"),
                    Map.entry("iran", "Ira"),
                    Map.entry("iraq", "Iraque"),
                    Map.entry("uzbekistan", "Uzbequistao"),
                    Map.entry("united arab emirates", "Emirados Arabes Unidos"),
                    Map.entry("qatar", "Catar"),
                    Map.entry("china", "China"),
                    Map.entry("syria", "Siria"),
                    Map.entry("vietnam", "Vietna"),
                    Map.entry("oman", "Oma"),
                    Map.entry("jordan", "Jordania"),
                    // OFC
                    Map.entry("new zealand", "Nova Zelandia"),
                    Map.entry("tahiti", "Taiti"));

    private String translateTeam(String teamName) {
        if (teamName == null || teamName.isBlank()) return "?";
        String key = teamName.toLowerCase().trim();
        if (TEAM_NAME_PT.containsKey(key)) {
            return TEAM_NAME_PT.get(key);
        }
        for (var entry : TEAM_NAME_PT.entrySet()) {
            if (key.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return teamName;
    }

    private static final Map<String, String> FLAG_MAP =
            Map.ofEntries(
                    // CONMEBOL
                    Map.entry("brazil", "🇧🇷"),
                    Map.entry("argentina", "🇦🇷"),
                    Map.entry("uruguay", "🇺🇾"),
                    Map.entry("colombia", "🇨🇴"),
                    Map.entry("ecuador", "🇪🇨"),
                    Map.entry("paraguay", "🇵🇾"),
                    Map.entry("peru", "🇵🇪"),
                    Map.entry("chile", "🇨🇱"),
                    Map.entry("bolivia", "🇧🇴"),
                    Map.entry("venezuela", "🇻🇪"),
                    // UEFA
                    Map.entry("germany", "🇩🇪"),
                    Map.entry("france", "🇫🇷"),
                    Map.entry("spain", "🇪🇸"),
                    Map.entry("england", "🏴󠁧󠁢󠁥󠁮󠁧󠁿"),
                    Map.entry("italy", "🇮🇹"),
                    Map.entry("netherlands", "🇳🇱"),
                    Map.entry("portugal", "🇵🇹"),
                    Map.entry("belgium", "🇧🇪"),
                    Map.entry("croatia", "🇭🇷"),
                    Map.entry("switzerland", "🇨🇭"),
                    Map.entry("denmark", "🇩🇰"),
                    Map.entry("sweden", "🇸🇪"),
                    Map.entry("poland", "🇵🇱"),
                    Map.entry("serbia", "🇷🇸"),
                    Map.entry("turkey", "🇹🇷"),
                    Map.entry("ukraine", "🇺🇦"),
                    Map.entry("austria", "🇦🇹"),
                    Map.entry("czech republic", "🇨🇿"),
                    Map.entry("bosnia & herzegovina", "🇧🇦"),
                    Map.entry("norway", "🇳🇴"),
                    Map.entry("scotland", "🏴󠁧󠁢󠁳󠁣󠁴󠁿"),
                    // CONCACAF
                    Map.entry("mexico", "🇲🇽"),
                    Map.entry("united states", "🇺🇸"),
                    Map.entry("usa", "🇺🇸"),
                    Map.entry("canada", "🇨🇦"),
                    Map.entry("panama", "🇵🇦"),
                    Map.entry("costa rica", "🇨🇷"),
                    Map.entry("honduras", "🇭🇳"),
                    Map.entry("jamaica", "🇯🇲"),
                    Map.entry("el salvador", "🇸🇻"),
                    Map.entry("haiti", "🇭🇹"),
                    Map.entry("curacao", "🇨🇼"),
                    // CAF
                    Map.entry("morocco", "🇲🇦"),
                    Map.entry("senegal", "🇸🇳"),
                    Map.entry("tunisia", "🇹🇳"),
                    Map.entry("algeria", "🇩🇿"),
                    Map.entry("nigeria", "🇳🇬"),
                    Map.entry("cameroon", "🇨🇲"),
                    Map.entry("ivory coast", "🇨🇮"),
                    Map.entry("ghana", "🇬🇭"),
                    Map.entry("egypt", "🇪🇬"),
                    Map.entry("mali", "🇲🇱"),
                    Map.entry("burkina faso", "🇧🇫"),
                    Map.entry("dr congo", "🇨🇩"),
                    Map.entry("south africa", "🇿🇦"),
                    Map.entry("cape verde", "🇨🇻"),
                    // AFC
                    Map.entry("japan", "🇯🇵"),
                    Map.entry("south korea", "🇰🇷"),
                    Map.entry("australia", "🇦🇺"),
                    Map.entry("saudi arabia", "🇸🇦"),
                    Map.entry("iran", "🇮🇷"),
                    Map.entry("iraq", "🇮🇶"),
                    Map.entry("uzbekistan", "🇺🇿"),
                    Map.entry("united arab emirates", "🇦🇪"),
                    Map.entry("qatar", "🇶🇦"),
                    Map.entry("china", "🇨🇳"),
                    Map.entry("syria", "🇸🇾"),
                    Map.entry("vietnam", "🇻🇳"),
                    Map.entry("oman", "🇴🇲"),
                    Map.entry("jordan", "🇯🇴"),
                    // OFC
                    Map.entry("new zealand", "🇳🇿"),
                    Map.entry("tahiti", "🇵🇫"));
}
