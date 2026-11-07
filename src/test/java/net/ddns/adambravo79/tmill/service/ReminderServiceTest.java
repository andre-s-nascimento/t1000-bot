/* (c) 2026 | 06/11/2026 */
package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import net.ddns.adambravo79.tmill.model.Reminder;
import net.ddns.adambravo79.tmill.repository.ReminderRepository;

/**
 * Testes do {@link ReminderService}.
 *
 * <p>Cobre:
 *
 * <ul>
 *   <li>Leitura: {@code getAll}, {@code getEnabled}, {@code getById}, {@code count} —
 *       delegação direta ao repository.
 *   <li>CRUD: {@code create}, {@code update}, {@code toggle}, {@code delete}.
 *   <li>Optimistic locking: {@code update} com versão errada → {@code IllegalStateException}
 *       (o controller traduz para HTTP 409).
 *   <li>Importação: {@code importAll} (merge e replace), com transação.
 *   <li>Listener: {@code setReloadListener}, {@code notifyReload} (após cada mutação,
 *       sucesso e falha parcial).
 *   <li>{@code reload}: apenas notifica o listener (não relê nada, o estado vive no banco).
 * </ul>
 *
 * <p><b>Não cobre:</b> SQL do repository (coberto em {@code ReminderRepositoryTest}),
 * serialização JSON (coberto em {@code ReminderJsonTest}), endpoints (coberto em
 * {@code ReminderControllerTest}).
 */
@ExtendWith(MockitoExtension.class)
class ReminderServiceTest {

    private static final String VALID_CRON = "0 0 16 * * WED";
    private static final String VALID_TZ = "America/Sao_Paulo";

    @Mock private ReminderRepository repository;

    @InjectMocks private ReminderService service;

    @BeforeEach
    void setUp() {
        lenient().when(repository.findAll()).thenReturn(List.of());
        lenient().when(repository.findEnabled()).thenReturn(List.of());
        lenient().when(repository.count()).thenReturn(0);
    }

    // =========================================================================
    // FIXTURES
    // =========================================================================

    private Reminder reminder(String id, boolean enabled) {
        return new Reminder(
                id,
                "Nome " + id,
                enabled,
                VALID_CRON,
                VALID_TZ,
                "<b>Msg " + id + "</b>",
                null,
                List.of("-100123"),
                List.of("cinema"));
    }

    private Reminder reminderComVersion(String id, int version) {
        return new Reminder(
                id,
                "Nome " + id,
                true,
                VALID_CRON,
                VALID_TZ,
                "msg",
                null,
                List.of("-100123"),
                List.of(),
                version);
    }

    // =========================================================================
    // LEITURA
    // =========================================================================

    @Nested
    @DisplayName("Leitura")
    class Leitura {

        @Test
        @DisplayName("getAll delega ao repository.findAll()")
        void getAll() {
            List<Reminder> expected = List.of(reminder("a", true), reminder("b", false));
            when(repository.findAll()).thenReturn(expected);

            assertThat(service.getAll()).isEqualTo(expected);
            verify(repository).findAll();
        }

        @Test
        @DisplayName("getEnabled delega ao repository.findEnabled()")
        void getEnabled() {
            List<Reminder> expected = List.of(reminder("a", true));
            when(repository.findEnabled()).thenReturn(expected);

            assertThat(service.getEnabled()).isEqualTo(expected);
            verify(repository).findEnabled();
        }

        @Test
        @DisplayName("getById delega ao repository.findById()")
        void getById() {
            Reminder r = reminder("a", true);
            when(repository.findById("a")).thenReturn(Optional.of(r));

            assertThat(service.getById("a")).contains(r);
            verify(repository).findById("a");
        }

        @Test
        @DisplayName("getById com id inexistente: Optional.empty()")
        void getByIdInexistente() {
            when(repository.findById("x")).thenReturn(Optional.empty());
            assertThat(service.getById("x")).isEmpty();
        }

        @Test
        @DisplayName("count delega ao repository.count()")
        void count() {
            when(repository.count()).thenReturn(7);
            assertThat(service.count()).isEqualTo(7);
        }
    }

