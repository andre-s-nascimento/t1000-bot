/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageStoreService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Salva uma mensagem no banco, com indicação se deve ser ignorada no digest.
     *
     * @param chatId identificador do chat
     * @param userId identificador do usuário
     * @param userName nome do usuário
     * @param text conteúdo da mensagem
     * @param ignoreInDigest true se a mensagem contém spoiler e deve ser excluída do resumo
     */
    public void saveMessage(
            long chatId, long userId, String userName, String text, boolean ignoreInDigest) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO messages (chat_id, user_id, user_name, text, ignore_in_digest)"
                            + " VALUES (?, ?, ?, ?, ?)",
                    chatId,
                    userId,
                    userName,
                    text,
                    ignoreInDigest ? 1 : 0);
        } catch (Exception e) {
            log.error("Erro ao salvar mensagem", e);
        }
    }
}
