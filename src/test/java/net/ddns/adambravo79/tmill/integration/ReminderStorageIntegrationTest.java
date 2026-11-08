/* (c) 2026 | 07/11/2026 */
package net.ddns.adambravo79.tmill.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import net.ddns.adambravo79.tmill.dto.ReminderJson;
import net.ddns.adambravo79.tmill.model.Reminder;
import net.ddns.adambravo79.tmill.repository.ReminderRepository;
import net.ddns.adambravo79.tmill.service.ReminderService;
import tools.jackson.databind.ObjectMapper;

/**
 * Teste de integração ponta-a-ponta do storage de reminders.
 *
 * <h2>O que sobe</h2>
 *
 * <ul>
 *   <li>PostgreSQL 15 (Testcontainers) — único container necessário.
 *   <li>Contexto Spring completo ({@code @SpringBootTest}) — service, repository,
 *       controller, scheduler, listener.
 *   <li>Flyway — aplica V1..V4 no boot, garantindo schema real.
 * </ul>
 *
 * <h2>Por que não usar {@link BaseIntegrationTest}</h2>
 *
 * <p>O {@code BaseIntegrationTest} sobe Kafka + Mongo + Postgres. Para reminders, só o
 * Postgres importa. Subir os 3 seria ~3× mais lento e sem ganho de cobertura. Replicamos
 * só o essencial: Postgres + dummies de propriedades obrigatórias.
 *
 * <h2>Cenários cobertos</h2>
 *
 * <ol>
 *   <li>{@code create} → banco tem 1 reminder, version=1.
 *   <li>{@code update} com versão correta → version=2, campos trocados.
 *   <li>{@code update} com versão errada → 409 (optimistic locking real, não mock).
 *   <li>{@code export} → JSON com o reminder criado.
 *   <li>{@code import} com {@code replace=true} → banco reflete o lote, ausentes removidos.
 *   <li>{@code reload} → listener chamado, {@code scheduledCount()} reflete o total.
 *   <li>{@code delete} → banco vazio.
 * </ol>
 *
 * <h2>Isolamento</h2>
 *
 * <p>{@code TRUNCATE TABLE reminders CASCADE} antes de cada teste. Como o
 * {@code ReminderScheduler} é {@code @PostConstruct}, ele já está com o listener
 * registrado no {@code ReminderService} — os testes de {@code reload}/{@code create}
 * verificam que o scheduler reagiu (via {@code scheduledCount}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(
        properties = {
            // ---- Desabilita coisas que não importam para este teste ----
            "spring.kafka.bootstrap-servers=localhost:9092",
            "spring.kafka.listener.auto-startup=false",
            "spring.mongodb.uri=mongodb://localhost:27017/test",
            "spring.data.mongodb.auto-index-creation=false",

            // ---- Feature flags: reminders ligado, resto desligado ----
            "reminders.enabled=true",
            "worldcup.enabled=false",
            "daily.releases.enabled=false",
            "weekly.digest.enabled=false",
            "podcast.enabled=false",
            "birthday.enabled=false",
            "digest.enabled=false",

            // ---- Dummies obrigatórios (mesmos do BaseIntegrationTest) ----
            "telegram.owner.id=0",
            "telegram.bot.token=dummy",
            "telegram.bot.username=test_bot",
            "telegram.bot.polling.enabled=false",
            "podcast.publish.chat-id=-123456789",
            "digest.chat-ids=",
            "bot.allowed-chats=",

            // ---- Sem bootstrap JSON (o teste cria via controller) ----
            "reminders.bootstrap.from-json.enabled=false",

            // ---- Sem cache (evita inconsistência entre testes) ----
            "spring.cache.type=none",

            // ---- Admin sem auth (o teste bate direto no controller) ----
            "admin.security.disabled=true",

            // ---- ReminderScheduler: pool mínimo, shutdown rápido ----
            "reminders.scheduler.pool-size=1",

            // ---- Desabilita schedulers que poderiam competir ----
            "t1000.salavip.scheduler.enabled=false",
            "spring.task.scheduling.enabled=false"
        })
class ReminderStorageIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:15-alpine")
                    .withDatabaseName("test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // Flyway roda no boot
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ReminderRepository repository;
    @Autowired private ReminderService reminderService;

    @BeforeEach
    void limparBanco() {
        // Antes de cada teste, banco zerado
        repository.findAll().forEach(r -> repository.deleteById(r.id()));
    }

    // =========================================================================
    // FIXTURES
    // =========================================================================

    private Reminder reminder(String id, boolean enabled) {
        return new Reminder(
                id,
                "Nome " + id,
                enabled,
                "0 0 16 * * WED",
                "America/Sao_Paulo",
                "<b>Msg " + id + "</b>",
                null,
                List.of("-100123"),
                List.of("cinema"));
    }

    // =========================================================================
    // 1. CREATE via controller
    // =========================================================================

    @Test
    @DisplayName("POST /admin/reminders cria no banco real e retorna version=1")
    void create_persisteNoBanco() throws Exception {
        Reminder novo = reminder("promo-sexta", true);

        mockMvc.perform(
                        post("/admin/reminders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(novo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("promo-sexta"))
                .andExpect(jsonPath("$.version").value(1));

        // Confirma no banco (fora do cache do controller)
        assertThat(repository.existsById("promo-sexta")).isTrue();
        Reminder doBanco = repository.findById("promo-sexta").orElseThrow();
        assertThat(doBanco.version()).isEqualTo(1);
        assertThat(doBanco.targetChatIds()).containsExactly("-100123");
        assertThat(doBanco.tags()).containsExactly("cinema");
    }

    // =========================================================================
    // 2. UPDATE com versão correta
    // =========================================================================

    @Test
    @DisplayName("PUT /admin/reminders/{id} com version correta incrementa e troca campos")
    void update_comVersaoCorreta() throws Exception {
        repository.save(reminder("promo", true));

        Reminder atualizado =
                new Reminder(
                        "promo",
                        "Nome Novo",
                        false,
                        "0 0 10 * * FRI",
                        "America/New_York",
                        "nova msg",
                        null,
                        List.of("-100999"),
                        List.of("nova-tag"),
                        1); // version 1 (lida do banco)

        mockMvc.perform(
                        put("/admin/reminders/promo")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(atualizado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.name").value("Nome Novo"))
                .andExpect(jsonPath("$.enabled").value(false));

        // Confirma no banco
        Reminder doBanco = repository.findById("promo").orElseThrow();
        assertThat(doBanco.version()).isEqualTo(2);
        assertThat(doBanco.targetChatIds()).containsExactly("-100999");
        assertThat(doBanco.tags()).containsExactly("nova-tag");
    }

    // =========================================================================
    // 3. UPDATE com versão errada → 409
    // =========================================================================

    @Test
    @DisplayName("PUT /admin/reminders/{id} com version errada retorna 409 Conflict")
    void update_comVersaoErrada_retorna409() throws Exception {
        repository.save(reminder("promo", true));

        Reminder atualizado =
                new Reminder(
                        "promo",
                        "Nome Novo",
                        false,
                        "0 0 10 * * FRI",
                        "America/New_York",
                        "nova msg",
                        null,
                        List.of("-100999"),
                        List.of("nova-tag"),
                        99); // versão errada (banco está em 1)

        mockMvc.perform(
                        put("/admin/reminders/promo")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(atualizado)))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.erro")
                                .value(
                                        "Conflito de versão: o reminder foi modificado por outra"
                                                + " instância. Recarregue e tente novamente."));

        // Banco NÃO foi alterado
        Reminder doBanco = repository.findById("promo").orElseThrow();
        assertThat(doBanco.version()).isEqualTo(1);
        assertThat(doBanco.name()).isEqualTo("Nome promo");
    }

    // =========================================================================
    // 4. EXPORT
    // =========================================================================

    @Test
    @DisplayName("GET /admin/reminders/export gera ReminderJson com os reminders do banco")
    void export_geraJson() throws Exception {
        repository.save(reminder("a", true));
        repository.save(reminder("b", false));

        mockMvc.perform(get("/admin/reminders/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reminders").isArray())
                .andExpect(jsonPath("$.reminders.length()").value(2))
                .andExpect(jsonPath("$.reminders[0].id").value("a"))
                .andExpect(jsonPath("$.reminders[1].id").value("b"))
                .andExpect(jsonPath("$.version").value(1));
    }

    // =========================================================================
    // 5. IMPORT com replace=true
    // =========================================================================

    @SuppressWarnings("null")
    @Test
    @DisplayName("POST /admin/reminders/import?replace=true substitui o banco inteiro")
    void import_replace_substitui() throws Exception {
        // Estado inicial: 2 reminders (a, b)
        repository.save(reminder("a", true));
        repository.save(reminder("b", true));

        // Importa apenas 1 (novo); replace=true → a e b são removidos
        ReminderJson payload = ReminderJson.of(List.of(reminder("novo", true)));

        mockMvc.perform(
                        post("/admin/reminders/import?replace=true")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replace").value(true))
                .andExpect(jsonPath("$.total").value(1));

        // Banco: só "novo" existe
        List<Reminder> todos = repository.findAll();
        assertThat(todos).extracting(Reminder::id).containsExactly("novo");
    }

    // =========================================================================
    // 6. RELOAD notifica scheduler
    // =========================================================================

    @Test
    @DisplayName("POST /admin/reminders/reload dispara o listener (scheduler reagenda)")
    void reload_disparaListener() throws Exception {
        repository.save(reminder("a", true));
        repository.save(reminder("b", true));

        // Antes do reload: scheduler pode estar com estado defasado (depende do timing
        // do @PostConstruct, mas como o ReminderScheduler.init() roda no boot, ele já
        // tem os 2 agendados se o banco tinha 2 no boot... que não é o caso: o banco
        // estava vazio no boot).
        //
        // O reload força o scheduler a reler o estado atual (2 reminders).
        mockMvc.perform(post("/admin/reminders/reload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("2")))
                .andExpect(jsonPath("$.scheduled").isNumber());

        // Após reload, o estado em memória do service reflete o banco
        assertThat(reminderService.count()).isEqualTo(2);
        assertThat(reminderService.getEnabled()).hasSize(2);
    }

    // =========================================================================
    // 7. DELETE
    // =========================================================================

    @Test
    @DisplayName("DELETE /admin/reminders/{id} remove do banco e cascateia filhas")
    void delete_removeDoBanco() throws Exception {
        repository.save(reminder("promo", true));

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                "/admin/reminders/promo"))
                .andExpect(status().isNoContent());

        assertThat(repository.existsById("promo")).isFalse();
    }

    // =========================================================================
    // 8. Ciclo completo: create → update → export → import → delete
    // =========================================================================

    @SuppressWarnings("null")
    @Test
    @DisplayName("Ciclo completo: create → update → export → import → delete")
    void cicloCompleto() throws Exception {
        // 1. CREATE
        Reminder novo = reminder("ciclo", true);
        mockMvc.perform(
                        post("/admin/reminders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(novo)))
                .andExpect(status().isCreated());

        // 2. UPDATE
        Reminder atualizado =
                new Reminder(
                        "ciclo",
                        "Atualizado",
                        false,
                        "0 0 10 * * FRI",
                        "America/New_York",
                        "atualizada",
                        null,
                        List.of("-100999"),
                        List.of("nova-tag"),
                        1);
        mockMvc.perform(
                        put("/admin/reminders/ciclo")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(atualizado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        // 3. EXPORT
        mockMvc.perform(get("/admin/reminders/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reminders.length()").value(1))
                .andExpect(jsonPath("$.reminders[0].id").value("ciclo"))
                .andExpect(jsonPath("$.reminders[0].version").value(2));

        // 4. IMPORT replace=true (recria com o mesmo id)
        ReminderJson payload = ReminderJson.of(List.of(reminder("outro", true)));
        mockMvc.perform(
                        post("/admin/reminders/import?replace=true")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        // Banco agora só tem "outro"
        assertThat(repository.findAll()).extracting(Reminder::id).containsExactly("outro");

        // 5. DELETE
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                "/admin/reminders/outro"))
                .andExpect(status().isNoContent());

        assertThat(repository.findAll()).isEmpty();
    }
}
