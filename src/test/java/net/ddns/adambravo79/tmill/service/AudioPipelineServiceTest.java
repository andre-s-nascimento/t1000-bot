package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;

import net.ddns.adambravo79.tmill.client.GroqClient;
import net.ddns.adambravo79.tmill.exception.AudioProcessingException;
import net.ddns.adambravo79.tmill.exception.GroqRateLimitException;
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

    // ============================================================
    // NOVOS TESTES (adicione no final da classe AudioPipelineServiceTest)
    // ============================================================

    // ===================== TESTES PARA processarFluxoAudio =====================

    @Test
    void processarFluxoAudio_deveLancarAudioProcessingException_quandoTranscricaoLancaHttpError()
            throws Exception {
        var audio = mock(AudioService.class);
        var groq = mock(GroqClient.class);
        var cache = mock(ChatTranscriptionCache.class);
        var transcriptStoreService = mock(TranscriptStoreService.class);
        var service = new AudioPipelineService(audio, groq, cache, transcriptStoreService);

        File input = Files.createFile(tempDir.resolve("a.oga")).toFile();
        File wav = Files.createFile(tempDir.resolve("a.wav")).toFile();

        when(audio.converterParaWav(input)).thenReturn(CompletableFuture.completedFuture(wav));
        when(groq.transcrever(wav))
                .thenThrow(
                        HttpClientErrorException.create(
                                HttpStatusCode.valueOf(400), "Bad Request", null, null, null));

        assertThatThrownBy(
                        () -> service.processarFluxoAudio(input, 1L, 1L, "Usuário", (t, b) -> {}))
                .isInstanceOf(AudioProcessingException.class)
                .hasMessageContaining("Falha na comunicação com Groq");
    }

    @Test
    void processarFluxoAudio_deveLancarGroqRateLimitException_quandoRefinamentoExcedeTentativas()
            throws Exception {
        var audio = mock(AudioService.class);
        var groq = mock(GroqClient.class);
        var cache = mock(ChatTranscriptionCache.class);
        var transcriptStoreService = mock(TranscriptStoreService.class);
        var service = new AudioPipelineService(audio, groq, cache, transcriptStoreService);

        File input = Files.createFile(tempDir.resolve("a.oga")).toFile();
        File wav = Files.createFile(tempDir.resolve("a.wav")).toFile();

        when(audio.converterParaWav(input)).thenReturn(CompletableFuture.completedFuture(wav));
        when(groq.transcrever(wav)).thenReturn("Bruto");

        HttpClientErrorException.TooManyRequests ex =
                mock(HttpClientErrorException.TooManyRequests.class);
        when(ex.getMessage()).thenReturn("429 Too Many Requests");
        when(groq.refinarTexto("Bruto")).thenThrow(ex);

        assertThatThrownBy(
                        () -> service.processarFluxoAudio(input, 1L, 1L, "Usuário", (t, b) -> {}))
                .isInstanceOf(GroqRateLimitException.class)
                .hasMessageContaining("Falha no refinamento após 4 tentativas");
    }

    @Test
    void
            processarFluxoAudio_deveLancarAudioProcessingException_quandoProcessarTranscricaoLancaRuntimeException()
                    throws Exception {
        var audio = mock(AudioService.class);
        var groq = mock(GroqClient.class);
        var cache = mock(ChatTranscriptionCache.class);
        var transcriptStoreService = mock(TranscriptStoreService.class);
        var service = new AudioPipelineService(audio, groq, cache, transcriptStoreService);

        File input = Files.createFile(tempDir.resolve("a.oga")).toFile();
        File wav = Files.createFile(tempDir.resolve("a.wav")).toFile();

        when(audio.converterParaWav(input)).thenReturn(CompletableFuture.completedFuture(wav));
        // Simula um NPE inesperado durante a transcrição
        when(groq.transcrever(wav)).thenThrow(new NullPointerException("NPE inesperado"));

        assertThatThrownBy(
                        () -> service.processarFluxoAudio(input, 1L, 1L, "Usuário", (t, b) -> {}))
                .isInstanceOf(AudioProcessingException.class)
                .hasMessageContaining("Erro inesperado no pipeline de áudio");
    }

    // ===================== TESTES PARA processarEArmazenar =====================

    @Test
    void processarEArmazenar_deveLancarIllegalArgumentException_quandoOgaFileNulo() {
        var audio = mock(AudioService.class);
        var groq = mock(GroqClient.class);
        var cache = mock(ChatTranscriptionCache.class);
        var transcriptStoreService = mock(TranscriptStoreService.class);
        var service = new AudioPipelineService(audio, groq, cache, transcriptStoreService);

        assertThatThrownBy(() -> service.processarEArmazenar(null, 1L, 1L, "Usuário"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ogaFile não pode ser nulo");
    }

    @Test
    void processarEArmazenar_deveCompletarExcepcionalmente_quandoConversaoFalha() throws Exception {
        var audio = mock(AudioService.class);
        var groq = mock(GroqClient.class);
        var cache = mock(ChatTranscriptionCache.class);
        var transcriptStoreService = mock(TranscriptStoreService.class);
        var service = new AudioPipelineService(audio, groq, cache, transcriptStoreService);

        File input = Files.createFile(tempDir.resolve("a.oga")).toFile();
        when(audio.converterParaWav(input))
                .thenReturn(
                        CompletableFuture.failedFuture(new RuntimeException("Falha na conversão")));

        CompletableFuture<AudioPipelineService.ProcessedAudio> future =
                service.processarEArmazenar(input, 1L, 1L, "Usuário");
        assertThatThrownBy(future::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("Falha na conversão");
    }

    @Test
    void processarEArmazenar_deveCompletarExcepcionalmente_quandoTranscricaoFalha()
            throws Exception {
        var audio = mock(AudioService.class);
        var groq = mock(GroqClient.class);
        var cache = mock(ChatTranscriptionCache.class);
        var transcriptStoreService = mock(TranscriptStoreService.class);
        var service = new AudioPipelineService(audio, groq, cache, transcriptStoreService);

        File input = Files.createFile(tempDir.resolve("a.oga")).toFile();
        File wav = Files.createFile(tempDir.resolve("a.wav")).toFile();

        when(audio.converterParaWav(input)).thenReturn(CompletableFuture.completedFuture(wav));
        when(groq.transcrever(wav)).thenThrow(new RuntimeException("Transcrição falhou"));

        CompletableFuture<AudioPipelineService.ProcessedAudio> future =
                service.processarEArmazenar(input, 1L, 1L, "Usuário");
        assertThatThrownBy(future::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(AudioProcessingException.class)
                .hasMessageContaining("Erro inesperado no processamento de áudio");
    }

    // ===================== TESTES PARA MÉTODOS PRIVADOS (via reflexão) =====================

    @Test
    void retryRefinamento_deveLancarAudioProcessingException_quandoHttpErrorNao429()
            throws Exception {
        var audio = mock(AudioService.class);
        var groq = mock(GroqClient.class);
        var cache = mock(ChatTranscriptionCache.class);
        var transcriptStoreService = mock(TranscriptStoreService.class);
        var service = new AudioPipelineService(audio, groq, cache, transcriptStoreService);

        Method method =
                AudioPipelineService.class.getDeclaredMethod("retryRefinamento", String.class);
        method.setAccessible(true);

        HttpClientErrorException httpError =
                HttpClientErrorException.create(
                        HttpStatusCode.valueOf(400), "Bad Request", null, null, null);
        when(groq.refinarTexto(anyString())).thenThrow(httpError);

        // Precisamos que o groqClient seja usado dentro do método, então passamos o mock
        // Mas como o método usa o campo groqClient, precisamos garantir que ele seja chamado.
        // Podemos usar doCallRealMethod() no spy, mas é mais fácil usar ReflectionTestUtils para
        // setar
        // o groqClient.
        // Como estamos testando o método privado, vamos invocá-lo com o objeto service que tem os
        // mocks.
        // Mas o método retryRefinamento usa o campo groqClient. Vamos definir o comportamento.
        // Então chamamos o método via reflexão.
        assertThatThrownBy(() -> method.invoke(service, "texto"))
                .hasCauseInstanceOf(AudioProcessingException.class);
    }

    @Test
    void retryRefinamento_deveLancarGroqRateLimitException_quandoInterrompido() throws Exception {
        var audio = mock(AudioService.class);
        var groq = mock(GroqClient.class);
        var cache = mock(ChatTranscriptionCache.class);
        var transcriptStoreService = mock(TranscriptStoreService.class);
        var service = new AudioPipelineService(audio, groq, cache, transcriptStoreService);

        Method method =
                AudioPipelineService.class.getDeclaredMethod("retryRefinamento", String.class);
        method.setAccessible(true);

        // Simula TooManyRequests na primeira tentativa
        HttpClientErrorException.TooManyRequests ex =
                mock(HttpClientErrorException.TooManyRequests.class);
        when(ex.getMessage()).thenReturn("429 Too Many Requests");
        when(groq.refinarTexto(anyString())).thenThrow(ex);

        // Interrompe a thread antes da chamada
        Thread.currentThread().interrupt();

        try {
            Throwable thrown = catchThrowable(() -> method.invoke(service, "texto"));
            assertThat(thrown)
                    .isInstanceOf(InvocationTargetException.class)
                    .hasCauseInstanceOf(GroqRateLimitException.class);
            assertThat(thrown.getCause())
                    .isInstanceOf(GroqRateLimitException.class)
                    .hasMessageContaining("Thread interrompida durante backoff");
        } finally {
            // Limpa a interrupção para não afetar outros testes
            Thread.interrupted();
        }
    }

    @Test
    void calcularWaitTime_deveExtrairTempoDaMensagem() throws Exception {
        var audio = mock(AudioService.class);
        var groq = mock(GroqClient.class);
        var cache = mock(ChatTranscriptionCache.class);
        var transcriptStoreService = mock(TranscriptStoreService.class);
        var service = new AudioPipelineService(audio, groq, cache, transcriptStoreService);

        Method method =
                AudioPipelineService.class.getDeclaredMethod(
                        "calcularWaitTime",
                        HttpClientErrorException.TooManyRequests.class,
                        int.class);
        method.setAccessible(true);

        HttpClientErrorException.TooManyRequests ex =
                mock(HttpClientErrorException.TooManyRequests.class);
        when(ex.getMessage()).thenReturn("429 Too Many Requests: try again in 2.5s");

        long waitTime = (long) method.invoke(service, ex, 0);
        assertThat(waitTime).isEqualTo(3000); // 2.5 * 1000 + 500 = 3000
    }

    @Test
    void calcularWaitTime_deveUsarBackoff_quandoMensagemSemTempo() throws Exception {
        var audio = mock(AudioService.class);
        var groq = mock(GroqClient.class);
        var cache = mock(ChatTranscriptionCache.class);
        var transcriptStoreService = mock(TranscriptStoreService.class);
        var service = new AudioPipelineService(audio, groq, cache, transcriptStoreService);

        Method method =
                AudioPipelineService.class.getDeclaredMethod(
                        "calcularWaitTime",
                        HttpClientErrorException.TooManyRequests.class,
                        int.class);
        method.setAccessible(true);

        HttpClientErrorException.TooManyRequests ex =
                mock(HttpClientErrorException.TooManyRequests.class);
        when(ex.getMessage()).thenReturn("429 Too Many Requests");

        long waitTime = (long) method.invoke(service, ex, 2);
        assertThat(waitTime).isEqualTo(6000L);
    }

    @Test
    void deletarSilenciosamente_deveIgnorarArquivoNulo() throws Exception {
        var audio = mock(AudioService.class);
        var groq = mock(GroqClient.class);
        var cache = mock(ChatTranscriptionCache.class);
        var transcriptStoreService = mock(TranscriptStoreService.class);
        var service = new AudioPipelineService(audio, groq, cache, transcriptStoreService);

        Method method =
                AudioPipelineService.class.getDeclaredMethod("deletarSilenciosamente", File.class);
        method.setAccessible(true);

        assertThatCode(() -> method.invoke(service, (File) null)).doesNotThrowAnyException();
    }

    @Test
    void deletarSilenciosamente_deveIgnorarArquivoInexistente() throws Exception {
        var audio = mock(AudioService.class);
        var groq = mock(GroqClient.class);
        var cache = mock(ChatTranscriptionCache.class);
        var transcriptStoreService = mock(TranscriptStoreService.class);
        var service = new AudioPipelineService(audio, groq, cache, transcriptStoreService);

        Method method =
                AudioPipelineService.class.getDeclaredMethod("deletarSilenciosamente", File.class);
        method.setAccessible(true);

        File inexistente = new File("/caminho/inexistente/arquivo.txt");
        assertThatCode(() -> method.invoke(service, inexistente)).doesNotThrowAnyException();
    }

    @Test
    void deletarSilenciosamente_deveLogarErro_quandoFalhaAoDeletar() throws Exception {
        var audio = mock(AudioService.class);
        var groq = mock(GroqClient.class);
        var cache = mock(ChatTranscriptionCache.class);
        var transcriptStoreService = mock(TranscriptStoreService.class);
        var service = new AudioPipelineService(audio, groq, cache, transcriptStoreService);

        Method method =
                AudioPipelineService.class.getDeclaredMethod("deletarSilenciosamente", File.class);
        method.setAccessible(true);

        // Cria um arquivo e deleta antes de chamar o método
        File arquivo = Files.createFile(tempDir.resolve("paraDeletar.txt")).toFile();
        arquivo.delete();

        assertThatCode(() -> method.invoke(service, arquivo)).doesNotThrowAnyException();
    }

    // ===================== TESTE PARA handlePipelineException (privado) =====================

    @Test
    void handlePipelineException_deveReproparAudioProcessingException() throws Exception {
        var audio = mock(AudioService.class);
        var groq = mock(GroqClient.class);
        var cache = mock(ChatTranscriptionCache.class);
        var transcriptStoreService = mock(TranscriptStoreService.class);
        var service = new AudioPipelineService(audio, groq, cache, transcriptStoreService);

        Method method =
                AudioPipelineService.class.getDeclaredMethod(
                        "handlePipelineException", Throwable.class);
        method.setAccessible(true);

        AudioProcessingException ape = new AudioProcessingException("Erro de áudio");
        assertThatThrownBy(() -> method.invoke(service, ape))
                .hasCause(ape); // O método lança a exceção diretamente (RuntimeException)
    }

    @Test
    void handlePipelineException_deveReproparGroqRateLimitException() throws Exception {
        var audio = mock(AudioService.class);
        var groq = mock(GroqClient.class);
        var cache = mock(ChatTranscriptionCache.class);
        var transcriptStoreService = mock(TranscriptStoreService.class);
        var service = new AudioPipelineService(audio, groq, cache, transcriptStoreService);

        Method method =
                AudioPipelineService.class.getDeclaredMethod(
                        "handlePipelineException", Throwable.class);
        method.setAccessible(true);

        GroqRateLimitException grle = new GroqRateLimitException("Rate limit");
        assertThatThrownBy(() -> method.invoke(service, grle)).hasCause(grle);
    }

    @Test
    void handlePipelineException_deveEnvolverRuntimeException() throws Exception {
        var audio = mock(AudioService.class);
        var groq = mock(GroqClient.class);
        var cache = mock(ChatTranscriptionCache.class);
        var transcriptStoreService = mock(TranscriptStoreService.class);
        var service = new AudioPipelineService(audio, groq, cache, transcriptStoreService);

        Method method =
                AudioPipelineService.class.getDeclaredMethod(
                        "handlePipelineException", Throwable.class);
        method.setAccessible(true);

        RuntimeException re = new RuntimeException("Erro inesperado");
        assertThatThrownBy(() -> method.invoke(service, re))
                .hasCauseInstanceOf(CompletionException.class);
    }

    // ========================================================================
    // TESTES ADICIONAIS PARA COBRIR BRANCHES FALTANTES
    // ========================================================================

    @Test
    void processarFluxoAudio_deveLancarIllegalArgumentException_quandoOgaFileNulo() {
        var audio = mock(AudioService.class);
        var groq = mock(GroqClient.class);
        var cache = mock(ChatTranscriptionCache.class);
        var transcriptStoreService = mock(TranscriptStoreService.class);
        var service = new AudioPipelineService(audio, groq, cache, transcriptStoreService);

        assertThatThrownBy(() -> service.processarFluxoAudio(null, 1L, 1L, "Usuário", (t, b) -> {}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ogaFile não pode ser nulo");
    }

    @Test
    void processarFluxoAudio_deveLancarIllegalArgumentException_quandoCallbackNulo()
            throws Exception {
        var audio = mock(AudioService.class);
        var groq = mock(GroqClient.class);
        var cache = mock(ChatTranscriptionCache.class);
        var transcriptStoreService = mock(TranscriptStoreService.class);
        var service = new AudioPipelineService(audio, groq, cache, transcriptStoreService);

        File input = Files.createFile(tempDir.resolve("a.oga")).toFile();

        assertThatThrownBy(() -> service.processarFluxoAudio(input, 1L, 1L, "Usuário", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("callback não pode ser nulo");
    }

    @Test
    void calcularWaitTime_deveUsarBackoff_quandoNumberFormatExceptionNoParsing() throws Exception {
        var audio = mock(AudioService.class);
        var groq = mock(GroqClient.class);
        var cache = mock(ChatTranscriptionCache.class);
        var transcriptStoreService = mock(TranscriptStoreService.class);
        var service = new AudioPipelineService(audio, groq, cache, transcriptStoreService);

        Method method =
                AudioPipelineService.class.getDeclaredMethod(
                        "calcularWaitTime",
                        HttpClientErrorException.TooManyRequests.class,
                        int.class);
        method.setAccessible(true);

        HttpClientErrorException.TooManyRequests ex =
                mock(HttpClientErrorException.TooManyRequests.class);
        when(ex.getMessage()).thenReturn("try again in abc");
        long waitTime = (long) method.invoke(service, ex, 1);
        assertThat(waitTime).isEqualTo(4000L);
    }

    @Test
    void deletarSilenciosamente_deveCapturarIOException() throws Exception {
        var audio = mock(AudioService.class);
        var groq = mock(GroqClient.class);
        var cache = mock(ChatTranscriptionCache.class);
        var transcriptStoreService = mock(TranscriptStoreService.class);
        var service = new AudioPipelineService(audio, groq, cache, transcriptStoreService);

        Method method =
                AudioPipelineService.class.getDeclaredMethod("deletarSilenciosamente", File.class);
        method.setAccessible(true);

        Path dir = Files.createDirectory(tempDir.resolve("dir"));
        File file = new File(dir.toFile(), "arquivo.txt");
        Files.createFile(file.toPath());
        file.setReadOnly();

        assertThatCode(() -> method.invoke(service, file)).doesNotThrowAnyException();

        file.setWritable(true);
        Files.deleteIfExists(file.toPath());
        Files.deleteIfExists(dir);
    }
}
