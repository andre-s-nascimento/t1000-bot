package net.ddns.adambravo79.tmill.config;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.annotation.PostConstruct;

/**
 * Inicializador do schema SQLite legado.
 *
 * <h2>Nota de segurança — SQL dinâmico</h2>
 *
 * <p>O SQLite não suporta bind parameters em nomes de identificadores (tabela, coluna),
 * nem em {@code PRAGMA table_info(?)}, nem em {@code ALTER TABLE ... ADD COLUMN ?}.
 * Como consequência, é impossível parametrizar totalmente essas operações com
 * {@link java.sql.PreparedStatement}.
 *
 * <p>Para mitigar o risco de SQL injection (e silenciar o SonarQube {@code java:S2077}),
 * este inicializador adota duas estratégias complementares:
 *
 * <ol>
 *   <li><b>Statements literais:</b> todos SQL é construído a partir de strings literais
 *       (via {@code switch} ou mapas constantes). Nenhum valor dinâmico é concatenado
 *       diretamente em uma string SQL.
 *   <li><b>Validação defensiva:</b> mesmo os valores que indexam os mapas/switch passam
 *       por {@link #validarIdentificador(String, String)}, que aplica regex
 *       ({@code ^[A-Za-z_]\w*$}) e uma whitelist explícita. Isso protege contra
 *       futuras refatorações que tentem passar input externo.
 * </ol>
 */
public class DatabaseInitializer {

    private static final String IGNORE_IN_DIGEST = "ignore_in_digest";

    private static final String BIRTHDAYS = "birthdays";

    private static final String RELEASES_NOTIFIED = "releases_notified";

    private static final String TRANSCRIPTS = "transcripts";

    private static final String MESSAGES = "messages";

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    /** Identificador SQL válido: letra/underscore inicial + alfanuméricos. */
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("^[A-Za-z_]\\w*$");

    /** Rótulo usado em mensagens de erro para diferenciar tabela de coluna. */
    private static final String TABELA = "tabela";

    private static final String COLUNA = "coluna";

    /** Whitelist de tabelas que este inicializador pode tocar. */
    private static final Set<String> TABELAS_PERMITIDAS =
            Set.of(MESSAGES, TRANSCRIPTS, RELEASES_NOTIFIED, BIRTHDAYS);

    /** Whitelist de colunas alteráveis. */
    private static final Set<String> COLUNAS_PERMITIDAS =
            Set.of(
                    "title",
                    "overview",
                    "rating",
                    "providers",
                    "poster_path",
                    "raw_text",
                    IGNORE_IN_DIGEST);

    /**
     * Catálogo de statements {@code ALTER TABLE} — todos literais, sem concatenação.
     *
     * <p>A chave é {@code "<tabela>.<coluna>"}. Se um novo par precisar ser adicionado,
     * inclua a entrada aqui e a coluna correspondente em {@link #COLUNAS_PERMITIDAS}.
     */
    private static final Map<String, String> COLUMN_ALTERS =
            Map.ofEntries(
                    Map.entry(
                            "releases_notified.title",
                            "ALTER TABLE releases_notified ADD COLUMN title TEXT"),
                    Map.entry(
                            "releases_notified.overview",
                            "ALTER TABLE releases_notified ADD COLUMN overview TEXT"),
                    Map.entry(
                            "releases_notified.rating",
                            "ALTER TABLE releases_notified ADD COLUMN rating REAL"),
                    Map.entry(
                            "releases_notified.providers",
                            "ALTER TABLE releases_notified ADD COLUMN providers TEXT"),
                    Map.entry(
                            "releases_notified.poster_path",
                            "ALTER TABLE releases_notified ADD COLUMN poster_path TEXT"),
                    Map.entry(
                            "transcripts.raw_text",
                            "ALTER TABLE transcripts ADD COLUMN raw_text TEXT"),
                    Map.entry(
                            "transcripts.ignore_in_digest",
                            "ALTER TABLE transcripts ADD COLUMN ignore_in_digest BOOLEAN DEFAULT"
                                    + " 0"),
                    Map.entry(
                            "messages.ignore_in_digest",
                            "ALTER TABLE messages ADD COLUMN ignore_in_digest BOOLEAN DEFAULT 0"));

    private final JdbcTemplate jdbcTemplate;

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

