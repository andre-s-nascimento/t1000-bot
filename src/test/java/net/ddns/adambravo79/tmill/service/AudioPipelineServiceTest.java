/* (c) 2026 | 02/05/2026 */
package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.ddns.adambravo79.tmill.cache.TranscricaoCache;
import net.ddns.adambravo79.tmill.client.GroqClient;
import net.ddns.adambravo79.tmill.exception.AudioProcessingException;

class AudioPipelineServiceTest {

    @TempDir Path tempDir;

    @Test
    void deveProcessarFluxoCompleto() throws Exception {
        var audio = mock(AudioService.class);
        var groq = mock(GroqClient.class);
        var cache = mock(TranscricaoCache.class);
        var transcriptStoreService = mock(TranscriptStoreService.class);
        var service = new AudioPipelineService(audio, groq, cache, transcriptStoreService);

        File input = Files.createFile(tempDir.resolve("a.oga")).toFile();
        File wav = Files.createFile(tempDir.resolve("a.wav")).toFile();

        when(audio.converterParaWav(input)).thenReturn(CompletableFuture.completedFuture(wav));
        when(groq.transcrever(wav)).thenReturn("Bruto");
        when(groq.refinarTexto("Bruto")).thenReturn("Refinado");

        List<String> chamadas = new ArrayList<>();
        List<Boolean> finais = new ArrayList<>();

        service.processarFluxoAudio(
                input,
                1L,
                1L,
                "Usuário Teste",
                (texto, isUltima) -> {
                    chamadas.add(texto);
                    finais.add(isUltima);
                });

        assertThat(chamadas).contains("🎙️ *Bruto:* \n_Bruto_", "✨ *Refinado:* \nRefinado");
        assertThat(finais).containsExactly(false, true);
        verify(cache).salvar(1L, "Refinado");
        assertThat(input).doesNotExist();
        assertThat(wav).doesNotExist();
    }

    @Test
    void deveLancarExcecaoQuandoConversaoFalhar() throws Exception {
        var audio = mock(AudioService.class);
        var groq = mock(GroqClient.class);
        var cache = mock(TranscricaoCache.class);
        var transcriptStoreService = mock(TranscriptStoreService.class);
        var service = new AudioPipelineService(audio, groq, cache, transcriptStoreService);

        File input = Files.createFile(tempDir.resolve("a.oga")).toFile();

        when(audio.converterParaWav(input))
                .thenReturn(
                        CompletableFuture.failedFuture(new RuntimeException("Falha na conversão")));

        assertThatThrownBy(
                        () ->
                                service.processarFluxoAudio(
                                        input, 1L, 1L, "Usuário Teste", (t, b) -> {}))
                .isInstanceOf(AudioProcessingException.class)
                .hasMessageContaining("Erro inesperado no pipeline de áudio");

        verifyNoInteractions(cache);
    }

    @Test
    void deveLancarExcecaoQuandoTranscricaoFalhar() throws Exception {
        var audio = mock(AudioService.class);
        var groq = mock(GroqClient.class);
        var cache = mock(TranscricaoCache.class);
        var transcriptStoreService = mock(TranscriptStoreService.class);
        var service = new AudioPipelineService(audio, groq, cache, transcriptStoreService);

        File input = Files.createFile(tempDir.resolve("a.oga")).toFile();
        File wav = Files.createFile(tempDir.resolve("a.wav")).toFile();

        when(audio.converterParaWav(input)).thenReturn(CompletableFuture.completedFuture(wav));
        when(groq.transcrever(wav)).thenThrow(new RuntimeException("Falha na transcrição"));

        assertThatThrownBy(
                        () ->
                                service.processarFluxoAudio(
                                        input, 1L, 1L, "Usuário Teste", (t, b) -> {}))
                .isInstanceOf(AudioProcessingException.class)
                .hasMessageContaining("Erro inesperado no pipeline de áudio");

        assertThat(wav).doesNotExist();
        verifyNoInteractions(cache);
    }

    @Test
    void deveLancarExcecaoQuandoRefinoFalhar() throws Exception {
        var audio = mock(AudioService.class);
        var groq = mock(GroqClient.class);
        var cache = mock(TranscricaoCache.class);
        var transcriptStoreService = mock(TranscriptStoreService.class);
        var service = new AudioPipelineService(audio, groq, cache, transcriptStoreService);

        File input = Files.createFile(tempDir.resolve("a.oga")).toFile();
        File wav = Files.createFile(tempDir.resolve("a.wav")).toFile();

        when(audio.converterParaWav(input)).thenReturn(CompletableFuture.completedFuture(wav));
        when(groq.transcrever(wav)).thenReturn("Bruto");
        // Simula uma exceção real (não de tamanho) – por exemplo, erro de rede
        when(groq.refinarTexto("Bruto")).thenThrow(new RuntimeException("Falha no refino"));

        assertThatThrownBy(
                        () ->
                                service.processarFluxoAudio(
                                        input, 1L, 1L, "Usuário Teste", (t, b) -> {}))
                .isInstanceOf(AudioProcessingException.class)
                .hasMessageContaining("Erro inesperado no pipeline de áudio");

        assertThat(wav).doesNotExist();
        verifyNoInteractions(cache);
    }
}
