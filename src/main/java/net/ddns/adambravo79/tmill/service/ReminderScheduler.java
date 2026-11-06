/* (c) 2026 | 06/11/2026 */
package net.ddns.adambravo79.tmill.service;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.model.Reminder;

/**
 * Agendador dinâmico de reminders.
 *
 * <p><b>Por que não {@code SchedulingConfigurer}?</b> O {@code configureTasks()} do Spring só é
 * chamado <b>uma vez</b> no boot. Como o admin pode criar/editar/deletar reminders em runtime
 * (via {@code /admin/reminders}), precisamos de um agendador que possa ser reconstruído a qualquer
 * momento. Por isso usamos {@link ThreadPoolTaskScheduler} + {@link ScheduledFuture} gerenciados
 * manualmente.
 *
 * <h2>Fluxo de vida</h2>
 *
 * <ol>
 *   <li>{@link #init()} — registra-se como listener do {@link ReminderService} e agenda tudo.
 *   <li>Quando o admin muta um reminder, o {@link ReminderService} chama {@link #reloadAll()}.
 *   <li>{@link #reloadAll()} cancela todos os {@code ScheduledFuture} antigos e reagenda a partir
 *       do estado atual do registry.
 *   <li>{@link #destroy()} — cancela tudo no shutdown do Spring.
 * </ol>
 *
 * <h2>Thread-safety</h2>
 *
 * <p>O mapa de {@code ScheduledFuture} é um {@link ConcurrentHashMap}. Os métodos {@link
 * #reloadAll()} e {@link #cancelAll()} são {@code synchronized} para serializar operações de
 * reagendamento (evita corrida entre dois reloads concorrentes). O {@link ThreadPoolTaskScheduler}
 * já é thread-safe internamente.
 *
 * <h2>Validação de cron</h2>
 *
 * <p>Antes de agendar, validamos a expressão com {@link CronExpression#parse(CharSequence)}.
 * Expressões inválidas são logadas e <b>ignoradas</b> — o resto dos reminders continua agendado.
 * Isso evita que um único reminder mal configurado derrube o agendador inteiro.
 *
 * <h2>Pool de threads</h2>
 *
 * <p>Configuramos um pool de {@code poolSize = 2} threads: suficiente para dezenas de reminders
 * (disparos são curtos — apenas chamam o {@link ReminderDispatcher} que envia mensagem para o
 * Telegram). Não usamos virtual threads aqui porque {@link ThreadPoolTaskScheduler} ainda não tem
 * integração nativa com elas no Spring Boot 4.
 */
@Slf4j
@Component
public class ReminderScheduler {

    /** Tamanho do pool — suficiente para centenas de reminders com disparos curtos. */
    private static final int POOL_SIZE = 2;

    /** Prefixo das threads — facilita identificação em thread dumps. */
    private static final String THREAD_PREFIX = "reminder-sched-";

    /** Delay do shutdown até cancelar tudo (evita disparos em voo travarem o shutdown). */
    private static final int SHUTDOWN_WAIT_SECONDS = 5;

    private final ReminderService reminderService;
    private final ReminderDispatcher dispatcher;

    /** Mapa {@code reminderId -> ScheduledFuture} para permitir cancelamento individual. */
    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    /** Scheduler do Spring — pool fixo, thread-safe, com graceful shutdown. */
    private ThreadPoolTaskScheduler taskScheduler;

    public ReminderScheduler(ReminderService reminderService, ReminderDispatcher dispatcher) {
        this.reminderService = reminderService;
        this.dispatcher = dispatcher;
    }

    // =========================================================================
    // CICLO DE VIDA
    // =========================================================================

    @PostConstruct
    public void init() {
        // 1. Configura o scheduler
        this.taskScheduler = new ThreadPoolTaskScheduler();
        this.taskScheduler.setPoolSize(POOL_SIZE);
        this.taskScheduler.setThreadNamePrefix(THREAD_PREFIX);
        this.taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        this.taskScheduler.setAwaitTerminationSeconds(SHUTDOWN_WAIT_SECONDS);
        this.taskScheduler.setRemoveOnCancelPolicy(true);
        this.taskScheduler.initialize();

        // 2. Registra-se como listener do registry (evita dependência circular)
        reminderService.setReloadListener(this::reloadAll);

        // 3. Agenda tudo que já está carregado no registry
        reloadAll();

        log.info(
                "✅ ReminderScheduler inicializado (pool={}, reminders={})",
                POOL_SIZE,
                futures.size());
    }

    @PreDestroy
    public void destroy() {
        log.info("🛑 ReminderScheduler: cancelando {} reminders agendados", futures.size());
        cancelAll();
        if (taskScheduler != null) {
            taskScheduler.shutdown();
        }
    }

