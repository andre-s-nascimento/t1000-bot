/* (c) 2026 | 01/10/2026 */
package net.ddns.adambravo79.tmill.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DigestPersonaTest {

    @Test
    @DisplayName("Deve manter construtores das personas padrao estaticas")
    void shouldMaintainStaticDefaultPersonas() {
        assertThat(DigestPersona.T1000.getId()).isEqualTo("T1000");
        assertThat(DigestPersona.BICENTENNIAL.getId()).isEqualTo("BICENTENNIAL");
        assertThat(DigestPersona.MATRIX_ARCHITECT.getId()).isEqualTo("MATRIX_ARCHITECT");
        assertThat(DigestPersona.ANALISTA.getId()).isEqualTo("ANALISTA");
    }

    @Test
    @DisplayName("Deve permitir criar personas dinamicas via fromString")
    void shouldCreateDynamicPersonaFromString() {
        DigestPersona custom = DigestPersona.fromString("edgard_edgardino");

        assertThat(custom.getId()).isEqualTo("EDGARD_EDGARDINO");
    }

    @Test
    @DisplayName("Deve retornar ANALISTA como fallback quando name for nulo ou em branco")
    void shouldFallbackToAnalistaWhenNameIsBlank() {
        assertThat(DigestPersona.fromString(null)).isEqualTo(DigestPersona.ANALISTA);
        assertThat(DigestPersona.fromString("   ")).isEqualTo(DigestPersona.ANALISTA);
    }

    @Test
    @DisplayName("Deve lancar IllegalArgumentException ao instanciar diretamente com ID invalido")
    void shouldThrowExceptionForInvalidId() {
        assertThatThrownBy(() -> new DigestPersona(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("O ID da persona não pode ser nulo ou vazio");
    }

    @Test
    @DisplayName("Deve comparar duas personas corretamente via equals e hashCode")
    void shouldEqualAndHashCodeCorrectly() {
        DigestPersona p1 = new DigestPersona("T1000");
        DigestPersona p2 = DigestPersona.fromString("t1000");

        assertThat(p1).isEqualTo(p2);
        assertThat(p1.hashCode()).hasSameHashCodeAs(p2.hashCode());
    }
}
