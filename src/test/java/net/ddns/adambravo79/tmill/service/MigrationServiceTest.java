package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import net.ddns.adambravo79.tmill.document.BotConversationDocument;
import net.ddns.adambravo79.tmill.document.InteractionLogDocument;
import net.ddns.adambravo79.tmill.document.UserIdeaDocument;
import net.ddns.adambravo79.tmill.dto.MigrationResult;
import net.ddns.adambravo79.tmill.repository.BotConversationRepository;
import net.ddns.adambravo79.tmill.repository.InteractionLogRepository;
import net.ddns.adambravo79.tmill.repository.UserIdeaRepository;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;

/**
 * Testes do MigrationService.
 *
 * <p>Estratégia:
 *
 * <ul>
 *   <li>Cria um SQLite real em {@link TempDir} com o schema legado.
 *   <li>Mocka {@link JdbcTemplate} (Postgres) e os repositórios Mongo.
 *   <li>Valida contadores, status, chamadas de I/O e métricas.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class MigrationServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private BotConversationRepository botConversationRepository;
    @Mock private InteractionLogRepository interactionLogRepository;
    @Mock private UserIdeaRepository userIdeaRepository;
    @Mock private MetricsService metricsService;

    @InjectMocks private MigrationService service;

    @TempDir Path tempDir;

    private Path sqliteFile;

    @BeforeEach
    void setUp() throws Exception {
        sqliteFile = tempDir.resolve("test.db");
        createFullSqliteSchema(sqliteFile);

        ReflectionTestUtils.setField(service, "sqlitePath", sqliteFile.toString());
        ReflectionTestUtils.setField(service, "migrationEnabled", true);
    }

    // ============================================================
    // SETUP: cria um SQLite com TODAS as tabelas legadas
    // ============================================================

    /**
     * 🔧 FIX: no rascunho anterior, o schema só criava 4 tabelas. Agora criamos as 7 (incluindo as 3
     * que vão pro Mongo) para que os testes possam validar os 3 caminhos: relacional, documento e
     * ausente.
     */
    private void createFullSqliteSchema(Path dbPath) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
                Statement st = conn.createStatement()) {

            // ---- Tabelas relacionais (Postgres) ----
            st.execute(
                    "CREATE TABLE messages ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                            + "chat_id INTEGER NOT NULL, "
                            + "user_id INTEGER NOT NULL, "
                            + "user_name TEXT, "
                            + "text TEXT NOT NULL, "
                            + "ignore_in_digest INTEGER DEFAULT 0, "
                            + "timestamp TEXT DEFAULT CURRENT_TIMESTAMP)");

            st.execute(
                    "CREATE TABLE transcripts ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                            + "chat_id INTEGER NOT NULL, "
                            + "user_id INTEGER NOT NULL, "
                            + "user_name TEXT, "
                            + "text TEXT NOT NULL, "
                            + "raw_text TEXT, "
                            + "ignore_in_digest INTEGER DEFAULT 0, "
                            + "timestamp TEXT DEFAULT CURRENT_TIMESTAMP)");

            st.execute(
                    "CREATE TABLE releases_notified ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                            + "tmdb_id INTEGER NOT NULL, "
                            + "media_type TEXT NOT NULL, "
                            + "release_date TEXT NOT NULL, "
                            + "title TEXT, "
                            + "overview TEXT, "
                            + "rating REAL, "
                            + "providers TEXT, "
                            + "poster_path TEXT)");

            st.execute(
                    "CREATE TABLE birthdays ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                            + "user_id INTEGER NOT NULL UNIQUE, "
                            + "user_name TEXT NOT NULL, "
                            + "day INTEGER NOT NULL, "
                            + "month INTEGER NOT NULL, "
                            + "last_sent_year INTEGER)");

            // ---- Tabelas de documento (Mongo) ----
            st.execute(
                    "CREATE TABLE user_ideas ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                            + "user_id INTEGER NOT NULL, "
                            + "original_text TEXT, "
                            + "tags TEXT, "
                            + "category TEXT, "
                            + "created_at TEXT)");

            st.execute(
                    "CREATE TABLE bot_conversations ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                            + "chat_id INTEGER NOT NULL, "
                            + "user_id INTEGER NOT NULL, "
                            + "user_name TEXT, "
                            + "message_type TEXT, "
                            + "content TEXT, "
                            + "bot_response TEXT, "
                            + "timestamp TEXT)");

            st.execute(
                    "CREATE TABLE interaction_logs ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                            + "chat_id INTEGER NOT NULL, "
                            + "user_id INTEGER NOT NULL, "
                            + "user_name TEXT, "
                            + "action_type TEXT, "
                            + "content TEXT, "
                            + "timestamp TEXT)");
        }
    }

    // ============================================================
    // SUCESSO
    // ============================================================

    @Test
    @DisplayName("migrateAll: SQLite vazio → SUCCESS com contadores zerados")
    void migrateAll_vazio_sucesso() {
        MigrationResult result = service.migrateAll();

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.tablesMigrated())
                .containsKeys(
                        "messages",
                        "transcripts",
                        "releases_notified",
                        "birthdays",
                        "user_ideas",
                        "bot_conversations",
                        "interaction_logs");

        // Todos zerados
        result.tablesMigrated().values().forEach(count -> assertThat(count).isZero());

        assertThat(result.errors()).isEmpty();
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.startedAt()).isNotNull();
        assertThat(result.finishedAt()).isNotNull();

        verify(metricsService).success("migration_sucesso");
        verify(metricsService, never()).error(anyString());
    }

    @Test
    @DisplayName("migrateAll: com registros relacionais → insere no Postgres e conta")
    void migrateAll_comRegistrosRelacionais() throws Exception {
        insertMessage(sqliteFile, 1L, 10L, "Fulano", "Olá mundo", 0, "2026-09-22T10:00:00");
        insertMessage(sqliteFile, 1L, 11L, "Beltrano", "Oi", 0, "2026-09-22T10:05:00");
        insertMessage(sqliteFile, 1L, 12L, "Sicrano", "Spoiler", 1, "2026-09-22T10:10:00");

        insertBirthday(sqliteFile, 10L, "Fulano", 5, 10, null);
        insertBirthday(sqliteFile, 11L, "Beltrano", 20, 12, 2025);

        MigrationResult result = service.migrateAll();

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.tablesMigrated().get("messages")).isEqualTo(3);
        assertThat(result.tablesMigrated().get("birthdays")).isEqualTo(2);
        assertThat(result.tablesMigrated().get("transcripts")).isZero();
        assertThat(result.tablesMigrated().get("releases_notified")).isZero();

        // Deve ter chamado o JdbcTemplate 5x (3 messages + 2 birthdays)
        verify(jdbcTemplate, times(5)).update(anyString(), any(Object[].class));
        verify(metricsService).success("migration_sucesso");
    }

    @Test
    @DisplayName("migrateAll: com registros de documento → salva no Mongo")
    void migrateAll_comRegistrosDeDocumento() throws Exception {
        insertUserIdea(sqliteFile, 10L, "Ideia A", "tag1,tag2", "BACKLOG", "2026-09-22T10:00:00Z");
        insertUserIdea(sqliteFile, 11L, "Ideia B", "tag3", "BACKLOG", "2026-09-22T10:05:00Z");

        insertBotConversation(
                sqliteFile, 1L, 10L, "Fulano", "TEXT", "Olá", "Oi!", "2026-09-22T10:00:00Z");

        insertInteractionLog(
                sqliteFile, 1L, 10L, "Fulano", "COMMAND", "/start", "2026-09-22T10:00:00Z");

        MigrationResult result = service.migrateAll();

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.tablesMigrated().get("user_ideas")).isEqualTo(2);
        assertThat(result.tablesMigrated().get("bot_conversations")).isEqualTo(1);
        assertThat(result.tablesMigrated().get("interaction_logs")).isEqualTo(1);

        // 🔧 FIX: usar ArgumentCaptor para inspecionar o que foi salvo
        org.mockito.ArgumentCaptor<UserIdeaDocument> ideaCaptor =
                org.mockito.ArgumentCaptor.forClass(UserIdeaDocument.class);
        verify(userIdeaRepository, times(2)).save(ideaCaptor.capture());
        assertThat(ideaCaptor.getAllValues().get(0).originalText()).isEqualTo("Ideia A");
        assertThat(ideaCaptor.getAllValues().get(0).tags()).containsExactly("tag1", "tag2");

        verify(botConversationRepository, times(1)).save(any(BotConversationDocument.class));
        verify(interactionLogRepository, times(1)).save(any(InteractionLogDocument.class));
        verify(metricsService).success("migration_sucesso");
    }

    @Test
    @DisplayName("migrateAll: tabelas ausentes no SQLite não quebram a migração")
    void migrateAll_tabelaAusente_naoQuebra() throws Exception {
        // 🔧 FIX: cria um SQLite SEM a tabela user_ideas
        Path minimalSqlite = tempDir.resolve("minimal.db");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + minimalSqlite);
                Statement st = conn.createStatement()) {
            st.execute(
                    "CREATE TABLE messages (id INTEGER PRIMARY KEY, chat_id INTEGER, user_id"
                        + " INTEGER, user_name TEXT, text TEXT, ignore_in_digest INTEGER, timestamp"
                        + " TEXT)");
        }
        ReflectionTestUtils.setField(service, "sqlitePath", minimalSqlite.toString());

        MigrationResult result = service.migrateAll();

        assertThat(result.status()).isEqualTo("SUCCESS");
        // Tabelas existentes: messages (0 registros). Ausentes: as outras 6.
        assertThat(result.tablesMigrated().get("messages")).isZero();
        assertThat(result.tablesMigrated().get("user_ideas")).isZero();
        assertThat(result.tablesMigrated().get("bot_conversations")).isZero();
        assertThat(result.tablesMigrated().get("interaction_logs")).isZero();
    }

    // ============================================================
    // FALHAS
    // ============================================================

    @Test
    @DisplayName("migrateAll: arquivo SQLite inexistente → IllegalStateException")
    void migrateAll_arquivoInexistente() {
        ReflectionTestUtils.setField(
                service, "sqlitePath", tempDir.resolve("nao-existe.db").toString());

        assertThatThrownBy(() -> service.migrateAll())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SQLite não encontrado");

        // 🔧 FIX: o serviço NÃO deve mexer em métricas nem em repositórios
        // quando falha na pré-condição.
        verify(metricsService, never()).success(anyString());
        verify(metricsService, never()).error(anyString());
    }

    @Test
    @DisplayName("migrateAll: migração desabilitada → IllegalStateException")
    void migrateAll_desabilitado() {
        ReflectionTestUtils.setField(service, "migrationEnabled", false);

        assertThatThrownBy(() -> service.migrateAll())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Migração desabilitada");

        verify(metricsService, never()).success(anyString());
        verify(metricsService, never()).error(anyString());
    }

    @Test
    @DisplayName("migrateAll: erro em uma tabela → PARTIAL, não FAILED")
    void migrateAll_erroEmUmaTabela_partial() throws Exception {
        insertMessage(sqliteFile, 1L, 10L, "Fulano", "msg", 0, "2026-09-22T10:00:00");
        insertBirthday(sqliteFile, 10L, "Fulano", 5, 10, null);

        // 🔧 FIX: lançar exceção SÓ na inserção em `messages` (primeira tabela).
        // O ideal é usar um matcher que olhe o SQL. Aqui usamos a assinatura
        // exata: como messages é o primeiro INSERT, o Mockito consome o stub na 1ª chamada.
        when(jdbcTemplate.update(contains("INSERT INTO messages"), any(Object[].class)))
                .thenThrow(new RuntimeException("DB down"));

        MigrationResult result = service.migrateAll();

        assertThat(result.status()).isEqualTo("PARTIAL");
        assertThat(result.errors()).isNotEmpty();
        assertThat(result.errors().get(0)).contains("messages");
        assertThat(result.tablesMigrated().get("messages")).isZero();
        assertThat(result.tablesMigrated().get("birthdays")).isEqualTo(1);

        verify(metricsService).error("migration_parcial");
        verify(metricsService, never()).success("migration_sucesso");
    }

    @Test
    @DisplayName("migrateAll: erro ao salvar em Mongo → PARTIAL com erro específico")
    void migrateAll_erroMongo_partial() throws Exception {
        insertUserIdea(sqliteFile, 10L, "Ideia", "tag", "BACKLOG", "2026-09-22T10:00:00Z");

        when(userIdeaRepository.save(any(UserIdeaDocument.class)))
                .thenThrow(new RuntimeException("Mongo down"));

        MigrationResult result = service.migrateAll();

        assertThat(result.status()).isEqualTo("PARTIAL");
        assertThat(result.errors()).anySatisfy(err -> assertThat(err).contains("user_ideas"));
        assertThat(result.tablesMigrated().get("user_ideas")).isZero();

        verify(metricsService).error("migration_parcial");
    }

    // ============================================================
    // PREVIEW
    // ============================================================

    @Test
    @DisplayName("previewCounts: retorna contadores sem migrar")
    void previewCounts_contaCorretamente() throws Exception {
        insertMessage(sqliteFile, 1L, 10L, "Fulano", "msg", 0, "2026-09-22T10:00:00");
        insertMessage(sqliteFile, 1L, 11L, "Beltrano", "msg", 0, "2026-09-22T10:01:00");
        insertBirthday(sqliteFile, 10L, "Fulano", 5, 10, null);

        var counts = service.previewCounts();

        assertThat(counts.get("messages")).isEqualTo(2);
        assertThat(counts.get("birthdays")).isEqualTo(1);
        assertThat(counts.get("transcripts")).isZero();
        assertThat(counts.get("user_ideas")).isZero();

        // 🔧 FIX: preview NUNCA deve chamar JdbcTemplate nem repositórios
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
        verify(userIdeaRepository, never()).save(any());
        verify(botConversationRepository, never()).save(any());
        verify(interactionLogRepository, never()).save(any());
        // E também não deve mexer em métricas
        verify(metricsService, never()).success(anyString());
        verify(metricsService, never()).error(anyString());
    }

    @Test
    @DisplayName("previewCounts: tabela ausente retorna 0, não quebra")
    void previewCounts_tabelaAusente_retornaZero() throws Exception {
        Path minimal = tempDir.resolve("minimal.db");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + minimal);
                Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE messages (id INTEGER PRIMARY KEY)");
        }
        ReflectionTestUtils.setField(service, "sqlitePath", minimal.toString());

        var counts = service.previewCounts();

        assertThat(counts.get("messages")).isZero();
        assertThat(counts.get("user_ideas")).isZero();
        // Total de 7 tabelas esperadas
        assertThat(counts).hasSize(7);
    }

    @Test
    @DisplayName("previewCounts: arquivo inexistente → IllegalStateException")
    void previewCounts_arquivoInexistente() {
        ReflectionTestUtils.setField(
                service, "sqlitePath", tempDir.resolve("nao-existe.db").toString());

        assertThatThrownBy(() -> service.previewCounts())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SQLite não encontrado");
    }

    // ============================================================
    // MIGRAÇÃO IDEMPOTENTE (documenta comportamento)
    // ============================================================

    @Test
    @DisplayName("migrateAll: rodar 2x insere 2x (NÃO é idempotente)")
    void migrateAll_naoEhIdempotente() throws Exception {
        insertMessage(sqliteFile, 1L, 10L, "Fulano", "msg", 0, "2026-09-22T10:00:00");

        service.migrateAll();
        service.migrateAll();

        // 🔧 FIX: documenta o comportamento — 2 execuções = 2 INSERTs.
        // O destino (Postgres) precisa de ON CONFLICT DO NOTHING (já tem no service).
        // Aqui só validamos que o método foi chamado 2x.
        verify(jdbcTemplate, times(2)).update(anyString(), any(Object[].class));
        verify(metricsService, times(2)).success("migration_sucesso");
    }

    // ============================================================
    // HELPERS: insert no SQLite legado
    // ============================================================

    private void insertMessage(
            Path dbPath,
            long chatId,
            long userId,
            String userName,
            String text,
            int ignoreInDigest,
            String timestamp)
            throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
                PreparedStatement ps =
                        conn.prepareStatement(
                                "INSERT INTO messages (chat_id, user_id, user_name, text,"
                                    + " ignore_in_digest, timestamp) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, chatId);
            ps.setLong(2, userId);
            ps.setString(3, userName);
            ps.setString(4, text);
            ps.setInt(5, ignoreInDigest);
            ps.setString(6, timestamp);
            ps.executeUpdate();
        }
    }

    private void insertBirthday(
            Path dbPath, long userId, String userName, int day, int month, Integer lastSentYear)
            throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
                PreparedStatement ps =
                        conn.prepareStatement(
                                "INSERT INTO birthdays (user_id, user_name, day, month,"
                                        + " last_sent_year) VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, userId);
            ps.setString(2, userName);
            ps.setInt(3, day);
            ps.setInt(4, month);
            if (lastSentYear != null) {
                ps.setInt(5, lastSentYear);
            } else {
                ps.setNull(5, java.sql.Types.INTEGER);
            }
            ps.executeUpdate();
        }
    }

    private void insertUserIdea(
            Path dbPath,
            long userId,
            String originalText,
            String tags,
            String category,
            String createdAt)
            throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
                PreparedStatement ps =
                        conn.prepareStatement(
                                "INSERT INTO user_ideas (user_id, original_text, tags, category,"
                                        + " created_at) VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, userId);
            ps.setString(2, originalText);
            ps.setString(3, tags);
            ps.setString(4, category);
            ps.setString(5, createdAt);
            ps.executeUpdate();
        }
    }

    private void insertBotConversation(
            Path dbPath,
            long chatId,
            long userId,
            String userName,
            String messageType,
            String content,
            String botResponse,
            String timestamp)
            throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
                PreparedStatement ps =
                        conn.prepareStatement(
                                "INSERT INTO bot_conversations (chat_id, user_id, user_name,"
                                    + " message_type, content, bot_response, timestamp) VALUES (?,"
                                    + " ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, chatId);
            ps.setLong(2, userId);
            ps.setString(3, userName);
            ps.setString(4, messageType);
            ps.setString(5, content);
            ps.setString(6, botResponse);
            ps.setString(7, timestamp);
            ps.executeUpdate();
        }
    }

    private void insertInteractionLog(
            Path dbPath,
            long chatId,
            long userId,
            String userName,
            String actionType,
            String content,
            String timestamp)
            throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
                PreparedStatement ps =
                        conn.prepareStatement(
                                "INSERT INTO interaction_logs (chat_id, user_id, user_name,"
                                        + " action_type, content, timestamp) VALUES (?, ?, ?, ?, ?,"
                                        + " ?)")) {
            ps.setLong(1, chatId);
            ps.setLong(2, userId);
            ps.setString(3, userName);
            ps.setString(4, actionType);
            ps.setString(5, content);
            ps.setString(6, timestamp);
            ps.executeUpdate();
        }
    }
}
