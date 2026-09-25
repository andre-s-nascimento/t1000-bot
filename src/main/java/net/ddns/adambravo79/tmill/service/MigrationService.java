package net.ddns.adambravo79.tmill.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.sqlite.SQLiteConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.document.BotConversationDocument;
import net.ddns.adambravo79.tmill.document.InteractionLogDocument;
import net.ddns.adambravo79.tmill.document.UserIdeaDocument;
import net.ddns.adambravo79.tmill.dto.MigrationResult;
import net.ddns.adambravo79.tmill.repository.BotConversationRepository;
import net.ddns.adambravo79.tmill.repository.InteractionLogRepository;
import net.ddns.adambravo79.tmill.repository.UserIdeaRepository;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;

@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationService {

    private final JdbcTemplate jdbcTemplate;
    private final BotConversationRepository botConversationRepository;
    private final InteractionLogRepository interactionLogRepository;
    private final UserIdeaRepository userIdeaRepository;
    private final MetricsService metricsService;
    private final TransactionTemplate transactionTemplate;

    @Value("${migration.sqlite.path:./data/t1000.db}")
    private String sqlitePath;

    @Value("${migration.enabled:false}")
    private boolean migrationEnabled;

    // =========================== API PÚBLICA ===========================

    public MigrationResult migrateAll() {
        return migrateAll(false);
    }

    public MigrationResult migrateAll(boolean dryRun) {
        if (!migrationEnabled) {
            throw new IllegalStateException(
                    "Migração desabilitada. Configure migration.enabled=true para executar.");
        }

        if (dryRun) {
            log.warn("⚠️ MODO DRY-RUN ativado — Postgres faz rollback, Mongo não é tocado");
            return transactionTemplate.execute(
                    status -> {
                        MigrationResult result = doMigrate(true); // 🔧 passa dryRun
                        status.setRollbackOnly();
                        log.info("🔄 DRY-RUN finalizado — rollback do Postgres executado");
                        return result;
                    });
        }

        return doMigrate(false); // 🔧 passa dryRun
    }

    // 🔧 FIX: recebe dryRun e propaga
    private MigrationResult doMigrate(boolean dryRun) {
        Path dbPath = Paths.get(sqlitePath).toAbsolutePath().normalize();
        if (!Files.exists(dbPath)) {
            throw new IllegalStateException("Arquivo SQLite não encontrado em: " + dbPath);
        }

        Instant start = Instant.now();
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        log.info(
                "🚚 Iniciando migração SQLite → Postgres/Mongo. Origem: {} (dryRun={})",
                dbPath,
                dryRun);

        try (Connection sqlite = openSqliteConnection(dbPath)) {
            // Postgres — escreve sempre (rollback cuida do dry-run)
            migrateMessages(sqlite, counts, errors, dryRun);
            migrateTranscripts(sqlite, counts, errors, dryRun);
            migrateReleasesNotified(sqlite, counts, errors, dryRun);
            migrateBirthdays(sqlite, counts, errors, dryRun);

            // Mongo — só escreve se NÃO for dry-run
            migrateUserIdeas(sqlite, counts, errors, dryRun); // 🔧
            migrateBotConversations(sqlite, counts, errors, dryRun); // 🔧
            migrateInteractionLogs(sqlite, counts, errors, dryRun); // 🔧

        } catch (Exception e) {
            log.error("❌ Falha crítica na migração", e);
            errors.add("Falha crítica: " + e.getMessage());
            metricsService.error("migration_falha");
            return MigrationResult.failed(start, Instant.now(), errors);
        }

        Instant end = Instant.now();
        if (errors.isEmpty()) {
            log.info(
                    "✅ Migração{} concluída em {}ms: {}",
                    dryRun ? " (DRY-RUN)" : "",
                    end.toEpochMilli() - start.toEpochMilli(),
                    counts);
            metricsService.success(dryRun ? "migration_dryrun_sucesso" : "migration_sucesso");
            return MigrationResult.success(start, end, counts);
        } else {
            log.warn("⚠️ Migração parcial: {} erros. Contadores: {}", errors.size(), counts);
            metricsService.error("migration_parcial");
            return MigrationResult.partial(start, end, counts, errors);
        }
    }

    // =========================== CONEXÃO ===========================

    private Connection openSqliteConnection(Path dbPath) throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        config.setBusyTimeout(5000);

        String url = "jdbc:sqlite:" + dbPath;
        Connection conn = config.createConnection(url);
        log.debug("🔌 Conexão SQLite aberta (read-only): {}", dbPath);
        return conn;
    }

    // =========================== RELACIONAL (Postgres) ===========================

    private void migrateMessages(
            Connection sqlite, Map<String, Integer> counts, List<String> errors, boolean dryRun) {
        migrateRelational(
                sqlite,
                counts,
                errors,
                "messages",
                "SELECT id, chat_id, user_id, user_name, text, ignore_in_digest, timestamp FROM"
                        + " messages",
                "INSERT INTO messages (chat_id, user_id, user_name, text, ignore_in_digest,"
                        + " timestamp) VALUES (?, ?, ?, ?, ?, ?)",
                rs ->
                        new Object[] {
                            rs.getLong("chat_id"),
                            rs.getLong("user_id"),
                            rs.getString("user_name"),
                            rs.getString("text"),
                            rs.getInt("ignore_in_digest") != 0,
                            parseLocalDateTime(rs.getString("timestamp"))
                        },
                dryRun);
    }

    private void migrateTranscripts(
            Connection sqlite, Map<String, Integer> counts, List<String> errors, boolean dryRun) {
        migrateRelational(
                sqlite,
                counts,
                errors,
                "transcripts",
                "SELECT id, chat_id, user_id, user_name, text, raw_text, ignore_in_digest,"
                        + " timestamp FROM transcripts",
                "INSERT INTO transcripts (chat_id, user_id, user_name, text, raw_text,"
                        + " ignore_in_digest, timestamp)VALUES (?, ?, ?, ?, ?, ?, ?)",
                rs ->
                        new Object[] {
                            rs.getLong("chat_id"),
                            rs.getLong("user_id"),
                            rs.getString("user_name"),
                            rs.getString("text"),
                            rs.getString("raw_text"),
                            rs.getInt("ignore_in_digest") != 0,
                            parseLocalDateTime(rs.getString("timestamp"))
                        },
                dryRun);
    }

    private void migrateReleasesNotified(
            Connection sqlite, Map<String, Integer> counts, List<String> errors, boolean dryRun) {
        migrateRelational(
                sqlite,
                counts,
                errors,
                "releases_notified",
                "SELECT tmdb_id, media_type, release_date, title, overview, rating, providers,"
                        + " poster_path FROM releases_notified",
                "INSERT INTO releases_notified (tmdb_id, media_type, release_date, title, overview,"
                        + " rating, providers, poster_path) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                rs ->
                        new Object[] {
                            rs.getLong("tmdb_id"),
                            rs.getString("media_type"),
                            parseLocalDate(rs.getString("release_date")),
                            rs.getString("title"),
                            rs.getString("overview"),
                            rs.getDouble("rating"),
                            rs.getString("providers"),
                            rs.getString("poster_path")
                        },
                dryRun);
    }

    private void migrateBirthdays(
            Connection sqlite, Map<String, Integer> counts, List<String> errors, boolean dryRun) {
        migrateRelational(
                sqlite,
                counts,
                errors,
                "birthdays",
                "SELECT user_id, user_name, day, month, last_sent_year FROM birthdays",
                "INSERT INTO birthdays (user_id, user_name, day, month, last_sent_year) "
                        + "VALUES (?, ?, ?, ?, ?) ON CONFLICT (user_id) DO NOTHING",
                rs ->
                        new Object[] {
                            rs.getLong("user_id"),
                            rs.getString("user_name"),
                            rs.getInt("day"),
                            rs.getInt("month"),
                            rs.getObject("last_sent_year") != null
                                    ? rs.getInt("last_sent_year")
                                    : null
                        },
                dryRun);
    }

    // =========================== MONGO — DRY-RUN CONSCIENTE ===========================

    // 🔧 FIX: novo parâmetro dryRun
    private void migrateUserIdeas(
            Connection sqlite, Map<String, Integer> counts, List<String> errors, boolean dryRun) {
        int migrated = 0;
        try (Statement st = sqlite.createStatement();
                ResultSet rs =
                        st.executeQuery(
                                "SELECT user_id, original_text, tags, category, created_at FROM"
                                        + " user_ideas")) {
            while (rs.next()) {
                try {
                    UserIdeaDocument doc =
                            new UserIdeaDocument(
                                    null,
                                    rs.getLong("user_id"),
                                    rs.getString("original_text"),
                                    parseTags(rs.getString("tags")),
                                    rs.getString("category"),
                                    parseTimestamp(rs.getString("created_at")));

                    // 🔧 FIX: só salva se NÃO for dry-run
                    if (!dryRun) {
                        userIdeaRepository.save(doc);
                    } else {
                        log.trace("🧪 [DRY-RUN] user_ideas: {}", doc.originalText());
                    }
                    migrated++;
                } catch (Exception e) {
                    errors.add("user_ideas: " + e.getMessage());
                }
            }
            counts.put("user_ideas", migrated);
            log.info(
                    "✅ user_ideas: {} registros {} para MongoDB",
                    migrated,
                    dryRun ? "simulados" : "migrados");
        } catch (SQLException e) {
            log.warn("⚠️ Tabela user_ideas não existe no SQLite: {}", e.getMessage());
            counts.put("user_ideas", 0);
        }
    }

    // 🔧 FIX: novo parâmetro dryRun
    private void migrateBotConversations(
            Connection sqlite, Map<String, Integer> counts, List<String> errors, boolean dryRun) {
        int migrated = 0;
        try (Statement st = sqlite.createStatement();
                ResultSet rs =
                        st.executeQuery(
                                "SELECT chat_id, user_id, user_name, message_type, content,"
                                        + " bot_response, timestamp FROM bot_conversations")) {
            while (rs.next()) {
                try {
                    BotConversationDocument doc =
                            new BotConversationDocument(
                                    null,
                                    rs.getLong("chat_id"),
                                    rs.getLong("user_id"),
                                    rs.getString("user_name"),
                                    rs.getString("message_type"),
                                    rs.getString("content"),
                                    rs.getString("bot_response"),
                                    parseTimestamp(rs.getString("timestamp")));

                    if (!dryRun) {
                        botConversationRepository.save(doc);
                    } else {
                        log.trace("🧪 [DRY-RUN] bot_conversations: chatId={}", doc.chatId());
                    }
                    migrated++;
                } catch (Exception e) {
                    errors.add("bot_conversations: " + e.getMessage());
                }
            }
            counts.put("bot_conversations", migrated);
            log.info(
                    "✅ bot_conversations: {} registros {} para MongoDB",
                    migrated,
                    dryRun ? "simulados" : "migrados");
        } catch (SQLException e) {
            log.warn("⚠️ Tabela bot_conversations não existe no SQLite: {}", e.getMessage());
            counts.put("bot_conversations", 0);
        }
    }

    // 🔧 FIX: novo parâmetro dryRun
    private void migrateInteractionLogs(
            Connection sqlite, Map<String, Integer> counts, List<String> errors, boolean dryRun) {
        int migrated = 0;
        try (Statement st = sqlite.createStatement();
                ResultSet rs =
                        st.executeQuery(
                                "SELECT chat_id, user_id, user_name, action_type, content,"
                                        + " timestamp FROM interaction_logs")) {
            while (rs.next()) {
                try {
                    InteractionLogDocument doc =
                            new InteractionLogDocument(
                                    null,
                                    rs.getLong("chat_id"),
                                    rs.getLong("user_id"),
                                    rs.getString("user_name"),
                                    rs.getString("action_type"),
                                    rs.getString("content"),
                                    parseTimestamp(rs.getString("timestamp")));

                    if (!dryRun) {
                        interactionLogRepository.save(doc);
                    } else {
                        log.trace("🧪 [DRY-RUN] interaction_logs: action={}", doc.actionType());
                    }
                    migrated++;
                } catch (Exception e) {
                    errors.add("interaction_logs: " + e.getMessage());
                }
            }
            counts.put("interaction_logs", migrated);
            log.info(
                    "✅ interaction_logs: {} registros {} para MongoDB",
                    migrated,
                    dryRun ? "simulados" : "migrados");
        } catch (SQLException e) {
            log.warn("⚠️ Tabela interaction_logs não existe no SQLite: {}", e.getMessage());
            counts.put("interaction_logs", 0);
        }
    }

    // =========================== HELPERS ===========================

    @FunctionalInterface
    private interface RowMapper {
        Object[] map(ResultSet rs) throws SQLException;
    }

    private void migrateRelational(
            Connection sqlite,
            Map<String, Integer> counts,
            List<String> errors,
            String tableName,
            String selectSql,
            String insertSql,
            RowMapper mapper,
            boolean dryRun) {

        final int BATCH_SIZE = 500;
        int migrated = 0;

        try (Statement st = sqlite.createStatement();
                ResultSet rs = st.executeQuery(selectSql)) {

            List<Object[]> batch = new ArrayList<>(BATCH_SIZE);

            while (rs.next()) {
                try {
                    batch.add(mapper.map(rs));

                    if (batch.size() >= BATCH_SIZE) {
                        migrated += flushBatch(tableName, insertSql, batch, errors);
                        batch.clear();
                    }
                } catch (Exception e) {
                    errors.add(tableName + " (row " + migrated + "): " + e.getMessage());
                }
            }

            if (!batch.isEmpty()) {
                migrated += flushBatch(tableName, insertSql, batch, errors);
            }

            counts.put(tableName, migrated);
            log.info(
                    "✅ {}: {} registros {} para PostgreSQL",
                    tableName,
                    migrated,
                    dryRun ? "simulados" : "migrados");

        } catch (SQLException e) {
            log.warn("⚠️ Tabela {} não existe no SQLite: {}", tableName, e.getMessage());
            counts.put(tableName, 0);
        }
    }

    /**
     * Executa INSERTs em lote e retorna o número de sucessos. Trata falhas parciais (uma linha ruim
     * não derruba o lote inteiro).
     */
    private int flushBatch(
            String tableName, String insertSql, List<Object[]> batch, List<String> errors) {
        try {
            int[] results = jdbcTemplate.batchUpdate(insertSql, batch);
            int ok = 0;
            for (int r : results) {
                if (r >= 0 || r == Statement.SUCCESS_NO_INFO) {
                    ok++;
                }
            }
            if (ok < batch.size()) {
                errors.add(
                        tableName
                                + ": "
                                + (batch.size() - ok)
                                + " linhas do lote falharam silenciosamente");
            }
            return ok;
        } catch (Exception e) {
            errors.add(tableName + " (lote de " + batch.size() + "): " + e.getMessage());
            log.error("Erro no batch de {}: {}", tableName, e.getMessage(), e);
            return 0;
        }
    }

    private List<String> parseTags(String tagsCsv) {
        if (tagsCsv == null || tagsCsv.isBlank()) {
            return List.of();
        }
        return List.of(tagsCsv.split(","));
    }

    // 🔧 FIX: parse robusto de timestamp do SQLite (aceita "YYYY-MM-DD HH:mm:ss" ou ISO)
    private Instant parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) {
            return Instant.now();
        }
        try {
            String normalized = raw.replace(" ", "T");
            if (!normalized.endsWith("Z") && !normalized.contains("+")) {
                normalized += "Z";
            }
            return Instant.parse(normalized);
        } catch (Exception e) {
            log.warn("⚠️ Timestamp inválido no SQLite: '{}'. Usando now().", raw);
            return Instant.now();
        }
    }

    public Map<String, Integer> previewCounts() {
        Path dbPath = Paths.get(sqlitePath).toAbsolutePath().normalize();
        if (!Files.exists(dbPath)) {
            throw new IllegalStateException("Arquivo SQLite não encontrado em: " + dbPath);
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        String[] tables = {
            "messages",
            "transcripts",
            "releases_notified",
            "birthdays",
            "user_ideas",
            "bot_conversations",
            "interaction_logs"
        };

        try (Connection sqlite = openSqliteConnection(dbPath)) {
            for (String table : tables) {
                counts.put(table, countRows(sqlite, table));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao contar registros: " + e.getMessage(), e);
        }
        return counts;
    }

    private int countRows(Connection sqlite, String table) {
        try (Statement st = sqlite.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            log.debug("Tabela {} não existe no SQLite ({}). Retornando 0.", table, e.getMessage());
            return 0;
        }
    }

    /**
     * Converte timestamps do SQLite (formato "yyyy-MM-dd HH:mm:ss" ou "yyyy-MM-dd HH:mm:ss.SSS") para
     * LocalDateTime. O driver do Postgres aceita LocalDateTime nativamente — a String não.
     */
    private LocalDateTime parseLocalDateTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            // Normaliza "2026-09-22 10:30:00" → "2026-09-22T10:30:00"
            String normalized = raw.replace(" ", "T");
            // Remove timezone se houver (SQLite às vezes grava "Z" ou "+00:00")
            if (normalized.endsWith("Z")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            if (normalized.contains("+")) {
                normalized = normalized.substring(0, normalized.indexOf("+"));
            }
            // Se não tem milissegundos, adiciona ".0"
            if (normalized.matches(".*T\\d{2}:\\d{2}:\\d{2}$")) {
                normalized += ".0";
            }
            return LocalDateTime.parse(normalized);
        } catch (Exception e) {
            log.warn("⚠️ Timestamp inválido no SQLite: '{}'. Usando null.", raw);
            return null;
        }
    }

    /**
     * Converte datas do SQLite ("yyyy-MM-dd") para LocalDate. O driver do Postgres aceita LocalDate
     * nativamente.
     */
    private LocalDate parseLocalDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (Exception e) {
            log.warn("⚠️ Data inválida no SQLite: '{}'. Usando null.", raw);
            return null;
        }
    }
}