    /**
     * Hook extra para {@link ContextClosedEvent} — garante cancelamento mesmo se {@link
     * PreDestroy} não rodar (ex.: shutdown abrupto).
     */
    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        cancelAll();
    }

    // =========================================================================
    // REAGENDAMENTO
    // =========================================================================

    /**
     * Cancela todos os {@link ScheduledFuture} atuais e reagenda a partir do estado atual do
     * {@link ReminderService}.
     *
     * <p>Chamado:
     *
     * <ul>
     *   <li>No {@link #init()} (boot).
     *   <li>Após cada mutação no {@link ReminderService} (create/update/delete/toggle/reload).
     *   <li>Manualmente pelo endpoint {@code POST /admin/reminders/reload}.
     * </ul>
     *
     * <p>É {@code synchronized} para evitar que dois reloads concorrentes criem {@code
     * ScheduledFuture} duplicados para o mesmo reminder.
     */
    public synchronized void reloadAll() {
        if (taskScheduler == null) {
            log.warn("⚠️ reloadAll() chamado antes de init() — ignorando");
            return;
        }

        cancelAll();

        Map<String, ScheduledFuture<?>> newFutures = new LinkedHashMap<>();
        int agendados = 0;
        int ignorados = 0;

        for (Reminder reminder : reminderService.getEnabled()) {
            ScheduledFuture<?> future = scheduleOne(reminder);
            if (future != null) {
                newFutures.put(reminder.id(), future);
                agendados++;
            } else {
                ignorados++;
            }
        }

        futures.putAll(newFutures);

        log.info(
                "📅 ReminderScheduler: {} agendados, {} ignorados (total no registry: {})",
                agendados,
                ignorados,
                reminderService.count());
    }

    /**
     * Agenda um único reminder.
     *
     * @param reminder record já validado (id, cron, timezone)
     * @return {@link ScheduledFuture} ou {@code null} se o cron for inválido
     */
    private ScheduledFuture<?> scheduleOne(Reminder reminder) {
        try {
            // Valida o cron antes de agendar (falha rápido, não derruba o scheduler)
            CronExpression.parse(reminder.cron());

            ZoneId zone = ZoneId.of(reminder.effectiveTimezone());
            CronTrigger trigger = new CronTrigger(reminder.cron(), zone);

            ScheduledFuture<?> future =
                    taskScheduler.schedule(() -> dispatchSafe(reminder.id()), trigger);

            if (future == null) {
                log.warn(
                        "⚠️ Falha ao agendar reminder '{}' (cron='{}'): taskScheduler retornou"
                                + " null",
                        reminder.id(),
                        reminder.cron());
                return null;
            }

            log.info(
                    "✅ Reminder '{}' agendado: cron='{}' zone='{}' chats={}",
                    reminder.id(),
                    reminder.cron(),
                    zone,
                    reminder.targetChatIds().size());
            return future;

        } catch (IllegalArgumentException e) {
            log.error(
                    "❌ Cron inválido no reminder '{}': '{}' — ignorado ({})",
                    reminder.id(),
                    reminder.cron(),
                    e.getMessage());
            return null;

        } catch (RuntimeException e) {
            log.error("❌ Falha inesperada ao agendar reminder '{}' — ignorado", reminder.id(), e);
            return null;
        }
    }

    /**
     * Envolve a chamada ao {@link ReminderDispatcher} em try/catch, para que uma falha de envio não
     * interrompa os próximos disparos.
     */
    private void dispatchSafe(String reminderId) {
        try {
            dispatcher.dispatch(reminderId);
        } catch (RuntimeException e) {
            log.error(
                    "❌ Falha ao disparar reminder '{}' (será tentado novamente no próximo cron)",
                    reminderId,
                    e);
        }
    }

    // =========================================================================
    // CANCELAMENTO
    // =========================================================================

    /**
     * Cancela todos os {@link ScheduledFuture} agendados.
     *
     * <p>O {@code false} em {@link ScheduledFuture#cancel(boolean)} significa "não interromper se
     * estiver rodando". Um disparo em voo (envio para o Telegram) termina normalmente; só o próximo
     * disparo é cancelado.
     */
    public synchronized void cancelAll() {
        if (futures.isEmpty()) {
            return;
        }
        int cancelados = 0;
        for (ScheduledFuture<?> future : futures.values()) {
            if (future != null && future.cancel(false)) {
                cancelados++;
            }
        }
        futures.clear();
        if (cancelados > 0) {
            log.info("🛑 ReminderScheduler: {} tasks canceladas", cancelados);
        }
    }

    /**
     * Cancela um reminder específico.
     *
     * @param reminderId identificador
     * @return {@code true} se cancelou, {@code false} se não estava agendado
     */
    public boolean cancel(String reminderId) {
        if (reminderId == null || reminderId.isBlank()) {
            return false;
        }
        ScheduledFuture<?> future = futures.remove(reminderId);
        if (future == null) {
            return false;
        }
        boolean cancelled = future.cancel(false);
        if (cancelled) {
            log.info("🛑 Reminder '{}' cancelado", reminderId);
        }
        return cancelled;
    }

    // =========================================================================
    // DIAGNÓSTICO
    // =========================================================================

    /** Retorna o número de reminders atualmente agendados. */
    public int scheduledCount() {
        return futures.size();
    }

    /**
     * Retorna um snapshot dos IDs agendados (imutável, útil para o endpoint de status).
     *
     * @return conjunto imutável de IDs (nunca {@code null})
     */
    public java.util.Set<String> scheduledIds() {
        return java.util.Set.copyOf(futures.keySet());
    }
}