    // =========================================================================
    // CREATE
    // =========================================================================

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("cria com sucesso: chama save, retorna com version=1, notifica listener")
        void sucesso() {
            when(repository.existsById("novo")).thenReturn(false);
            AtomicInteger chamadas = new AtomicInteger();
            service.setReloadListener(chamadas::incrementAndGet);

            Reminder novo = reminder("novo", true);
            Reminder criado = service.create(novo);

            verify(repository).save(novo);
            assertThat(criado.version()).isEqualTo(1);
            assertThat(chamadas.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("id duplicado: IllegalArgumentException, sem save, sem listener")
        void idDuplicado() {
            when(repository.existsById("dup")).thenReturn(true);
            AtomicInteger chamadas = new AtomicInteger();
            service.setReloadListener(chamadas::incrementAndGet);

            Reminder dup = reminder("dup", true);

            assertThatThrownBy(() -> service.create(dup))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("já existe");

            verify(repository, never()).save(any());
            assertThat(chamadas.get()).isZero();
        }

        @Test
        @DisplayName("record nulo: NullPointerException")
        void recordNulo() {
            assertThatThrownBy(() -> service.create(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("não pode ser nulo");
        }

        @Test
        @DisplayName("record com version alta: força version=1 no retorno e no save")
        void forcaVersionUm() {
            when(repository.existsById("x")).thenReturn(false);
            Reminder comVersion99 = reminderComVersion("x", 99);

            Reminder criado = service.create(comVersion99);

            verify(repository).save(comVersion99);
            assertThat(criado.version()).isEqualTo(1);
        }
    }

    // =========================================================================
    // UPDATE
    // =========================================================================

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("sucesso: chama update, retorna com version+1, notifica listener")
        void sucesso() {
            AtomicInteger chamadas = new AtomicInteger();
            service.setReloadListener(chamadas::incrementAndGet);

            Reminder atualizado = reminderComVersion("a", 2);

            Reminder retorno = service.update("a", atualizado);

            verify(repository).update(atualizado, 2);
            assertThat(retorno.version()).isEqualTo(3);
            assertThat(chamadas.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("conflito de versão: IllegalStateException, sem listener")
        void conflitoDeVersao() {
            doThrow(new OptimisticLockingFailureException("conflito"))
                    .when(repository)
                    .update(any(Reminder.class), anyInt());

            AtomicInteger chamadas = new AtomicInteger();
            service.setReloadListener(chamadas::incrementAndGet);

            Reminder atualizado = reminderComVersion("a", 2);

            assertThatThrownBy(() -> service.update("a", atualizado))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("modificado por outra instância");

            assertThat(chamadas.get()).isZero();
        }

        @Test
        @DisplayName("id do path diverge do body: IllegalArgumentException")
        void idDivergente() {
            Reminder r = reminderComVersion("outro", 1);

            assertThatThrownBy(() -> service.update("path", r))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("diverge");

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("id do path nulo/vazio: IllegalArgumentException")
        void idPathInvalido() {
            Reminder r = reminderComVersion("a", 1);

            assertThatThrownBy(() -> service.update(null, r))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.update("", r))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.update("   ", r))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("record nulo: NullPointerException")
        void recordNulo() {
            assertThatThrownBy(() -> service.update("a", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // =========================================================================
    // TOGGLE
    // =========================================================================

    @Nested
    @DisplayName("toggle()")
    class Toggle {

        @Test
        @DisplayName("desabilita: chama getById + update, preserva campos, notifica listener")
        void desabilita() {
            Reminder atual = reminder("a", true);
            when(repository.findById("a")).thenReturn(Optional.of(atual));

            AtomicInteger chamadas = new AtomicInteger();
            service.setReloadListener(chamadas::incrementAndGet);

            Reminder retorno = service.toggle("a", false);

            ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
            verify(repository).update(captor.capture(), eq(1));

            Reminder passado = captor.getValue();
            assertThat(passado.enabled()).isFalse();
            assertThat(passado.id()).isEqualTo("a");
            assertThat(passado.cron()).isEqualTo(atual.cron());
            assertThat(passado.message()).isEqualTo(atual.message());

            assertThat(retorno.enabled()).isFalse();
            assertThat(chamadas.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("habilita: inverte para true")
        void habilita() {
            Reminder atual = reminder("a", false);
            when(repository.findById("a")).thenReturn(Optional.of(atual));

            service.toggle("a", true);

            ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
            verify(repository).update(captor.capture(), eq(1));
            assertThat(captor.getValue().enabled()).isTrue();
        }

        @Test
        @DisplayName("id inexistente: IllegalArgumentException")
        void idInexistente() {
            when(repository.findById("x")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.toggle("x", true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("não encontrado");

            verify(repository, never()).update(any(), anyInt());
        }

        @Test
        @DisplayName("conflito de versão: propaga IllegalStateException")
        void conflitoDeVersao() {
            when(repository.findById("a")).thenReturn(Optional.of(reminderComVersion("a", 5)));
            doThrow(new OptimisticLockingFailureException("conflito"))
                    .when(repository)
                    .update(any(Reminder.class), eq(5));

            assertThatThrownBy(() -> service.toggle("a", false))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("modificado por outra instância");
        }
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("sucesso: chama deleteById, notifica listener")
        void sucesso() {
            when(repository.deleteById("a")).thenReturn(true);

            AtomicInteger chamadas = new AtomicInteger();
            service.setReloadListener(chamadas::incrementAndGet);

            assertThat(service.delete("a")).isTrue();
            verify(repository).deleteById("a");
            assertThat(chamadas.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("id inexistente: retorna false, NÃO notifica listener")
        void inexistente() {
            when(repository.deleteById("x")).thenReturn(false);

            AtomicInteger chamadas = new AtomicInteger();
            service.setReloadListener(chamadas::incrementAndGet);

            assertThat(service.delete("x")).isFalse();
            assertThat(chamadas.get()).isZero();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("id nulo/vazio: false sem tocar no repository")
        void idInvalido(String id) {
            assertThat(service.delete(id)).isFalse();
            verify(repository, never()).deleteById(any());
        }
    }

    // =========================================================================
    // IMPORT ALL
    // =========================================================================

    @Nested
    @DisplayName("importAll()")
    class ImportAll {

        @Test
        @DisplayName("lista vazia: retorna 0, sem interações")
        void listaVazia() {
            assertThat(service.importAll(List.of(), false)).isZero();
            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("lista nula: retorna 0, sem interações")
        void listaNula() {
            assertThat(service.importAll(null, false)).isZero();
            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("merge: novos → save, existentes → update com versão atual do banco")
        void merge() {
            Reminder novo = reminder("novo", true);
            Reminder existente = reminder("existente", true);
            Reminder doBanco = reminderComVersion("existente", 7);

            when(repository.findById("novo")).thenReturn(Optional.empty());
            when(repository.findById("existente")).thenReturn(Optional.of(doBanco));
            when(repository.findAll()).thenReturn(List.of(doBanco));

            AtomicInteger chamadas = new AtomicInteger();
            service.setReloadListener(chamadas::incrementAndGet);

            int imported = service.importAll(List.of(novo, existente), false);

            assertThat(imported).isEqualTo(2);
            verify(repository).save(novo);
            verify(repository).update(existente, 7);
            verify(repository, never()).deleteById(any());
            assertThat(chamadas.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("replace=true: deleta ausentes do lote")
        void replace() {
            Reminder novo = reminder("novo", true);
            Reminder doBanco = reminder("ausente", true);

            when(repository.findById("novo")).thenReturn(Optional.empty());
            when(repository.findAll()).thenReturn(List.of(doBanco));

            int imported = service.importAll(List.of(novo), true);

            assertThat(imported).isEqualTo(1);
            verify(repository).save(novo);
            verify(repository).deleteById("ausente");
        }

        @Test
        @DisplayName("replace=false: NÃO deleta ausentes")
        void naoReplace() {
            Reminder novo = reminder("novo", true);
            Reminder doBanco = reminder("preservado", true);

            when(repository.findById("novo")).thenReturn(Optional.empty());
            when(repository.findAll()).thenReturn(List.of(doBanco));

            service.importAll(List.of(novo), false);

            verify(repository, never()).deleteById(any());
        }

        @Test
        @DisplayName("falha no meio do lote: propaga exceção (transação rollbacka no repo)")
        void falhaNoMeio() {
            Reminder a = reminder("a", true);
            Reminder b = reminder("b", true);

            when(repository.findById("a")).thenReturn(Optional.empty());
            doThrow(new RuntimeException("DB down")).when(repository).save(a);

            // 🔧 FIX (java:S5778): catchThrowable em vez de assertThatThrownBy(lambda).
            // O Sonar exige que a lambda tenha apenas 1 invocação que possa lançar;
            // importAll chama findById + save + findAll, então usamos catchThrowable.
            Throwable thrown = catchThrowable(() -> service.importAll(List.of(a, b), false));

            assertThat(thrown).isInstanceOf(RuntimeException.class).hasMessageContaining("DB down");

            verify(repository, never()).findById("b");
            verify(repository, never()).save(b);
        }
    }

    // =========================================================================
    // RELOAD
    // =========================================================================

    @Nested
    @DisplayName("reload()")
    class Reload {

        @Test
        @DisplayName("notifica o listener sem reler do banco")
        void notificaListener() {
            AtomicInteger chamadas = new AtomicInteger();
            service.setReloadListener(chamadas::incrementAndGet);

            service.reload();

            assertThat(chamadas.get()).isEqualTo(1);
            verify(repository, never()).findAll();
        }
    }

    // =========================================================================
    // LISTENER
    // =========================================================================

    @Nested
    @DisplayName("Listener")
    class Listener {

        @Test
        @DisplayName("setReloadListener(null): instala no-op, não quebra")
        void listenerNulo() {
            when(repository.existsById("a")).thenReturn(false);
            service.setReloadListener(null);

            assertThatCode(() -> service.create(reminder("a", true))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("listener que lança: mutação completa, exceção não propaga")
        void listenerLanca() {
            when(repository.existsById("a")).thenReturn(false);
            service.setReloadListener(
                    () -> {
                        throw new RuntimeException("boom");
                    });

            Reminder criado = service.create(reminder("a", true));
            assertThat(criado.id()).isEqualTo("a");
        }

        @Test
        @DisplayName("listener é chamado após cada mutação")
        void chamadoPorMutacao() {
            when(repository.existsById("a")).thenReturn(false);
            when(repository.findById("a"))
                    .thenReturn(Optional.of(reminder("a", true)))
                    .thenReturn(Optional.of(reminder("a", true)));
            when(repository.deleteById("a")).thenReturn(true);

            AtomicInteger chamadas = new AtomicInteger();
            service.setReloadListener(chamadas::incrementAndGet);

            service.create(reminder("a", true));
            service.update("a", reminderComVersion("a", 1));
            service.toggle("a", false);
            service.delete("a");

            assertThat(chamadas.get()).isEqualTo(4);
        }
    }
}
