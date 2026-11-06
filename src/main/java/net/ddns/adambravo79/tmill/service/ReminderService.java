/* (c) 2026 | 06/11/2026 */
package net.ddns.adambravo79.tmill.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.dto.ReminderJson;
import net.ddns.adambravo79.tmill.model.Reminder;
import net.ddns.adambravo79.tmill.service.config.JsonConfigLoader;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Registry central dos lembretes configuráveis.
 *
 * <p><b>Responsabilidades:</b>
 *
 * <ol>
 *   <li>Carregar {@code config/reminders.json} no boot ({@link #init()}).
 *   <li>Manter o estado atual em memória ({@link AtomicReference}) para leitura O(1).
 *   <li>Expor operações CRUD ({@link #create}, {@link #update}, {@link #delete}, {@link #toggle}).
 *   <li>Persistir cada mutação no disco de forma atômica ({@code .tmp} + {@link
 *       StandardCopyOption#ATOMIC_MOVE}).
 *   <li>Notificar o {@link ReminderScheduler} (via {@link #setReloadListener(Runnable)}) para
 *       reagendar tarefas após cada mutação.
 * </ol>
 *
 * <h2>Thread-safety</h2>
 *
 * <p>O estado é mantido em {@link AtomicReference} — garante visibilidade e atomicidade das trocas,
 * sem o problema de {@code volatile} sobre tipo mutável (SonarQube {@code java:S3077}). O conteúdo
 * interno é sempre uma lista imutável ({@link List#copyOf}) e nunca é mutado após a publicação.
 *
 * <p>As operações CRUD são {@code synchronized} para serializar escritas e evitar condição de
 * corrida entre dois admins alterando simultaneamente. Leituras ({@link #getAll()}, {@link
 * #getById(String)}, etc.) não são sincronizadas — {@link AtomicReference#get()} é lock-free.
 *
 * <h2>Estratégia de persistência</h2>
 *
 * <ul>
 *   <li><b>Arquivo em disco</b> ({@code file:/app/config/reminders.json}) — persiste de verdade.
 *   <li><b>Classpath</b> ({@code classpath:reminders.json}) — carrega apenas para leitura;
 *       mutações atualizam memória mas não gravam (útil em dev/testes).
 *   <li><b>Path nulo/vazio</b> — mesmo comportamento do classpath, mas loga warning explícito.
 * </ul>
 *
 * <p>Escrita atômica: grava em {@code reminders.json.tmp} e move via {@link
 * StandardCopyOption#ATOMIC_MOVE}. Se o processo cair no meio, o arquivo original permanece
 * intacto (nunca corrompido).
 *
 * <h2>Listener de reload</h2>
 *
 * <p>Após cada mutação, este serviço chama o {@link Runnable} registrado via {@link
 * #setReloadListener(Runnable)}. O {@link ReminderScheduler} usa esse gancho para cancelar e
 * reagendar todas as tarefas — desacoplando o registry do scheduler (evita dependência circular).
 */
@Slf4j
@Service
public class ReminderService {

    /** Nome do arquivo de configuração para o {@link JsonConfigLoader}. */
    private static final String CONFIG_FILE_NAME = "reminders.json";

    private final JsonConfigLoader jsonConfigLoader;
    private final ObjectMapper objectMapper;

    /** Path do arquivo (property-driven; aceita {@code file:} ou {@code classpath:}). */
    @Value("${reminders.data.file:classpath:reminders.json}")
    private String remindersFile;

    /** Estado atual — lista imutável trocada atomicamente. */
    private final AtomicReference<List<Reminder>> remindersRef = new AtomicReference<>(List.of());

    /** Callback opcional para o scheduler reagendar (evita dependência circular). */
    private final AtomicReference<Runnable> reloadListener = new AtomicReference<>(() -> {});

    public ReminderService(JsonConfigLoader jsonConfigLoader, ObjectMapper objectMapper) {
        this.jsonConfigLoader = jsonConfigLoader;
        this.objectMapper = objectMapper;
    }

    // =========================================================================
    // CICLO DE VIDA
    // =========================================================================

    /** Carrega o arquivo no boot. Falhas não impedem o boot (lista vazia + warning). */
    @PostConstruct
    public void init() {
        reload();
    }

    /**
     * Registra um callback para ser invocado após cada mutação (create/update/delete/toggle) e após
     * {@link #reload()}.
     *
     * <p>O {@link ReminderScheduler} usa este gancho para reagendar todos os reminders. Se o
     * callback lançar, o erro é logado mas a mutação é considerada bem-sucedida (o arquivo já foi
     * gravado).
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
    // LEITURA (lock-free)
    // =========================================================================

    /** Retorna todos os reminders (imutável, nunca {@code null}). */
    public List<Reminder> getAll() {
        return remindersRef.get();
    }

    /** Retorna apenas os reminders habilitados. */
    @SuppressWarnings("null")
    public List<Reminder> getEnabled() {
        return remindersRef.get().stream().filter(Reminder::enabled).toList();
    }

    /** Busca por ID; {@link Optional#empty()} se não existir. */
    public Optional<Reminder> getById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return remindersRef.get().stream().filter(r -> id.equals(r.id())).findFirst();
    }

    /** Retorna o total de reminders (habilitados + desabilitados). */
    public int count() {
        return remindersRef.get().size();
    }

    // =========================================================================
    // CRUD (synchronized — serializa escritas)
    // =========================================================================

    /**
     * Cria um novo reminder.
     *
     * @param reminder record já validado
     * @return o reminder criado
     * @throws IllegalArgumentException se já existir um reminder com o mesmo {@code id}
     */
    public synchronized Reminder create(Reminder reminder) {
        Objects.requireNonNull(reminder, "reminder não pode ser nulo");

        List<Reminder> current = remindersRef.get();
        if (current.stream().anyMatch(r -> r.id().equals(reminder.id()))) {
            throw new IllegalArgumentException("Reminder com id '" + reminder.id() + "' já existe");
        }

        List<Reminder> updated = new ArrayList<>(current);
        updated.add(reminder);
        remindersRef.set(List.copyOf(updated));

        log.info("📢 Reminder criado: id='{}' cron='{}'", reminder.id(), reminder.cron());
        persistAndNotify();
        return reminder;
    }

    /**
     * Atualiza um reminder existente (substitui todos os campos exceto {@code id}).
     *
     * <p>O {@code id} do record recebido deve ser igual ao {@code id} do path; caso contrário, a
     * operação falha com {@link IllegalArgumentException} para evitar renames implícitos.
     *
     * @param id identificador do reminder a atualizar
     * @param reminder novo conteúdo
     * @return o reminder atualizado
     * @throws IllegalArgumentException se não existir ou se {@code id} divergir
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

        List<Reminder> current = remindersRef.get();
        boolean exists = current.stream().anyMatch(r -> r.id().equals(id));
        if (!exists) {
            throw new IllegalArgumentException("Reminder '" + id + "' não encontrado");
        }

        List<Reminder> updated =
                current.stream().map(r -> r.id().equals(id) ? reminder : r).toList();
        remindersRef.set(updated);

        log.info("📢 Reminder atualizado: id='{}' cron='{}'", reminder.id(), reminder.cron());
        persistAndNotify();
        return reminder;
    }

    /**
     * Alterna {@code enabled} de um reminder, preservando todos os outros campos.
     *
     * @param id identificador
     * @param enabled novo valor
     * @return o reminder atualizado
     * @throws IllegalArgumentException se não existir
     */
    public synchronized Reminder toggle(String id, boolean enabled) {
        Reminder current =
                getById(id)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Reminder '" + id + "' não encontrado"));

        Reminder updated = current.withEnabled(enabled);
        return update(id, updated);
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
        List<Reminder> current = remindersRef.get();
        List<Reminder> updated = current.stream().filter(r -> !r.id().equals(id)).toList();
        if (updated.size() == current.size()) {
            log.warn("⚠️ Tentativa de remover reminder inexistente: id='{}'", id);
            return false;
        }

        remindersRef.set(updated);
        log.info("🗑️ Reminder removido: id='{}'", id);
        persistAndNotify();
        return true;
    }

    // =========================================================================
    // RELOAD / PERSISTÊNCIA
    // =========================================================================

    /**
     * Recarrega o arquivo de configuração, substituindo o estado em memória.
     *
     * <p>Em caso de falha (arquivo ausente, JSON inválido), mantém o estado anterior e loga
     * warning — o bot continua funcional com a última configuração válida.
     */
    public synchronized void reload() {
        try {
            Optional<ReminderJson> loaded =
                    jsonConfigLoader.loadConfig(
                            CONFIG_FILE_NAME,
                            ReminderJson.class,
                            (remindersFile == null || remindersFile.isBlank())
                                    ? null
                                    : remindersFile);

            if (loaded.isEmpty()) {
                log.warn(
                        "⚠️ {} não encontrado em nenhum local — iniciando com lista vazia",
                        CONFIG_FILE_NAME);
                remindersRef.set(List.of());
                notifyReload();
                return;
            }

            List<Reminder> reminders = loaded.get().reminders();
            remindersRef.set(reminders);
            log.info("✅ {} reminders carregados de {}", reminders.size(), remindersFile);
            notifyReload();
        } catch (Exception e) {
            log.error(
                    "❌ Falha ao carregar {} — mantendo os {} reminders anteriores",
                    CONFIG_FILE_NAME,
                    remindersRef.get().size(),
                    e);
        }
    }

    /**
     * Persiste o estado atual em disco e notifica o listener.
     *
     * <p>Se o arquivo está no classpath (ou o path é nulo/vazio), apenas notifica e loga warning —
     * a mutação vale só em memória e se perde no próximo restart.
     */
    private void persistAndNotify() {
        persistToDisk();
        notifyReload();
    }

    private void persistToDisk() {
        if (!isPersistable()) {
            log.warn(
                    "⚠️ Mutações não serão persistidas (path='{}'). Configure "
                            + "'reminders.data.file' para um arquivo em disco (ex.: "
                            + "'file:/app/config/reminders.json').",
                    remindersFile);
            return;
        }

        Path path = toPath(remindersFile);
        try {
            Files.createDirectories(path.getParent());

            ReminderJson json = ReminderJson.of(remindersRef.get());
            String content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);

            // Escrita atômica: grava em .tmp e move
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(tmp, content);
            Files.move(
                    tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            log.info(
                    "💾 {} reminders persistidos em {} (updatedAt={})",
                    remindersRef.get().size(),
                    path,
                    json.updatedAt());
        } catch (IOException | JacksonException e) {
            log.error("❌ Falha ao persistir reminders em {}", path, e);
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /** Retorna {@code true} se o path atual permite persistência (file:, não classpath). */
    private boolean isPersistable() {
        return remindersFile != null
                && !remindersFile.isBlank()
                && !remindersFile.startsWith("classpath:");
    }

    /** Converte o path configurado (com ou sem prefixo {@code file:}) em {@link Path}. */
    private static Path toPath(String uriOrPath) {
        String clean =
                uriOrPath.startsWith("file:") ? uriOrPath.substring("file:".length()) : uriOrPath;
        return Paths.get(clean).toAbsolutePath().normalize();
    }

    /**
     * Exposto para testes: retorna o timestamp da última persistência bem-sucedida (ou {@code
     * null} se nunca persistiu).
     *
     * <p>Não usado em produção — apenas para diagnósticos em testes de integração.
     */
    Instant lastPersistedAt() {
        if (!isPersistable()) {
            return null;
        }
        Path path = toPath(remindersFile);
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            return null;
        }
    }
}
