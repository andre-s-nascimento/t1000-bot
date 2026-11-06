/* (c) 2026 | 06/11/2026 */
package net.ddns.adambravo79.tmill.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Testes do record {@link Reminder}.
 *
 * <p>Embora {@code model/**} esteja excluído da cobertura JaCoCo e do Sonar, testamos as
 * <b>validações do construtor compacto</b> e os helpers ({@link Reminder#displayName()}, {@link
 * Reminder#effectiveTimezone()}, {@link Reminder#withEnabled(boolean)}) por robustez — um bug aqui
 * afeta o scheduler inteiro (cron inválido derruba agendamento; lista mutável quebra thread-safety).
 *
 * <p><b>Nota sobre nomes de classe interna:</b> as classes {@link Nested} deste arquivo <b>não</b>
 * podem se chamar {@code DisplayName}, porque o Java dá precedência a símbolos da própria classe
 * sobre imports estáticos — e {@code @DisplayName} quebraria. Por isso a classe do helper é
 * {@link NomeExibicao}.
 */
class ReminderTest {

    private static final String ID = "quatro-horas-quarta";
    private static final String CRON = "0 0 16 * * WED";
    private static final String TZ = "America/Sao_Paulo";
    private static final String MESSAGE = "<b>Lembrete!</b>";

    // =========================================================================
    // FIXTURES
    // =========================================================================

    private Reminder valido() {
        return new Reminder(
                ID,
                "Quatro Horas",
                true,
                CRON,
                TZ,
                MESSAGE,
                null,
                List.of("-100123"),
                List.of("cinema"));
    }

    private Reminder reminderComId(String id) {
        return new Reminder(id, "nome", true, CRON, TZ, MESSAGE, null, List.of("-100"), List.of());
    }

    // =========================================================================
    // CONSTRUTOR COMPACTO — SUCESSO
    // =========================================================================

    @Nested
    @DisplayName("Construtor compacto — sucesso")
    class ConstrutorSucesso {

        @Test
        @DisplayName("aceita reminder válido e preserva todos os campos")
        void camposPreservados() {
            Reminder r = valido();

            assertThat(r.id()).isEqualTo(ID);
            assertThat(r.name()).isEqualTo("Quatro Horas");
            assertThat(r.enabled()).isTrue();
            assertThat(r.cron()).isEqualTo(CRON);
            assertThat(r.timezone()).isEqualTo(TZ);
            assertThat(r.message()).isEqualTo(MESSAGE);
            assertThat(r.mediaUrl()).isNull();
            assertThat(r.targetChatIds()).containsExactly("-100123");
            assertThat(r.tags()).containsExactly("cinema");
        }

        @Test
        @DisplayName("targetChatIds e tags nulos viram lista vazia")
        void listasNulasViramVazias() {
            Reminder r =
                    new Reminder(
                            ID,
                            "n",
                            true,
                            CRON,
                            TZ,
                            MESSAGE,
                            null,
                            /* chats */ null,
                            /* tags */ null);

            assertThat(r.targetChatIds()).isNotNull().isEmpty();
            assertThat(r.tags()).isNotNull().isEmpty();
        }

        @ParameterizedTest(name = "[{index}] id=''{0}''")
        @ValueSource(strings = {"a", "abc", "a-b-c", "promo-sexta", "reuniao-2026", "x1"})
        @DisplayName("aceita IDs válidos (slug kebab-case)")
        void idsValidos(String id) {
            assertThat(reminderComId(id).id()).isEqualTo(id);
        }
    }

    // =========================================================================
    // CONSTRUTOR COMPACTO — VALIDAÇÕES
    // =========================================================================

    @Nested
    @DisplayName("Construtor compacto — validações")
    class ConstrutorInvalido {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t"})
        @DisplayName("id nulo/vazio/branco: IllegalArgumentException")
        void idNuloVazio(String id) {
            assertThatThrownBy(() -> reminderComId(id))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("id");
        }

        @ParameterizedTest(name = "[{index}] id=''{0}''")
        @ValueSource(
                strings = {
                    "ABC", // maiúsculas
                    "Abc", // maiúscula inicial
                    "1abc", // começa com número
                    "-abc", // começa com hífen
                    "a_b", // underscore
                    "a b", // espaço
                    "a.b", // ponto
                    "a@b", // arroba
                    "açúcar", // acento
                    "a/b", // barra
                    "a:b" // dois-pontos
                })
        @DisplayName("id com formato inválido: IllegalArgumentException")
        void idFormatoInvalido(String id) {
            assertThatThrownBy(() -> reminderComId(id))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("id");
        }

        @Test
        @DisplayName("cron nulo: IllegalArgumentException")
        void cronNulo() {
            assertThatThrownBy(
                            () ->
                                    new Reminder(
                                            ID, "n", true, null, TZ, MESSAGE, null, List.of(),
                                            List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cron");
        }

        @Test
        @DisplayName("cron vazio: IllegalArgumentException")
        void cronVazio() {
            assertThatThrownBy(
                            () ->
                                    new Reminder(
                                            ID, "n", true, "  ", TZ, MESSAGE, null, List.of(),
                                            List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cron");
        }

        @Test
        @DisplayName("message nula: IllegalArgumentException")
        void messageNula() {
            assertThatThrownBy(
                            () ->
                                    new Reminder(
                                            ID, "n", true, CRON, TZ, null, null, List.of(),
                                            List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("message");
        }

        @Test
        @DisplayName("message vazia: IllegalArgumentException")
        void messageVazia() {
            assertThatThrownBy(
                            () ->
                                    new Reminder(
                                            ID, "n", true, CRON, TZ, "   ", null, List.of(),
                                            List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("message");
        }
    }

    // =========================================================================
    // IMUTABILIDADE DAS LISTAS
    // =========================================================================

    @Nested
    @DisplayName("Imutabilidade das listas")
    class Imutabilidade {

        @Test
        @DisplayName("targetChatIds é imutável (List.copyOf)")
        void targetChatIdsImutavel() {
            Reminder r = valido();
            // ✅ S5778: extrair a lista ANTES do assertThatThrownBy
            //    (deixa só o .add() como invocação que pode lançar)
            List<String> lista = r.targetChatIds();

            assertThatThrownBy(() -> lista.add("novo"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("tags é imutável (List.copyOf)")
        void tagsImutavel() {
            Reminder r = valido();
            // ✅ S5778: extrair a lista ANTES do assertThatThrownBy
            List<String> lista = r.tags();

            assertThatThrownBy(() -> lista.add("nova"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("alterar a lista original NÃO afeta o record")
        void listaOriginalNaoAfetaRecord() {
            List<String> chats = new ArrayList<>(List.of("-100"));
            List<String> tags = new ArrayList<>(List.of("cinema"));
            Reminder r = new Reminder(ID, "n", true, CRON, TZ, MESSAGE, null, chats, tags);

            chats.add("-200");
            tags.add("semanal");

            assertThat(r.targetChatIds()).containsExactly("-100");
            assertThat(r.tags()).containsExactly("cinema");
        }
    }

    // =========================================================================
    // displayName()
    // =========================================================================

    @Nested
    @DisplayName("displayName()")
    class NomeExibicao {

        @Test
        @DisplayName("com name preenchido: retorna name")
        void comName() {
            Reminder r =
                    new Reminder(
                            ID, "Nome Bonito", true, CRON, TZ, MESSAGE, null, List.of(), List.of());
            assertThat(r.displayName()).isEqualTo("Nome Bonito");
        }

        @Test
        @DisplayName("com name nulo: retorna id")
        void nameNulo() {
            Reminder r =
                    new Reminder(ID, null, true, CRON, TZ, MESSAGE, null, List.of(), List.of());
            assertThat(r.displayName()).isEqualTo(ID);
        }

        @Test
        @DisplayName("com name vazio: retorna id")
        void nameVazio() {
            Reminder r =
                    new Reminder(ID, "  ", true, CRON, TZ, MESSAGE, null, List.of(), List.of());
            assertThat(r.displayName()).isEqualTo(ID);
        }
    }

    // =========================================================================
    // effectiveTimezone()
    // =========================================================================

    @Nested
    @DisplayName("effectiveTimezone()")
    class FusoEfetivo {

        @Test
        @DisplayName("com timezone preenchido: retorna timezone")
        void comTimezone() {
            assertThat(valido().effectiveTimezone()).isEqualTo(TZ);
        }

        @Test
        @DisplayName("com timezone nulo: retorna default da JVM")
        void tzNulo() {
            Reminder r =
                    new Reminder(ID, "n", true, CRON, null, MESSAGE, null, List.of(), List.of());
            assertThat(r.effectiveTimezone()).isEqualTo(ZoneId.systemDefault().getId());
        }

        @Test
        @DisplayName("com timezone vazio: retorna default da JVM")
        void tzVazio() {
            Reminder r =
                    new Reminder(ID, "n", true, CRON, "  ", MESSAGE, null, List.of(), List.of());
            assertThat(r.effectiveTimezone()).isEqualTo(ZoneId.systemDefault().getId());
        }
    }

    // =========================================================================
    // withEnabled()
    // =========================================================================

    @Nested
    @DisplayName("withEnabled()")
    class ComEnabled {

        @Test
        @DisplayName("altera enabled e preserva todos os outros campos")
        void alteraEPreserva() {
            Reminder original = valido();

            Reminder alterado = original.withEnabled(false);

            assertThat(alterado.enabled()).isFalse();
            assertThat(alterado.id()).isEqualTo(original.id());
            assertThat(alterado.name()).isEqualTo(original.name());
            assertThat(alterado.cron()).isEqualTo(original.cron());
            assertThat(alterado.timezone()).isEqualTo(original.timezone());
            assertThat(alterado.message()).isEqualTo(original.message());
            assertThat(alterado.mediaUrl()).isEqualTo(original.mediaUrl());
            assertThat(alterado.targetChatIds()).isEqualTo(original.targetChatIds());
            assertThat(alterado.tags()).isEqualTo(original.tags());
        }

        @Test
        @DisplayName("original NÃO é mutado (record é imutável)")
        void originalNaoMuta() {
            Reminder original = valido();
            original.withEnabled(false);

            assertThat(original.enabled()).isTrue();
        }

        @Test
        @DisplayName("withEnabled com mesmo valor retorna record equivalente")
        void mesmoValor() {
            Reminder original = valido();

            assertThat(original.withEnabled(true)).isEqualTo(original);
        }
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
            Reminder a = valido();
            Reminder b = valido();

            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        @DisplayName("records com enabled diferente NÃO são iguais")
        void enabledDiferente() {
            Reminder a = valido();
            Reminder b = a.withEnabled(false);

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("records com cron diferente NÃO são iguais")
        void cronDiferente() {
            Reminder a = valido();
            Reminder b =
                    new Reminder(
                            ID,
                            "n",
                            true,
                            "0 0 10 * * FRI",
                            TZ,
                            MESSAGE,
                            null,
                            List.of("-100"),
                            List.of());

            assertThat(a).isNotEqualTo(b);
        }
    }
}
