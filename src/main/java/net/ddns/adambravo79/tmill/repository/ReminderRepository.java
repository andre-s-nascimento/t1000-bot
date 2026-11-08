/* (c) 2026 | 07/11/2026 */
package net.ddns.adambravo79.tmill.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.model.Reminder;

/**
 * Repositório de {@link Reminder} em PostgreSQL.
 *
 * <h2>Padrão</h2>
 *
 * <p>Segue o mesmo estilo do {@code BirthdayRepository} / {@code ReleaseNotifiedRepository}:
 * {@link JdbcTemplate} + {@link RowMapper}, sem JPA/Hibernate. O time já usa esse padrão em 5+
 * repositórios, então mantê-lo reduz surpresa e risco de regressão.
 *
 * <h2>Consultas</h2>
 *
 * <p>A tabela {@code reminders} é a fonte de verdade; {@code reminder_chats} e {@code
 * reminder_tags} são 1:N. A hidratação é feita com <b>N+1 controlado</b>: para cada linha pai,
 * duas queries adicionais (chats, tags). Para o volume esperado (dezenas de reminders, endpoints
 * admin + scheduler que roda 1× por hora), o custo é desprezível e a legibilidade é máxima. Se um
 * dia precisar otimizar, o método {@link #hydrate(List)} é o único a tocar — a API pública
 * ({@link #findAll()}, {@link #findEnabled()}, {@link #findById(String)}) não muda.
 *
 * <h2>Transações</h2>
 *
 * <p>Os métodos de escrita ({@link #save(Reminder)}, {@link #update(Reminder, int)}, {@link
 * #deleteById(String)}) são {@link Transactional}: ou o reminder + seus chats/tags são gravados
 * juntos, ou nada é. As leituras de lista marcam {@code readOnly = true} para permitir
 * roteamento para réplicas no futuro (hoje é no-op).
 *
 * <h2>Optimistic locking</h2>
 *
 * <p>O campo {@code version} é incrementado a cada {@link #update(Reminder, int)}. Se outra
 * instância atualizou o mesmo reminder entre a leitura e a escrita, o UPDATE afeta 0 linhas e
 * lançamos {@link OptimisticLockingFailureException} — o caller (admin) é avisado para recarregar.
 *
 * <p><b>Contrato de {@code version} na criação:</b> {@link #save(Reminder)} <b>ignora</b> a
 * {@code version} do record e força {@code 1} no INSERT. O record pode vir com qualquer valor
 * (o construtor de 9 args assume 1, o de 10 aceita qualquer coisa); o banco é a fonte de verdade
 * na criação.
 *
 * <h2>Soft delete</h2>
 *
 * <p>A coluna {@code deleted_at} existe mas <b>não é usada</b> nesta versão: {@link
 * #deleteById(String)} faz hard delete, com paridade ao comportamento atual do JSON. Se no futuro
 * for necessário histórico, basta trocar o DELETE por UPDATE e filtrar nas leituras.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ReminderRepository {

    private final JdbcTemplate jdbcTemplate;

    // =========================================================================
    // RowMapper (1 linha → 1 Reminder SEM chats/tags; merge é feito em hydrate)
    // =========================================================================

    /**
     * Mapeia uma linha de {@code reminders} para {@link Reminder}.
     *
     * <p>Os campos {@code targetChatIds} e {@code tags} vêm vazios porque não estão nesta
     * query — são preenchidos em {@link #hydrate(List)}. O {@code version} é lido direto do
     * banco (é o valor que o próximo UPDATE vai usar como {@code expectedVersion}).
     */
    private static final RowMapper<Reminder> REMINDER_ROW_MAPPER =
            (rs, rowNum) -> mapReminderRow(rs);

    private static Reminder mapReminderRow(ResultSet rs) throws SQLException {
        return new Reminder(
                rs.getString("id"),
                rs.getString("name"),
                rs.getBoolean("enabled"),
                rs.getString("cron"),
                rs.getString("timezone"),
                rs.getString("message"),
                rs.getString("media_url"),
                List.of(), // chats: preenchido por hydrate()
                List.of(), // tags: preenchido por hydrate()
                rs.getInt("version"));
    }

    // =========================================================================
    // LEITURA
    // =========================================================================

    /** Retorna todos os reminders (com chats e tags carregados). */
    @Transactional(readOnly = true)
    public List<Reminder> findAll() {
        List<Reminder> base =
                jdbcTemplate.query(
                        "SELECT id, name, enabled, cron, timezone, message, media_url, version"
                                + " FROM reminders ORDER BY id",
                        REMINDER_ROW_MAPPER);

        return hydrate(base);
    }

    /** Retorna apenas os reminders habilitados (usado pelo scheduler). */
    @Transactional(readOnly = true)
    public List<Reminder> findEnabled() {
        List<Reminder> base =
                jdbcTemplate.query(
                        "SELECT id, name, enabled, cron, timezone, message, media_url, version"
                                + " FROM reminders WHERE enabled = TRUE ORDER BY id",
                        REMINDER_ROW_MAPPER);

        return hydrate(base);
    }

    /** Busca por ID; {@link Optional#empty()} se não existir. */
    @Transactional(readOnly = true)
    public Optional<Reminder> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        List<Reminder> rows =
                jdbcTemplate.query(
                        "SELECT id, name, enabled, cron, timezone, message, media_url, version"
                                + " FROM reminders WHERE id = ?",
                        REMINDER_ROW_MAPPER,
                        id);

        if (rows.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(hydrate(List.of(rows.get(0))).get(0));
    }

    /** Retorna o total de reminders (habilitados + desabilitados). */
    public int count() {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reminders", Integer.class);
        return n != null ? n : 0;
    }

    /** Retorna {@code true} se existe um reminder com este id. */
    public boolean existsById(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        Integer n =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM reminders WHERE id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    // =========================================================================
    // ESCRITA
    // =========================================================================

    /**
     * Insere um novo reminder (pai + chats + tags) em uma única transação.
     *
     * <p><b>Pré-condição:</b> o id não pode existir. O caller ({@code ReminderService}) já valida
     * isso via {@link #existsById(String)} para dar mensagem amigável — mas o PK do banco também
     * protege (vai lançar {@code DuplicateKeyException}).
     *
     * <p><b>{@code version} no INSERT:</b> o valor do record é <b>ignorado</b>; o banco é a fonte
     * de verdade na criação, então sempre grava {@code 1}. O record pode vir com qualquer valor
     * (o de 9 args assume 1, o de 10 aceita qualquer coisa). Se o caller quiser criar com uma
     * versão específica (import de backup), o {@code ReminderService.importAll} usa
     * {@code update} em vez de {@code save} — e aí respeita a versão.
     *
     * @param reminder reminder a inserir (não nulo, id não vazio)
     */
    @Transactional
    public void save(Reminder reminder) {
        jdbcTemplate.update(
                "INSERT INTO reminders (id, name, enabled, cron, timezone, message, media_url,"
                        + " version) VALUES (?, ?, ?, ?, ?, ?, ?, 1)",
                reminder.id(),
                reminder.name(),
                reminder.enabled(),
                reminder.cron(),
                reminder.timezone(),
                reminder.message(),
                reminder.mediaUrl());

        insertChats(reminder.id(), reminder.targetChatIds());
        insertTags(reminder.id(), reminder.tags());

        log.debug(
                "💾 Reminder inserido: id='{}' ({} chats, {} tags)",
                reminder.id(),
                reminder.targetChatIds().size(),
                reminder.tags().size());
    }

    /**
     * Atualiza um reminder existente (pai + chats + tags) em uma única transação.
     *
     * <p>Usa <b>optimistic locking</b>: o UPDATE só é efetivado se {@code version} no banco ainda
     * for o mesmo que o caller leu. Se outra instância atualizou entre o read e o write, o UPDATE
     * afeta 0 linhas e lançamos {@link OptimisticLockingFailureException}.
     *
     * <p>A estratégia para chats/tags é <b>delete + reinsert</b>: mais simples que diff, e
     * perfeitamente aceitável para dezenas de linhas. Fica tudo na mesma transação, então ou tudo
     * commita ou tudo rollbacka.
     *
     * @param reminder reminder com os novos valores
     * @param expectedVersion versão que o caller leu (deve bater com a do banco)
     * @throws OptimisticLockingFailureException se a versão não bater ou o id não existir
     */
    @Transactional
    public void update(Reminder reminder, int expectedVersion) {
        int rows =
                jdbcTemplate.update(
                        "UPDATE reminders"
                                + " SET name = ?, enabled = ?, cron = ?, timezone = ?,"
                                + "     message = ?, media_url = ?, version = version + 1,"
                                + "     updated_at = NOW()"
                                + " WHERE id = ? AND version = ?",
                        reminder.name(),
                        reminder.enabled(),
                        reminder.cron(),
                        reminder.timezone(),
                        reminder.message(),
                        reminder.mediaUrl(),
                        reminder.id(),
                        expectedVersion);

        if (rows == 0) {
            throw new OptimisticLockingFailureException(
                    "Reminder '"
                            + reminder.id()
                            + "' foi modificado por outra instância (versão esperada: "
                            + expectedVersion
                            + ")");
        }

        // Substitui chats e tags inteiramente (delete + reinsert na mesma transação)
        jdbcTemplate.update("DELETE FROM reminder_chats WHERE reminder_id = ?", reminder.id());
        jdbcTemplate.update("DELETE FROM reminder_tags WHERE reminder_id = ?", reminder.id());
        insertChats(reminder.id(), reminder.targetChatIds());
        insertTags(reminder.id(), reminder.tags());

        log.debug(
                "💾 Reminder atualizado: id='{}' (version {} → {})",
                reminder.id(),
                expectedVersion,
                expectedVersion + 1);
    }

    /**
     * Remove um reminder (hard delete). As tabelas filhas são limpas em cascata pela FK
     * {@code ON DELETE CASCADE}.
     *
     * @param id id do reminder
     * @return {@code true} se removeu (existia), {@code false} caso contrário
     */
    @Transactional
    public boolean deleteById(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        int rows = jdbcTemplate.update("DELETE FROM reminders WHERE id = ?", id);
        if (rows > 0) {
            log.debug("🗑️ Reminder removido: id='{}'", id);
        }
        return rows > 0;
    }

    // =========================================================================
    // HIDRATAÇÃO (N+1 controlado: 2 queries por linha pai)
    // =========================================================================

    /**
     * Carrega chats e tags das tabelas filhas e anexa aos reminders em memória.
     *
     * <p>Para cada linha pai, executa 2 queries (chats, tags). Para o volume esperado (dezenas de
     * reminders), isso é desprezível e mantém o código simples. Endpoints que consomem essa
     * leitura são admin (raro) e scheduler (1× por hora).
     *
     * <p><b>Preserva {@code version}:</b> o record reconstruído usa a {@code version} lida do
     * banco — o próximo UPDATE vai usar exatamente esse valor como {@code expectedVersion}.
     */
    private List<Reminder> hydrate(List<Reminder> base) {
        if (base.isEmpty()) {
            return base;
        }

        List<Reminder> result = new ArrayList<>(base.size());
        for (Reminder r : base) {
            List<String> chats = loadChats(r.id());
            List<String> tags = loadTags(r.id());
            result.add(
                    new Reminder(
                            r.id(),
                            r.name(),
                            r.enabled(),
                            r.cron(),
                            r.timezone(),
                            r.message(),
                            r.mediaUrl(),
                            chats,
                            tags,
                            r.version()));
        }
        return result;
    }

    private List<String> loadChats(String reminderId) {
        List<Long> ids =
                jdbcTemplate.queryForList(
                        "SELECT chat_id FROM reminder_chats WHERE reminder_id = ? ORDER BY chat_id",
                        Long.class,
                        reminderId);

        // Converte Long → String (o model Reminder usa List<String>)
        List<String> result = new ArrayList<>(ids.size());
        for (Long id : ids) {
            result.add(String.valueOf(id));
        }
        return result;
    }

    private List<String> loadTags(String reminderId) {
        return jdbcTemplate.queryForList(
                "SELECT tag FROM reminder_tags WHERE reminder_id = ? ORDER BY tag",
                String.class,
                reminderId);
    }

    // =========================================================================
    // HELPERS DE INSERT
    // =========================================================================

    private void insertChats(String reminderId, List<String> chatIds) {
        if (chatIds == null || chatIds.isEmpty()) {
            return;
        }

        List<Object[]> batch = new ArrayList<>(chatIds.size());
        for (String c : chatIds) {
            try {
                long chatId = Long.parseLong(c.trim());
                batch.add(new Object[] {reminderId, chatId});
            } catch (NumberFormatException e) {
                log.warn("⚠️ Chat ID inválido ignorado no reminder '{}': '{}'", reminderId, c);
            }
        }

        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO reminder_chats (reminder_id, chat_id) VALUES (?, ?)", batch);
        }
    }

    private void insertTags(String reminderId, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return;
        }
        List<Object[]> batch = new ArrayList<>(tags.size());
        for (String t : tags) {
            batch.add(new Object[] {reminderId, t});
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO reminder_tags (reminder_id, tag) VALUES (?, ?)", batch);
    }
}
