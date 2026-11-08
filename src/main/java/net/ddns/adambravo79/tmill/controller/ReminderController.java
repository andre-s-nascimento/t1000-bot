/* (c) 2026 | 06/11/2026 */
package net.ddns.adambravo79.tmill.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.dto.ReminderJson;
import net.ddns.adambravo79.tmill.model.Reminder;
import net.ddns.adambravo79.tmill.service.ReminderDispatcher;
import net.ddns.adambravo79.tmill.service.ReminderScheduler;
import net.ddns.adambravo79.tmill.service.ReminderService;
import net.ddns.adambravo79.tmill.util.LogSanitizer;

/**
 * API REST para CRUD de reminders.
 *
 * <p><b>Endpoints:</b>
 *
 * <ul>
 *   <li>{@code GET    /admin/reminders}              — lista todos
 *   <li>{@code GET    /admin/reminders/{id}}         — busca por ID
 *   <li>{@code POST   /admin/reminders}              — cria novo
 *   <li>{@code PUT    /admin/reminders/{id}}         — substitui existente
 *   <li>{@code PATCH  /admin/reminders/{id}/toggle}  — alterna enabled
 *   <li>{@code DELETE /admin/reminders/{id}}         — remove
 *   <li>{@code POST   /admin/reminders/{id}/test}    — dispara manualmente (teste)
 *   <li>{@code POST   /admin/reminders/reload}       — recarrega do disco
 *   <li>{@code GET    /admin/reminders/status}       — diagnóstico (schedulados vs. registry)
 *   <li>{@code GET    /admin/reminders/export}       — exporta todos como {@link ReminderJson}
 *   <li>{@code POST   /admin/reminders/import}       — importa um {@link ReminderJson}
 * </ul>
 *
 * <h2>Códigos HTTP</h2>
 *
 * <ul>
 *   <li>{@code 200} — OK (GET, PUT, PATCH, POST test/reload/import)
 *   <li>{@code 201} — Created (POST novo)
 *   <li>{@code 204} — No Content (DELETE)
 *   <li>{@code 400} — Body inválido, cron inválido, ID divergente
 *   <li>{@code 404} — Reminder não encontrado
 *   <li>{@code 409} — Conflito (ID duplicado no POST, conflito de versão no PUT/PATCH)
 *   <li>{@code 500} — Erro inesperado
 * </ul>
 *
 * <h2>Validação de cron</h2>
 *
 * <p>Antes de delegar ao {@link ReminderService}, validamos a expressão cron com {@link
 * CronExpression#parse(CharSequence)}. Isso falha rápido com {@code 400} em vez de deixar o
 * scheduler descobrir o problema depois (o scheduler já ignora cron inválido, mas o admin
 * merece um erro explícito).
 *
 * <h2>Optimistic locking (PUT / PATCH)</h2>
 *
 * <p>O body de {@code PUT} deve conter {@code version} igual à versão atual do reminder
 * (lida via {@code GET /admin/reminders/{id}}). Se outra instância alterou o reminder entre a
 * leitura e a escrita, o {@link ReminderService} relança como {@link IllegalStateException},
 * que mapeamos para {@code 409 Conflict}. O admin deve reler e reenviar.
 *
 * <p>Para {@code PATCH /{id}/toggle}, o service lê a versão atual do banco e usa — o cliente
 * não precisa enviar {@code version} (o toggle é atômico: read + update na mesma transação).
 *
 * <h2>Separação de responsabilidades</h2>
 *
 * <p>Este controller <b>não</b> persiste nem agenda diretamente:
 *
 * <ul>
 *   <li>{@link ReminderService} — CRUD + persistência + notificação de reload.
 *   <li>{@link ReminderScheduler} — reagendamento (via listener registrado no service).
 *   <li>{@link ReminderDispatcher} — envio efetivo.
 * </ul>
 *
 * <p>O controller é uma camada fina de validação + mapeamento HTTP.
 */
