/* (c) 2026 */
package net.ddns.adambravo79.tmill.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DatabaseInitializerTest {

    private JdbcTemplate jdbcTemplate;
    private DatabaseInitializer databaseInitializer;

    @BeforeEach
    void setUp() {
        // Configura um DataSource SQLite em memória para os testes
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite::memory:");

        jdbcTemplate = new JdbcTemplate(dataSource);
        databaseInitializer = new DatabaseInitializer(jdbcTemplate);
    }

    @Test
    @DisplayName("Deve inicializar todas as tabelas e garantir colunas com sucesso")
    void shouldInitializeTablesAndColumnsSuccessfully() {
        // Executa a inicialização
        databaseInitializer.init();

        // Valida se as tabelas principais foram criadas
        assertThat(tableExists("messages")).isTrue();
        assertThat(tableExists("transcripts")).isTrue();
        assertThat(tableExists("releases_notified")).isTrue();
        assertThat(tableExists("birthdays")).isTrue();

        // Valida se as colunas adicionadas via migração/alter table existem
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
        // Primeira execução
        databaseInitializer.init();

        // Segunda execução para testar se ignora colunas duplicadas com segurança
        databaseInitializer.init();

        assertThat(tableExists("messages")).isTrue();
        assertThat(columnExists("transcripts", "ignore_in_digest")).isTrue();
    }

    // Helpers auxiliares para inspecionar o banco de dados de teste
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
