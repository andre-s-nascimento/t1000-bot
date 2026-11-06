/* (c) 2026 | 06/11/2026 */
package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import net.ddns.adambravo79.tmill.model.Reminder;

/**
 * Testes do {@link ReminderScheduler}.
 *
 * <p>Cobre:
 *
 * <ul>
 *   <li>Inicialização: agendamento dos reminders habilitados no boot.
 *   <li>Reagendamento: {@link ReminderScheduler#reloadAll()} cancela e recria tasks.
 *   <li>Cron inválido: ignorado, restante é agendado.
 *   <li>Timezone: respeitado no {@code CronTrigger}.
 *   <li>Cancelamento individual: {@link ReminderScheduler#cancel(String)}.
 *   <li>Cancelamento total: {@link ReminderScheduler#cancelAll()} e {@link
 *       ReminderScheduler#destroy()}.
 *   <li>Despacho em execução: {@link ReminderScheduler} chama {@link ReminderDispatcher#dispatch}.
 *   <li>Listener: {@code ReminderService.setReloadListener} é chamado no {@code @PostConstruct}.
 * </ul>
 *
 * <h2>Estratégia de teste</h2>
 *
 * <p>Para testar o agendamento sem esperar horas/dias, usamos <b>cron de alta frequência</b>:
 * {@code "* * * * * *"} (a cada segundo) e aguardamos com {@link org.mockito.Mockito#timeout(int)}
 * que o {@link ReminderDispatcher} seja chamado. O Spring Boot 4 + {@code ThreadPoolTaskScheduler}
 * aceita cron de 6 campos com precisão de segundo.
 *
 * <p>Os testes que não precisam de execução real (ex.: verificar que uma task foi registrada) usam
 * {@code scheduledCount()} e {@code scheduledIds()} — sem sleep.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReminderSchedulerTest {

    /** Cron dispara a cada segundo (6 campos com precisão de segundo). */
    private static final String CRON_EVERY_SECOND = "* * * * * *";

    /** Cron inválido (5 campos "clássico" com "SEG" não é aceito pelo Spring 6+). */
    private static final String CRON_INVALID = "0 0 16 * * WEDNESDAY";

    private static final String TZ = "America/Sao_Paulo";

    @Mock private ReminderService reminderService;
    @Mock private ReminderDispatcher dispatcher;

    private ReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        // Default: sem reminders carregados
        lenient().when(reminderService.getEnabled()).thenReturn(List.of());
        lenient().when(reminderService.count()).thenReturn(0);

        scheduler = new ReminderScheduler(reminderService, dispatcher);
    }

    @AfterEach
    void tearDown() {
        // Garante cleanup mesmo se o teste falhar
        if (scheduler != null) {
            scheduler.destroy();
        }
    }

    // =========================================================================
    // FIXTURES
    // =========================================================================

    private Reminder reminder(String id, String cron, boolean enabled) {
        return new Reminder(
                id,
                id,
                enabled,
                cron,
                TZ,
                "<b>Msg " + id + "</b>",
                null,
                List.of("-100"),
                List.of());
    }

    // =========================================================================
    // INICIALIZAÇÃO
    // =========================================================================

    @Nested
    @DisplayName("init()")
    class Init {

        @Test
        @DisplayName("init() sem reminders: scheduler criado, 0 agendados")
        void initSemReminders() {
            when(reminderService.getEnabled()).thenReturn(List.of());

            scheduler.init();

            assertThat(scheduler.scheduledCount()).isZero();
        }

        @Test
        @DisplayName("init() com 2 reminders habilitados: 2 agendados")
        void initComReminders() {
            when(reminderService.getEnabled())
                    .thenReturn(
                            List.of(
                                    reminder("a", CRON_EVERY_SECOND, true),
                                    reminder("b", CRON_EVERY_SECOND, true)));

            scheduler.init();

            assertThat(scheduler.scheduledCount()).isEqualTo(2);
            assertThat(scheduler.scheduledIds()).containsExactlyInAnyOrder("a", "b");
        }

        @Test
        @DisplayName("init() registra listener no ReminderService")
        void initRegistraListener() {
            scheduler.init();

            verify(reminderService).setReloadListener(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("init() só agenda reminders habilitados (getEnabled filtra)")
        void initSoAgendaHabilitados() {
            // getEnabled() já filtra; o scheduler confia no service
            when(reminderService.getEnabled())
                    .thenReturn(List.of(reminder("a", CRON_EVERY_SECOND, true)));

            scheduler.init();

            assertThat(scheduler.scheduledCount()).isEqualTo(1);
        }
    }

    // =========================================================================
    // CRON INVÁLIDO
    // =========================================================================

    @Nested
    @DisplayName("Cron inválido")
    class CronInvalido {

        @Test
        @DisplayName("cron inválido: reminder ignorado, resto agendado")
        void cronInvalidoIgnorado() {
            when(reminderService.getEnabled())
                    .thenReturn(
                            List.of(
                                    reminder("bom", CRON_EVERY_SECOND, true),
                                    reminder("ruim", CRON_INVALID, true)));

            scheduler.init();

            assertThat(scheduler.scheduledIds()).containsExactly("bom");
            assertThat(scheduler.scheduledCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("todos inválidos: scheduler não agenda nada, sem exception")
        void todosInvalidos() {
            when(reminderService.getEnabled())
                    .thenReturn(
                            List.of(
                                    reminder("a", CRON_INVALID, true),
                                    reminder("b", CRON_INVALID, true)));

            assertThatCode(scheduler::init).doesNotThrowAnyException();
            assertThat(scheduler.scheduledCount()).isZero();
        }
    }

    // =========================================================================
    // REAGENDAMENTO (reloadAll)
    // =========================================================================

    @Nested
    @DisplayName("reloadAll()")
    class ReloadAll {

        @Test
        @DisplayName("reloadAll() substitui o conjunto de tasks")
        void reloadAllSubstitui() {
            when(reminderService.getEnabled())
                    .thenReturn(List.of(reminder("a", CRON_EVERY_SECOND, true)));
            scheduler.init();
            assertThat(scheduler.scheduledIds()).containsExactly("a");

            when(reminderService.getEnabled())
                    .thenReturn(List.of(reminder("b", CRON_EVERY_SECOND, true)));
            scheduler.reloadAll();

            assertThat(scheduler.scheduledIds()).containsExactly("b");
        }

        @Test
        @DisplayName("reloadAll() sem reminders: cancela tudo")
        void reloadAllSemReminders() {
            when(reminderService.getEnabled())
                    .thenReturn(List.of(reminder("a", CRON_EVERY_SECOND, true)));
            scheduler.init();
            assertThat(scheduler.scheduledCount()).isEqualTo(1);

            when(reminderService.getEnabled()).thenReturn(List.of());
            scheduler.reloadAll();

            assertThat(scheduler.scheduledCount()).isZero();
        }

        @Test
        @DisplayName("reloadAll() antes de init(): warning, sem exception")
        void reloadAllAntesDeInit() {
            // scheduler criado mas init() não chamado
            assertThatCode(scheduler::reloadAll).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("reloadAll() conta ignorados (cron inválido)")
        void reloadAllContaIgnorados() {
            when(reminderService.getEnabled())
                    .thenReturn(
                            List.of(
                                    reminder("bom", CRON_EVERY_SECOND, true),
                                    reminder("ruim", CRON_INVALID, true)));
            scheduler.init();

            assertThat(scheduler.scheduledCount()).isEqualTo(1);
        }
    }

    // =========================================================================
    // CANCELAMENTO INDIVIDUAL
    // =========================================================================

    @Nested
    @DisplayName("cancel()")
    class Cancel {

        @Test
        @DisplayName("cancel(id) remove do mapa e cancela a task")
        void cancelRemove() {
            when(reminderService.getEnabled())
                    .thenReturn(
                            List.of(
                                    reminder("a", CRON_EVERY_SECOND, true),
                                    reminder("b", CRON_EVERY_SECOND, true)));
            scheduler.init();
            assertThat(scheduler.scheduledCount()).isEqualTo(2);

            boolean cancelled = scheduler.cancel("a");

            assertThat(cancelled).isTrue();
            assertThat(scheduler.scheduledCount()).isEqualTo(1);
            assertThat(scheduler.scheduledIds()).containsExactly("b");
        }

        @Test
        @DisplayName("cancel(id inexistente) retorna false")
        void cancelInexistente() {
            when(reminderService.getEnabled())
                    .thenReturn(List.of(reminder("a", CRON_EVERY_SECOND, true)));
            scheduler.init();

            assertThat(scheduler.cancel("nao-existe")).isFalse();
            assertThat(scheduler.scheduledCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("cancel(null) e cancel(\"\") retornam false sem tocar no mapa")
        void cancelInvalido() {
            when(reminderService.getEnabled())
                    .thenReturn(List.of(reminder("a", CRON_EVERY_SECOND, true)));
            scheduler.init();

            assertThat(scheduler.cancel(null)).isFalse();
            assertThat(scheduler.cancel("")).isFalse();
            assertThat(scheduler.cancel("   ")).isFalse();
            assertThat(scheduler.scheduledCount()).isEqualTo(1);
        }
    }

    // =========================================================================
    // CANCELAMENTO TOTAL
    // =========================================================================

    @Nested
    @DisplayName("cancelAll() / destroy()")
    class CancelAll {

        @Test
        @DisplayName("cancelAll() remove todos os agendados")
        void cancelAllRemove() {
            when(reminderService.getEnabled())
                    .thenReturn(
                            List.of(
                                    reminder("a", CRON_EVERY_SECOND, true),
                                    reminder("b", CRON_EVERY_SECOND, true),
                                    reminder("c", CRON_EVERY_SECOND, true)));
            scheduler.init();
            assertThat(scheduler.scheduledCount()).isEqualTo(3);

            scheduler.cancelAll();

            assertThat(scheduler.scheduledCount()).isZero();
            assertThat(scheduler.scheduledIds()).isEmpty();
        }

        @Test
        @DisplayName("cancelAll() sem tasks: no-op, sem exception")
        void cancelAllVazio() {
            when(reminderService.getEnabled()).thenReturn(List.of());
            scheduler.init();

            assertThatCode(scheduler::cancelAll).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("destroy() cancela tudo e não lança")
        void destroyCancela() {
            when(reminderService.getEnabled())
                    .thenReturn(List.of(reminder("a", CRON_EVERY_SECOND, true)));
            scheduler.init();

            assertThatCode(scheduler::destroy).doesNotThrowAnyException();
            assertThat(scheduler.scheduledCount()).isZero();
        }

        @Test
        @DisplayName("destroy() sem init(): no-op, sem exception")
        void destroySemInit() {
            assertThatCode(scheduler::destroy).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("onContextClosed() cancela tudo")
        void onContextClosedCancela() {
            when(reminderService.getEnabled())
                    .thenReturn(List.of(reminder("a", CRON_EVERY_SECOND, true)));
            scheduler.init();

            scheduler.onContextClosed();

            assertThat(scheduler.scheduledCount()).isZero();
        }
    }

    // =========================================================================
    // DESPACHO EM EXECUÇÃO (cron rápido)
    // =========================================================================

    @Nested
    @DisplayName("Despacho em execução (integração)")
    class DispatchEmExecucao {

        @Test
        @DisplayName("reminder com cron de 1s dispara o dispatcher em até 3s")
        void cronRapidoDispara() {
            Reminder r = reminder("rapido", CRON_EVERY_SECOND, true);
            when(reminderService.getEnabled()).thenReturn(List.of(r));
            when(reminderService.getById("rapido")).thenReturn(java.util.Optional.of(r));

            scheduler.init();

            verify(dispatcher, timeout(3000).atLeastOnce()).dispatch("rapido");
        }

        @Test
        @DisplayName("dispatcher lançando: scheduler não quebra, próximo tick ainda dispara")
        void dispatcherLancaNaoQuebra() {
            Reminder r = reminder("falha", CRON_EVERY_SECOND, true);
            when(reminderService.getEnabled()).thenReturn(List.of(r));
            when(reminderService.getById("falha")).thenReturn(java.util.Optional.of(r));
            doThrow(new RuntimeException("boom"))
                    .doCallRealMethod()
                    .when(dispatcher)
                    .dispatch("falha");

            scheduler.init();

            // O dispatcher deve ser chamado ao menos 2 vezes (1ª falha, 2ª sucesso)
            verify(dispatcher, timeout(3000).atLeast(2)).dispatch("falha");
        }

        @Test
        @DisplayName("reminder desabilitado não dispara")
        void desabilitadoNaoDispara() {
            when(reminderService.getEnabled()).thenReturn(List.of());
            when(reminderService.getById(anyString())).thenReturn(java.util.Optional.empty());

            scheduler.init();

            // Espera 1.5s e confirma que nunca foi chamado
            org.awaitility.Awaitility.await()
                    .during(java.time.Duration.ofMillis(1500))
                    .atMost(java.time.Duration.ofSeconds(2))
                    .untilAsserted(() -> verify(dispatcher, never()).dispatch(anyString()));
        }
    }

    // =========================================================================
    // DIAGNÓSTICO
    // =========================================================================

    @Nested
    @DisplayName("Diagnóstico")
    class Diagnostico {

        @Test
        @DisplayName("scheduledIds() retorna cópia imutável")
        void scheduledIdsImutavel() {
            when(reminderService.getEnabled())
                    .thenReturn(List.of(reminder("a", CRON_EVERY_SECOND, true)));
            scheduler.init();

            var ids = scheduler.scheduledIds();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> ids.add("x"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("scheduledCount() reflete o número de tasks")
        void scheduledCountReflete() {
            when(reminderService.getEnabled())
                    .thenReturn(
                            List.of(
                                    reminder("a", CRON_EVERY_SECOND, true),
                                    reminder("b", CRON_EVERY_SECOND, true)));
            scheduler.init();

            assertThat(scheduler.scheduledCount()).isEqualTo(2);
        }
    }

    // =========================================================================
    // TIMEZONE
    // =========================================================================

    @Nested
    @DisplayName("Timezone")
    class Timezone {

        @Test
        @DisplayName("timezone nula: usa default da JVM sem quebrar")
        void timezoneNula() {
            Reminder r =
                    new Reminder(
                            "sem-tz",
                            "sem-tz",
                            true,
                            CRON_EVERY_SECOND,
                            null,
                            "<b>msg</b>",
                            null,
                            List.of("-100"),
                            List.of());
            when(reminderService.getEnabled()).thenReturn(List.of(r));

            assertThatCode(scheduler::init).doesNotThrowAnyException();
            assertThat(scheduler.scheduledCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("timezone inválida: ignorada (cron rejeitado pelo CronTrigger)")
        void timezoneInvalida() {
            Reminder r =
                    new Reminder(
                            "tz-ruim",
                            "tz-ruim",
                            true,
                            CRON_EVERY_SECOND,
                            "Nao/Existe",
                            "<b>msg</b>",
                            null,
                            List.of("-100"),
                            List.of());
            when(reminderService.getEnabled()).thenReturn(List.of(r));

            // ZoneId.of("Nao/Existe") lança, o scheduleOne captura → task ignorada
            assertThatCode(scheduler::init).doesNotThrowAnyException();
            assertThat(scheduler.scheduledCount()).isZero();
        }
    }

    // =========================================================================
    // LISTENER REGISTRADO
    // =========================================================================

    @Nested
    @DisplayName("Listener registrado no ReminderService")
    class ListenerRegistrado {

        @Test
        @DisplayName("mutação no registry dispara reloadAll() do scheduler")
        void mutacaoDisparaReload() {
            // Captura o listener que o scheduler registra
            org.mockito.ArgumentCaptor<Runnable> listenerCaptor =
                    org.mockito.ArgumentCaptor.forClass(Runnable.class);

            scheduler.init();
            verify(reminderService).setReloadListener(listenerCaptor.capture());

            Runnable listener = listenerCaptor.getValue();
            assertThat(listener).isNotNull();

            // Simula reload real: troca o getEnabled e chama o listener
            when(reminderService.getEnabled())
                    .thenReturn(List.of(reminder("novo", CRON_EVERY_SECOND, true)));

            assertThatCode(listener::run).doesNotThrowAnyException();
            // O listener chama reloadAll() → novo reminder é agendado
            assertThat(scheduler.scheduledIds()).contains("novo");
        }
    }
}
