/* (c) 2026 | 04/10/2026 */
package net.ddns.adambravo79.tmill.service;

import static net.ddns.adambravo79.tmill.constant.BotMessages.BRAZIL_ZONE;
import static net.ddns.adambravo79.tmill.constant.BotMessages.FMT_DD_MM_YYYY_HH_MM;
import static net.ddns.adambravo79.tmill.constant.BotMessages.FMT_HH_MM;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.client.GroqClient;
import net.ddns.adambravo79.tmill.exception.DigestGenerationException;
import net.ddns.adambravo79.tmill.exception.DigestSendException;
import net.ddns.adambravo79.tmill.exception.GroqRateLimitException;
import net.ddns.adambravo79.tmill.prompt.DigestPersona;
import net.ddns.adambravo79.tmill.service.config.JsonConfigLoader;
import net.ddns.adambravo79.tmill.service.feature.FeatureFlagAdminService;
import net.ddns.adambravo79.tmill.service.prompt.PromptRegistryService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;
import net.ddns.adambravo79.tmill.telegram.util.TelegramMessageSplitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyDigestService implements SchedulingConfigurer {

    private static final int MAX_PROMPT_SIZE = 18000;
    private static final int ALLOWED_MESSAGES_MARGIN = 2000;
    private static final int TRUNCATE_SLICE_DIVISOR = 3;

    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern(FMT_HH_MM);
    private static final DateTimeFormatter HEADER_FORMAT =
            DateTimeFormatter.ofPattern(FMT_DD_MM_YYYY_HH_MM);

    private final JdbcTemplate jdbcTemplate;
    private final GroqClient groqClient;
    private final TelegramFacade telegramFacade;
    private final MetricsService metricsService;
    private final FeatureFlagAdminService featureFlags;
    private final PromptRegistryService promptRegistryService;
    private final JsonConfigLoader jsonConfigLoader;

    @Value("${digest.chat-ids:}")
    private String digestChatIdsStr;

    @Value("${digest.persona.default:T1000}")
    private String defaultPersonaName;

    private final Set<Long> digestChatIds = new HashSet<>();

    @PostConstruct
    public void init() {
        if (digestChatIdsStr == null || digestChatIdsStr.isBlank()) {
            log.info("Nenhum chat configurado para digest.");
            return;
        }

        for (String s : digestChatIdsStr.split(",")) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) continue;
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

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(
                this::generateMorningDigest,
                ctx ->
                        new CronTrigger(
                                        getCron("morningCron", "0 30 8 * * *"),
                                        ZoneId.of(BRAZIL_ZONE))
                                .nextExecution(ctx));
        taskRegistrar.addTriggerTask(
                this::generateEveningDigest,
                ctx ->
                        new CronTrigger(
                                        getCron("eveningCron", "0 30 20 * * *"),
                                        ZoneId.of(BRAZIL_ZONE))
                                .nextExecution(ctx));
    }

    private String getCron(String key, String defaultCron) {
        return jsonConfigLoader
                .loadConfig("config/digest-config.json", Map.class, "config/digest-config.json")
                .map(m -> (String) m.get(key))
                .orElse(defaultCron);
    }

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

    public void generateMorningDigest() {
        if (!featureFlags.isEnabled("digest.enabled") || digestChatIds.isEmpty()) {
            log.debug("Digest matinal desabilitado ou sem chats configurados.");
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of(BRAZIL_ZONE));
        LocalDateTime from = now.minusDays(1).withHour(20).withMinute(30).withSecond(0);
        LocalDateTime to = now.withHour(8).withMinute(30).withSecond(0);
        generateDigest(from, to, "RESUMO DA MADRUGADA/MANHÃ", null);
    }

    public void generateEveningDigest() {
        if (!featureFlags.isEnabled("digest.enabled") || digestChatIds.isEmpty()) {
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
                metricsService.error("digest_sem_mensagens");
                return;
            }

            String finalMessages = buildMessagesBlock(allMessages);
            finalMessages = truncateIfNeeded(finalMessages);

            log.info("📦 Mensagens finais size={}", finalMessages.length());

            String summary = generateSummary(finalMessages, periodLabel);
            if (summary == null || summary.isBlank()) {
                log.warn("Resumo vazio do Groq para período {}.", periodLabel);
                metricsService.error("digest_groq_vazio");
                return;
            }

            String finalMessage = buildHeader(periodLabel, from, to) + sanitizeDigestText(summary);
            Set<Long> targets = specificChatId != null ? Set.of(specificChatId) : digestChatIds;

            for (Long chatId : targets) {
                sendDigestToChat(chatId, finalMessage);
            }

            metricsService.success("digest_gerado_sucesso");

        } catch (DataAccessException e) {
            log.error("❌ Erro de acesso ao banco de dados ao gerar digest {}", periodLabel, e);
            metricsService.error("digest_db_error");

        } catch (HttpClientErrorException e) {
            log.error(
                    "❌ Erro HTTP do Groq ao gerar digest {}: {}",
                    periodLabel,
                    e.getStatusCode(),
                    e);
            metricsService.error("digest_groq_http_error");

        } catch (GroqRateLimitException e) {
            log.error(
                    "❌ Rate limit do Groq ao gerar digest {}. Considerar retry agendado.",
                    periodLabel,
                    e);
            metricsService.error("digest_groq_rate_limit");

        } catch (DigestGenerationException e) {
            log.error("❌ Falha na geração do digest {}", periodLabel, e);
            metricsService.error("digest_groq_indisponivel");

        } catch (DigestSendException e) {
            log.error("❌ Falha no envio do digest {}", periodLabel, e);
            metricsService.error("digest_envio_erro");

        } catch (RuntimeException e) {
            log.error("❌ Erro inesperado de runtime ao gerar digest {}", periodLabel, e);
            metricsService.error("digest_erro_inesperado");
            throw new DigestGenerationException(
                    "Erro inesperado ao gerar digest: " + periodLabel, e);
        }
    }

    // ======================== FETCH & BUILD ========================

    private List<ChatMessage> fetchMessages(LocalDateTime from, LocalDateTime to) {
        // 🔧 FIX: passar LocalDateTime direto — o driver do Postgres converte para TIMESTAMP.
        // Formatar como String quebra no Postgres (timestamp >= varchar não existe).
        List<Map<String, Object>> messages =
                jdbcTemplate.queryForList(
                        """
                        SELECT user_name, text, timestamp
                        FROM messages
                        WHERE timestamp BETWEEN ? AND ?
                        AND ignore_in_digest = false
                        ORDER BY timestamp ASC
                        """,
                        from,
                        to);

        List<Map<String, Object>> transcripts =
                jdbcTemplate.queryForList(
                        """
                        SELECT user_name, text, timestamp
                        FROM transcripts
                        WHERE timestamp BETWEEN ? AND ?
                        AND ignore_in_digest = false
                        ORDER BY timestamp ASC
                        """,
                        from,
                        to);

        return Stream.concat(
                        messages.stream().map(row -> buildChatMessage(row, false)),
                        transcripts.stream().map(row -> buildChatMessage(row, true)))
                .sorted(Comparator.comparing(msg -> msg.getTimestamp()))
                .toList();
    }

    private ChatMessage buildChatMessage(Map<String, Object> row, boolean isAudio) {
        String user = (String) row.get("user_name");
        String text = (String) row.get("text");
        Object rawTimestamp = row.get("timestamp"); // 👈 pode ser Timestamp OU LocalDateTime

        // 🔧 FIX: normalizar para String ISO (formato aceito pelo parseTimestampSafely)
        String timestamp;
        if (rawTimestamp instanceof java.sql.Timestamp ts) {
            timestamp = ts.toLocalDateTime().toString(); // "2026-09-22T10:00"
        } else if (rawTimestamp instanceof LocalDateTime ldt) {
            timestamp = ldt.toString();
        } else if (rawTimestamp != null) {
            timestamp = rawTimestamp.toString();
        } else {
            timestamp = null;
        }

        return ChatMessage.builder()
                .user(user != null ? user : "Desconhecido")
                .text(text != null ? text : "")
                .timestamp(timestamp)
                .audio(isAudio)
                .build();
    }

    @SuppressWarnings("TimeZone")
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

        String start = findCutPoint(finalMessages, 0, slice);
        String middle =
                findCutPoint(
                        finalMessages,
                        Math.max(0, (len / 2) - (slice / 2)),
                        Math.min(len, (len / 2) + (slice / 2)));
        String end = findCutPoint(finalMessages, Math.max(0, len - slice), len);

        return start + "\n\n[...]\n\n" + middle + "\n\n[...]\n\n" + end;
    }

    private String findCutPoint(String text, int begin, int end) {
        int safeEnd = Math.min(end, text.length());
        if (safeEnd >= text.length()) {
            return text.substring(Math.min(begin, text.length()));
        }
        int cut = text.lastIndexOf('\n', safeEnd);
        if (cut > begin) {
            return text.substring(begin, cut);
        }
        return text.substring(begin, safeEnd);
    }

    // ======================== SUMMARY ========================
    private String generateSummary(String finalMessages, String periodLabel) {
        try {
            // Obtém dinamicamente o nome da persona definida no JSON recarregado
            String activePersonaName = promptRegistryService.getActivePersonaName();
            DigestPersona persona = DigestPersona.fromString(activePersonaName);

            return groqClient.gerarResumoDigest(finalMessages, persona, periodLabel);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new GroqRateLimitException("Rate limit do Groq ao gerar resumo", e);
        } catch (HttpClientErrorException e) {
            throw e;
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
        if (text == null) return "";

        String sanitized =
                text.replaceAll("(?i)<br\\s*/?>", "\n")
                        .replaceAll("(?i)</?ul\\s*>", "")
                        .replaceAll("(?i)<li\\s*>", "• ")
                        .replaceAll("(?i)</li\\s*>", "\n");

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
                        .replaceAll("(?i)<a[^>]*>", "##A_OPEN##")
                        .replace("</a>", "##A_CLOSE##");

        String escaped = protectedText.replace("<", "&lt;").replace(">", "&gt;");

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

        return restored.replaceAll("<a>\\s*</a>", "");
    }

    // ======================== SEND ========================

    private void sendDigestToChat(Long chatId, String finalMessage) {
        try {
            List<String> chunks = TelegramMessageSplitter.split(finalMessage);
            for (String chunk : chunks) {
                sendChunk(chatId, chunk);
            }
            log.info("✅ Digest enviado chatId={}", chatId);
        } catch (DigestSendException e) {
            log.error("❌ Falha ao enviar digest chatId={}", chatId, e);
            throw e;
        } catch (RuntimeException e) {
            log.error("❌ Erro inesperado ao enviar digest chatId={}", chatId, e);
            throw new DigestSendException(
                    "Erro inesperado no envio do digest para chatId=" + chatId, e);
        }
    }

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
