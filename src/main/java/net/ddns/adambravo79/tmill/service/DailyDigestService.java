/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.client.GroqClient;
import net.ddns.adambravo79.tmill.prompt.DigestPersona;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.TelegramMessageSplitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyDigestService {

    private static final int MAX_PROMPT_SIZE = 32000;

    private final JdbcTemplate jdbcTemplate;
    private final GroqClient groqClient;
    private final TelegramFacade telegramFacade;

    @org.springframework.beans.factory.annotation.Value("${digest.enabled:false}")
    private boolean digestEnabled;

    @org.springframework.beans.factory.annotation.Value("${digest.chat-ids:}")
    private String digestChatIdsStr;

    private final Set<Long> digestChatIds = new HashSet<>();

    @PostConstruct
    public void init() {

        if (digestChatIdsStr != null && !digestChatIdsStr.isBlank()) {

            for (String s : digestChatIdsStr.split(",")) {

                try {

                    digestChatIds.add(Long.parseLong(s.trim()));

                } catch (NumberFormatException e) {

                    log.warn("ID inválido em digest.chat-ids: {}", s);
                }
            }

            log.info("📊 Digests serão enviados para os chats: {}", digestChatIds);

        } else {

            log.info("Nenhum chat configurado para digest.");
        }
    }

    public void generateDigestCustom(LocalDateTime from, LocalDateTime to, Long specificChatId) {

        generateDigest(from, to, "PERÍODO PERSONALIZADO", specificChatId);
    }

    @Scheduled(cron = "0 30 8 * * *", zone = "America/Sao_Paulo")
    public void generateMorningDigest() {

        if (!digestEnabled || digestChatIds.isEmpty()) {

            return;
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));

        LocalDateTime from = now.minusDays(1).withHour(20).withMinute(30).withSecond(0);

        LocalDateTime to = now.withHour(8).withMinute(30).withSecond(0);

        generateDigest(from, to, "RESUMO DA MADRUGADA/MANHÃ", null);
    }

    @Scheduled(cron = "0 30 20 * * *", zone = "America/Sao_Paulo")
    public void generateEveningDigest() {

        if (!digestEnabled || digestChatIds.isEmpty()) {

            return;
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));

        LocalDateTime from = now.withHour(8).withMinute(30).withSecond(0);

        LocalDateTime to = now.withHour(20).withMinute(30).withSecond(0);

        generateDigest(from, to, "RESUMO DO DIA", null);
    }

    private void generateDigest(
            LocalDateTime from, LocalDateTime to, String periodLabel, Long specificChatId) {

        log.info("Gerando {} | {} -> {}", periodLabel, from, to);

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        List<Map<String, Object>> messages =
                jdbcTemplate.queryForList(
                        """
            SELECT
              user_name,
              text,
              timestamp
            FROM messages
            WHERE datetime(timestamp, 'localtime')
            BETWEEN ? AND ?
            ORDER BY timestamp ASC
            """,
                        from.format(dtf),
                        to.format(dtf));

        List<Map<String, Object>> transcripts =
                jdbcTemplate.queryForList(
                        """
            SELECT
              user_name,
              text,
              timestamp
            FROM transcripts
            WHERE datetime(timestamp, 'localtime')
            BETWEEN ? AND ?
            ORDER BY timestamp ASC
            """,
                        from.format(dtf),
                        to.format(dtf));

        if (messages.isEmpty() && transcripts.isEmpty()) {

            log.info("Nenhuma interação encontrada.");

            return;
        }

        List<ChatMessage> allMessages = new ArrayList<>();

        for (Map<String, Object> row : messages) {

            allMessages.add(
                    ChatMessage.builder()
                            .user((String) row.get("user_name"))
                            .text((String) row.get("text"))
                            .timestamp(String.valueOf(row.get("timestamp")))
                            .audio(false)
                            .build());
        }

        for (Map<String, Object> row : transcripts) {

            allMessages.add(
                    ChatMessage.builder()
                            .user((String) row.get("user_name"))
                            .text((String) row.get("text"))
                            .timestamp(String.valueOf(row.get("timestamp")))
                            .audio(true)
                            .build());
        }

        allMessages.sort(Comparator.comparing(ChatMessage::getTimestamp));

        String finalMessages = buildMessagesBlock(allMessages);

        if (finalMessages.length() > MAX_PROMPT_SIZE) {

            int allowedMessagesSize = MAX_PROMPT_SIZE - 4000;

            log.warn(
                    "✂️ Mensagens truncadas de {} para {} chars",
                    finalMessages.length(),
                    allowedMessagesSize);

            int slice = allowedMessagesSize / 3;

            String start = finalMessages.substring(0, Math.min(slice, finalMessages.length()));

            String middle =
                    finalMessages.substring(
                            Math.max(0, (finalMessages.length() / 2) - (slice / 2)),
                            Math.min(
                                    finalMessages.length(),
                                    (finalMessages.length() / 2) + (slice / 2)));

            String end = finalMessages.substring(Math.max(0, finalMessages.length() - slice));

            finalMessages = start + "\n\n[...]\n\n" + middle + "\n\n[...]\n\n" + end;
        }

        log.info("📦 Mensagens finais size={}", finalMessages.length());

        try {

            DigestPersona persona = DigestPersona.T1000;

            String summary = groqClient.gerarResumoDigest(finalMessages, persona, periodLabel);

            if (summary == null || summary.isBlank()) {

                log.warn("Resumo vazio.");

                return;
            }

            String finalMessage = buildHeader(periodLabel, from, to) + sanitizeHtml(summary);

            Set<Long> targets = specificChatId != null ? Set.of(specificChatId) : digestChatIds;

            for (Long chatId : targets) {
                sendDigestToChat(chatId, finalMessage);
            }

        } catch (Exception e) {

            log.error("❌ Erro ao gerar digest", e);
        }
    }

    private String buildMessagesBlock(List<ChatMessage> messages) {

        StringBuilder sb = new StringBuilder();
        DateTimeFormatter hourFormat = DateTimeFormatter.ofPattern("HH:mm");
        LocalDateTime previous = null;

        for (ChatMessage msg : messages) {
            LocalDateTime current = LocalDateTime.parse(msg.getTimestamp().replace(" ", "T"));

            if (previous != null) {
                long diff = Duration.between(previous, current).toMinutes();
                if (diff >= 20) {

                    sb.append("\n==============================\n");
                    sb.append("NOVO BLOCO DE CONVERSA\n");
                    sb.append("==============================\n\n");
                }
            }

            String line =
                    String.format(
                            "[%s] %s%s: %s%n",
                            hourFormat.format(current),
                            msg.getUser(),
                            msg.isAudio() ? " (áudio)" : "",
                            msg.getText());

            sb.append(line);

            previous = current;
        }

        return sb.toString();
    }

    private String buildHeader(String periodLabel, LocalDateTime from, LocalDateTime to) {

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        return String.format(
                """
        <b>📊 %s</b>
        <i>Período: %s - %s</i>

        """,
                periodLabel, from.format(fmt), to.format(fmt));
    }

    private String sanitizeHtml(String text) {

        if (text == null) {

            return "";
        }

        return text.replace("&", "&amp;")
                .replace("<b>", "##B_OPEN##")
                .replace("</b>", "##B_CLOSE##")
                .replace("<i>", "##I_OPEN##")
                .replace("</i>", "##I_CLOSE##")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("##B_OPEN##", "<b>")
                .replace("##B_CLOSE##", "</b>")
                .replace("##I_OPEN##", "<i>")
                .replace("##I_CLOSE##", "</i>");
    }

    @Data
    @Builder
    private static class ChatMessage {

        private String user;
        private String text;
        private String timestamp;
        private boolean audio;
    }

    private void sendDigestToChat(Long chatId, String finalMessage) {
        try {
            List<String> chunks = TelegramMessageSplitter.split(finalMessage);
            for (String chunk : chunks) {
                telegramFacade.enviarMensagemHtml(chatId, chunk);
            }
            log.info("✅ Digest enviado chatId={}", chatId);
        } catch (Exception e) {
            log.error("❌ Falha ao enviar digest chatId={}", chatId, e);
        }
    }
}