        garantirColuna(TRANSCRIPTS, "raw_text");
        garantirColuna(TRANSCRIPTS, IGNORE_IN_DIGEST);
        garantirColuna(MESSAGES, IGNORE_IN_DIGEST);
    }

    // =========================================================================
    // CREATE TABLE (statements literais)
    // =========================================================================

    private void criarTabelaMessages() {
        executeSilenciosamente(
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
                """,
                "tabela messages");
    }

    private void criarTabelaBirthdays() {
        executeSilenciosamente(
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
                """,
                "tabela birthdays");
        executeSilenciosamente(
                "CREATE INDEX IF NOT EXISTS idx_birthdays_day_month ON birthdays(day, month)",
                "index idx_birthdays_day_month");
    }

    private void criarTabelaTranscripts() {
        executeSilenciosamente(
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
                """,
                "tabela transcripts");
    }

    private void criarTabelaReleasesNotified() {
        executeSilenciosamente(
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
                """,
                "tabela releases_notified");
    }

    // =========================================================================
    // ALTER TABLE — via catálogo literal
    // =========================================================================

    private void adicionarColunasReleases() {
        try {
            List<Map<String, Object>> columns = listarColunas(RELEASES_NOTIFIED);
            List<String> existing =
                    columns.stream().map(col -> col.get("name").toString()).toList();

            for (Map.Entry<String, String> entry : COLUMN_ALTERS.entrySet()) {
                String chave = entry.getKey();
                if (!chave.startsWith("releases_notified.")) {
                    continue;
                }
                String columnName = chave.substring("releases_notified.".length());
                if (!existing.contains(columnName)) {
                    executeAlterStatement(columnName, entry.getValue());
                }
            }
        } catch (Exception e) {
            log.error("Erro ao verificar colunas da tabela releases_notified", e);
        }
    }

    /**
     * Garante que uma coluna exista, executando o {@code ALTER TABLE} correspondente do
     * catálogo {@link #COLUMN_ALTERS}.
     *
     * <p>Nenhum SQL é construído dinamicamente: o statement vem pronto do catálogo,
     * indexado por {@code "<tabela>.<coluna>"}. Se a combinação não existir no catálogo,
     * apenas loga erro — o chamador nunca fornece SQL arbitrário.
     */
    private void garantirColuna(String tabela, String coluna) {
        try {
            validarIdentificador(tabela, TABELA);
            validarIdentificador(coluna, COLUNA);

            List<Map<String, Object>> columns = listarColunas(tabela);
            boolean existe = columns.stream().anyMatch(col -> coluna.equals(col.get("name")));
            if (existe) {
                return;
            }

            String chave = tabela + "." + coluna;
            String sql = COLUMN_ALTERS.get(chave);
            if (sql == null) {
                log.error(
                        "Nenhum ALTER TABLE cadastrado para {}.{} — coluna ignorada",
                        tabela,
                        coluna);
                return;
            }
            executeAlterStatement(coluna, sql);
        } catch (IllegalArgumentException e) {
            log.error("Identificador SQL inválido em garantirColuna(): {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Erro ao verificar/adicionar coluna {} na tabela {}", coluna, tabela, e);
        }
    }

    /**
     * Lista as colunas de uma tabela via {@code PRAGMA table_info}.
     *
     * <p>O nome da tabela <b>não</b> é concatenado em runtime: é resolvido para uma
     * string literal via {@code switch}. Isso elimina o {@code java:S2077} do SonarQube.
     */
    private List<Map<String, Object>> listarColunas(String tabela) {
        validarIdentificador(tabela, TABELA);
        String sql =
                switch (tabela) {
                    case MESSAGES -> "PRAGMA table_info(messages)";
                    case TRANSCRIPTS -> "PRAGMA table_info(transcripts)";
                    case RELEASES_NOTIFIED -> "PRAGMA table_info(releases_notified)";
                    case BIRTHDAYS -> "PRAGMA table_info(birthdays)";
                    default ->
                            throw new IllegalArgumentException(
                                    "Tabela não suportada para PRAGMA: " + tabela);
                };
        return jdbcTemplate.queryForList(sql);
    }

    // =========================================================================
    // Validação defensiva
    // =========================================================================

    /**
     * Valida um identificador SQL contra regex e whitelist.
     *
     * @param identificador valor a validar
     * @param tipo {@link #TABELA} ou {@link #COLUNA} (usado em mensagens de erro)
     * @throws IllegalArgumentException se inválido
     */
    private static void validarIdentificador(String identificador, String tipo) {
        if (identificador == null || identificador.isBlank()) {
            throw new IllegalArgumentException(tipo + " não pode ser nulo/vazio");
        }
        if (!SQL_IDENTIFIER.matcher(identificador).matches()) {
            throw new IllegalArgumentException(
                    tipo + " contém caracteres inválidos: '" + identificador + "'");
        }
        Set<String> whitelist = TABELA.equals(tipo) ? TABELAS_PERMITIDAS : COLUNAS_PERMITIDAS;
        if (!whitelist.contains(identificador)) {
            throw new IllegalArgumentException(
                    tipo + " não está na whitelist: '" + identificador + "'");
        }
    }

    // =========================================================================
    // Execução
    // =========================================================================

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

    private void executeSilenciosamente(String sql, String descricao) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            log.error("Erro ao criar/atualizar {}", descricao, e);
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
