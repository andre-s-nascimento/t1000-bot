/* (c) 2026 | 11/06/2026 */

package net.ddns.adambravo79.tmill.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.model.WorldCupMatch;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;

@Slf4j
@Service
public class WorldCupSchedulerService {

    private final StaticWorldCupService worldCupService;
    private final TelegramFacade telegramFacade;
    private final Set<Long> allowedGroups = new HashSet<>();
    private final Set<String> remindersSent = ConcurrentHashMap.newKeySet();

    @Value("${worldcup.enabled:false}")
    private boolean worldcupEnabled;

    @Value("${bot.allowed-chats:}")
    private String allowedChatsStr;

    public WorldCupSchedulerService(
            StaticWorldCupService worldCupService, TelegramFacade telegramFacade) {
        this.worldCupService = worldCupService;
        this.telegramFacade = telegramFacade;
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
                    log.warn("ID inválido para Copa: {}", s);
                }
            }
        }
        log.info("🏆 Serviço de Copa ativo para grupos: {}", allowedGroups);
    }

    @Scheduled(cron = "0 0 12 * * *", zone = "America/Sao_Paulo")
    public void sendNoonMatches() {
        if (!worldcupEnabled || allowedGroups.isEmpty()) return;
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        sendMatchesMessage(today, "🏆 JOGOS DE HOJE (meio-dia)");
    }

    @Scheduled(cron = "0 30 18 * * *", zone = "America/Sao_Paulo")
    public void sendEveningMatches() {
        if (!worldcupEnabled || allowedGroups.isEmpty()) return;
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        sendMatchesMessage(today, "🏆 RESUMO DOS JOGOS DE HOJE");
    }

    @Scheduled(cron = "0 * * * * *", zone = "America/Sao_Paulo")
    public void checkThirtyMinutesBefore() {
        if (!worldcupEnabled || allowedGroups.isEmpty()) return;
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));

        worldCupService
                .getFirstMatchOfDay(today)
                .ifPresent(
                        firstMatch -> {
                            ZonedDateTime matchZdt =
                                    firstMatch.getMatchDateTime(ZoneId.of("America/Sao_Paulo"));
                            LocalDateTime matchTime = matchZdt.toLocalDateTime();
                            LocalDateTime reminderTime = matchTime.minusMinutes(30);
                            String reminderKey = today.toString() + "_30min";

                            if (now.isAfter(reminderTime)
                                    && now.isBefore(matchTime)
                                    && !remindersSent.contains(reminderKey)) {
                                sendThirtyMinuteReminder(firstMatch);
                                remindersSent.add(reminderKey);
                            }
                        });
    }

    @Scheduled(cron = "0 * * * * *", zone = "America/Sao_Paulo")
    public void checkThirtyMinutesBeforeEachMatch() {
        if (!worldcupEnabled || allowedGroups.isEmpty()) return;
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));

        List<WorldCupMatch> matches = worldCupService.getMatchesForDay(today);
        for (WorldCupMatch match : matches) {
            ZonedDateTime matchZdt = match.getMatchDateTime(ZoneId.of("America/Sao_Paulo"));
            LocalDateTime matchTime = matchZdt.toLocalDateTime();
            LocalDateTime reminderTime = matchTime.minusMinutes(30);
            String reminderKey = today + "_" + match.homeTeam() + "_" + match.awayTeam();

            if (now.isAfter(reminderTime) && now.isBefore(matchTime)) {
                if (remindersSent.add(reminderKey)) {
                    sendThirtyMinuteReminder(match);
                    log.info(
                            "⏰ Aviso enviado para jogo: {} vs {} às {}",
                            match.homeTeam(),
                            match.awayTeam(),
                            matchTime);
                }
            }
        }
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "America/Sao_Paulo")
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

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        StringBuilder sb = new StringBuilder();
        sb.append("<b>").append(title).append("</b>\n\n");
        for (WorldCupMatch m : matches) {
            ZonedDateTime localTime = m.getMatchDateTime(ZoneId.of("America/Sao_Paulo"));
            String line =
                    String.format(
                            "%s (%s) x (%s) %s - %s",
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
        ZonedDateTime localTime = match.getMatchDateTime(ZoneId.of("America/Sao_Paulo"));
        String message =
                String.format(
                        "<b>⏰ Faltam 30 minutos para o início do jogo!</b>\n\n"
                                + "⚽ %s(%s) vs %s(%s) - %s",
                        translateTeam(match.homeTeam()),
                        flagEmoji(match.homeTeam()),
                        flagEmoji(match.awayTeam()),
                        translateTeam(match.awayTeam()),
                        localTime.format(DateTimeFormatter.ofPattern("HH:mm")));
        for (Long groupId : allowedGroups) {
            telegramFacade.enviarMensagemHtml(groupId, message);
        }
    }

    public void sendManualTest() {
        if (!worldcupEnabled || allowedGroups.isEmpty()) {
            log.warn("Teste manual ignorado: serviço desabilitado ou sem grupos");
            return;
        }
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        sendMatchesMessage(today, "🧪 TESTE MANUAL - Copa 2026");
    }

    private String flagEmoji(String team) {
        if (team == null || team.isBlank()) return "🏳️";
        String normalized = team.toLowerCase().trim();
        String flag = FLAG_MAP.get(normalized);
        if (flag != null) return flag;
        // Fallback por substring
        for (var entry : FLAG_MAP.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        log.warn("Bandeira não encontrada para: {}", team);
        return "🏳️";
    }

    // Dentro de WorldCupSchedulerService.java
    public void sendMatchesToChat(long chatId, LocalDate date) {
        if (!worldcupEnabled) {
            telegramFacade.enviarMensagemHtml(chatId, "⛔ Serviço de Copa desativado.");
            return;
        }
        List<WorldCupMatch> matches = worldCupService.getMatchesForDay(date);
        if (matches.isEmpty()) {
            telegramFacade.enviarMensagemHtml(chatId, "📭 Nenhum jogo programado para hoje.");
            return;
        }

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        StringBuilder sb = new StringBuilder();
        sb.append("<b>🏆 JOGOS DE HOJE</b>\n\n");
        for (WorldCupMatch m : matches) {
            ZonedDateTime localTime = m.getMatchDateTime(ZoneId.of("America/Sao_Paulo"));
            sb.append(
                    String.format(
                            "%s (%s) x (%s) %s - %s",
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
                    Map.entry("colombia", "Colômbia"),
                    Map.entry("ecuador", "Equador"),
                    Map.entry("paraguay", "Paraguai"),
                    Map.entry("peru", "Peru"),
                    Map.entry("chile", "Chile"),
                    Map.entry("bolivia", "Bolívia"),
                    Map.entry("venezuela", "Venezuela"),
                    // UEFA
                    Map.entry("germany", "Alemanha"),
                    Map.entry("france", "França"),
                    Map.entry("spain", "Espanha"),
                    Map.entry("england", "Inglaterra"),
                    Map.entry("italy", "Itália"),
                    Map.entry("netherlands", "Holanda"),
                    Map.entry("portugal", "Portugal"),
                    Map.entry("belgium", "Bélgica"),
                    Map.entry("croatia", "Croácia"),
                    Map.entry("switzerland", "Suíça"),
                    Map.entry("denmark", "Dinamarca"),
                    Map.entry("sweden", "Suécia"),
                    Map.entry("poland", "Polônia"),
                    Map.entry("serbia", "Sérvia"),
                    Map.entry("turkey", "Turquia"),
                    Map.entry("ukraine", "Ucrânia"),
                    Map.entry("austria", "Áustria"),
                    Map.entry("czech republic", "República Tcheca"),
                    Map.entry("bosnia & herzegovina", "Bósnia"),
                    Map.entry("norway", "Noruega"),
                    Map.entry("scotland", "Escócia"),
                    // CONCACAF
                    Map.entry("mexico", "México"),
                    Map.entry("united states", "Estados Unidos"),
                    Map.entry("usa", "Estados Unidos"),
                    Map.entry("canada", "Canadá"),
                    Map.entry("panama", "Panamá"),
                    Map.entry("costa rica", "Costa Rica"),
                    Map.entry("honduras", "Honduras"),
                    Map.entry("jamaica", "Jamaica"),
                    Map.entry("el salvador", "El Salvador"),
                    Map.entry("haiti", "Haiti"),
                    Map.entry("curaçao", "Curaçao"),
                    // CAF
                    Map.entry("morocco", "Marrocos"),
                    Map.entry("senegal", "Senegal"),
                    Map.entry("tunisia", "Tunísia"),
                    Map.entry("algeria", "Argélia"),
                    Map.entry("nigeria", "Nigéria"),
                    Map.entry("cameroon", "Camarões"),
                    Map.entry("ivory coast", "Costa do Marfim"),
                    Map.entry("ghana", "Gana"),
                    Map.entry("egypt", "Egito"),
                    Map.entry("mali", "Mali"),
                    Map.entry("burkina faso", "Burkina Faso"),
                    Map.entry("dr congo", "República Democrática do Congo"),
                    Map.entry("south africa", "África do Sul"),
                    Map.entry("cape verde", "Cabo Verde"),
                    // AFC
                    Map.entry("japan", "Japão"),
                    Map.entry("south korea", "Coreia do Sul"),
                    Map.entry("australia", "Austrália"),
                    Map.entry("saudi arabia", "Arábia Saudita"),
                    Map.entry("iran", "Irã"),
                    Map.entry("iraq", "Iraque"),
                    Map.entry("uzbekistan", "Uzbequistão"),
                    Map.entry("united arab emirates", "Emirados Árabes Unidos"),
                    Map.entry("qatar", "Catar"),
                    Map.entry("china", "China"),
                    Map.entry("syria", "Síria"),
                    Map.entry("vietnam", "Vietnã"),
                    Map.entry("oman", "Omã"),
                    Map.entry("jordan", "Jordânia"),
                    // OFC
                    Map.entry("new zealand", "Nova Zelândia"),
                    Map.entry("tahiti", "Taiti"));

    private String translateTeam(String teamName) {
        if (teamName == null || teamName.isBlank()) return "?";
        String key = teamName.toLowerCase().trim();
        // Tenta primeira correspondência exata
        if (TEAM_NAME_PT.containsKey(key)) {
            return TEAM_NAME_PT.get(key);
        }
        // Fallback: tenta substring (ex.: "Czech Republic" vs "czech republic")
        for (var entry : TEAM_NAME_PT.entrySet()) {
            if (key.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        // Se não encontrar, mantém o original
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
                    Map.entry("curaçao", "🇨🇼"),
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
