/* (c) 2026 | 07/05/2026 */
package net.ddns.adambravo79.tmill.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptStoreService {

    private final JdbcTemplate jdbcTemplate;

    public void saveTranscript(long chatId, long userId, String userName, String text) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO transcripts (chat_id, user_id, user_name, text) VALUES (?, ?, ?,"
                            + " ?)",
                    chatId,
                    userId,
                    userName,
                    text);
        } catch (Exception e) {
            log.error("Erro ao salvar transcrição", e);
        }
    }
}
