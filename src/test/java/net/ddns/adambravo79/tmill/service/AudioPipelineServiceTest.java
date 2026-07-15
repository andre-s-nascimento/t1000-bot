package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.HttpClientErrorException;

import net.ddns.adambravo79.tmill.client.GroqClient;
import net.ddns.adambravo79.tmill.exception.AudioProcessingException;
import net.ddns.adambravo79.tmill.service.cache.ChatTranscriptionCache;

class AudioPipelineServiceTest {

    @TempDir Path tempDir;

    // =========================
    // TESTES DE processarFluxoAudio (já existentes)
    // =========================

    @Test
    void deveProcessarFluxoCompleto() throws Exception {
        var audio = mock(AudioService.class);
        var groq = mock(GroqClient.class);
        var cache = mock(ChatTranscriptionCache.class);
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
        var cache = mock(ChatTranscriptionCache.class);
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
        var cache = mock(ChatTranscriptionCache.class);
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

    // =========================
    // NOVOS TESTES PARA processarEArmazenar E retryRefinamento
    // =========================

    @Test
    void deveProcessarEArmazenarComSucesso() throws Exception {
        var audio = mock(AudioService.class);
        var groq = mock(GroqClient.class);
        var cache = mock(ChatTranscriptionCache.class);
        var transcriptStoreService = mock(TranscriptStoreService.class);
        var service = new AudioPipelineService(audio, groq, cache, transcriptStoreService);

        File input = Files.createFile(tempDir.resolve("a.oga")).toFile();
        File wav = Files.createFile(tempDir.resolve("a.wav")).toFile();

        when(audio.converterParaWav(input)).thenReturn(CompletableFuture.completedFuture(wav));
        when(groq.transcrever(wav)).thenReturn("Bruto");
        when(groq.refinarTexto("Bruto")).thenReturn("Refinado");

        CompletableFuture<AudioPipelineService.ProcessedAudio> future =
                service.processarEArmazenar(input, 1L, 1L, "Usuário Teste");
        AudioPipelineService.ProcessedAudio result = future.join();

        assertThat(result.bruto()).isEqualTo("Bruto");
        assertThat(result.refinado()).isEqualTo("Refinado");
        assertThat(input).doesNotExist();
        assertThat(wav).doesNotExist();
    }

    @Test
    void deveRetryNoRefinamentoQuandoRecebe429() throws Exception {
        var audio = mock(AudioService.class);
        var groq = mock(GroqClient.class);
        var cache = mock(ChatTranscriptionCache.class);
        var transcriptStoreService = mock(TranscriptStoreService.class);
        var service = new AudioPipelineService(audio, groq, cache, transcriptStoreService);

        File input = Files.createFile(tempDir.resolve("a.oga")).toFile();
        File wav = Files.createFile(tempDir.resolve("a.wav")).toFile();

        when(audio.converterParaWav(input)).thenReturn(CompletableFuture.completedFuture(wav));
        when(groq.transcrever(wav)).thenReturn("Bruto");

        // Simula 429 na primeira chamada, sucesso na segunda
        HttpClientErrorException.TooManyRequests tooManyRequests =
                mock(HttpClientErrorException.TooManyRequests.class);
        when(tooManyRequests.getMessage()).thenReturn("429 Too Many Requests: try again in 2.0s");
        when(groq.refinarTexto("Bruto")).thenThrow(tooManyRequests).thenReturn("Refinado");

        CompletableFuture<AudioPipelineService.ProcessedAudio> future =
                service.processarEArmazenar(input, 1L, 1L, "Usuário Teste");
        AudioPipelineService.ProcessedAudio result = future.join();

        assertThat(result.refinado()).isEqualTo("Refinado");
        verify(groq, times(2)).refinarTexto("Bruto"); // primeira falhou, segunda sucesso
        assertThat(input).doesNotExist();
        assertThat(wav).doesNotExist();
    }

    @Test
    void deveFalharNoRefinamentoAposTodasTentativas() throws Exception {
        var audio = mock(AudioService.class);
        var groq = mock(GroqClient.class);
        var cache = mock(ChatTranscriptionCache.class);
        var transcriptStoreService = mock(TranscriptStoreService.class);
        var service = new AudioPipelineService(audio, groq, cache, transcriptStoreService);

        File input = Files.createFile(tempDir.resolve("a.oga")).toFile();
        File wav = Files.createFile(tempDir.resolve("a.wav")).toFile();

        when(audio.converterParaWav(input)).thenReturn(CompletableFuture.completedFuture(wav));
        when(groq.transcrever(wav)).thenReturn("Bruto");

        HttpClientErrorException.TooManyRequests tooManyRequests =
                mock(HttpClientErrorException.TooManyRequests.class);
        when(tooManyRequests.getMessage()).thenReturn("429 Too Many Requests: try again in 2.0s");
        when(groq.refinarTexto("Bruto")).thenThrow(tooManyRequests);

        CompletableFuture<AudioPipelineService.ProcessedAudio> future =
                service.processarEArmazenar(input, 1L, 1L, "Usuário Teste");

        assertThatThrownBy(future::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("Falha no refinamento após 4 tentativas");
        assertThat(input).doesNotExist();
        assertThat(wav).doesNotExist();
    }
}
