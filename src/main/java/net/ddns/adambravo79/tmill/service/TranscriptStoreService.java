/* (c) 2026 | 07/05/2026 */
package net.ddns.adambravo79.tmill.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptStoreService {

  private final JdbcTemplate jdbcTemplate;

  public void saveTranscriptWithRaw(
      long chatId, long userId, String userName, String rawText, String refinedText) {
    try {
      jdbcTemplate.update(
          "INSERT INTO transcripts (chat_id, user_id, user_name, text, raw_text) VALUES (?, ?, ?,"
              + " ?, ?)",
          chatId,
          userId,
          userName,
          refinedText,
          rawText);
    } catch (Exception e) {
      log.error("Erro ao salvar transcrição (com raw)", e);
    }
  }

  public void saveTranscript(long chatId, long userId, String userName, String text) {
    try {
      jdbcTemplate.update(
          "INSERT INTO transcripts (chat_id, user_id, user_name, text) VALUES (?, ?, ?," + " ?)",
          chatId,
          userId,
          userName,
          text);
    } catch (Exception e) {
      log.error("Erro ao salvar transcrição", e);
    }
  }
}
