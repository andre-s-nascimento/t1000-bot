/* (c) 2026 | 06/11/2026 */
package net.ddns.adambravo79.tmill.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.model.Reminder;
import net.ddns.adambravo79.tmill.repository.ReminderRepository;

/**
 * Registry central dos lembretes configuráveis.
 *
 * <p><b>Fonte de verdade:</b> PostgreSQL, via {@link ReminderRepository}. O JSON
 * ({@code config/reminders.json}) é apenas import/export (bootstrap, backup, migração
 * entre ambientes) — nunca lido nem escrito em runtime por este service.
 *
 * <h2>Optimistic locking</h2>
 *
 * <p>{@link #update(String, Reminder)} passa {@code reminder.version()} ao repository,
 * que executa {@code UPDATE ... WHERE id = ? AND version = ?}. Se outra instância já
 * atualizou o registro, o UPDATE afeta 0 linhas, o repository lança {@link
 * OptimisticLockingFailureException}, e o service relança como {@link
 * IllegalStateException} (o controller traduz para HTTP 409).
 *
 * <p>O cliente (admin) deve reler o reminder via {@link #getById(String)} e reenviar
 * o body com a nova {@code version}. É o padrão de optimistic locking.
 *
 * <h2>Importação em lote</h2>
 *
 * <p>{@link #importAll(List, boolean)} substitui o estado do banco pelo lote informado
 * (ou faz merge, se {@code replace=false}). Roda em uma única transação: ou o
 * lote completo commita, ou nada. Para cada item:
 *
 * <ul>
 *   <li>Se já existe (mesmo id): usa {@link ReminderRepository#update(Reminder, int)}
 *       com a versão atual do banco — não dá conflito.
 *   <li>Se não existe: usa {@link ReminderRepository#save(Reminder)} com versão 1.
 * </ul>
 *
 * <p>Com {@code replace=true}, os reminders que existem no banco mas não estão no lote
 * são removidos. Com {@code replace=false}, são preservados (merge).
 *
 * <h2>Listener de reload</h2>
 *
 * <p>Após cada mutação (create/update/delete/toggle/reload/import) o {@link Runnable}
 * registrado via {@link #setReloadListener(Runnable)} é invocado. O {@link
 * ReminderScheduler} usa esse gancho para cancelar e reagendar todas as tarefas.
 *
 * <h2>Thread-safety</h2>
 *
 * <p>As operações CRUD são {@code synchronized} para serializar escritas. As leituras
 * delegam direto ao repository (que é thread-safe — {@link
 * org.springframework.jdbc.core.JdbcTemplate} usa conexões do pool).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    private final ReminderRepository repository;

    /** Callback opcional para o scheduler reagendar (evita dependência circular). */
    private final AtomicReference<Runnable> reloadListener = new AtomicReference<>(() -> {});

    // =========================================================================
    // CICLO DE VIDA
    // =========================================================================

    /**
     * Loga o estado inicial no boot. Não faz mais nada — o estado vive no banco,
     * o scheduler lê via {@link #getEnabled()} quando precisar.
     */
    @PostConstruct
    public void init() {
        int total = repository.count();
        int enabled = repository.findEnabled().size();
        log.info("📢 ReminderService inicializado: {} reminders ({} habilitados)", total, enabled);
    }

    /**
     * Registra um callback para ser invocado após cada mutação e após {@link #reload()}.
     *
     * @param listener callback (nunca nulo; se nulo, instala um no-op)
     */
    public void setReloadListener(Runnable listener) {
        this.reloadListener.set(listener == null ? () -> {} : listener);
    }

    /** Notifica o listener registrado, capturando qualquer exceção (não propaga). */
    private void notifyReload() {
        try {
            reloadListener.get().run();
        } catch (RuntimeException e) {
            log.error("❌ Falha ao notificar listener de reload (mutação já persistida)", e);
        }
    }

    // =========================================================================
    // LEITURA
    // =========================================================================

    /** Retorna todos os reminders (habilitados + desabilitados). */
    public List<Reminder> getAll() {
        return repository.findAll();
    }

    /** Retorna apenas os reminders habilitados (usado pelo scheduler). */
    public List<Reminder> getEnabled() {
        return repository.findEnabled();
    }

    /** Busca por ID; {@link Optional#empty()} se não existir. */
    public Optional<Reminder> getById(String id) {
        return repository.findById(id);
    }

    /** Retorna o total de reminders (habilitados + desabilitados). */
    public int count() {
        return repository.count();
    }

    // =========================================================================
    // CRUD
    // =========================================================================

    /**
     * Cria um novo reminder.
     *
     * <p>O {@code version} do record é ignorado — o banco grava {@code 1}. Ver
     * {@link ReminderRepository#save(Reminder)}.
     *
     * @param reminder record já validado (id, cron, message não vazios)
     * @return o reminder criado (com {@code version = 1})
     * @throws IllegalArgumentException se já existir um reminder com o mesmo {@code id}
     */
    public synchronized Reminder create(Reminder reminder) {
        Objects.requireNonNull(reminder, "reminder não pode ser nulo");

        if (repository.existsById(reminder.id())) {
            throw new IllegalArgumentException("Reminder com id '" + reminder.id() + "' já existe");
        }

        repository.save(reminder);
        log.info("📢 Reminder criado: id='{}' cron='{}'", reminder.id(), reminder.cron());
        notifyReload();
        return reminder.withVersion(1);
    }

    /**
     * Atualiza um reminder existente (substitui todos os campos exceto {@code id}).
     *
     * <p><b>Optimistic locking:</b> o caller deve enviar {@code version} igual à versão
     * lida via {@link #getById(String)}. Se outra instância atualizou entre a leitura e a
     * escrita, o repository lança {@link OptimisticLockingFailureException} e este método
     * relança como {@link IllegalStateException} (o controller traduz para HTTP 409).
     *
     * @param id identificador do reminder a atualizar
     * @param reminder novo conteúdo (com {@code version} atual)
     * @return o reminder atualizado (com {@code version} incrementada)
     * @throws IllegalArgumentException se não existir ou se {@code id} divergir
     * @throws IllegalStateException se houver conflito de versão
     */
    public synchronized Reminder update(String id, Reminder reminder) {
        Objects.requireNonNull(reminder, "reminder não pode ser nulo");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id do path não pode ser nulo/vazio");
        }
        if (!id.equals(reminder.id())) {
            throw new IllegalArgumentException(
                    "id do path ('" + id + "') diverge do id do body ('" + reminder.id() + "')");
        }

        int expectedVersion = reminder.version();

        try {
            repository.update(reminder, expectedVersion);
        } catch (OptimisticLockingFailureException e) {
            log.warn(
                    "⚠️ Conflito de versão no reminder '{}' (expectedVersion={})",
                    id,
                    expectedVersion);
            throw new IllegalStateException(
                    "Reminder '"
                            + id
                            + "' foi modificado por outra instância. Recarregue e tente"
                            + " novamente.",
                    e);
        }

        Reminder updated = reminder.withVersion(expectedVersion + 1);
        log.info(
                "📢 Reminder atualizado: id='{}' cron='{}' (version {} → {})",
                id,
                reminder.cron(),
                expectedVersion,
                updated.version());
        notifyReload();
        return updated;
    }

    /**
     * Alterna {@code enabled} de um reminder, preservando todos os outros campos.
     *
     * @param id identificador
     * @param enabled novo valor
     * @return o reminder atualizado
     * @throws IllegalArgumentException se não existir
     * @throws IllegalStateException se houver conflito de versão
     */
    public synchronized Reminder toggle(String id, boolean enabled) {
        Reminder current =
                getById(id)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Reminder '" + id + "' não encontrado"));

        return update(id, current.withEnabled(enabled));
    }

    /**
     * Remove um reminder.
     *
     * @param id identificador
     * @return {@code true} se removeu, {@code false} se não existia
     */
    public synchronized boolean delete(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        boolean removed = repository.deleteById(id);
        if (removed) {
            log.info("🗑️ Reminder removido: id='{}'", id);
            notifyReload();
        } else {
            log.warn("⚠️ Tentativa de remover reminder inexistente: id='{}'", id);
        }
        return removed;
    }

    // =========================================================================
    // IMPORTAÇÃO EM LOTE (bootstrap / restore / migração entre ambientes)
    // =========================================================================

    /**
     * Importa um lote de reminders do JSON (bootstrap, restore, migração entre ambientes).
     *
     * <p><b>Estratégia:</b> para cada reminder do lote:
     *
     * <ul>
     *   <li>Se já existe (mesmo id): atualiza via {@link ReminderRepository#update} com a
     *       versão atual do banco. Isso <b>não</b> dispara optimistic locking, porque a versão
     *       lida e enviada são as mesmas.
     *   <li>Se não existe: insere via {@link ReminderRepository#save} com versão 1.
     * </ul>
     *
     * <p>Com {@code replace=true}, os reminders que existem no banco mas <b>não</b> estão no
     * lote são removidos. Com {@code replace=false}, são preservados (merge — útil para
     * bootstrap, onde o operador pode ter criado reminders extras).
     *
     * <p><b>Transação:</b> o lote inteiro roda em uma única transação. Se falhar no meio,
     * nada é gravado (rollback total).
     *
     * @param reminders lista de reminders a importar
     * @param replace se {@code true}, deleta os reminders ausentes do lote
     * @return quantos reminders foram criados/atualizados
     */
    @Transactional
    public synchronized int importAll(List<Reminder> reminders, boolean replace) {
        if (reminders == null || reminders.isEmpty()) {
            log.warn("⚠️ Import recebido com lista vazia — nada a fazer");
            return 0;
        }

        int imported = 0;
        for (Reminder r : reminders) {
            Optional<Reminder> existing = repository.findById(r.id());
            if (existing.isPresent()) {
                Reminder current = existing.get();
                // Atualiza com a versão ATUAL do banco — não dispara optimistic locking
                repository.update(r, current.version());
            } else {
                repository.save(r);
            }
            imported++;
        }

        if (replace) {
            Set<String> importedIds =
                    reminders.stream().map(Reminder::id).collect(Collectors.toSet());
            for (Reminder current : repository.findAll()) {
                if (!importedIds.contains(current.id())) {
                    repository.deleteById(current.id());
                }
            }
        }

        log.info(
                "📥 {} reminders importados{}",
                imported,
                replace ? " (substituição completa)" : " (merge)");
        notifyReload();
        return imported;
    }

    // =========================================================================
    // RELOAD
    // =========================================================================

    /**
     * Re-notifica o listener para o scheduler reagendar tudo.
     *
     * <p>Não há cache local a invalidar — o estado vive no banco e {@link #getEnabled()}
     * lê direto. O nome é mantido para compatibilidade com o endpoint
     * {@code POST /admin/reminders/reload}.
     */
    public synchronized void reload() {
        int total = repository.count();
        int enabled = repository.findEnabled().size();
        log.info("🔄 Reminders recarregados: {} total, {} habilitados", total, enabled);
        notifyReload();
    }
}
