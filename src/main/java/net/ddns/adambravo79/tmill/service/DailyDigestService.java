/* (c) 2026 | 22/07/2026 */
package net.ddns.adambravo79.tmill.service;

import static net.ddns.adambravo79.tmill.constant.BotMessages.BRAZIL_ZONE;
import static net.ddns.adambravo79.tmill.constant.BotMessages.FMT_DD_MM_YYYY_HH_MM;
import static net.ddns.adambravo79.tmill.constant.BotMessages.FMT_HH_MM;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.client.GroqClient;
import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.exception.DigestGenerationException;
import net.ddns.adambravo79.tmill.exception.DigestSendException;
import net.ddns.adambravo79.tmill.exception.GroqRateLimitException;
import net.ddns.adambravo79.tmill.prompt.DigestPersona;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.TelegramMessageSplitter;

/**
 * Serviço responsável pela geração e envio de digests diários de conversas.
 *
 * <p>Exception handling strategy:
 *
 * <ul>
 *   <li>{@link DataAccessException} — erro no banco de dados; log + skip digest.
 *   <li>{@link HttpClientErrorException} — erro no Groq (rate limit, auth, etc.).
 *   <li>{@link GroqRateLimitException} — rate limit específico do Groq.
 *   <li>{@link DigestGenerationException} — erro na geração do conteúdo do digest.
 *   <li>{@link DigestSendException} — erro no envio para o Telegram.
 *   <li>Erros fatais (Error, InterruptedException) — NUNCA engolidos.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyDigestService {

    private static final int MAX_PROMPT_SIZE = 32000;
    private static final int ALLOWED_MESSAGES_MARGIN = 4000;
    private static final int TRUNCATE_SLICE_DIVISOR = 3;

    /** Formato interno para queries SQL: yyyy-MM-dd HH:mm:ss */
    private static final DateTimeFormatter SQL_DTF =
            DateTimeFormatter.ofPattern(BotMessages.FMT_YYYY_MM_DD + " HH:mm:ss");

    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern(FMT_HH_MM);
    private static final DateTimeFormatter HEADER_FORMAT =
            DateTimeFormatter.ofPattern(FMT_DD_MM_YYYY_HH_MM);

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
        if (digestChatIdsStr == null || digestChatIdsStr.isBlank()) {
            log.info("Nenhum chat configurado para digest.");
            return;
        }

        for (String s : digestChatIdsStr.split(",")) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                digestChatIds.add(Long.parseLong(trimmed));
            } catch (NumberFormatException e) {
                log.warn("ID inválido em digest.chat-ids: '{}' — ignorado", s);
            }
        }

        if (!digestChatIds.isEmpty()) {
            log.info("📊 Digests serão enviados para os chats: {}", digestChatIds);
        }
    }

    /**
     * Gera um digest para um período personalizado e chat específico.
     *
     * @param from início do período
     * @param to fim do período
     * @param specificChatId chat alvo (null para todos os chats configurados)
     * @throws IllegalArgumentException se from estiver após to
     * @throws DigestGenerationException se houver erro na geração do digest
     * @throws DigestSendException se houver erro no envio
     */
    public void generateDigestCustom(LocalDateTime from, LocalDateTime to, Long specificChatId) {
        if (from == null || to == null) {
            throw new IllegalArgumentException(
                    "Período não pode ser nulo (from=" + from + ", to=" + to + ")");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "Período inválido: from (" + from + ") está após to (" + to + ")");
        }
        generateDigest(from, to, "PERÍODO PERSONALIZADO", specificChatId);
    }

    @Scheduled(cron = "0 30 8 * * *", zone = BRAZIL_ZONE)
    public void generateMorningDigest() {
        if (!digestEnabled || digestChatIds.isEmpty()) {
            log.debug("Digest matinal desabilitado ou sem chats configurados.");
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of(BRAZIL_ZONE));
        LocalDateTime from = now.minusDays(1).withHour(20).withMinute(30).withSecond(0);
        LocalDateTime to = now.withHour(8).withMinute(30).withSecond(0);
        generateDigest(from, to, "RESUMO DA MADRUGADA/MANHÃ", null);
    }

    @Scheduled(cron = "0 30 20 * * *", zone = BRAZIL_ZONE)
    public void generateEveningDigest() {
        if (!digestEnabled || digestChatIds.isEmpty()) {
            log.debug("Digest noturno desabilitado ou sem chats configurados.");
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of(BRAZIL_ZONE));
        LocalDateTime from = now.withHour(8).withMinute(30).withSecond(0);
        LocalDateTime to = now.withHour(20).withMinute(30).withSecond(0);
        generateDigest(from, to, "RESUMO DO DIA", null);
    }

    // ======================== CORE PIPELINE ========================

    private void generateDigest(
            LocalDateTime from, LocalDateTime to, String periodLabel, Long specificChatId) {

        log.info("Gerando {} | {} -> {}", periodLabel, from, to);

        try {
            List<ChatMessage> allMessages = fetchMessages(from, to);

            if (allMessages.isEmpty()) {
                log.info("Nenhuma interação encontrada no período.");
                return;
            }

            String finalMessages = buildMessagesBlock(allMessages);
            finalMessages = truncateIfNeeded(finalMessages);

            log.info("📦 Mensagens finais size={}", finalMessages.length());

            String summary = generateSummary(finalMessages, periodLabel);
            if (summary == null || summary.isBlank()) {
                log.warn("Resumo vazio do Groq para período {}.", periodLabel);
                return;
            }

            String finalMessage = buildHeader(periodLabel, from, to) + sanitizeDigestText(summary);
            Set<Long> targets = specificChatId != null ? Set.of(specificChatId) : digestChatIds;

            for (Long chatId : targets) {
                sendDigestToChat(chatId, finalMessage);
            }

        } catch (DataAccessException e) {
            log.error("❌ Erro de acesso ao banco de dados ao gerar digest {}", periodLabel, e);
            // Não relança — digest é best-effort, mas logamos severamente

        } catch (HttpClientErrorException e) {
            log.error(
                    "❌ Erro HTTP do Groq ao gerar digest {}: {}",
                    periodLabel,
                    e.getStatusCode(),
                    e);
            // Rate limit ou erro de autenticação — não retry aqui, apenas log

        } catch (GroqRateLimitException e) {
            log.error(
                    "❌ Rate limit do Groq ao gerar digest {}. Considerar retry agendado.",
                    periodLabel,
                    e);

        } catch (DigestGenerationException e) {
            log.error("❌ Falha na geração do digest {}", periodLabel, e);

        } catch (DigestSendException e) {
            log.error("❌ Falha no envio do digest {}", periodLabel, e);

        } catch (RuntimeException e) {
            log.error("❌ Erro inesperado de runtime ao gerar digest {}", periodLabel, e);
            throw new DigestGenerationException(
                    "Erro inesperado ao gerar digest: " + periodLabel, e);
        }
    }

    // ======================== FETCH & BUILD ========================

    @SuppressWarnings("null")
    private List<ChatMessage> fetchMessages(LocalDateTime from, LocalDateTime to) {
        String fromStr = from.format(SQL_DTF);
        String toStr = to.format(SQL_DTF);

        List<Map<String, Object>> messages =
                jdbcTemplate.queryForList(
                        """
            SELECT user_name, text, timestamp
            FROM messages
            WHERE datetime(timestamp, 'localtime') BETWEEN ? AND ?
            ORDER BY timestamp ASC
            """,
                        fromStr,
                        toStr);

        List<Map<String, Object>> transcripts =
                jdbcTemplate.queryForList(
                        """
            SELECT user_name, text, timestamp
            FROM transcripts
            WHERE datetime(timestamp, 'localtime') BETWEEN ? AND ?
            ORDER BY timestamp ASC
            """,
                        fromStr,
                        toStr);

        List<ChatMessage> allMessages = new ArrayList<>(messages.size() + transcripts.size());

        for (Map<String, Object> row : messages) {
            allMessages.add(buildChatMessage(row, false));
        }
        for (Map<String, Object> row : transcripts) {
            allMessages.add(buildChatMessage(row, true));
        }

        allMessages.sort(Comparator.comparing(ChatMessage::getTimestamp));
        return allMessages;
    }

    private ChatMessage buildChatMessage(Map<String, Object> row, boolean isAudio) {
        String user = (String) row.get("user_name");
        String text = (String) row.get("text");
        String timestamp = String.valueOf(row.get("timestamp"));
        return ChatMessage.builder()
                .user(user != null ? user : "Desconhecido")
                .text(text != null ? text : "")
                .timestamp(timestamp)
                .audio(isAudio)
                .build();
    }

    // ======================== FETCH & BUILD ========================

    @SuppressWarnings({"null", "TimeZone"})
    private String buildMessagesBlock(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        LocalDateTime previous = null;

        for (ChatMessage msg : messages) {
            LocalDateTime current = parseTimestampSafely(msg.getTimestamp());
            if (current == null) {
                log.warn("Timestamp inválido ignorado: {}", msg.getTimestamp());
                continue;
            }

            if (previous != null) {
                ZoneId zone = ZoneId.of(BRAZIL_ZONE);
                long diff =
                        Duration.between(previous.atZone(zone), current.atZone(zone)).toMinutes();
                if (diff >= 20) {
                    sb.append("\n==============================\n");
                    sb.append("NOVO BLOCO DE CONVERSA\n");
                    sb.append("==============================\n\n");
                }
            }

            String line =
                    String.format(
                            "[%s] %s%s: %s%n",
                            HOUR_FORMAT.format(current),
                            msg.getUser(),
                            msg.isAudio() ? " (áudio)" : "",
                            msg.getText());
            sb.append(line);
            previous = current;
        }
        return sb.toString();
    }

    /** Faz parse seguro de timestamp, suportando formatos com e sem 'T'. */
    private LocalDateTime parseTimestampSafely(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return null;
        }
        try {
            String normalized = timestamp.replace(" ", "T");
            return LocalDateTime.parse(normalized);
        } catch (DateTimeParseException e) {
            log.warn("Não foi possível parsear timestamp: '{}'", timestamp);
            return null;
        }
    }

    private String truncateIfNeeded(String finalMessages) {
        if (finalMessages.length() <= MAX_PROMPT_SIZE) {
            return finalMessages;
        }

        int allowedMessagesSize = MAX_PROMPT_SIZE - ALLOWED_MESSAGES_MARGIN;
        log.warn(
                "✂️ Mensagens truncadas de {} para {} chars",
                finalMessages.length(),
                allowedMessagesSize);

        int slice = allowedMessagesSize / TRUNCATE_SLICE_DIVISOR;
        int len = finalMessages.length();

        String start = safeSubstring(finalMessages, 0, slice);
        String middle =
                safeSubstring(
                        finalMessages,
                        Math.max(0, (len / 2) - (slice / 2)),
                        Math.min(len, (len / 2) + (slice / 2)));
        String end = safeSubstring(finalMessages, Math.max(0, len - slice), len);

        return start + "\n\n[...]\n\n" + middle + "\n\n[...]\n\n" + end;
    }

    // ======================== TRUNCATE ========================

    private String safeSubstring(String str, int begin, int end) {
        int safeBegin = Math.clamp(begin, 0, str.length());
        int safeEnd = Math.clamp(end, safeBegin, str.length());
        return str.substring(safeBegin, safeEnd);
    }

    // ======================== SUMMARY ========================

    private String generateSummary(String finalMessages, String periodLabel) {
        try {
            DigestPersona persona = DigestPersona.T1000;
            return groqClient.gerarResumoDigest(finalMessages, persona, periodLabel);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new GroqRateLimitException("Rate limit do Groq ao gerar resumo", e);
        } catch (HttpClientErrorException e) {
            throw new DigestGenerationException(
                    "Erro HTTP do Groq ao gerar resumo: " + e.getStatusCode(), e);
        } catch (ResourceAccessException e) {
            throw new DigestGenerationException("Falha de conectividade com Groq", e);
        }
    }

    private String buildHeader(String periodLabel, LocalDateTime from, LocalDateTime to) {
        return String.format(
                """
        <b>📊 %s</b>
        <i>Período: %s - %s</i>

        """,
                periodLabel, from.format(HEADER_FORMAT), to.format(HEADER_FORMAT));
    }

    // ======================== SANITIZE ========================

    private String sanitizeDigestText(String text) {
        if (text == null) {
            return "";
        }

        // Converte quebras de linha
        String sanitized =
                text.replaceAll("(?i)<br\\s*/?>", "\n")
                        .replaceAll("(?i)</?ul\\s*>", "")
                        .replaceAll("(?i)<li\\s*>", "• ")
                        .replaceAll("(?i)</li\\s*>", "\n");

        // Protege tags permitidas (usando regex mais simples para <a>)
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
                        .replaceAll("(?i)<a[^>]*>", "##A_OPEN##") // regex simplificada
                        .replace("</a>", "##A_CLOSE##");

        // Escapa caracteres < e > restantes
        String escaped = protectedText.replace("<", "&lt;").replace(">", "&gt;");

        // Restaura tags permitidas
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
                        .replace("##A_OPEN##", "<a>")
                        .replace("##A_CLOSE##", "</a>");

        // Remove tags <a> vazias
        return restored.replaceAll("<a>\\s*</a>", "");
    }

    // ======================== SEND ========================

    /**
     * Envia o digest para um chat específico. Se falhar, lança DigestSendException para que o caller
     * possa decidir.
     */
    private void sendDigestToChat(Long chatId, String finalMessage) {
        try {
            List<String> chunks = TelegramMessageSplitter.split(finalMessage);
            for (String chunk : chunks) {
                sendChunk(chatId, chunk);
            }
            log.info("✅ Digest enviado chatId={}", chatId);
        } catch (DigestSendException e) {
            log.error("❌ Falha ao enviar digest chatId={}", chatId, e);
            throw e; // Repropaga para o caller decidir
        } catch (RuntimeException e) {
            log.error("❌ Erro inesperado ao enviar digest chatId={}", chatId, e);
            throw new DigestSendException(
                    "Erro inesperado no envio do digest para chatId=" + chatId, e);
        }
    }

    /**
     * Envia um chunk de mensagem, tentando com HTML primeiro. Se houver erro de parse de entidades
     * HTML, reenvia em texto puro.
     */
    private void sendChunk(Long chatId, String chunk) {
        try {
            telegramFacade.enviarMensagemHtml(chatId, chunk);
        } catch (HttpClientErrorException.BadRequest e) {
            if (isHtmlParseError(e)) {
                log.warn(
                        "⚠️ Erro de parse HTML (BadRequest), reenviando como texto puro para"
                                + " chatId={}",
                        chatId);
                try {
                    telegramFacade.enviarMensagem(chatId, chunk);
                } catch (RuntimeException fallbackEx) {
                    throw new DigestSendException(
                            "Falha no fallback de texto puro para chatId=" + chatId, fallbackEx);
                }
            } else {
                throw new DigestSendException(
                        "Erro BadRequest do Telegram (não é parse HTML) para chatId=" + chatId, e);
            }
        } catch (HttpClientErrorException e) {
            throw new DigestSendException(
                    "Erro HTTP " + e.getStatusCode() + " do Telegram para chatId=" + chatId, e);
        } catch (ResourceAccessException e) {
            throw new DigestSendException(
                    "Falha de conectividade com Telegram para chatId=" + chatId, e);
        }
    }

    private boolean isHtmlParseError(HttpClientErrorException.BadRequest e) {
        String msg = e.getMessage();
        return msg != null && msg.contains("can't parse entities");
    }

    // ======================== INNER CLASS ========================

    @Data
    @Builder
    private static class ChatMessage {
        private String user;
        private String text;
        private String timestamp;
        private boolean audio;
    }
}
