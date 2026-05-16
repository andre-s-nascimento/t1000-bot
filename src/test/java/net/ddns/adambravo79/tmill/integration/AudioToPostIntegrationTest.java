/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.ddns.adambravo79.tmill.client.TmdbClient;
import net.ddns.adambravo79.tmill.model.MovieRecord;
import net.ddns.adambravo79.tmill.model.MovieSearchResponse;

class AudioToPostIntegrationTest {

    @Test
    void fluxoDeBuscaDeFilme() {
        var tmdbClient = mock(TmdbClient.class);
        var registro =
                new MovieRecord(
                        1L, "Duna", "Dune", "2021", "desc", 8.5, 9.0, "poster.jpg", List.of());
        when(tmdbClient.pesquisarFilme("duna"))
                .thenReturn(new MovieSearchResponse(1, 1, 1, List.of(registro)));

        var resultado = tmdbClient.pesquisarFilme("duna");

        assertThat(resultado.results()).hasSize(1);
        assertThat(resultado.results().get(0).title()).isEqualTo("Duna");
        verify(tmdbClient).pesquisarFilme("duna");
    }
}
