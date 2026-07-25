package net.ddns.adambravo79.tmill.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class DatabaseInitializerTest {

    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks private DatabaseInitializer databaseInitializer;

    @BeforeEach
    void setUp() {
        // Comportamento padrão para PRAGMA table_info - retorna lista vazia por padrão
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());
    }

    // ===================== TESTES DE CRIAÇÃO DE TABELAS =====================

    @Test
    void init_deveCriarTabelaTranscripts() {
        databaseInitializer.init();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).execute(sqlCaptor.capture());

        List<String> executedSqls = sqlCaptor.getAllValues();
        boolean hasCreateTranscripts =
                executedSqls.stream()
                        .anyMatch(sql -> sql.contains("CREATE TABLE IF NOT EXISTS transcripts"));
        assertThat(hasCreateTranscripts).isTrue();
    }

    @Test
    void init_deveCriarTabelaReleasesNotified() {
        databaseInitializer.init();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).execute(sqlCaptor.capture());

        List<String> executedSqls = sqlCaptor.getAllValues();
        boolean hasCreateReleases =
                executedSqls.stream()
                        .anyMatch(
                                sql ->
                                        sql.contains(
                                                "CREATE TABLE IF NOT EXISTS releases_notified"));
        assertThat(hasCreateReleases).isTrue();
    }

    // ===================== TESTES DE MIGRAÇÃO DE COLUNAS =====================

    @Test
    void init_quandoFaltaRawText_deveAdicionarColuna() {
        when(jdbcTemplate.queryForList("PRAGMA table_info(transcripts)"))
                .thenReturn(
                        List.of(
                                Map.of("name", "id"),
                                Map.of("name", "chat_id"),
                                Map.of("name", "text")));

        databaseInitializer.init();

        verify(jdbcTemplate).execute("ALTER TABLE transcripts ADD COLUMN raw_text TEXT");
    }

    @Test
    void init_quandoRawTextJaExiste_naoAdicionaColuna() {
        when(jdbcTemplate.queryForList("PRAGMA table_info(transcripts)"))
                .thenReturn(
                        List.of(
                                Map.of("name", "id"),
                                Map.of("name", "chat_id"),
                                Map.of("name", "text"),
                                Map.of("name", "raw_text")));

        databaseInitializer.init();

        verify(jdbcTemplate, never()).execute("ALTER TABLE transcripts ADD COLUMN raw_text TEXT");
    }

    @Test
    void init_quandoFaltaColunaNaReleases_deveAdicionar() {
        when(jdbcTemplate.queryForList("PRAGMA table_info(releases_notified)"))
                .thenReturn(
                        List.of(
                                Map.of("name", "id"),
                                Map.of("name", "tmdb_id"),
                                Map.of("name", "media_type")));

        databaseInitializer.init();

        verify(jdbcTemplate).execute("ALTER TABLE releases_notified ADD COLUMN title TEXT");
        verify(jdbcTemplate).execute("ALTER TABLE releases_notified ADD COLUMN overview TEXT");
        verify(jdbcTemplate).execute("ALTER TABLE releases_notified ADD COLUMN rating REAL");
        verify(jdbcTemplate).execute("ALTER TABLE releases_notified ADD COLUMN providers TEXT");
        verify(jdbcTemplate).execute("ALTER TABLE releases_notified ADD COLUMN poster_path TEXT");
    }

    @Test
    void init_quandoTodasColunasJaExistem_naoAdicionaNenhuma() {
        when(jdbcTemplate.queryForList("PRAGMA table_info(releases_notified)"))
                .thenReturn(
                        List.of(
                                Map.of("name", "id"),
                                Map.of("name", "tmdb_id"),
                                Map.of("name", "media_type"),
                                Map.of("name", "release_date"),
                                Map.of("name", "title"),
                                Map.of("name", "overview"),
                                Map.of("name", "rating"),
                                Map.of("name", "providers"),
                                Map.of("name", "poster_path")));

        databaseInitializer.init();

        verify(jdbcTemplate, never()).execute(contains("ALTER TABLE releases_notified"));
    }

    // ===================== TESTES DE TRATAMENTO DE EXCEÇÕES =====================

    @Test
    void init_quandoErroAoVerificarRawText_deveLogarMasContinuar() {
        when(jdbcTemplate.queryForList("PRAGMA table_info(transcripts)"))
                .thenThrow(new RuntimeException("Erro no banco"));

        databaseInitializer.init();

        // Continua e cria a tabela releases_notified
        verify(jdbcTemplate, atLeastOnce())
                .execute(contains("CREATE TABLE IF NOT EXISTS releases_notified"));
        // Não deve tentar adicionar a coluna raw_text
        verify(jdbcTemplate, never())
                .execute(contains("ALTER TABLE transcripts ADD COLUMN raw_text"));
    }

    @Test
    void init_quandoErroAoAdicionarColuna_naoInterrompeFluxo() {
        // Simula que a coluna raw_text NÃO existe
        when(jdbcTemplate.queryForList("PRAGMA table_info(transcripts)"))
                .thenReturn(List.of(Map.of("name", "id")));

        // Lenient evita PotentialStubbingProblem se o stub não for usado
        lenient()
                .doThrow(new RuntimeException("Erro ao adicionar coluna"))
                .when(jdbcTemplate)
                .execute("ALTER TABLE transcripts ADD COLUMN raw_text TEXT");

        databaseInitializer.init();

        // Verifica que tentou adicionar a coluna
        verify(jdbcTemplate).execute("ALTER TABLE transcripts ADD COLUMN raw_text TEXT");
        // E continuou para criar a tabela releases_notified
        verify(jdbcTemplate, atLeastOnce())
                .execute(contains("CREATE TABLE IF NOT EXISTS releases_notified"));
    }

    @Test
    void init_quandoExcecaoAoAdicionarColunaNaReleases_deveLogar() {
        // Simula que releases_notified existe mas falta a coluna title
        when(jdbcTemplate.queryForList("PRAGMA table_info(releases_notified)"))
                .thenReturn(List.of(Map.of("name", "id")));

        // Lenient para evitar problema de stubbing não utilizado
        lenient()
                .doThrow(new RuntimeException("Erro no ALTER"))
                .when(jdbcTemplate)
                .execute("ALTER TABLE releases_notified ADD COLUMN title TEXT");

        databaseInitializer.init();

        // Verifica que tentou adicionar todas as colunas
        verify(jdbcTemplate).execute("ALTER TABLE releases_notified ADD COLUMN title TEXT");
        verify(jdbcTemplate).execute("ALTER TABLE releases_notified ADD COLUMN overview TEXT");
        verify(jdbcTemplate).execute("ALTER TABLE releases_notified ADD COLUMN rating REAL");
        verify(jdbcTemplate).execute("ALTER TABLE releases_notified ADD COLUMN providers TEXT");
        verify(jdbcTemplate).execute("ALTER TABLE releases_notified ADD COLUMN poster_path TEXT");
    }
}
