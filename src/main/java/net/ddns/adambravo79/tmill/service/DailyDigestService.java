/* (c) 2026 | 18/05/2026 */
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
            SELECT user_name, text, timestamp
            FROM messages
            WHERE datetime(timestamp, 'localtime') BETWEEN ? AND ?
            ORDER BY timestamp ASC
            """,
                        from.format(dtf),
                        to.format(dtf));

        List<Map<String, Object>> transcripts =
                jdbcTemplate.queryForList(
                        """
            SELECT user_name, text, timestamp
            FROM transcripts
            WHERE datetime(timestamp, 'localtime') BETWEEN ? AND ?
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

            String finalMessage = buildHeader(periodLabel, from, to) + sanitizeDigestText(summary);
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

    private String sanitizeDigestText(String text) {
        if (text == null) return "";

        // 1. Converte quebras de linha
        String sanitized =
                text.replaceAll("(?i)<br\\s*/?>", "\n")
                        .replaceAll("(?i)</?ul\\s*>", "")
                        .replaceAll("(?i)<li\\s*>", "• ")
                        .replaceAll("(?i)</li\\s*>", "\n");

        // 2. Remove todas as tags HTML, exceto as permitidas
        // Lista de tags permitidas (abertura e fechamento)
        String allowedTags = "(/?(?:b|i|u|s|code|pre|a)(?:\\s+[^>]*)?)";
        // Substitui qualquer tag que não seja permitida por vazio
        // Usamos um loop para lidar com tags aninhadas (simplificado)
        // Primeiro, escapamos todas as tags que não são permitidas
        // Vamos usar uma abordagem com regex para remover tags não permitidas
        // Mas cuidado: não podemos simplesmente remover tudo, pois pode quebrar o texto.
        // Vamos usar uma abordagem mais segura: substituir < por &lt; e > por &gt; fora das tags
        // permitidas.
        // Por simplicidade, faremos uma limpeza passo a passo:

        // Protege as tags permitidas temporariamente
        String protectedText =
                sanitized
                        .replace("<b>", "##B_OPEN##")
                        .replace("</b>", "##B_CLOSE##")
                        .replace("<i>", "##I_OPEN##")
                        .replace("</i>", "##I_CLOSE##")
                        .replace("<u>", "##U_OPEN##")
                        .replace("</u>", "##U_CLOSE##")
                        .replace("<s>", "##S_OPEN##")
                        .replace("</s>", "##S_CLOSE##")
                        .replace("<code>", "##C_OPEN##")
                        .replace("</code>", "##C_CLOSE##")
                        .replace("<pre>", "##P_OPEN##")
                        .replace("</pre>", "##P_CLOSE##")
                        // Protege tags <a> com seus atributos (simplificado)
                        .replaceAll("<a\\s+[^>]*>", "##A_OPEN##")
                        .replace("</a>", "##A_CLOSE##");

        // Agora escapa todos os caracteres < e > que sobraram (não protegidos)
        // Substitui < por &lt; e > por &gt;
        String escaped = protectedText.replace("<", "&lt;").replace(">", "&gt;");

        // Restaura as tags permitidas
        String restored =
                escaped.replace("##B_OPEN##", "<b>")
                        .replace("##B_CLOSE##", "</b>")
                        .replace("##I_OPEN##", "<i>")
                        .replace("##I_CLOSE##", "</i>")
                        .replace("##U_OPEN##", "<u>")
                        .replace("##U_CLOSE##", "</u>")
                        .replace("##S_OPEN##", "<s>")
                        .replace("##S_CLOSE##", "</s>")
                        .replace("##C_OPEN##", "<code>")
                        .replace("##C_CLOSE##", "</code>")
                        .replace("##P_OPEN##", "<pre>")
                        .replace("##P_CLOSE##", "</pre>")
                        .replace(
                                "##A_OPEN##",
                                "<a>") // Simplificado: perde atributos, mas evita erro
                        .replace("##A_CLOSE##", "</a>");

        // Remove tags <a> vazias (sem href) - opcional
        restored = restored.replaceAll("<a>\\s*</a>", "");

        return restored;
    }

    @Data
    @Builder
    private static class ChatMessage {
        private String user;
        private String text;
        private String timestamp;
        private boolean audio;
    }

    /**
     * Envia o digest para um chat específico, tentando com HTML primeiro. Se houver erro de parse
     * (tags não fechadas), reenvia o mesmo conteúdo em texto puro.
     */
    private void sendDigestToChat(Long chatId, String finalMessage) {
        try {
            List<String> chunks = TelegramMessageSplitter.split(finalMessage);
            for (String chunk : chunks) {
                sendChunk(chatId, chunk);
            }
            log.info("✅ Digest enviado chatId={}", chatId);
        } catch (Exception e) {
            log.error("❌ Falha ao enviar digest chatId={}", chatId, e);
        }
    }

    /**
     * Envia um chunk de mensagem, tentando com HTML primeiro. Se houver erro de parse, reenvia em
     * texto puro.
     */
    private void sendChunk(Long chatId, String chunk) throws Exception {
        try {
            telegramFacade.enviarMensagemHtml(chatId, chunk);
        } catch (Exception e) {
            // Se falhar por parse de entidades, reenvia o mesmo chunk sem parse_mode
            if (e.getMessage() != null && e.getMessage().contains("can't parse entities")) {
                log.warn(
                        "⚠️ Erro de parse HTML, reenviando como texto puro para chatId={}", chatId);
                telegramFacade.enviarMensagem(chatId, chunk);
            } else {
                throw e;
            }
        }
    }
}
