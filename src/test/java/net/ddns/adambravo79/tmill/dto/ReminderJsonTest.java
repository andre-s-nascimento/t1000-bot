/* (c) 2026 | 06/11/2026 */
package net.ddns.adambravo79.tmill.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.ddns.adambravo79.tmill.model.Reminder;

/**
 * Testes do record {@link ReminderJson}.
 *
 * <p>Embora {@code dto/**} esteja excluído da cobertura JaCoCo e do Sonar, testamos o construtor
 * compacto (normalização de {@code version} e {@code reminders} nulos), as fábricas ({@link
 * ReminderJson#of(List)}, {@link ReminderJson#withReminders(List)}, {@link ReminderJson#empty()}) e
 * a imutabilidade da lista interna.
 *
 * <p><b>Não há helpers com mesmo nome de método de teste</b> — para evitar colisão de escopo (o bug
 * clássico que aconteceu no {@code ReminderTest}). Os helpers aqui são {@code criaReminder(...)} e
 * {@code jsonComReminders(...)}.
 */
class ReminderJsonTest {

    private static final int CURRENT_VERSION = 1;
    private static final String CRON = "0 0 16 * * WED";
    private static final String TZ = "America/Sao_Paulo";
    private static final String MESSAGE = "<b>Lembrete!</b>";

    // =========================================================================
    // HELPERS (nomes diferentes dos métodos de teste — sem shadowing)
    // =========================================================================

    private Reminder criaReminder(String id) {
        return new Reminder(
                id, "Nome " + id, true, CRON, TZ, MESSAGE, null, List.of("-100"), List.of());
    }

    private ReminderJson jsonComReminders(Reminder... reminders) {
        return ReminderJson.of(List.of(reminders));
    }

    // =========================================================================
    // CONSTRUTOR COMPACTO — NORMALIZAÇÃO
    // =========================================================================

    @Nested
    @DisplayName("Construtor compacto — normalização")
    class ConstrutorCompacto {

        @Test
        @DisplayName("preserva os valores quando version e reminders são válidos")
        void preservaValoresValidos() {
            Reminder a = criaReminder("a");
            Reminder b = criaReminder("b");
            ReminderJson json = new ReminderJson(1, "2026-11-06T10:00:00Z", List.of(a, b));

            assertThat(json.version()).isEqualTo(1);
            assertThat(json.updatedAt()).isEqualTo("2026-11-06T10:00:00Z");
            assertThat(json.reminders()).containsExactly(a, b);
        }

        @Test
        @DisplayName("version nulo vira CURRENT_VERSION")
        void versionNulaNormaliza() {
            ReminderJson json = new ReminderJson(null, null, List.of());

            assertThat(json.version()).isEqualTo(CURRENT_VERSION);
        }

        @Test
        @DisplayName("version zero ou negativa vira CURRENT_VERSION")
        void versionInvalidaNormaliza() {
            assertThat(new ReminderJson(0, null, List.of()).version()).isEqualTo(CURRENT_VERSION);
            assertThat(new ReminderJson(-1, null, List.of()).version()).isEqualTo(CURRENT_VERSION);
        }

        @Test
        @DisplayName("version maior que CURRENT_VERSION é preservada (forward-compat)")
        void versionFuturaPreservada() {
            ReminderJson json = new ReminderJson(99, null, List.of());

            assertThat(json.version()).isEqualTo(99);
        }

