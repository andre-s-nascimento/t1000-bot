package net.ddns.adambravo79.tmill.config;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);
    private final JdbcTemplate jdbcTemplate;

    // Static map of column → full, hard‑coded ALTER statement (no dynamic parts)
    private static final Map<String, String> RELEASE_ALTER_STATEMENTS =
            Map.of(
                    "title",
                    "ALTER TABLE releases_notified ADD COLUMN title TEXT",
                    "overview",
                    "ALTER TABLE releases_notified ADD COLUMN overview TEXT",
                    "rating",
                    "ALTER TABLE releases_notified ADD COLUMN rating REAL",
                    "providers",
                    "ALTER TABLE releases_notified ADD COLUMN providers TEXT",
                    "poster_path",
                    "ALTER TABLE releases_notified ADD COLUMN poster_path TEXT",
                    "ignore_in_digest",
                    "ALTER TABLE messages ADD COLUMN ignore_in_digest BOOLEAN DEFAULT 0");

    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        criarTabelaTranscripts();
        criarTabelaReleasesNotified();
        adicionarColunaRawText();
        adicionarColunasReleases();
    }

    private void criarTabelaTranscripts() {
        String sql =
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
                """;
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            log.error("Erro ao criar tabela transcripts", e);
        }
    }

    private void criarTabelaReleasesNotified() {
        String sql =
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
                    poster_path TEXT
                )
                """;
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            log.error("Erro ao criar tabela releases_notified", e);
        }
    }

    private void adicionarColunaRawText() {
        try {
            List<Map<String, Object>> columns =
                    jdbcTemplate.queryForList("PRAGMA table_info(transcripts)");
            boolean hasRawText =
                    columns.stream().anyMatch(col -> "raw_text".equals(col.get("name")));
            if (!hasRawText) {
                jdbcTemplate.execute("ALTER TABLE transcripts ADD COLUMN raw_text TEXT");
            }
        } catch (Exception e) {
            log.error("Erro ao verificar/adicionar coluna raw_text", e);
        }
    }

    private void adicionarColunasReleases() {
        try {
            List<Map<String, Object>> columns =
                    jdbcTemplate.queryForList("PRAGMA table_info(releases_notified)");
            // Use Stream.toList() instead of Collectors.toList()
            List<String> existing =
                    columns.stream().map(col -> col.get("name").toString()).toList();

            for (Map.Entry<String, String> entry : RELEASE_ALTER_STATEMENTS.entrySet()) {
                String columnName = entry.getKey();
                if (!existing.contains(columnName)) {
                    // Extract the actual execution into a separate method
                    executeAlterStatement(columnName, entry.getValue());
                }
            }
        } catch (Exception e) {
            log.error("Erro ao verificar colunas da tabela releases_notified", e);
        }
    }

    /**
     * Executes a static ALTER TABLE statement and logs success/failure. This method contains its own
     * try-catch, so the caller does not have a nested try block.
     */
    private void executeAlterStatement(String columnName, String sql) {
        try {
            jdbcTemplate.execute(sql);
            log.info("Coluna {} adicionada à tabela releases_notified", columnName);
        } catch (Exception e) {
            log.error("Erro ao adicionar coluna {} em releases_notified", columnName, e);
        }
    }
}
