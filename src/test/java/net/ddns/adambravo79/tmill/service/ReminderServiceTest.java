/* (c) 2026 | 06/11/2026 */
package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import net.ddns.adambravo79.tmill.dto.ReminderJson;
import net.ddns.adambravo79.tmill.model.Reminder;
import net.ddns.adambravo79.tmill.service.config.JsonConfigLoader;
import tools.jackson.databind.ObjectMapper;

/**
 * Testes do {@link ReminderService}.
 *
 * <p>Cobre:
 *
 * <ul>
 *   <li>Carregamento inicial ({@link ReminderService#init()} e {@link ReminderService#reload()}).
 *   <li>CRUD completo com persistência atômica em disco.
 *   <li>Toggle de {@code enabled} preservando demais campos.
 *   <li>Listener de reload (chamado após cada mutação e após reload).
 *   <li>Comportamento com {@code classpath:} (não persiste) vs {@code file:} (persiste).
 *   <li>Concorrência: escrita serializada (2 threads criando IDs diferentes).
 *   <li>Validações de {@code id} (nulo, branco).
 * </ul>
 *
 * <p>O {@link JsonConfigLoader} é mockado porque ele já tem cobertura própria
 * ({@code JsonConfigLoaderTest}). Aqui testamos <b>o comportamento do service</b> com o loader
 * respondendo o que queremos — não a leitura de disco do loader em si.
 */
class ReminderServiceTest {

    private static final String VALID_CRON = "0 0 16 * * WED";
    private static final String VALID_TZ = "America/Sao_Paulo";

    @TempDir Path tempDir;

    private JsonConfigLoader loader;
    private ObjectMapper objectMapper;
    private ReminderService service;

    @BeforeEach
    void setUp() {
        loader = Mockito.mock(JsonConfigLoader.class);
        objectMapper = new ObjectMapper();
        service = new ReminderService(loader, objectMapper);
        // Default: classpath (não persiste) — testes que precisam de file: sobrescrevem
        ReflectionTestUtils.setField(service, "remindersFile", "classpath:reminders.json");
    }

    // =========================================================================
    // FIXTURES
    // =========================================================================

    private Reminder reminder(String id, boolean enabled, String... chatIds) {
        return new Reminder(
                id,
                id,
                enabled,
                VALID_CRON,
                VALID_TZ,
                "<b>Mensagem " + id + "</b>",
                null,
                List.of(chatIds),
                List.of());
    }

    /** Mocka o {@code loadConfig} para devolver os reminders informados. */
    private void stubLoad(Reminder... reminders) {
        ReminderJson json = ReminderJson.of(List.of(reminders));
        Mockito.when(
                        loader.loadConfig(
                                Mockito.eq("reminders.json"),
                                Mockito.eq(ReminderJson.class),
                                Mockito.any()))
                .thenReturn(Optional.of(json));
    }

    /** Mocka o {@code loadConfig} para simular arquivo ausente. */
    private void stubLoadEmpty() {
        Mockito.when(
                        loader.loadConfig(
                                Mockito.eq("reminders.json"),
                                Mockito.eq(ReminderJson.class),
                                Mockito.any()))
                .thenReturn(Optional.empty());
    }

    /** Mocka o {@code loadConfig} para lançar exceção (arquivo corrompido, etc). */
    private void stubLoadFails() {
        Mockito.when(
                        loader.loadConfig(
                                Mockito.eq("reminders.json"),
                                Mockito.eq(ReminderJson.class),
                                Mockito.any()))
                .thenThrow(new RuntimeException("boom"));
    }

    // =========================================================================
    // INICIALIZAÇÃO / RELOAD
    // =========================================================================

    @Nested
    @DisplayName("init() / reload()")
    class Inicializacao {

        @Test
        @DisplayName("init() carrega os reminders do arquivo")
        void initCarrega() {
            stubLoad(reminder("a", true), reminder("b", false));

            service.init();

            assertThat(service.getAll()).hasSize(2);
            assertThat(service.count()).isEqualTo(2);
        }

        @Test
        @DisplayName("init() com arquivo ausente: lista vazia, sem exception")
        void initArquivoAusente() {
            stubLoadEmpty();

            service.init();

            assertThat(service.getAll()).isEmpty();
        }

