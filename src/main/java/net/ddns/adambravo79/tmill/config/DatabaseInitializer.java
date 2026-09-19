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

    // Removido o 'ignore_in_digest' daqui. Esse mapa agora é exclusivo da tabela releases_notified.
    private static final Map<String, String> RELEASE_ALTER_STATEMENTS =
            Map.of(
                    "title", "ALTER TABLE releases_notified ADD COLUMN title TEXT",
                    "overview", "ALTER TABLE releases_notified ADD COLUMN overview TEXT",
                    "rating", "ALTER TABLE releases_notified ADD COLUMN rating REAL",
                    "providers", "ALTER TABLE releases_notified ADD COLUMN providers TEXT",
                    "poster_path", "ALTER TABLE releases_notified ADD COLUMN poster_path TEXT");

    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        criarTabelaMessages();
        criarTabelaTranscripts();
        criarTabelaReleasesNotified();
        criarTabelaBirthdays();

        adicionarColunasReleases();

        // Verificações independentes e seguras de colunas
        garantirColuna("transcripts", "raw_text", "TEXT");
        garantirColuna("transcripts", "ignore_in_digest", "BOOLEAN DEFAULT 0");
        garantirColuna("messages", "ignore_in_digest", "BOOLEAN DEFAULT 0");
    }

    private void criarTabelaMessages() {
        String sql =
                """
                CREATE TABLE IF NOT EXISTS messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    chat_id INTEGER NOT NULL,
                    user_id INTEGER NOT NULL,
                    user_name TEXT,
                    text TEXT NOT NULL,
                    ignore_in_digest BOOLEAN DEFAULT 0,
                    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """;
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            log.error("Erro ao criar tabela messages", e);
        }
    }

    private void criarTabelaBirthdays() {
        String sql =
                """
                CREATE TABLE IF NOT EXISTS birthdays (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL UNIQUE,
                    user_name TEXT NOT NULL,
                    day INTEGER NOT NULL CHECK (day BETWEEN 1 AND 31),
                    month INTEGER NOT NULL CHECK (month BETWEEN 1 AND 12),
                    last_sent_year INTEGER,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """;
        try {
            jdbcTemplate.execute(sql);
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_birthdays_day_month ON birthdays(day, month)");
            log.info("Tabela birthdays garantida.");
        } catch (Exception e) {
            log.error("Erro ao criar tabela birthdays", e);
        }
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
                    ignore_in_digest BOOLEAN DEFAULT 0,
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

    private void adicionarColunasReleases() {
        try {
            List<Map<String, Object>> columns =
                    jdbcTemplate.queryForList("PRAGMA table_info(releases_notified)");
            List<String> existing =
                    columns.stream().map(col -> col.get("name").toString()).toList();

            for (Map.Entry<String, String> entry : RELEASE_ALTER_STATEMENTS.entrySet()) {
                String columnName = entry.getKey();
                if (!existing.contains(columnName)) {
                    executeAlterStatement(columnName, entry.getValue());
                }
            }
        } catch (Exception e) {
            log.error("Erro ao verificar colunas da tabela releases_notified", e);
        }
    }

    /** Helper genérico para garantir que uma coluna exista em qualquer tabela */
    private void garantirColuna(String tabela, String coluna, String definicao) {
        try {
            List<Map<String, Object>> columns =
                    jdbcTemplate.queryForList("PRAGMA table_info(" + tabela + ")");
            boolean existe = columns.stream().anyMatch(col -> coluna.equals(col.get("name")));

            if (!existe) {
                String sql =
                        String.format("ALTER TABLE %s ADD COLUMN %s %s", tabela, coluna, definicao);
                executeAlterStatement(coluna, sql);
            }
        } catch (Exception e) {
            log.error("Erro ao verificar/adicionar coluna {} na tabela {}", coluna, tabela, e);
        }
    }

    private void executeAlterStatement(String columnName, String sql) {
        try {
            jdbcTemplate.execute(sql);
            log.info("Coluna {} adicionada com sucesso", columnName);
        } catch (org.springframework.jdbc.UncategorizedSQLException e) {
            if (isDuplicateColumnError(e)) {
                log.info("Coluna {} já existe (migração ignorada)", columnName);
            } else {
                log.error("Erro ao adicionar coluna {} via ALTER TABLE", columnName, e);
            }
        } catch (Exception e) {
            log.error("Erro inesperado ao adicionar coluna {}", columnName, e);
        }
    }

    private boolean isDuplicateColumnError(Throwable e) {
        Throwable current = e;
        while (current != null) {
            String msg = current.getMessage();
            if (msg != null && msg.contains("duplicate column name")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
