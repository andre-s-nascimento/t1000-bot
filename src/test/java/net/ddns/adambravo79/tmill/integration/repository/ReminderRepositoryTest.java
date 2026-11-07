/* (c) 2026 | 07/11/2026 */
package net.ddns.adambravo79.tmill.integration.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import net.ddns.adambravo79.tmill.model.Reminder;
import net.ddns.adambravo79.tmill.repository.ReminderRepository;

/**
 * Testes de integração do {@link ReminderRepository} com PostgreSQL real
 * (Testcontainers).
 *
 * <h2>Estratégia</h2>
 *
 * <p>Sobe um container Postgres 15, aplica as migrations do Flyway (via
 * {@code @JdbcTest} + {@code spring.flyway.enabled=true} do base), e exercita o
 * repositório contra o banco real. Nenhum mock de {@link JdbcTemplate} — o
 * objetivo é testar o SQL, não o wrapper.
 *
 * <p>Não sobe Kafka/Mongo (seria lento e irrelevante): este teste é focado no
 * {@code ReminderRepository}. Para os fluxos completos, use o
 * {@code ReminderStorageIntegrationTest}.
 *
 * <h2>Isolamento</h2>
 *
 * <p>{@code TRUNCATE TABLE reminders CASCADE} antes de cada teste. O
 * {@code CASCADE} limpa {@code reminder_chats} e {@code reminder_tags} em
 * cascata (graças à FK). Roda rápido e não deixa estado entre testes.
 */
