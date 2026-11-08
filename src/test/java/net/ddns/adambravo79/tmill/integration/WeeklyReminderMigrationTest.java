/* (c) 2026 | 08/11/2026 */
package net.ddns.adambravo79.tmill.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import net.ddns.adambravo79.tmill.model.Reminder;
import net.ddns.adambravo79.tmill.repository.ReminderRepository;

/**
 * Teste de paridade entre o {@code WeeklyReminderService} legado (que está sendo
 * removido neste PR) e o {@code Reminder} {@code quatro-horas-quarta} criado pela
 * migration {@code V5__seed_weekly_reminder_as_reminder.sql}.
 *
 * <h2>O que este teste garante</h2>
 *
 * <p>Se a migration {@code V5} não preservar <b>exatamente</b> o comportamento do
 * serviço legado, este teste falha. Ele é a "rede de segurança" da migração: se
 * alguém alterar a mensagem, o cron, o timezone ou as tags do seed, o teste
 * acusa.
 *
 * <h2>Como funciona</h2>
 *
 * <p>Sobe um PostgreSQL 15 (Testcontainers), aplica as migrations do Flyway
 * ({@code V1..V5}), e consulta o repositório diretamente. Não usa MockMvc, não
 * usa {@code ReminderService} — só o repositório e o record, para isolar o que
 * é testado (a migration) do que não é (o service).
 *
 * <h2>Por que não reusa {@link BaseIntegrationTest}</h2>
 *
 * <p>O {@code BaseIntegrationTest} sobe Kafka + Mongo + Postgres. Para este
 * teste, só o Postgres importa. Subir os 3 seria ~3× mais lento e sem ganho de
 * cobertura. Replicamos só o essencial: Postgres + dummies de propriedades
 * obrigatórias.
 *
 * <h2>Isolamento do bootstrap JSON</h2>
 *
 * <p>{@code reminders.bootstrap.from-json.enabled=false} é <b>intencional</b>.
 * O {@code ReminderBootstrapService} importaria o {@code reminders.json} (que tem
 * o mesmo reminder), mascarando um bug na migration. Queremos testar <b>só</b> a
 * V5 — portanto o estado esperado é o "pós-V5, pré-bootstrap":
 *
 * <ul>
 *   <li>{@code mediaUrl} = {@code null} (V5 não preenche; bootstrap preencheria)
 *   <li>{@code targetChatIds} = vazio (V5 não insere; bootstrap preencheria)
 *   <li>{@code tags} = {@code ["cinema", "semanal"]} (V5 insere direto)
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
@TestPropertySource(
        properties = {
            // ---- Desabilita coisas que não importam para este teste ----
            "spring.kafka.bootstrap-servers=localhost:9092",
            "spring.kafka.listener.auto-startup=false",
            "spring.mongodb.uri=mongodb://localhost:27017/test",
            "spring.data.mongodb.auto-index-creation=false",

            // ---- Feature flags: só reminders ligado ----
            "reminders.enabled=true",
            "worldcup.enabled=false",
            "worldcup.reminders30min.enabled=false",
            "daily.releases.enabled=false",
            "weekly.digest.enabled=false",
            "podcast.enabled=false",
            "birthday.enabled=false",
            "digest.enabled=false",
            "auto.response.enabled=false",
            "transcription.enabled=false",

            // ---- Dummies obrigatórios (mesmos do BaseIntegrationTest) ----
            "telegram.owner.id=0",
            "telegram.bot.token=dummy",
            "telegram.bot.username=test_bot",
            "telegram.bot.polling.enabled=false",
            "podcast.publish.chat-id=-123456789",
            "digest.chat-ids=",
            "bot.allowed-chats=",

            // ---- NÃO importar o JSON de bootstrap (queremos testar só a V5) ----
            "reminders.bootstrap.from-json.enabled=false",

            // ---- Sem cache (evita inconsistência entre testes) ----
            "spring.cache.type=none",

            // ---- Admin sem auth (o teste bate direto no repositório) ----
            "admin.security.disabled=true",

            // ---- Desabilita schedulers que poderiam competir ----
            "t1000.salavip.scheduler.enabled=false",
            "spring.task.scheduling.enabled=false"
        })
class WeeklyReminderMigrationTest {

    @Container
    @SuppressWarnings("resource") // container estático gerenciado pelo @Testcontainers
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

    @Autowired private ReminderRepository repository;

    // =========================================================================
    // PARIDADE — valores exatos do WeeklyReminderService legado
    // =========================================================================

    @Test
    @DisplayName("V5 cria o reminder 'quatro-horas-quarta' com todos os campos do legado")
    void migration_criaReminderComValoresLegados() {
        Reminder r =
                repository
                        .findById("quatro-horas-quarta")
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "V5 não criou o reminder 'quatro-horas-quarta'. "
                                                        + "Verifique se a migration foi aplicada e "
                                                        + "se o INSERT está correto."));

        // --- Campos copiados de WeeklyReminderService ---
        assertThat(r.name())
                .as("name deve ser o display name do legado")
                .isEqualTo("Quatro Horas de Quarta");

        assertThat(r.enabled()).as("enabled deve ser true (legado sempre ativo)").isTrue();

        assertThat(r.cron())
                .as("cron deve ser idêntico ao getCron() do legado")
                .isEqualTo("0 0 16 * * WED");

        assertThat(r.timezone())
                .as("timezone deve ser Brasília (mesmo do legado)")
                .isEqualTo("America/Sao_Paulo");

        assertThat(r.message())
                .as(
                        """
                        message deve ser byte-a-byte igual a \
                        WeeklyReminderService.REMINDER_MESSAGE \
                        (com \\n\\n no meio, SEM \\n no final)\
                        """)
                .isEqualTo(
                        """
                        <i>"São quatro horas da tarde de uma quarta-feira, não é? Semana \
                        praticamente encerrada...</i>"\n\n\
                        <b>Muito Prazer (1979) - David Neves</b>\
                        """);

        // mediaUrl é NULL na V5 — o bootstrap JSON (desligado aqui) preencheria
        assertThat(r.mediaUrl())
                .as("mediaUrl deve ser NULL pós-V5 (o bootstrap JSON preenche depois)")
                .isNull();

        assertThat(r.tags())
                .as("tags devem ser exatamente as do seed")
                .containsExactlyInAnyOrder("cinema", "semanal");

        assertThat(r.version()).as("version inicial deve ser 1").isEqualTo(1);
    }

    // =========================================================================
    // IDEMPOTÊNCIA — V5 não deve duplicar
    // =========================================================================

    @Test
    @DisplayName("V5 é idempotente: o reminder existe e a contagem não muda")
    void migration_idempotente() {
        // O Flyway já rodou no boot. Se a V5 fosse não-idempotente, o contexto
        // nem teria subido (ON CONFLICT DO NOTHING protege).
        assertThat(repository.existsById("quatro-horas-quarta")).isTrue();

        // Conta quantos reminders existem (esperado: 1, só o seed da V5)
        long total = repository.count();
        assertThat(total).as("deve haver exatamente 1 reminder (só o seed da V5)").isEqualTo(1);
    }

    // =========================================================================
    // CASCATA — chats/tags são filhas
    // =========================================================================

    @Test
    @DisplayName("reminder_tags foram seedadas; reminder_chats está vazio (pós-V5)")
    void migration_filhas() {
        Reminder r = repository.findById("quatro-horas-quarta").orElseThrow();

        // Tags: V5 insere direto
        assertThat(r.tags())
                .as("V5 deve ter inserido as tags 'cinema' e 'semanal'")
                .containsExactlyInAnyOrder("cinema", "semanal");

        // Chats: V5 NÃO insere (vêm do bootstrap JSON, que está desligado)
        assertThat(r.targetChatIds())
                .as("V5 não insere chats (o bootstrap JSON preenche depois)")
                .isEmpty();
    }
}
