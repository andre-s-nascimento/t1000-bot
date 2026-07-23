package net.ddns.adambravo79.tmill.config;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.util.LogSanitizer;

@Slf4j
@Component
public class DatabaseInitializer {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        // Tabela transcripts (já existente)
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

        try {
            List<Map<String, Object>> columns =
                    jdbcTemplate.queryForList("PRAGMA table_info(transcripts)");
            boolean hasRawText =
                    columns.stream().anyMatch(row -> "raw_text".equals(row.get("name")));
            if (!hasRawText) {
                jdbcTemplate.execute("ALTER TABLE transcripts ADD COLUMN raw_text TEXT");
                log.info("Coluna raw_text adicionada à tabela transcripts");
            }
        } catch (Exception e) {
            log.error("Erro ao verificar/adicionar coluna raw_text: {}", e.getMessage());
        }

        // Tabela releases_notified
        jdbcTemplate.execute(
                """
            CREATE TABLE IF NOT EXISTS releases_notified (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                tmdb_id INTEGER NOT NULL,
                media_type TEXT NOT NULL,
                release_date TEXT NOT NULL,
                title TEXT,
                overview TEXT,
                rating REAL,
                providers TEXT,
                poster_path TEXT,
                notified_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // Migração para adicionar colunas faltantes (caso a tabela já exista sem elas)
        ensureColumnExists("releases_notified", "title", "TEXT");
        ensureColumnExists("releases_notified", "overview", "TEXT");
        ensureColumnExists("releases_notified", "rating", "REAL");
        ensureColumnExists("releases_notified", "providers", "TEXT");
        ensureColumnExists("releases_notified", "poster_path", "TEXT");

        log.info("Tabela releases_notified verificada/criada");
    }

    private void ensureColumnExists(String table, String column, String type) {
        try {
            List<Map<String, Object>> columns =
                    jdbcTemplate.queryForList("PRAGMA table_info(" + table + ")");
            boolean exists = columns.stream().anyMatch(row -> column.equals(row.get("name")));
            if (!exists) {
                jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
                log.info("Coluna {} adicionada à tabela {}", column, table);
            }
        } catch (Exception e) {
            log.warn(
                    "Não foi possível verificar/adicionar coluna {}: {}",
                    column,
                    LogSanitizer.sanitize(e.getMessage()));
        }
    }
}