        @Test
        @DisplayName("init() com loader lançando: mantém estado anterior (lista vazia)")
        void initLoaderFalha() {
            stubLoadFails();

            service.init();

            assertThat(service.getAll()).isEmpty();
        }

        @Test
        @DisplayName("reload() substitui o estado atual")
        void reloadSubstitui() {
            stubLoad(reminder("a", true));
            service.reload();
            assertThat(service.count()).isEqualTo(1);

            stubLoad(reminder("b", true), reminder("c", true));
            service.reload();

            assertThat(service.count()).isEqualTo(2);
            assertThat(service.getById("a")).isEmpty();
            assertThat(service.getById("b")).isPresent();
        }

        @Test
        @DisplayName("reload() com falha mantém estado anterior")
        void reloadFalhaMantemEstado() {
            stubLoad(reminder("a", true));
            service.reload();
            assertThat(service.count()).isEqualTo(1);

            stubLoadFails();
            service.reload();

            assertThat(service.count()).isEqualTo(1);
            assertThat(service.getById("a")).isPresent();
        }

        @Test
        @DisplayName("reload() notifica o listener")
        void reloadNotificaListener() {
            stubLoad(reminder("a", true));
            AtomicInteger chamadas = new AtomicInteger();
            service.setReloadListener(chamadas::incrementAndGet);

            service.reload();

            assertThat(chamadas.get()).isEqualTo(1);
        }
    }

    // =========================================================================
    // LEITURA
    // =========================================================================

    @Nested
    @DisplayName("Leitura")
    class Leitura {

        @Test
        @DisplayName("getAll() retorna todos (enabled + disabled)")
        void getAllTodos() {
            stubLoad(reminder("a", true), reminder("b", false));
            service.reload();

            assertThat(service.getAll()).hasSize(2);
        }

        @Test
        @DisplayName("getEnabled() filtra apenas habilitados")
        @SuppressWarnings("null")
        void getEnabledFiltra() {
            stubLoad(reminder("a", true), reminder("b", false), reminder("c", true));
            service.reload();

            assertThat(service.getEnabled())
                    .extracting(Reminder::id)
                    .containsExactlyInAnyOrder("a", "c");
        }

        @Test
        @DisplayName("getById() encontra existente")
        void getByIdExistente() {
            stubLoad(reminder("a", true));
            service.reload();

            assertThat(service.getById("a")).isPresent();
        }

        @Test
        @DisplayName("getById() retorna empty para inexistente")
        void getByIdInexistente() {
            stubLoad(reminder("a", true));
            service.reload();

            assertThat(service.getById("nao-existe")).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("getById() com id nulo/vazio retorna empty")
        void getByIdInvalido(String id) {
            assertThat(service.getById(id)).isEmpty();
        }

        @Test
        @DisplayName("count() retorna total")
        void count() {
            stubLoad(reminder("a", true), reminder("b", false));
            service.reload();

            assertThat(service.count()).isEqualTo(2);
        }
    }

    // =========================================================================
    // CRUD — create()
    // =========================================================================

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("create() adiciona e retorna o reminder")
        void createAdiciona() {
            stubLoadEmpty();
            service.reload();

            Reminder novo = reminder("novo", true, "-100123");
            Reminder criado = service.create(novo);

            assertThat(criado).isEqualTo(novo);
            assertThat(service.count()).isEqualTo(1);
            assertThat(service.getById("novo")).isPresent();
        }

        @Test
        @DisplayName("create() com ID duplicado lança IllegalArgumentException")
        void createIdDuplicado() {
            stubLoad(reminder("a", true));
            service.reload();

            Reminder duplicado = reminder("a", true);
            assertThatThrownBy(() -> service.create(duplicado))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("já existe");
        }

