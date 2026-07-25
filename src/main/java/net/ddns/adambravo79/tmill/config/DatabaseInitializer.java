package net.ddns.adambravo79.tmill.config;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.util.LogSanitizer;

@Slf4j
@Component
public class DatabaseInitializer {

    private static final String RELEASES_NOTIFIED = "releases_notified";

    // Whitelists para garantir segurança
    private static final Set<String> ALLOWED_TABLES = Set.of(RELEASES_NOTIFIED);
    private static final Set<String> ALLOWED_COLUMNS =
            Set.of("title", "overview", "rating", "providers", "poster_path");
    private static final Set<String> ALLOWED_TYPES = Set.of("TEXT", "REAL");

    private final JdbcTemplate jdbcTemplate;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        // Tabela transcripts
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

        // Verifica/adiciona coluna raw_text
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

        // Migrações
        ensureColumnExists(RELEASES_NOTIFIED, "title", "TEXT");
        ensureColumnExists(RELEASES_NOTIFIED, "overview", "TEXT");
        ensureColumnExists(RELEASES_NOTIFIED, "rating", "REAL");
        ensureColumnExists(RELEASES_NOTIFIED, "providers", "TEXT");
        ensureColumnExists(RELEASES_NOTIFIED, "poster_path", "TEXT");

        log.info("Tabela releases_notified verificada/criada");
    }

    private void ensureColumnExists(String table, String column, String type) {
        // ✅ Validação de segurança (whitelist)
        if (!ALLOWED_TABLES.contains(table)) {
            log.warn("Tabela não permitida para migração: {}", table);
            return;
        }
        if (!ALLOWED_COLUMNS.contains(column)) {
            log.warn("Coluna não permitida para migração: {}", column);
            return;
        }
        if (!ALLOWED_TYPES.contains(type)) {
            log.warn("Tipo não permitido para migração: {}", type);
            return;
        }

        try {
            // Chamada ao método seguro que executa a SQL
            boolean exists = columnExists(table, column);
            if (!exists) {
                addColumn(table, column, type);
                log.info("Coluna {} adicionada à tabela {}", column, table);
            }
        } catch (Exception e) {
            log.warn(
                    "Não foi possível verificar/adicionar coluna {}: {}",
                    column,
                    LogSanitizer.sanitize(e.getMessage()));
        }
    }

    /** Verifica se uma coluna existe em uma tabela. Os parâmetros são validados antes da chamada. */
    @SuppressWarnings("squid:S2077") // SQL dinâmico seguro com validação de whitelist
    private boolean columnExists(String table, String column) {
        String sql = "PRAGMA table_info(" + table + ")";
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(sql);
        return columns.stream().anyMatch(row -> column.equals(row.get("name")));
    }

    /** Adiciona uma coluna a uma tabela. Os parâmetros são validados antes da chamada. */
    @SuppressWarnings("squid:S2077") // SQL dinâmico seguro com validação de whitelist
    private void addColumn(String table, String column, String type) {
        String sql = String.format("ALTER TABLE %s ADD COLUMN %s %s", table, column, type);
        jdbcTemplate.execute(sql);
    }
}