        @Test
        @DisplayName("reminders nulo vira lista vazia")
        void remindersNuloViraListaVazia() {
            ReminderJson json = new ReminderJson(1, null, null);

            assertThat(json.reminders()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("updatedAt nulo é preservado (JSON recém-criado)")
        void updatedAtNuloPreservado() {
            ReminderJson json = new ReminderJson(1, null, List.of());

            assertThat(json.updatedAt()).isNull();
        }
    }

    // =========================================================================
    // IMUTABILIDADE DA LISTA
    // =========================================================================

    @Nested
    @DisplayName("Imutabilidade da lista")
    class Imutabilidade {

        @Test
        @DisplayName("reminders é imutável (List.copyOf)")
        void remindersImutavel() {
            ReminderJson json = jsonComReminders(criaReminder("a"));
            Reminder novo = criaReminder("b"); // ← extraído
            List<Reminder> lista = json.reminders();

            assertThatThrownBy(() -> lista.add(novo))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("alterar a lista original NÃO afeta o record")
        void listaOriginalNaoAfetaRecord() {
            List<Reminder> original = new ArrayList<>();
            original.add(criaReminder("a"));
            ReminderJson json = new ReminderJson(1, null, original);

            original.add(criaReminder("b"));

            assertThat(json.reminders()).hasSize(1);
        }
    }

    // =========================================================================
    // of(List)
    // =========================================================================

    @Nested
    @DisplayName("of(List)")
    class FactoryOf {

        @Test
        @DisplayName("cria envelope com version = CURRENT_VERSION")
        void versionAtual() {
            ReminderJson json = ReminderJson.of(List.of(criaReminder("a")));

            assertThat(json.version()).isEqualTo(CURRENT_VERSION);
        }

        @Test
        @DisplayName("preenche updatedAt com timestamp ISO-8601 do presente")
        void updatedAtPreenchido() {
            Instant antes = Instant.now().minusSeconds(2);
            ReminderJson json = ReminderJson.of(List.of(criaReminder("a")));
            Instant depois = Instant.now().plusSeconds(2);

            assertThat(json.updatedAt()).isNotNull();
            Instant atual = Instant.parse(json.updatedAt());
            assertThat(atual).isBetween(antes, depois);
        }

        @Test
        @DisplayName("copia os reminders informados")
        void copiaReminders() {
            Reminder a = criaReminder("a");
            Reminder b = criaReminder("b");

            ReminderJson json = ReminderJson.of(List.of(a, b));

            assertThat(json.reminders()).containsExactly(a, b);
        }

        @Test
        @DisplayName("lista nula vira lista vazia")
        void listaNulaViraVazia() {
            ReminderJson json = ReminderJson.of(null);

            assertThat(json.reminders()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("lista vazia é preservada")
        void listaVazia() {
            ReminderJson json = ReminderJson.of(List.of());

            assertThat(json.reminders()).isEmpty();
        }
    }

    // =========================================================================
    // withReminders(List)
    // =========================================================================

    @Nested
    @DisplayName("withReminders(List)")
    class FactoryWithReminders {

        @Test
        @DisplayName("substitui a lista mantendo version")
        void substituiLista() {
            Reminder a = criaReminder("a");
            Reminder b = criaReminder("b");
            ReminderJson original = new ReminderJson(1, null, List.of(a));

            ReminderJson novo = original.withReminders(List.of(b));

            assertThat(novo.version()).isEqualTo(original.version());
            assertThat(novo.reminders()).containsExactly(b);
        }

        @Test
        @DisplayName("atualiza updatedAt para o presente")
        void atualizaUpdatedAt() {
            ReminderJson original =
                    new ReminderJson(1, "2020-01-01T00:00:00Z", List.of(criaReminder("a")));
            Instant antes = Instant.now().minusSeconds(2);

            ReminderJson novo = original.withReminders(List.of());

            Instant depois = Instant.now().plusSeconds(2);
            assertThat(novo.updatedAt()).isNotEqualTo(original.updatedAt());
            Instant atual = Instant.parse(novo.updatedAt());
            assertThat(atual).isBetween(antes, depois);
        }

        @Test
        @DisplayName("lista nula vira lista vazia")
        void listaNulaViraVazia() {
            ReminderJson original = jsonComReminders(criaReminder("a"));

            ReminderJson novo = original.withReminders(null);

            assertThat(novo.reminders()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("original NÃO é mutado (record é imutável)")
        void originalNaoMuta() {
            Reminder a = criaReminder("a");
            Reminder b = criaReminder("b");
            ReminderJson original = new ReminderJson(1, null, List.of(a));

            original.withReminders(List.of(b));

            assertThat(original.reminders()).containsExactly(a);
        }
    }

    // =========================================================================
    // empty()
    // =========================================================================

    @Nested
    @DisplayName("empty()")
    class FactoryEmpty {

        @Test
        @DisplayName("cria envelope com version = CURRENT_VERSION")
        void versionAtual() {
            ReminderJson json = ReminderJson.empty();

            assertThat(json.version()).isEqualTo(CURRENT_VERSION);
        }

        @Test
        @DisplayName("reminders é lista vazia")
        void listaVazia() {
            ReminderJson json = ReminderJson.empty();

            assertThat(json.reminders()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("updatedAt é nulo (nunca foi salvo)")
        void updatedAtNulo() {
            ReminderJson json = ReminderJson.empty();

            assertThat(json.updatedAt()).isNull();
        }
    }

    // =========================================================================
    // CURRENT_VERSION
    // =========================================================================

    @Test
    @DisplayName("CURRENT_VERSION é 1")
    void currentVersion() {
        assertThat(ReminderJson.CURRENT_VERSION).isEqualTo(1);
    }

    // =========================================================================
    // EQUALS / HASHCODE
    // =========================================================================

    @Nested
    @DisplayName("equals / hashCode")
    class EqualsHashCode {

        @Test
        @DisplayName("records com mesmos valores são iguais")
        void iguais() {
            Reminder a = criaReminder("a");
            ReminderJson x = new ReminderJson(1, "2026-11-06T10:00:00Z", List.of(a));
            ReminderJson y = new ReminderJson(1, "2026-11-06T10:00:00Z", List.of(a));

            assertThat(x).isEqualTo(y).hasSameHashCodeAs(y);
        }

        @Test
        @DisplayName("records com version diferente NÃO são iguais")
        void versionDiferente() {
            Reminder a = criaReminder("a");
            ReminderJson x = new ReminderJson(1, null, List.of(a));
            ReminderJson y = new ReminderJson(2, null, List.of(a));

            assertThat(x).isNotEqualTo(y);
        }

        @Test
        @DisplayName("records com updatedAt diferente NÃO são iguais")
        void updatedAtDiferente() {
            Reminder a = criaReminder("a");
            ReminderJson x = new ReminderJson(1, "2026-01-01T00:00:00Z", List.of(a));
            ReminderJson y = new ReminderJson(1, "2026-12-31T00:00:00Z", List.of(a));

            assertThat(x).isNotEqualTo(y);
        }
    }
}
