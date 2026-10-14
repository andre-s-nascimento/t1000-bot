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
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.model.Goal;
import net.ddns.adambravo79.tmill.model.Score;
import net.ddns.adambravo79.tmill.model.WorldCupMatch;
import net.ddns.adambravo79.tmill.service.config.JsonConfigLoader;
import net.ddns.adambravo79.tmill.service.config.WorldCupTeamsService;
import net.ddns.adambravo79.tmill.service.feature.FeatureFlagAdminService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;

@Slf4j
@Service
public class WorldCupSchedulerService implements SchedulingConfigurer {

    private static final String WORLDCUP_DESABILITADO = "worldcup_desabilitado";
    private static final String WORLDCUP_ENABLED = "worldcup.enabled";
    private static final String WORLDCUP_REMINDERS_30MIN_ENABLED =
            "worldcup.reminders30min.enabled";
    private static final String WORLDCUP_JOGOS_ENVIADOS = "worldcup_jogos_enviados";
    private static final String WORLDCUP_SEM_JOGOS = "worldcup_sem_jogos";
    private static final String POSICAO_PLACAR = "%s (%s) x (%s) %s - %s";
    private static final String FECHA_BOLD = "</b>\n\n";

    private final StaticWorldCupService worldCupService;
    private final TelegramFacade telegramFacade;
    private final Set<Long> allowedGroups = new HashSet<>();
    private final Set<String> remindersSent = ConcurrentHashMap.newKeySet();
    private final WorldCupUpdaterService worldCupUpdaterService;
    private final Clock clock;
    private final MetricsService metricsService;
    private final FeatureFlagAdminService featureFlags;
    private final JsonConfigLoader jsonConfigLoader;
    private final WorldCupTeamsService teamsService;

    @Value(BotMessages.DEFAULT_BOT_ALLOWED_CHATS)
    private String allowedChatsStr;

    public WorldCupSchedulerService(
            StaticWorldCupService worldCupService,
            TelegramFacade telegramFacade,
            WorldCupUpdaterService worldCupUpdaterService,
            Clock clock,
            MetricsService metricsService,
            FeatureFlagAdminService featureFlags,
            JsonConfigLoader jsonConfigLoader,
            WorldCupTeamsService teamsService) {
        this.worldCupService = worldCupService;
        this.telegramFacade = telegramFacade;
        this.worldCupUpdaterService = worldCupUpdaterService;
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
        this.metricsService = metricsService;
        this.featureFlags = featureFlags;
        this.jsonConfigLoader = jsonConfigLoader;
        this.teamsService = teamsService;
    }

    @PostConstruct
    public void init() {
        if (!featureFlags.isEnabled(WORLDCUP_ENABLED)) return;
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

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(
                this::sendNoonMatches,
                ctx ->
                        new CronTrigger(
                                        getCron("noonCron", "0 0 12 * * *"),
                                        ZoneId.of(BotMessages.BRAZIL_ZONE))
                                .nextExecution(ctx));
        taskRegistrar.addTriggerTask(
                this::sendEveningMatches,
                ctx ->
                        new CronTrigger(
                                        getCron("eveningCron", "0 30 18 * * *"),
                                        ZoneId.of(BotMessages.BRAZIL_ZONE))
                                .nextExecution(ctx));
        taskRegistrar.addTriggerTask(
                this::checkThirtyMinutesBeforeEachMatch,
                ctx ->
                        new CronTrigger(
                                        getCron("checkCron", "0 * * * * *"),
                                        ZoneId.of(BotMessages.BRAZIL_ZONE))
                                .nextExecution(ctx));
        taskRegistrar.addTriggerTask(
                this::cleanReminders,
                ctx ->
                        new CronTrigger(
                                        getCron("cleanCron", "0 5 0 * * *"),
                                        ZoneId.of(BotMessages.BRAZIL_ZONE))
                                .nextExecution(ctx));
    }

    private String getCron(String key, String defaultCron) {
        return jsonConfigLoader
                .loadConfig("config/worldcup-config.json", Map.class, "config/worldcup-config.json")
                .map(m -> (String) m.get(key))
                .orElse(defaultCron);
    }

