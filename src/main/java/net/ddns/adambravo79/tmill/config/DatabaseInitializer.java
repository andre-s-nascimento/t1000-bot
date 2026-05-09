/* (c) 2026 | 09/05/2026 */
package net.ddns.adambravo79.tmill.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DatabaseInitializer {

  private final JdbcTemplate jdbcTemplate;

  public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @PostConstruct
  public void init() {
    try {
      jdbcTemplate.execute(
          """
              CREATE TABLE IF NOT EXISTS messages (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  chat_id INTEGER NOT NULL,
                  user_id INTEGER NOT NULL,
                  user_name TEXT,
                  text TEXT NOT NULL,
                  timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
              )
          """);
      log.info("Tabela 'messages' verificada/criada com sucesso.");
    } catch (Exception e) {
      log.error("Erro ao criar tabela messages", e);
    }

    try {
      jdbcTemplate.execute(
          """
              CREATE TABLE IF NOT EXISTS transcripts (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  chat_id INTEGER NOT NULL,
                  user_id INTEGER NOT NULL,
                  user_name TEXT,
                  text TEXT NOT NULL,
                  timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
              )
          """);
      log.info("Tabela 'transcripts' verificada/criada com sucesso.");
    } catch (Exception e) {
      log.error("Erro ao criar tabela transcripts", e);
    }
  }
}