@JdbcTest
@Import(ReminderRepository.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ReminderRepositoryTest {

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
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ReminderRepository repository;

    @BeforeEach
    void limparBanco() {
        // CASCADE limpa reminder_chats e reminder_tags em cascata
        jdbcTemplate.execute("TRUNCATE TABLE reminders CASCADE");
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
                List.of("-100123", "-100456"),
                List.of("cinema", "semanal"));
    }

    private Reminder reminderSimples(String id) {
        return new Reminder(
                id,
                id,
                true,
                "0 0 12 * * MON",
                "America/Sao_Paulo",
                "msg",
                null,
                List.of(),
                List.of());
    }

    // =========================================================================
    // save()
    // =========================================================================

    @Nested
    @DisplayName("save()")
    class Save {

        @Test
        @DisplayName("insere pai + chats + tags na mesma transação")
        void inserePaiFilhas() {
            Reminder r = reminder("promo-sexta", true);

            repository.save(r);

            Integer countPai =
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM reminders WHERE id = ?",
                            Integer.class,
                            "promo-sexta");
            assertThat(countPai).isEqualTo(1);

            Integer countChats =
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM reminder_chats WHERE reminder_id = ?",
                            Integer.class,
                            "promo-sexta");
            Integer countTags =
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM reminder_tags WHERE reminder_id = ?",
                            Integer.class,
                            "promo-sexta");
            assertThat(countChats).isEqualTo(2);
            assertThat(countTags).isEqualTo(2);
        }

        @Test
        @DisplayName("força version=1 mesmo que o record venha com outro valor")
        void forcaVersionUm() {
            Reminder r =
                    new Reminder(
                            "forca-version",
                            "n",
                            true,
                            "0 0 16 * * WED",
                            "America/Sao_Paulo",
                            "msg",
                            null,
                            List.of(),
                            List.of(),
                            99);

            repository.save(r);

            Integer version =
                    jdbcTemplate.queryForObject(
                            "SELECT version FROM reminders WHERE id = ?",
                            Integer.class,
                            "forca-version");
            assertThat(version).isEqualTo(1);
        }

        @Test
        @DisplayName("sem chats/tags: insere pai vazio nas filhas")
        void semChatsNemTags() {
            repository.save(reminderSimples("vazio"));

            assertThat(repository.findById("vazio")).isPresent();
            Integer chats =
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM reminder_chats WHERE reminder_id = ?",
                            Integer.class,
                            "vazio");
            assertThat(chats).isZero();
        }

        @Test
        @DisplayName("ignora chat ID inválido (não numérico) com warn")
        void chatIdInvalidoIgnorado() {
            Reminder r =
                    new Reminder(
                            "chat-invalido",
                            "n",
                            true,
                            "0 0 16 * * WED",
                            "America/Sao_Paulo",
                            "msg",
                            null,
                            List.of("-100", "abc", "456"),
                            List.of());

            repository.save(r);

            Integer chats =
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM reminder_chats WHERE reminder_id = ?",
                            Integer.class,
                            "chat-invalido");
            assertThat(chats).isEqualTo(2);
        }
    }

    // =========================================================================
    // findById() / findAll() / findEnabled()
    // =========================================================================

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("hidrata chats e tags")
        void hidrataFilhas() {
            repository.save(reminder("promo-sexta", true));

            Optional<Reminder> r = repository.findById("promo-sexta");

            assertThat(r).isPresent();
            Reminder found = r.get();
            assertThat(found.id()).isEqualTo("promo-sexta");
            assertThat(found.targetChatIds()).containsExactlyInAnyOrder("-100123", "-100456");
            assertThat(found.tags()).containsExactlyInAnyOrder("cinema", "semanal");
            assertThat(found.version()).isEqualTo(1);
        }

        @Test
        @DisplayName("id inexistente: Optional.empty()")
        void idInexistente() {
            assertThat(repository.findById("nao-existe")).isEmpty();
        }

        @Test
        @DisplayName("id nulo/vazio: Optional.empty()")
        void idInvalido() {
            assertThat(repository.findById(null)).isEmpty();
            assertThat(repository.findById("")).isEmpty();
            assertThat(repository.findById("   ")).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("retorna lista ordenada por id")
        void ordenadoPorId() {
            repository.save(reminderSimples("zeta"));
            repository.save(reminderSimples("alfa"));
            repository.save(reminderSimples("beta"));

            List<Reminder> all = repository.findAll();

            // 🔧 FIX (Null type safety): map em vez de extracting(Reminder::id)
            // O JDT infere nullability via stream(), e o assertThat(List<String>)
            // não gera o warning do ThrowingExtractor.
            @SuppressWarnings("null")
            List<String> ids = all.stream().map(Reminder::id).toList();
            assertThat(ids).containsExactly("alfa", "beta", "zeta");
        }

        @Test
        @DisplayName("banco vazio: lista vazia")
        void vazio() {
            assertThat(repository.findAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("findEnabled()")
    class FindEnabled {

        @Test
        @DisplayName("filtra apenas habilitados")
        void apenasHabilitados() {
            repository.save(reminderSimples("a"));
            repository.save(reminder("b", false));
            repository.save(reminderSimples("c"));

            List<Reminder> enabled = repository.findEnabled();

            // 🔧 FIX (Null type safety): map em vez de extracting(Reminder::id)
            @SuppressWarnings("null")
            List<String> ids = enabled.stream().map(Reminder::id).toList();
            assertThat(ids).containsExactly("a", "c");
        }
    }

    // =========================================================================
    // update()
    // =========================================================================

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("incrementa version e substitui chats/tags")
        void atualizaCampos() {
            repository.save(reminder("promo", true));

            Reminder atualizado =
                    new Reminder(
                            "promo",
                            "Novo Nome",
                            false,
                            "0 0 10 * * FRI",
                            "America/New_York",
                            "nova msg",
                            "https://exemplo.com/x.mp4",
                            List.of("-100999"),
                            List.of("nova-tag"));

            repository.update(atualizado, 1);

            Reminder found = repository.findById("promo").orElseThrow();
            assertThat(found.name()).isEqualTo("Novo Nome");
            assertThat(found.enabled()).isFalse();
            assertThat(found.cron()).isEqualTo("0 0 10 * * FRI");
            assertThat(found.timezone()).isEqualTo("America/New_York");
            assertThat(found.message()).isEqualTo("nova msg");
            assertThat(found.mediaUrl()).isEqualTo("https://exemplo.com/x.mp4");
            assertThat(found.targetChatIds()).containsExactly("-100999");
            assertThat(found.tags()).containsExactly("nova-tag");
            assertThat(found.version()).isEqualTo(2);
        }

        @Test
        @DisplayName("version errada: OptimisticLockingFailureException")
        void versionErradaLancaExcecao() {
            repository.save(reminder("promo", true));

            Reminder atualizado = reminder("promo", true);

            assertThatThrownBy(() -> repository.update(atualizado, 99))
                    .isInstanceOf(OptimisticLockingFailureException.class)
                    .hasMessageContaining("promo");
        }

        @Test
        @DisplayName("trigger trg_reminders_updated_at está instalado e é BEFORE UPDATE")
        void updatedAtTriggerInstalado() {
            // Não testamos o *comportamento* do trigger via updated_at porque o
            // @JdbcTest do Spring envolve cada teste em uma transação, e NOW()
            // em Postgres é transaction_timestamp() — todos os statements do teste
            // compartilham o mesmo timestamp. O trigger sobrescreveria updated_at
            // com o mesmo valor, tornando o teste inútil.
            //
            // Em vez disso, testamos que o trigger EXISTE no catálogo do Postgres,
            // é BEFORE UPDATE, FOR EACH ROW, e aponta para a função set_updated_at().
            // Isso cobre o que importa: se a V4 for revertida ou o trigger removido,
            // o teste falha.
            Map<String, Object> trigger =
                    jdbcTemplate.queryForMap(
                            """
                            SELECT t.tgname AS name,
                                   t.tgtype AS type,
                                   p.proname AS function
                              FROM pg_trigger t
                              JOIN pg_proc p ON p.oid = t.tgfoid
                              JOIN pg_class c ON c.oid = t.tgrelid
                             WHERE c.relname = 'reminders'
                               AND t.tgname = 'trg_reminders_updated_at'
                            """);

            assertThat(trigger)
                    .isNotNull()
                    .containsEntry("name", "trg_reminders_updated_at")
                    .containsEntry("function", "set_updated_at");

            // tgtype é uma bitmask: verifica que é BEFORE (bit 1) e ROW (bit 0)
            // https://www.postgresql.org/docs/current/catalog-pg-trigger.html
            int tgtype = ((Number) trigger.get("type")).intValue();
            boolean isBefore = (tgtype & 2) != 0; // TRIGGER_TYPE_BEFORE
            boolean isRow = (tgtype & 1) != 0; // TRIGGER_TYPE_ROW
            assertThat(isBefore).as("trigger deve ser BEFORE").isTrue();
            assertThat(isRow).as("trigger deve ser FOR EACH ROW").isTrue();
        }
    }

    // =========================================================================
    // deleteById()
    // =========================================================================

    @Nested
    @DisplayName("deleteById()")
    class DeleteById {

        @Test
        @DisplayName("remove pai + filhas em cascata")
        void removeComCascata() {
            repository.save(reminder("promo", true));
            assertThat(repository.existsById("promo")).isTrue();

            boolean removed = repository.deleteById("promo");

            assertThat(removed).isTrue();
            assertThat(repository.existsById("promo")).isFalse();

            Integer chats =
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM reminder_chats WHERE reminder_id = ?",
                            Integer.class,
                            "promo");
            Integer tags =
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM reminder_tags WHERE reminder_id = ?",
                            Integer.class,
                            "promo");
            assertThat(chats).isZero();
            assertThat(tags).isZero();
        }

        @Test
        @DisplayName("id inexistente: retorna false")
        void inexistente() {
            assertThat(repository.deleteById("nao-existe")).isFalse();
        }

        @Test
        @DisplayName("id nulo/vazio: retorna false sem tocar no banco")
        void idInvalido() {
            assertThat(repository.deleteById(null)).isFalse();
            assertThat(repository.deleteById("")).isFalse();
            assertThat(repository.deleteById("   ")).isFalse();
        }
    }

    // =========================================================================
    // count() / existsById()
    // =========================================================================

    @Nested
    @DisplayName("count() / existsById()")
    class CountExists {

        @Test
        @DisplayName("count reflete inserções")
        void count() {
            assertThat(repository.count()).isZero();
            repository.save(reminderSimples("a"));
            repository.save(reminderSimples("b"));
            assertThat(repository.count()).isEqualTo(2);
        }

        @Test
        @DisplayName("existsById retorna true/false corretamente")
        void exists() {
            repository.save(reminderSimples("a"));
            assertThat(repository.existsById("a")).isTrue();
            assertThat(repository.existsById("b")).isFalse();
            assertThat(repository.existsById(null)).isFalse();
        }
    }
}
