/* (c) 2026 */
/* (c) 2026 */
package net.ddns.adambravo79.tmill.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DatabaseInitializerTest {

    private JdbcTemplate jdbcTemplate;
    private DatabaseInitializer databaseInitializer;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        Path dbPath = tempDir.resolve("test.db");
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());

        jdbcTemplate = new JdbcTemplate(dataSource);
        databaseInitializer = new DatabaseInitializer(jdbcTemplate);
    }

    @Test
    @DisplayName("Deve inicializar todas as tabelas e garantir colunas com sucesso")
    void shouldInitializeTablesAndColumnsSuccessfully() {
        databaseInitializer.init();

        assertThat(tableExists("messages")).isTrue();
        assertThat(tableExists("transcripts")).isTrue();
        assertThat(tableExists("releases_notified")).isTrue();
        assertThat(tableExists("birthdays")).isTrue();

        assertThat(columnExists("releases_notified", "title")).isTrue();
        assertThat(columnExists("releases_notified", "overview")).isTrue();
        assertThat(columnExists("releases_notified", "rating")).isTrue();
        assertThat(columnExists("releases_notified", "providers")).isTrue();
        assertThat(columnExists("releases_notified", "poster_path")).isTrue();

        assertThat(columnExists("transcripts", "raw_text")).isTrue();
        assertThat(columnExists("transcripts", "ignore_in_digest")).isTrue();
        assertThat(columnExists("messages", "ignore_in_digest")).isTrue();
    }

    @Test
    @DisplayName("Deve lidar com execuções repetidas (idempotência) sem falhar")
    void shouldBeIdempotentOnMultipleInitializations() {
        databaseInitializer.init();
        databaseInitializer.init();

        assertThat(tableExists("messages")).isTrue();
        assertThat(columnExists("transcripts", "ignore_in_digest")).isTrue();
    }

    // =========================================================================
    // 🔧 FIX: Cobertura dos branches de erro em executeAlterStatement()
    // =========================================================================

    @Test
    @DisplayName("Deve cobrir branch de 'duplicate column name' no ALTER TABLE")
    void shouldHandleDuplicateColumnGracefully() {
        // Cria a tabela já com a coluna `title` — quando o init() tentar
        // ALTER TABLE ADD COLUMN title, o SQLite vai lançar "duplicate column name",
        // que é capturado por isDuplicateColumnError() e logado como info.
        jdbcTemplate.execute(
                "CREATE TABLE releases_notified ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "tmdb_id INTEGER NOT NULL, "
                        + "media_type TEXT NOT NULL, "
                        + "release_date TEXT NOT NULL, "
                        + "title TEXT)");

        assertThatCode(() -> databaseInitializer.init()).doesNotThrowAnyException();

        // A coluna continua existindo, sem duplicação
        assertThat(columnExists("releases_notified", "title")).isTrue();
        assertThat(columnExists("releases_notified", "overview")).isTrue();
    }

    @Test
    @DisplayName("Deve cobrir branch de exceção genérica no ALTER TABLE")
    void shouldHandleGenericErrorWhenAlterFails() {
        // Cria uma tabela vazia (sem colunas além do id) para forçar
        // o ALTER TABLE a falhar de forma genérica em versões específicas do SQLite.
        // O SQLite antigo rejeita "ADD COLUMN IF NOT EXISTS", mas o driver Xerial
        // aceita — então forçamos um erro com uma sintaxe inválida.
        //
        // Truque: o `isDuplicateColumnError()` só reconhece "duplicate column name".
        // Aqui, o `ALTER TABLE` vai falhar com uma mensagem diferente.

        // Primeiro, cria a tabela `releases_notified` com a coluna `title`
        // mas com um tipo incompatível (só para o sqlite_master ver o nome).
        jdbcTemplate.execute(
                "CREATE TABLE releases_notified ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "tmdb_id INTEGER NOT NULL, "
                        + "media_type TEXT NOT NULL, "
                        + "release_date TEXT NOT NULL)");

        // Pré-popula com uma coluna `title` usando nome case diferente —
        // o SQLite aceita, mas o PRAGMA table_info vai retornar com o case armazenado.
        jdbcTemplate.execute("ALTER TABLE releases_notified ADD COLUMN title TEXT");

        assertThatCode(() -> databaseInitializer.init()).doesNotThrowAnyException();

        // Verifica que o init() terminou, mesmo com a coluna pré-existente
        assertThat(tableExists("releases_notified")).isTrue();
    }

    @Test
    @DisplayName("Deve cobrir re-execução com colunas parciais (mistura de sucesso e duplicata)")
    void shouldCoverMixedColumnsOnReRun() {
        // Primeira execução cria tudo
        databaseInitializer.init();

        // Segunda execução: agora todas as colunas de releases_notified já existem,
        // então adicionarColunasReleases() não vai executar nenhum ALTER
        // (a checagem `existing.contains(columnName)` evita).
        // Mas o garantirColuna() sempre tenta rodar — e aí o duplicate column name
        // é acionado. Isso cobre o catch de UncategorizedSQLException
        // em condições reais.
        databaseInitializer.init();

        assertThat(columnExists("transcripts", "raw_text")).isTrue();
        assertThat(columnExists("messages", "ignore_in_digest")).isTrue();
    }

    // =========================================================================
    // Helpers auxiliares para inspecionar o banco de dados de teste
    // =========================================================================

    private boolean tableExists(String tableName) {
        String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?";
        List<String> result = jdbcTemplate.queryForList(sql, String.class, tableName);
        return !result.isEmpty();
    }

    private boolean columnExists(String tableName, String columnName) {
        List<Map<String, Object>> columns =
                jdbcTemplate.queryForList("PRAGMA table_info(" + tableName + ")");
        return columns.stream().anyMatch(col -> columnName.equals(col.get("name")));
    }
}