@Slf4j
@RestController
@RequestMapping("/admin/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private static final String BODY_NAO_PODE_SER_NULO = "Body não pode ser nulo.";
    private static final String ERR_ID_OBRIGATORIO = "Parâmetro 'id' é obrigatório.";
    private static final String ERR_ID_DIVERGENTE = "O id do body diverge do id do path.";
    private static final String ERR_CRON_INVALIDO = "Expressão cron inválida: ";
    private static final String ERR_REMINDER_NAO_ENCONTRADO = "Reminder não encontrado: ";
    private static final String ERR_ERRO_INTERNO = "Erro interno. Verifique os logs do servidor.";
    private static final String ERR_CONFLITO_VERSAO =
            "Conflito de versão: o reminder foi modificado por outra instância. "
                    + "Recarregue e tente novamente.";
    private static final String KEY_ERRO = "erro";
    private static final String KEY_MENSAGEM = "mensagem";

    private final ReminderService reminderService;
    private final ReminderScheduler reminderScheduler;
    private final ReminderDispatcher reminderDispatcher;

    // =========================================================================
    // LIST / GET
    // =========================================================================

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Reminder>> list() {
        return ResponseEntity.ok(reminderService.getAll());
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getById(@PathVariable("id") String id) {
        if (id == null || id.isBlank()) {
            return badRequest(ERR_ID_OBRIGATORIO);
        }
        Optional<Reminder> reminder = reminderService.getById(id);
        if (reminder.isEmpty()) {
            return notFound(ERR_REMINDER_NAO_ENCONTRADO + id);
        }
        return ResponseEntity.ok(reminder.get());
    }

    // =========================================================================
    // CREATE / UPDATE
    // =========================================================================

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> create(@RequestBody Reminder reminder) {
        if (reminder == null) {
            return badRequest(BODY_NAO_PODE_SER_NULO);
        }

        String cronError = validateCron(reminder.cron());
        if (cronError != null) {
            return badRequest(cronError);
        }

        try {
            Reminder created = reminderService.create(reminder);
            log.info(
                    "📢 [REST] Reminder criado: id='{}' por admin",
                    LogSanitizer.sanitize(reminder.id()));
            return ResponseEntity.status(HttpStatus.CREATED).body(created);

        } catch (IllegalArgumentException e) {
            // ID duplicado → 409 Conflict
            log.warn("⚠️ [REST] Falha ao criar reminder: {}", e.getMessage());
            return conflict(e.getMessage());

        } catch (RuntimeException e) {
            log.error("❌ [REST] Erro inesperado ao criar reminder", e);
            return internalError();
        }
    }

    @PutMapping(
            value = "/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> update(
            @PathVariable("id") String id, @RequestBody Reminder reminder) {
        if (reminder == null) {
            return badRequest(BODY_NAO_PODE_SER_NULO);
        }
        if (id == null || id.isBlank()) {
            return badRequest(ERR_ID_OBRIGATORIO);
        }
        if (!id.equals(reminder.id())) {
            return badRequest(ERR_ID_DIVERGENTE);
        }

        String cronError = validateCron(reminder.cron());
        if (cronError != null) {
            return badRequest(cronError);
        }

        try {
            Reminder updated = reminderService.update(id, reminder);
            log.info(
                    "📢 [REST] Reminder atualizado: id='{}' (version={}) por admin",
                    LogSanitizer.sanitize(id),
                    updated.version());
            return ResponseEntity.ok(updated);

        } catch (IllegalStateException e) {
            // Conflito de versão (optimistic locking) → 409
            log.warn(
                    "⚠️ [REST] Conflito de versão no reminder '{}': {}",
                    LogSanitizer.sanitize(id),
                    e.getMessage());
            return conflict(ERR_CONFLITO_VERSAO);

        } catch (IllegalArgumentException e) {
            // Não encontrado → 404
            log.warn("⚠️ [REST] Falha ao atualizar reminder '{}': {}", id, e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("não encontrado")) {
                return notFound(e.getMessage());
            }
            return badRequest(e.getMessage());

        } catch (RuntimeException e) {
            log.error("❌ [REST] Erro inesperado ao atualizar reminder '{}'", id, e);
            return internalError();
        }
    }

    // =========================================================================
    // TOGGLE / DELETE
    // =========================================================================

    @PostMapping(value = "/{id}/toggle", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> toggle(
            @PathVariable("id") String id,
            @RequestParam(value = "enabled", required = false) Boolean enabled) {
        if (id == null || id.isBlank()) {
            return badRequest(ERR_ID_OBRIGATORIO);
        }

        try {
            Reminder current =
                    reminderService
                            .getById(id)
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    ERR_REMINDER_NAO_ENCONTRADO + id));

            // Se 'enabled' não for informado, alterna o valor atual
            boolean newValue = (enabled != null) ? enabled : !current.enabled();

            Reminder updated = reminderService.toggle(id, newValue);
            log.info(
                    "🔁 [REST] Reminder '{}' alternado para enabled={} por admin",
                    LogSanitizer.sanitize(id),
                    newValue);
            return ResponseEntity.ok(updated);

        } catch (IllegalStateException e) {
            // Conflito de versão (corrida entre toggle e outro update) → 409
            log.warn(
                    "⚠️ [REST] Conflito de versão no toggle do reminder '{}'",
                    LogSanitizer.sanitize(id),
                    e);
            return conflict(ERR_CONFLITO_VERSAO);

        } catch (IllegalArgumentException e) {
            log.warn("⚠️ [REST] Falha ao alternar reminder '{}': {}", id, e.getMessage());
            return notFound(e.getMessage());

        } catch (RuntimeException e) {
            log.error("❌ [REST] Erro inesperado ao alternar reminder '{}'", id, e);
            return internalError();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(@PathVariable("id") String id) {
        if (id == null || id.isBlank()) {
            return badRequest(ERR_ID_OBRIGATORIO);
        }

        try {
            boolean deleted = reminderService.delete(id);
            if (!deleted) {
                return notFound(ERR_REMINDER_NAO_ENCONTRADO + id);
            }
            log.info("🗑️ [REST] Reminder '{}' removido por admin", LogSanitizer.sanitize(id));
            return ResponseEntity.noContent().build();

        } catch (RuntimeException e) {
            log.error("❌ [REST] Erro inesperado ao remover reminder '{}'", id, e);
            return internalError();
        }
    }

    // =========================================================================
    // TEST / RELOAD / STATUS
    // =========================================================================

    /**
     * Dispara um reminder manualmente para um chat específico (teste).
     *
     * <p>{@code chatId} obrigatório — evita disparar acidentalmente para todos os chats do
     * reminder em produção.
     */
    @PostMapping(value = "/{id}/test", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> test(
            @PathVariable("id") String id, @RequestParam("chatId") long chatId) {
        if (id == null || id.isBlank()) {
            return badRequest(ERR_ID_OBRIGATORIO);
        }

        try {
            boolean enviado = reminderDispatcher.dispatchToChat(id, chatId);
            if (!enviado) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                                Map.of(
                                        KEY_ERRO,
                                        "Falha ao enviar reminder '"
                                                + id
                                                + "' para chat "
                                                + chatId));
            }
            log.info(
                    "🧪 [REST] Teste manual do reminder '{}' para chat {} concluído",
                    LogSanitizer.sanitize(id),
                    chatId);
            return ResponseEntity.ok(
                    Map.of(KEY_MENSAGEM, "Reminder '" + id + "' enviado para chat " + chatId));

        } catch (RuntimeException e) {
            log.error("❌ [REST] Erro inesperado no teste do reminder '{}'", id, e);
            return internalError();
        }
    }

    /** Recarrega o estado do banco e reagenda (força releitura + reagendamento). */
    @PostMapping(value = "/reload", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> reload() {
        try {
            reminderService.reload();
            log.info(
                    "🔄 [REST] Reminders recarregados do banco por admin (total={})",
                    reminderService.count());
            return ResponseEntity.ok(
                    Map.of(
                            KEY_MENSAGEM,
                            "Reminders recarregados: " + reminderService.count() + " no total",
                            "scheduled",
                            reminderScheduler.scheduledCount()));

        } catch (RuntimeException e) {
            log.error("❌ [REST] Erro inesperado ao recarregar reminders", e);
            return internalError();
        }
    }

    /**
     * Diagnóstico: quantos reminders estão no banco vs. quantos estão agendados.
     *
     * <p>Útil para identificar "reminder fantasma" (habilitado no banco mas não agendado por
     * cron inválido ou falha no scheduler).
     */
    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(
                Map.of(
                        "total", reminderService.count(),
                        "enabled", reminderService.getEnabled().size(),
                        "scheduled", reminderScheduler.scheduledCount(),
                        "scheduledIds", reminderScheduler.scheduledIds()));
    }

    // =========================================================================
    // EXPORT / IMPORT
    // =========================================================================

    /**
     * Exporta todos os reminders como {@link ReminderJson} (para backup, migração entre
     * ambientes ou versionamento em git).
     *
     * <p><b>Formato:</b> JSON pretty-printed, com {@code version}, {@code updatedAt} e a
     * lista de {@code reminders}. Cada reminder inclui o {@code version} interno do banco
     * (útil para diagnóstico, mas ignorado em reimport).
     *
     * <p><b>Content-Disposition:</b> sugere um nome de arquivo com timestamp UTC. O
     * browser respeita o filename; cURL/Postman só veem o header.
     */
    @GetMapping(value = "/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReminderJson> export() {
        List<Reminder> all = reminderService.getAll();
        ReminderJson json = ReminderJson.of(all);
        log.info("📤 [REST] Export de {} reminders solicitado por admin", all.size());

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"reminders-" + java.time.Instant.now() + ".json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    /**
     * Importa um {@link ReminderJson} (bootstrap, restore, migração entre ambientes).
     *
     * <p><b>Estratégia:</b>
     *
     * <ul>
     *   <li>Com {@code replace=false} (default): para cada reminder do lote, faz
     *       <b>upsert</b> (update se existe, insert se não). Reminders existentes no banco
     *       que <b>não</b> estão no lote são <b>preservados</b> (merge).
     *   <li>Com {@code replace=true}: idem, mas <b>remove</b> os reminders do banco que não
     *       estão no lote. Use para restore exato.
     * </ul>
     *
     * <p><b>Transação:</b> o lote inteiro roda em uma única transação. Se uma linha falhar,
     * nada é gravado (rollback total).
     *
     * <p><b>Optimistic locking:</b> ignorado no import — a versão do banco é usada
     * (o import é uma "sobrescrita autoritativa", o caller não está editando).
     *
     * @param json payload com a lista de reminders
     * @param replace se {@code true}, deleta os ausentes do lote
     * @return contagem de importados
     */
    @PostMapping(
            value = "/import",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> importReminders(
            @RequestBody ReminderJson json,
            @RequestParam(value = "replace", defaultValue = "false") boolean replace) {

        if (json == null) {
            return badRequest(BODY_NAO_PODE_SER_NULO);
        }
        if (json.reminders().isEmpty()) {
            return badRequest("Lista de reminders está vazia.");
        }

        try {
            int imported = reminderService.importAll(json.reminders(), replace);
            log.info("📥 [REST] Import de {} reminders (replace={}) por admin", imported, replace);
            return ResponseEntity.ok(
                    Map.of(
                            KEY_MENSAGEM,
                            imported + " reminders importados",
                            "replace",
                            replace,
                            "total",
                            reminderService.count()));

        } catch (IllegalArgumentException e) {
            log.warn("⚠️ [REST] Falha no import: {}", e.getMessage());
            return badRequest(e.getMessage());

        } catch (RuntimeException e) {
            log.error("❌ [REST] Erro inesperado no import", e);
            return internalError();
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /**
     * Valida a expressão cron usando o parser oficial do Spring.
     *
     * @return {@code null} se válido, ou a mensagem de erro
     */
    private static String validateCron(String cron) {
        if (cron == null || cron.isBlank()) {
            return ERR_CRON_INVALIDO + "(vazio)";
        }
        try {
            CronExpression.parse(cron);
            return null;
        } catch (IllegalArgumentException e) {
            return ERR_CRON_INVALIDO + e.getMessage();
        }
    }

    private static ResponseEntity<Object> badRequest(String message) {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(KEY_ERRO, message));
    }

    private static ResponseEntity<Object> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(KEY_ERRO, message));
    }

    private static ResponseEntity<Object> conflict(String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(KEY_ERRO, message));
    }

    private static ResponseEntity<Object> internalError() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(KEY_ERRO, ERR_ERRO_INTERNO));
    }
}