    public void sendNoonMatches() {
        if (!featureFlags.isEnabled(WORLDCUP_ENABLED) || allowedGroups.isEmpty()) return;
        LocalDate today = LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE));
        sendMatchesMessage(today, "🏆 JOGOS DE HOJE (meio-dia)");
    }

    public void sendEveningMatches() {
        if (!featureFlags.isEnabled(WORLDCUP_ENABLED) || allowedGroups.isEmpty()) return;
        LocalDate today = LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE));
        sendMatchesMessage(today, "🏆 RESUMO DOS JOGOS DE HOJE");
    }

    /**
     * Verifica se algum jogo começa em 30 minutos e envia aviso.
     *
     * <p>Roda a cada minuto (via {@code checkCron}). O aviso é enviado apenas uma vez por jogo,
     * rastreado via {@link #remindersSent}.
     *
     * <p><b>Feature flag:</b> se {@code worldcup.reminders30min.enabled} estiver desabilitada, este
     * método apenas retorna. Os jogos continuam sendo enviados nos horários agendados (noon/evening)
     * — apenas os avisos individuais de 30 min são suprimidos.
     */
    public void checkThirtyMinutesBeforeEachMatch() {
        if (!featureFlags.isEnabled(WORLDCUP_ENABLED)) return;

        if (!featureFlags.isEnabled(WORLDCUP_REMINDERS_30MIN_ENABLED)) {
            log.debug("⏰ Lembretes 30 min desabilitados por feature flag — pulando.");
            return;
        }

        if (allowedGroups.isEmpty()) return;

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

    public void cleanReminders() {
        remindersSent.clear();
        log.info("🧹 Avisos de jogos limpos para um novo dia");
    }

    private void sendMatchesMessage(LocalDate date, String title) {
        List<WorldCupMatch> matches = worldCupService.getMatchesForDay(date);
        if (matches.isEmpty()) {
            log.info("Nenhum jogo na data {}", date);
            metricsService.error(WORLDCUP_SEM_JOGOS);
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
                            teamsService.translateTeam(m.homeTeam()),
                            teamsService.flagEmoji(m.homeTeam()),
                            teamsService.flagEmoji(m.awayTeam()),
                            teamsService.translateTeam(m.awayTeam()),
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
        metricsService.success(WORLDCUP_JOGOS_ENVIADOS);
    }

    private void sendThirtyMinuteReminder(WorldCupMatch match) {
        ZonedDateTime localTime = match.getMatchDateTime(ZoneId.of(BotMessages.BRAZIL_ZONE));
        String message =
                """
                <b>⏰ Faltam 30 minutos para o inicio do jogo!</b>

                ⚽ %s (%s) x (%s) %s - %s
                """
                        .formatted(
                                teamsService.translateTeam(match.homeTeam()),
                                teamsService.flagEmoji(match.homeTeam()),
                                teamsService.flagEmoji(match.awayTeam()),
                                teamsService.translateTeam(match.awayTeam()),
                                localTime.format(
                                        DateTimeFormatter.ofPattern(BotMessages.FMT_HH_MM)));
        for (Long groupId : allowedGroups) {
            telegramFacade.enviarMensagemHtml(groupId, message);
        }
        metricsService.success("worldcup_lembrete_30min_enviado");
    }

    public void sendResultsToChat(long chatId, LocalDate date) {
        if (!featureFlags.isEnabled(WORLDCUP_ENABLED)) {
            telegramFacade.enviarMensagemHtml(chatId, BotMessages.WORLD_CUP_DISABLED);
            metricsService.error(WORLDCUP_DESABILITADO);
            return;
        }

        List<WorldCupMatch> matches = worldCupService.getMatchesForDay(date);
        if (matches.isEmpty()) {
            telegramFacade.enviarMensagemHtml(
                    chatId,
                    BotMessages.WORLD_CUP_NO_MATCHES_DATE
                            + date.format(DateTimeFormatter.ofPattern(BotMessages.FMT_DD_MM_YYYY)));
            metricsService.error(WORLDCUP_SEM_JOGOS);
            return;
        }

        String resultsMessage = buildResultsMessage(date, matches);
        telegramFacade.enviarMensagemHtml(chatId, resultsMessage);
        metricsService.success("worldcup_resultados_enviados");
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
                    .append(teamsService.translateTeam(match.homeTeam()))
                    .append(" (")
                    .append(teamsService.flagEmoji(match.homeTeam()))
                    .append(") x (")
                    .append(teamsService.flagEmoji(match.awayTeam()))
                    .append(") ")
                    .append(teamsService.translateTeam(match.awayTeam()))
                    .append(" - Aguardando resultado\n\n");
            return sb.toString();
        }

        sb.append(formatScoreHeader(match));

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
        header.append(teamsService.flagEmoji(match.homeTeam()))
                .append(" ")
                .append(teamsService.translateTeam(match.homeTeam()))
                .append(" ")
                .append(homeGoals)
                .append(" x ")
                .append(awayGoals)
                .append(" ")
                .append(teamsService.translateTeam(match.awayTeam()))
                .append(" ")
                .append(teamsService.flagEmoji(match.awayTeam()));

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
                        .append(teamsService.flagEmoji(team))
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
            if (parts.length < 2) return 0;
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
        if (!featureFlags.isEnabled(WORLDCUP_ENABLED)) {
            telegramFacade.enviarMensagemHtml(chatId, BotMessages.WORLD_CUP_DISABLED);
            metricsService.error(WORLDCUP_DESABILITADO);
            return;
        }
        LocalDate today = LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE));
        sendMatchesMessageToChat(chatId, today, "🏆 JOGOS DE HOJE (meio-dia)");
    }

    public void sendEveningMatchesToChat(long chatId) {
        if (!featureFlags.isEnabled(WORLDCUP_ENABLED)) {
            telegramFacade.enviarMensagemHtml(chatId, BotMessages.WORLD_CUP_DISABLED);
            metricsService.error(WORLDCUP_DESABILITADO);
            return;
        }
        LocalDate today = LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE));
        sendMatchesMessageToChat(chatId, today, "🏆 RESUMO DOS JOGOS DE HOJE");
    }

    public void sendManualTestToChat(long chatId) {
        if (!featureFlags.isEnabled(WORLDCUP_ENABLED)) {
            telegramFacade.enviarMensagemHtml(chatId, BotMessages.WORLD_CUP_DISABLED);
            metricsService.error(WORLDCUP_DESABILITADO);
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
            metricsService.error(WORLDCUP_SEM_JOGOS);
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
                            teamsService.translateTeam(m.homeTeam()),
                            teamsService.flagEmoji(m.homeTeam()),
                            teamsService.flagEmoji(m.awayTeam()),
                            teamsService.translateTeam(m.awayTeam()),
                            localTime.format(timeFormatter)));
            if (m.stadium() != null && !m.stadium().isBlank()) {
                sb.append(" - ").append(m.stadium());
            }
            sb.append("\n");
        }
        telegramFacade.enviarMensagemHtml(chatId, sb.toString());
        metricsService.success(WORLDCUP_JOGOS_ENVIADOS);
    }

    public void sendManualTest() {
        if (!featureFlags.isEnabled(WORLDCUP_ENABLED) || allowedGroups.isEmpty()) {
            log.warn("Teste manual ignorado: servico desabilitado ou sem grupos");
            metricsService.error(WORLDCUP_DESABILITADO);
            return;
        }
        LocalDate today = LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE));
        sendMatchesMessage(today, "🧪 TESTE MANUAL - Copa 2026");
    }

    public void sendMatchesToChat(long chatId, LocalDate date) {
        if (!featureFlags.isEnabled(WORLDCUP_ENABLED)) {
            telegramFacade.enviarMensagemHtml(chatId, BotMessages.WORLD_CUP_DISABLED);
            metricsService.error(WORLDCUP_DESABILITADO);
            return;
        }
        List<WorldCupMatch> matches = worldCupService.getMatchesForDay(date);
        if (matches.isEmpty()) {
            telegramFacade.enviarMensagemHtml(chatId, BotMessages.WORLD_CUP_NO_MATCHES_TODAY);
            metricsService.error(WORLDCUP_SEM_JOGOS);
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
                            teamsService.translateTeam(m.homeTeam()),
                            teamsService.flagEmoji(m.homeTeam()),
                            teamsService.flagEmoji(m.awayTeam()),
                            teamsService.translateTeam(m.awayTeam()),
                            localTime.format(timeFormatter)));
            if (m.stadium() != null && !m.stadium().isBlank()) {
                sb.append(" - ").append(m.stadium());
            }
            sb.append("\n");
        }
        telegramFacade.enviarMensagemHtml(chatId, sb.toString());
        metricsService.success(WORLDCUP_JOGOS_ENVIADOS);
    }
}