        @Test
        @DisplayName("create(null) lança NullPointerException")
        void createNull() {
            assertThatThrownBy(() -> service.create(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("create() notifica o listener")
        void createNotificaListener() {
            stubLoadEmpty();
            service.reload();
            AtomicInteger chamadas = new AtomicInteger();
            service.setReloadListener(chamadas::incrementAndGet);

            service.create(reminder("a", true));

            assertThat(chamadas.get()).isEqualTo(1);
        }
    }

    // =========================================================================
    // CRUD — update()
    // =========================================================================

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("update() substitui todos os campos do reminder")
        void updateSubstitui() {
            stubLoad(reminder("a", true));
            service.reload();

            Reminder atualizado =
                    new Reminder(
                            "a",
                            "novo nome",
                            false,
                            "0 0 10 * * FRI",
                            "America/New_York",
                            "<b>Nova msg</b>",
                            "https://exemplo.com/x.mp4",
                            List.of("-100999"),
                            List.of("t1"));

            service.update("a", atualizado);

            Reminder fromRegistry = service.getById("a").orElseThrow();
            assertThat(fromRegistry.name()).isEqualTo("novo nome");
            assertThat(fromRegistry.enabled()).isFalse();
            assertThat(fromRegistry.cron()).isEqualTo("0 0 10 * * FRI");
            assertThat(fromRegistry.timezone()).isEqualTo("America/New_York");
            assertThat(fromRegistry.mediaUrl()).isEqualTo("https://exemplo.com/x.mp4");
        }

        @Test
        @DisplayName("update() com id divergente do body lança IllegalArgumentException")
        void updateIdDivergente() {
            stubLoad(reminder("a", true));
            service.reload();

            Reminder diferente = reminder("b", true);
            assertThatThrownBy(() -> service.update("a", diferente))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("diverge");
        }

        @Test
        @DisplayName("update() com reminder inexistente lança IllegalArgumentException")
        void updateInexistente() {
            stubLoadEmpty();
            service.reload();

            Reminder novo = reminder("x", true);
            assertThatThrownBy(() -> service.update("x", novo))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("não encontrado");
        }

        @Test
        @DisplayName("update() com id do path nulo/vazio lança IllegalArgumentException")
        void updateIdPathInvalido() {
            stubLoad(reminder("a", true));
            service.reload();

            Reminder r = reminder("a", true);
            assertThatThrownBy(() -> service.update(null, r))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("update(null, ...) lança NullPointerException")
        void updateReminderNull() {
            assertThatThrownBy(() -> service.update("a", null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("update() notifica o listener")
        void updateNotificaListener() {
            stubLoad(reminder("a", true));
            service.reload();
            AtomicInteger chamadas = new AtomicInteger();
            service.setReloadListener(chamadas::incrementAndGet);

            service.update("a", reminder("a", false));

            assertThat(chamadas.get()).isEqualTo(1);
        }
    }

    // =========================================================================
    // CRUD — toggle()
    // =========================================================================

    @Nested
    @DisplayName("toggle()")
    class Toggle {

        @Test
        @DisplayName("toggle(true) habilita")
        void toggleHabilita() {
            stubLoad(reminder("a", false));
            service.reload();

            service.toggle("a", true);

            assertThat(service.getById("a").orElseThrow().enabled()).isTrue();
        }

        @Test
        @DisplayName("toggle(false) desabilita")
        void toggleDesabilita() {
            stubLoad(reminder("a", true));
            service.reload();

            service.toggle("a", false);

            assertThat(service.getById("a").orElseThrow().enabled()).isFalse();
        }

        @Test
        @DisplayName("toggle() preserva todos os outros campos")
        void togglePreservaCampos() {
            Reminder original =
                    new Reminder(
                            "a",
                            "Nome original",
                            true,
                            "0 0 16 * * WED",
                            VALID_TZ,
                            "msg",
                            "https://exemplo.com/x.mp4",
                            List.of("-100"),
                            List.of("tag1"));
            stubLoad(original);
            service.reload();

            service.toggle("a", false);

            Reminder atual = service.getById("a").orElseThrow();
            assertThat(atual.name()).isEqualTo("Nome original");
            assertThat(atual.cron()).isEqualTo("0 0 16 * * WED");
            assertThat(atual.mediaUrl()).isEqualTo("https://exemplo.com/x.mp4");
            assertThat(atual.targetChatIds()).containsExactly("-100");
            assertThat(atual.tags()).containsExactly("tag1");
        }

        @Test
        @DisplayName("toggle() com id inexistente lança IllegalArgumentException")
        void toggleInexistente() {
            stubLoadEmpty();
            service.reload();

            assertThatThrownBy(() -> service.toggle("x", true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("não encontrado");
        }
    }

    // =========================================================================
    // CRUD — delete()
    // =========================================================================

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("delete() remove existente e retorna true")
        void deleteRemove() {
            stubLoad(reminder("a", true), reminder("b", true));
            service.reload();

            boolean removido = service.delete("a");

            assertThat(removido).isTrue();
            assertThat(service.count()).isEqualTo(1);
            assertThat(service.getById("a")).isEmpty();
        }

        @Test
        @DisplayName("delete() com id inexistente retorna false")
        void deleteInexistente() {
            stubLoad(reminder("a", true));
            service.reload();

            assertThat(service.delete("nao-existe")).isFalse();
            assertThat(service.count()).isEqualTo(1);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("delete() com id nulo/vazio retorna false sem chamar persist")
        void deleteIdInvalido(String id) {
            stubLoad(reminder("a", true));
            service.reload();

            assertThat(service.delete(id)).isFalse();
            assertThat(service.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("delete() notifica o listener")
        void deleteNotificaListener() {
            stubLoad(reminder("a", true));
            service.reload();
            AtomicInteger chamadas = new AtomicInteger();
            service.setReloadListener(chamadas::incrementAndGet);

            service.delete("a");

            assertThat(chamadas.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("delete() com id inexistente NÃO notifica o listener")
        void deleteInexistenteNaoNotifica() {
            stubLoad(reminder("a", true));
            service.reload();
            AtomicInteger chamadas = new AtomicInteger();
            service.setReloadListener(chamadas::incrementAndGet);

            service.delete("nao-existe");

            assertThat(chamadas.get()).isZero();
        }
    }

    // =========================================================================
    // PERSISTÊNCIA EM DISCO
    // =========================================================================

    @Nested
    @DisplayName("Persistência em disco")
    class Persistencia {

        private Path remindersFile;

        @BeforeEach
        void setUpFile() {
            remindersFile = tempDir.resolve("reminders.json");
            ReflectionTestUtils.setField(
                    service, "remindersFile", "file:" + remindersFile.toAbsolutePath());
            stubLoadEmpty();
            service.reload();
        }

        @Test
        @DisplayName("create() grava o arquivo")
        void createGrava() throws IOException {
            service.create(reminder("a", true));

            assertThat(remindersFile).exists();
            String content = Files.readString(remindersFile);
            assertThat(content).contains("\"a\"").contains("0 0 16 * * WED");
        }

        @Test
        @DisplayName("update() grava o arquivo")
        void updateGrava() throws IOException {
            service.create(reminder("a", true));
            service.update("a", reminder("a", false));

            String content = Files.readString(remindersFile);
            assertThat(content).contains("\"a\"").contains("\"enabled\" : false");
        }

        @Test
        @DisplayName("delete() grava o arquivo sem o removido")
        void deleteGrava() throws IOException {
            service.create(reminder("a", true));
            service.create(reminder("b", true));
            service.delete("a");

            String content = Files.readString(remindersFile);
            assertThat(content).doesNotContain("\"a\"").contains("\"b\"");
        }

        @Test
        @DisplayName("escrita atômica não deixa .tmp para trás")
        void escritaAtomica() {
            service.create(reminder("a", true));

            Path tmp = remindersFile.resolveSibling(remindersFile.getFileName() + ".tmp");
            assertThat(tmp).doesNotExist();
        }

        @Test
        @DisplayName("múltiplas mutações preservam apenas o estado final")
        void multiplasMutacoes() throws IOException {
            service.create(reminder("a", true));
            service.create(reminder("b", true));
            service.create(reminder("c", true));
            service.delete("b");
            service.toggle("a", false);

            String content = Files.readString(remindersFile);
            assertThat(content).contains("\"a\"").doesNotContain("\"b\"").contains("\"c\"");
        }

        @Test
        @DisplayName("lastPersistedAt() retorna timestamp após create")
        void lastPersistedAtAposCreate() {
            assertThat(service.lastPersistedAt()).isNull();

            service.create(reminder("a", true));

            assertThat(service.lastPersistedAt()).isNotNull();
        }

        @Test
        @DisplayName("lastPersistedAt() retorna null em classpath")
        void lastPersistedAtClasspath() {
            ReflectionTestUtils.setField(service, "remindersFile", "classpath:reminders.json");
            assertThat(service.lastPersistedAt()).isNull();
        }
    }

    // =========================================================================
    // COMPORTAMENTO COM CLASSPATH (não persiste)
    // =========================================================================

    @Nested
    @DisplayName("Comportamento com classpath:")
    class Classpath {

        @Test
        @DisplayName("create() em memória funciona, sem exception")
        void createEmMemoria() {
            stubLoadEmpty();
            service.reload();

            Reminder criado = service.create(reminder("a", true));

            assertThat(criado.id()).isEqualTo("a");
            assertThat(service.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("update() em memória funciona, sem exception")
        void updateEmMemoria() {
            stubLoad(reminder("a", true));
            service.reload();

            service.update("a", reminder("a", false));

            assertThat(service.getById("a").orElseThrow().enabled()).isFalse();
        }
    }

    // =========================================================================
    // LISTENER DE RELOAD
    // =========================================================================

    @Nested
    @DisplayName("Listener de reload")
    class ListenerReload {

        @Test
        @DisplayName("setReloadListener(null) instala no-op")
        void listenerNull() {
            stubLoadEmpty();
            service.reload();
            service.setReloadListener(null);

            // Não deve lançar
            service.create(reminder("a", true));
        }

        @Test
        @DisplayName("listener que lança não propaga para o caller")
        void listenerLanca() {
            stubLoadEmpty();
            service.reload();
            service.setReloadListener(
                    () -> {
                        throw new RuntimeException("boom");
                    });

            // create() deve completar com sucesso mesmo com listener falhando
            Reminder criado = service.create(reminder("a", true));
            assertThat(criado.id()).isEqualTo("a");
        }

        @Test
        @DisplayName("listener é chamado após cada mutação")
        void listenerChamadoPorMutacao() {
            stubLoadEmpty();
            service.reload();
            AtomicInteger chamadas = new AtomicInteger();
            service.setReloadListener(chamadas::incrementAndGet);

            service.create(reminder("a", true)); // 1
            service.update("a", reminder("a", false)); // 2
            service.toggle("a", true); // 3
            service.delete("a"); // 4

            assertThat(chamadas.get()).isEqualTo(4);
        }

        @Test
        @DisplayName("listener pode ser trocado em runtime")
        void listenerTrocado() {
            stubLoadEmpty();
            service.reload();
            AtomicInteger primeiro = new AtomicInteger();
            AtomicInteger segundo = new AtomicInteger();

            service.setReloadListener(primeiro::incrementAndGet);
            service.create(reminder("a", true));

            service.setReloadListener(segundo::incrementAndGet);
            service.create(reminder("b", true));

            assertThat(primeiro.get()).isEqualTo(1);
            assertThat(segundo.get()).isEqualTo(1);
        }
    }

    // =========================================================================
    // CONCORRÊNCIA
    // =========================================================================

    @Nested
    @DisplayName("Concorrência")
    class Concorrencia {

        @Test
        @DisplayName("create() concorrente de IDs diferentes: ambos persistem, sem corrupção")
        void createConcorrente() throws Exception {
            stubLoadEmpty();
            service.reload();

            int total = 10;
            CountDownLatch latch = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(total);
            List<Throwable> erros = new ArrayList<>();

            try {
                for (int i = 0; i < total; i++) {
                    final int idx = i;
                    pool.submit(
                            () -> {
                                try {
                                    latch.await();
                                    service.create(reminder("r-" + idx, true));
                                } catch (Throwable t) {
                                    synchronized (erros) {
                                        erros.add(t);
                                    }
                                }
                            });
                }
                latch.countDown();
            } finally {
                pool.shutdown();
                assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            }

            assertThat(erros).isEmpty();
            assertThat(service.count()).isEqualTo(total);
        }

        @Test
        @DisplayName("delete() concorrente do mesmo ID: apenas um retorna true")
        void deleteConcorrente() throws Exception {
            stubLoad(reminder("a", true));
            service.reload();

            int total = 5;
            AtomicInteger sucessos = new AtomicInteger();
            CountDownLatch latch = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(total);

            try {
                for (int i = 0; i < total; i++) {
                    pool.submit(
                            () -> {
                                try {
                                    latch.await();
                                    if (service.delete("a")) {
                                        sucessos.incrementAndGet();
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            });
                }
                latch.countDown();
            } finally {
                pool.shutdown();
                assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            }

            assertThat(sucessos.get()).isEqualTo(1);
            assertThat(service.count()).isZero();
        }
    }
}
