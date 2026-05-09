/* (c) 2026 | 09/05/2026 */
package net.ddns.adambravo79.tmill.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DatabaseInitializer {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        // cria tabela se não existir
        jdbcTemplate.execute(
                """
            CREATE TABLE IF NOT EXISTS transcripts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                chat_id INTEGER NOT NULL,
                user_id INTEGER NOT NULL,
                user_name TEXT,
                text TEXT NOT NULL,
                raw_text TEXT,
                timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);
        // se a tabela já existir sem a coluna raw_text, adiciona
        try {
            jdbcTemplate.execute("ALTER TABLE transcripts ADD COLUMN raw_text TEXT");
            log.info("Coluna raw_text adicionada à tabela transcripts");
        } catch (Exception e) {
            // coluna já existe (ignorar erro)
            log.debug("Coluna raw_text já presente na tabela transcripts");
        }
    }
}
