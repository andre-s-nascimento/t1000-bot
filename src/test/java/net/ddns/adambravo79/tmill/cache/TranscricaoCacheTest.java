/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TranscricaoCacheTest {

    @Test
    void deveSalvarRecuperarRemover() {
        var cache = new TranscricaoCache();

        cache.salvar(1L, "texto");

        assertThat(cache.recuperar(1L)).isEqualTo("texto");

        cache.remover(1L);

        assertThat(cache.recuperar(1L)).isNull();
    }
}
