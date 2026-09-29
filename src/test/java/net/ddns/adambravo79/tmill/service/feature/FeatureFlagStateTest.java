/* (c) 2026 | 28/09/2026 */
package net.ddns.adambravo79.tmill.service.feature;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeatureFlagStateTest {

    @Test
    @DisplayName("fromDefinition copia todos os campos")
    void fromDefinition_copiesAllFields() {
        FeatureFlagDefinition def =
                new FeatureFlagDefinition("a.b.c", "Descrição original", true, false);

        FeatureFlagState state = FeatureFlagState.fromDefinition(def);

        assertThat(state.key()).isEqualTo("a.b.c");
        assertThat(state.description()).isEqualTo("Descrição original");
        assertThat(state.readOnly()).isTrue();
        assertThat(state.enabled()).isFalse();
    }

    @Test
    @DisplayName("withEnabled cria novo state com valor alterado e preserva metadados")
    void withEnabled_createsNewStatePreservingMetadata() {
        FeatureFlagState original = new FeatureFlagState("a", false, "Desc", false);

        FeatureFlagState alterado = original.withEnabled(true);

        assertThat(alterado.enabled()).isTrue();
        assertThat(alterado.key()).isEqualTo("a");
        assertThat(alterado.description()).isEqualTo("Desc");
        assertThat(alterado.readOnly()).isFalse();

        // original não é mutado (record é imutável)
        assertThat(original.enabled()).isFalse();
    }

    @Test
    @DisplayName("withEnabled para o mesmo valor retorna state equivalente")
    void withEnabled_sameValue() {
        FeatureFlagState original = new FeatureFlagState("a", true, "Desc", false);

        FeatureFlagState alterado = original.withEnabled(true);

        assertThat(alterado).isEqualTo(original);
    }
}
