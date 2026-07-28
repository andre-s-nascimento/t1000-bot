/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AudioServiceTest {

    @TempDir Path tempDir;

    @Test
    void deveConverterComSucesso() throws Exception {
        // Arrange
        var service = spy(new AudioService());

        // Cria ficheiro de entrada e de saída no directório temporário
        File input = new File(tempDir.toFile(), "audio.oga");
        File expectedOutput = new File(tempDir.toFile(), "audio.wav");

        // Garante que ambos existem (o de saída é criado para simular o FFmpeg)
        if (!input.exists()) input.createNewFile();
        if (!expectedOutput.exists()) expectedOutput.createNewFile();

        Process process = mock(Process.class);
        doReturn(process).when(service).startProcess(any(ProcessBuilder.class));

        // Usa doAnswer para maior controlo e evitar problemas de correspondência
        when(process.waitFor(anyLong(), any(TimeUnit.class)))
                .thenAnswer(
                        inv -> {
                            long timeout = inv.getArgument(0);
                            TimeUnit unit = inv.getArgument(1);
                            System.out.println(
                                    "waitFor chamado com timeout=" + timeout + ", unit=" + unit);
                            return true;
                        });
        when(process.exitValue()).thenReturn(0);

        // Act
        CompletableFuture<File> result = service.converterParaWav(input);
        File file = result.join();

        // Assert
        assertThat(file)
                .isNotNull()
                .hasName("audio.wav")
                .exists(); // verifica que o ficheiro foi devolvido (existe)

        // Verifica que o método startProcess foi chamado exactamente uma vez
        verify(service, times(1)).startProcess(any(ProcessBuilder.class));
    }

    @Test
    void deveFalharQuandoExitCodeDiferenteDeZero() throws Exception {
        // Arrange
        var service = spy(new AudioService());
        File input = new File(tempDir.toFile(), "audio.oga");

        Process process = mock(Process.class);
        doReturn(process).when(service).startProcess(any());

        when(process.waitFor(anyLong(), any())).thenReturn(true);
        when(process.exitValue()).thenReturn(1);

        // Act & Assert
        CompletableFuture<File> result = service.converterParaWav(input);
        assertThatThrownBy(result::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("FFmpeg falhou");
    }

    @Test
    void deveFalharQuandoTimeout() throws Exception {
        // Arrange
        var service = spy(new AudioService());
        File input = new File(tempDir.toFile(), "audio.oga");

        Process process = mock(Process.class);
        doReturn(process).when(service).startProcess(any());

        when(process.waitFor(anyLong(), any())).thenReturn(false);

        // Act & Assert
        CompletableFuture<File> result = service.converterParaWav(input);
        assertThatThrownBy(result::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void deveFalharQuandoProcessoLancaExcecao() throws Exception {
        // Arrange
        var service = spy(new AudioService());
        File input = new File(tempDir.toFile(), "audio.oga");

        doThrow(new RuntimeException("erro original")).when(service).startProcess(any());

        // Act & Assert
        CompletableFuture<File> result = service.converterParaWav(input);
        assertThatThrownBy(result::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("erro original"); // Nota: a mensagem original é substituída
    }

    // ========================================================================
    // NOVOS TESTES PARA COBERTURA DE AudioService
    // ========================================================================

    // -----------------------------
    // drainStream (cobertura do catch)
    // -----------------------------
    @Test
    void drainStream_deveCapturarExcecao() throws Exception {
        // Arrange
        var service = new AudioService();
        java.io.InputStream mockStream = mock(java.io.InputStream.class);
        doThrow(new java.io.IOException("simulated error")).when(mockStream).transferTo(any());

        // Act: invoca via reflexão
        java.lang.reflect.Method method =
                AudioService.class.getDeclaredMethod("drainStream", java.io.InputStream.class);
        method.setAccessible(true);
        method.invoke(service, mockStream);

        // Assert: não lança exceção; cobertura do catch alcançada
    }

    // -----------------------------
    // startProcess (método real)
    // -----------------------------
    @Test
    void startProcess_deveIniciarProcesso() throws Exception {
        // Arrange
        var service = new AudioService();
        String os = System.getProperty("os.name").toLowerCase();
        String[] cmd;
        if (os.contains("win")) {
            cmd = new String[] {"cmd", "/c", "exit", "0"};
        } else {
            cmd = new String[] {"true"};
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);

        // Act
        Process process = service.startProcess(pb);

        // Assert
        assertThat(process).isNotNull();
        int exitCode = process.waitFor(); // espera terminar
        assertThat(exitCode).isZero();
    }

    // -----------------------------
    // Teste melhorado para converterParaWav (cobrir a lambda)
    // -----------------------------
    @Test
    void deveConverterComSucesso_eExecutarLambda() throws Exception {
        // Arrange
        var service = spy(new AudioService());
        File input = new File(tempDir.toFile(), "audio.oga");
        File expectedOutput = new File(tempDir.toFile(), "audio.wav");
        input.createNewFile();
        expectedOutput.createNewFile();

        Process process = mock(Process.class);
        // Fornece um InputStream real para que a thread execute drainStream
        when(process.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(new byte[0]));
        doReturn(process).when(service).startProcess(any(ProcessBuilder.class));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(process.exitValue()).thenReturn(0);

        // Act
        CompletableFuture<File> result = service.converterParaWav(input);
        File file = result.join();

        // Assert
        assertThat(file).exists();
        verify(service, times(1)).startProcess(any(ProcessBuilder.class));

        // Dá um tempo para a thread de drenagem executar (a lambda)
        await().atMost(2, TimeUnit.SECONDS);
    }
}
