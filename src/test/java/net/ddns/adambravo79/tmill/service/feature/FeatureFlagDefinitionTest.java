/* (c) 2026 | 28/09/2026 */
package net.ddns.adambravo79.tmill.service.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeatureFlagDefinitionTest {

    @Test
    @DisplayName("constrói com valores válidos")
    void constructsWithValidValues() {
        FeatureFlagDefinition def = new FeatureFlagDefinition("a.b.c", "Descrição", false, true);

        assertThat(def.key()).isEqualTo("a.b.c");
        assertThat(def.description()).isEqualTo("Descrição");
        assertThat(def.readOnly()).isFalse();
        assertThat(def.defaultValue()).isTrue();
    }

    @Test
    @DisplayName("key null → IllegalArgumentException")
    void nullKey_throws() {
        assertThatThrownBy(() -> new FeatureFlagDefinition(null, "Desc", false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key");
    }

    @Test
    @DisplayName("key em branco → IllegalArgumentException")
    void blankKey_throws() {
        assertThatThrownBy(() -> new FeatureFlagDefinition("   ", "Desc", false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key");
    }

    @Test
    @DisplayName("key vazia → IllegalArgumentException")
    void emptyKey_throws() {
        assertThatThrownBy(() -> new FeatureFlagDefinition("", "Desc", false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key");
    }

    @Test
    @DisplayName("equals e hashCode funcionam (record)")
    void equalsAndHashCode() {
        FeatureFlagDefinition a = new FeatureFlagDefinition("a", "D", false, true);
        FeatureFlagDefinition b = new FeatureFlagDefinition("a", "D", false, true);
        FeatureFlagDefinition c = new FeatureFlagDefinition("b", "D", false, true);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
    }
}
