/* (c) 2026 | 27/04/2026 */
package net.ddns.adambravo79.tmill.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import net.ddns.adambravo79.tmill.cache.TranscricaoCache;
import net.ddns.adambravo79.tmill.client.BloggerClient;
import net.ddns.adambravo79.tmill.client.GroqClient;
import net.ddns.adambravo79.tmill.client.TmdbClient;
import net.ddns.adambravo79.tmill.model.MovieRecord;
import net.ddns.adambravo79.tmill.model.MovieSearchResponse;
import net.ddns.adambravo79.tmill.service.AudioPipelineService;
import net.ddns.adambravo79.tmill.service.AudioService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;

class AudioToPostIntegrationTest {

    @Test
    void fluxoCompletoDeAudioParaPostagemComBlogger() {
        var groqClient = mock(GroqClient.class);
        var telegramFacade = mock(TelegramFacade.class);
        var audioService = mock(AudioService.class);
        var bloggerClient = mock(BloggerClient.class);
        var cache = new TranscricaoCache();

        // Stub do AudioService
        File wavFile = new File("fake.wav");
        when(audioService.converterParaWav(any(File.class)))
                .thenReturn(CompletableFuture.completedFuture(wavFile));

        // Stub do GroqClient
        when(groqClient.transcrever(any(File.class))).thenReturn("texto bruto");
        when(groqClient.refinarTexto("texto bruto")).thenReturn("texto refinado");

        // Stub do BloggerClient
        when(bloggerClient.criarRascunho("Post automático", "texto refinado"))
                .thenReturn("http://blogger.com/post/123");

        // Pipeline de áudio
        var pipeline = new AudioPipelineService(audioService, groqClient, cache);

        // Executa pipeline
        pipeline.processarFluxoAudio(
                new File("fake.oga"),
                123L,
                (msg, isUltima) -> telegramFacade.enviarMensagem(123L, msg));

        // ✅ Verifica cache
        assertThat(cache.recuperar(123L)).isEqualTo("texto refinado");

        // ✅ Publica no Blogger
        String url = bloggerClient.criarRascunho("Post automático", cache.recuperar(123L));
        telegramFacade.enviarMensagem(123L, "✅ Publicado: " + url);

        // ✅ Verificações finais
        verify(telegramFacade, atLeast(3))
                .enviarMensagem(eq(123L), anyString()); // bruto + refinado
        verify(bloggerClient).criarRascunho("Post automático", "texto refinado");
        verify(telegramFacade).enviarMensagem(123L, "✅ Publicado: http://blogger.com/post/123");
    }

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
