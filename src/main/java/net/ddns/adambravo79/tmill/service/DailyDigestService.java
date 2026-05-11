/* (c) 2026 | 09/05/2026 */
package net.ddns.adambravo79.tmill.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.client.GroqClient;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyDigestService {

    private final JdbcTemplate jdbcTemplate;
    private final GroqClient groqClient;
    private final TelegramFacade telegramFacade;

    @Value("${digest.enabled:false}")
    private boolean digestEnabled;

    @Value("${digest.chat-ids:}")
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
            log.info("Nenhum chat configurado para digest. As mensagens não serão enviadas.");
        }
    }

    // DailyDigestService.java
    public void generateDigestCustom(LocalDateTime from, LocalDateTime to) {
        generateDigest(from, to, "PERÍODO PERSONALIZADO");
    }

    // Resumo da manhã: cobre das 20:30 do dia anterior até 08:29 do dia atual
    @Scheduled(cron = "0 30 8 * * *", zone = "America/Sao_Paulo")
    public void generateMorningDigest() {
        log.info("🔁 Método generateMorningDigest foi chamado.");
        if (!digestEnabled) {
            log.debug("Digest desabilitado por configuração.");
            return;
        }
        if (digestChatIds.isEmpty()) {
            log.warn("Nenhum chat configurado para envio do digest da manhã.");
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        LocalDateTime from = now.minusDays(1).withHour(20).withMinute(30).withSecond(0);
        LocalDateTime to = now.withHour(8).withMinute(30).withSecond(0);
        generateDigest(from, to, "RESUMO DA MADRUGADA/MANHÃ");
    }

    // Resumo da noite: cobre das 08:30 até 20:29 do mesmo dia
    @Scheduled(cron = "0 30 20 * * *", zone = "America/Sao_Paulo")
    public void generateEveningDigest() {
        if (!digestEnabled) {
            log.debug("Digest desabilitado por configuração.");
            return;
        }
        if (digestChatIds.isEmpty()) {
            log.warn("Nenhum chat configurado para envio do digest da noite.");
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        LocalDateTime from = now.withHour(8).withMinute(30).withSecond(0);
        LocalDateTime to = now.withHour(20).withMinute(30).withSecond(0);
        generateDigest(from, to, "RESUMO DO DIA");
    }

    private void generateDigest(LocalDateTime from, LocalDateTime to, String periodLabel) {
        log.info("Gerando {} período {} - {}", periodLabel, from, to);

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String fromStr = from.format(dtf);
        String toStr = to.format(dtf);

        // Buscar mensagens de texto
        List<Map<String, Object>> messages =
                jdbcTemplate.queryForList(
                        "SELECT user_name, text FROM messages WHERE datetime(timestamp,"
                                + " 'localtime') BETWEEN ? AND ? ORDER BY timestamp ASC",
                        fromStr,
                        toStr);

        // Buscar transcrições de áudio
        List<Map<String, Object>> transcripts =
                jdbcTemplate.queryForList(
                        "SELECT user_name, text FROM transcripts WHERE datetime(timestamp,"
                                + " 'localtime') BETWEEN ? AND ? ORDER BY timestamp ASC",
                        fromStr,
                        toStr);

        if (messages.isEmpty() && transcripts.isEmpty()) {
            log.info("Nenhuma mensagem ou transcrição no período.");
            return;
        }

        // Construir prompt
        StringBuilder prompt = new StringBuilder();
        prompt.append(
                        "Você é um assistente especializado em resumir conversas de um grupo de"
                                + " cinema. ")
                .append(
                        "Considere que as mensagens podem ser textos ou transcrições de áudios (já"
                                + " corrigidos). ")
                .append(
                        "Faça um resumo de no máximo 300 palavras, destacando os principais"
                                + " assuntos discutidos, ")
                .append(
                        "quem participou mais, e inclua um toque de humor no estilo do Exterminador"
                                + " do Futuro (T-1000). ")
                .append(
                        "**IMPORTANTE:** Use formatação HTML para negrito (<b>texto</b>) e itálico"
                                + " (<i>texto</i>). ")
                .append(
                        "NÃO use Markdown (nem **, nem *). Escreva o resumo em português do"
                                + " Brasil.\n\n")
                .append(
                        "Abaixo estão as interações do período (cada linha é usuário:"
                                + " mensagem):\n");

        for (Map<String, Object> msg : messages) {
            String user = (String) msg.get("user_name");
            String text = (String) msg.get("text");
            prompt.append(user).append(": ").append(text).append("\n");
        }
        for (Map<String, Object> trans : transcripts) {
            String user = (String) trans.get("user_name");
            String text = (String) trans.get("text");
            prompt.append(user).append(" (áudio): ").append(text).append("\n");
        }

        String promptStr = prompt.toString();
        // 8000 caracteres é um limite seguro para a maioria dos modelos
        if (promptStr.length() > 8000) {
            promptStr = promptStr.substring(0, 8000);
            log.warn("Prompt truncado para 8000 caracteres.");
        }

        try {
            String summary = groqClient.refinarTexto(promptStr);
            if (summary == null || summary.isBlank()) {
                log.warn("Resumo vazio retornado pela IA.");
                return;
            }

            // Converte possíveis markdown para HTML (fallback)
            String summaryHtml = markdownToHtml(summary);

            DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            String header =
                    String.format(
                            "<b>📊 %s</b>\n<i>Período: %s - %s</i>\n\n",
                            periodLabel, from.format(dateFormat), to.format(dateFormat));
            String finalMessage = header + summaryHtml;

            // Enviar para cada chat configurado
            for (Long chatId : digestChatIds) {
                try {
                    telegramFacade.enviarMensagemHtml(chatId, finalMessage);
                    log.info("Resumo enviado para chatId={}", chatId);
                } catch (Exception e) {
                    log.error("Erro ao enviar resumo para chatId={}", chatId, e);
                }
            }
        } catch (Exception e) {
            log.error("Erro ao gerar resumo com IA", e);
        }
    }

    private String markdownToHtml(String text) {
        // Proteger itens já com tags HTML
        // Converter **negrito** primeiro (para não confundir com itálico)
        String html = text.replaceAll("\\*\\*([^*]+)\\*\\*", "<b>$1</b>");
        // Depois converter *itálico* (que não seja parte de negrito)
        html = html.replaceAll("(?<!\\*)\\*([^*]+)\\*(?!\\*)", "<i>$1</i>");
        return html;
    }
}
