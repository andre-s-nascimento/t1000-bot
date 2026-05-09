/* (c) 2026 | 07/05/2026 */
package net.ddns.adambravo79.tmill.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.client.GroqClient;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyDigestService {

  private final JdbcTemplate jdbcTemplate;
  private final GroqClient groqClient;
  private final TelegramFacade telegramFacade;

  @Value("${digest.enabled:false}")
  private boolean digestEnabled;

  @Value("${digest.chat-id:0}")
  private long digestChatId;

  @Value("${digest.interval:day}") // "day" or "split"
  private String digestInterval;

  // Job para resumo da manhã (20:30 do dia anterior até 08:29 do dia atual)
  @Scheduled(cron = "0 30 8 * * *", zone = "America/Sao_Paulo")
  public void generateMorningDigest() {
    if (!digestEnabled || digestChatId == 0) return;
    generateDigest(
        LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).minusDays(1).withHour(20).withMinute(30),
        LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).withHour(8).withMinute(30));
  }

  // Job para resumo da noite (08:30 até 20:29 do mesmo dia)
  @Scheduled(cron = "0 30 20 * * *", zone = "America/Sao_Paulo")
  public void generateEveningDigest() {
    if (!digestEnabled || digestChatId == 0) return;
    generateDigest(
        LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).withHour(8).withMinute(30),
        LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).withHour(20).withMinute(30));
  }

  private void generateDigest(LocalDateTime from, LocalDateTime to) {
    // Coletar mensagens de texto
    List<Map<String, Object>> messages =
        jdbcTemplate.queryForList(
            "SELECT user_name, text FROM messages WHERE timestamp BETWEEN ? AND ? ORDER BY"
                + " timestamp ASC",
            from,
            to);

    // Coletar transcrições
    List<Map<String, Object>> transcripts =
        jdbcTemplate.queryForList(
            "SELECT user_name, text FROM transcripts WHERE timestamp BETWEEN ? AND ? ORDER BY"
                + " timestamp ASC",
            from,
            to);

    if (messages.isEmpty() && transcripts.isEmpty()) {
      log.info("Nenhuma mensagem ou transcrição no período {} - {}", from, to);
      return;
    }

    // Construir prompt
    StringBuilder prompt = new StringBuilder();
    prompt
        .append(
            "Você é um assistente que resume conversas de um grupo de cinema. Abaixo estão as"
                + " mensagens e transcrições de áudio do período de ")
        .append(from)
        .append(" até ")
        .append(to)
        .append(
            ". Faça um resumo de no máximo 300 palavras destacando os principais assuntos"
                + " discutidos, quem participou mais, e adicione um toque de humor típico do"
                + " Exterminador do Futuro (T-1000) no final.\n\n");
    prompt.append("Mensagens:\n");
    for (Map<String, Object> msg : messages) {
      prompt
          .append("- ")
          .append(msg.get("user_name"))
          .append(": ")
          .append(msg.get("text"))
          .append("\n");
    }
    prompt.append("\nTranscrições:\n");
    for (Map<String, Object> trans : transcripts) {
      prompt
          .append("- ")
          .append(trans.get("user_name"))
          .append(": ")
          .append(trans.get("text"))
          .append("\n");
    }

    // Chamar Groq
    try {
      String summary = groqClient.refinarTexto(prompt.toString());
      if (summary == null || summary.isBlank()) {
        log.warn("Resumo vazio recebido da IA");
        return;
      }
      // Enviar para o chat configurado
      String message = "<b>📊 Resumo do período</b>\n" + summary;
      telegramFacade.enviarMensagemHtml(digestChatId, message);
      log.info("Resumo enviado para chatId={}", digestChatId);
    } catch (Exception e) {
      log.error("Erro ao gerar resumo com IA", e);
    }
  }
}
