package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pengrad.telegrambot.model.File;

import lombok.SneakyThrows;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.exception.TelegramFileException;

@ExtendWith(MockitoExtension.class)
class TelegramFileServiceTest {

    @Mock private TelegramFacade telegramFacade;
    @InjectMocks private TelegramFileService service;

    private static final String FILE_ID = "test-file-id";
    private static final byte[] DATA = "conteúdo do arquivo".getBytes();

    @Test
    void deveBaixarArquivoComSucesso() {
        File tgFile = mock(File.class);
        when(telegramFacade.getFile(FILE_ID)).thenReturn(tgFile);
        when(telegramFacade.downloadFile(tgFile)).thenReturn(DATA);

        java.io.File result = service.baixarArquivo(FILE_ID);

        assertThat(result).exists().hasContent("conteúdo do arquivo");

        assertThat(result.getName()).startsWith("audio").endsWith(".oga");

        verify(telegramFacade).getFile(FILE_ID);
        verify(telegramFacade).downloadFile(tgFile);

        result.delete();
    }

    @Test
    void deveLancarExcecaoQuandoGetFileFalha() {
        when(telegramFacade.getFile(FILE_ID))
                .thenThrow(new TelegramFileException("Falha ao obter arquivo", null));

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> service.baixarArquivo(FILE_ID))
                .withMessage("Falha ao baixar arquivo após 3 tentativas");
    }

    @Test
    void deveLancarExcecaoQuandoDownloadFalha() {
        File tgFile = mock(File.class);
        when(telegramFacade.getFile(FILE_ID)).thenReturn(tgFile);
        when(telegramFacade.downloadFile(tgFile))
                .thenThrow(new TelegramFileException("Erro no download", null));

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> service.baixarArquivo(FILE_ID))
                .withMessage("Falha ao baixar arquivo após 3 tentativas");
    }

    @Test
    void deveLancarExcecaoQuandoDownloadRetornaDadosNulos() {
        File tgFile = mock(File.class);
        when(telegramFacade.getFile(FILE_ID)).thenReturn(tgFile);
        when(telegramFacade.downloadFile(tgFile)).thenReturn(null);

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> service.baixarArquivo(FILE_ID))
                .withMessage("Falha ao baixar arquivo após 3 tentativas");
    }

    @Test
    void deveLancarExcecaoQuandoSleepInterrompido() {
        // Simula falha na primeira tentativa para entrar no sleep
        when(telegramFacade.getFile(FILE_ID))
                .thenThrow(new TelegramFileException("Falha na primeira", null));

        // Interrompe a thread antes da chamada
        Thread.currentThread().interrupt();

        try {
            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> service.baixarArquivo(FILE_ID))
                    .withMessage("Download interrompido");
        } finally {
            // Limpa a interrupção para não afetar outros testes
            Thread.interrupted();
        }

        // getFile foi chamado apenas uma vez (não houve retry)
        verify(telegramFacade, times(1)).getFile(FILE_ID);
    }

    @Test
    @SneakyThrows
    void deveLancarExcecaoQuandoFalhaAoCriarArquivoTemporario() {
        try (var mockedFiles = mockStatic(Files.class)) {
            mockedFiles
                    .when(() -> Files.createTempFile("audio", ".oga"))
                    .thenThrow(new IOException("Erro ao criar arquivo"));

            File tgFile = mock(File.class);
            when(telegramFacade.getFile(FILE_ID)).thenReturn(tgFile);
            when(telegramFacade.downloadFile(tgFile)).thenReturn(DATA);

            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> service.baixarArquivo(FILE_ID))
                    .withMessage("Falha ao baixar arquivo após 3 tentativas");

            verify(telegramFacade, times(3)).getFile(FILE_ID);
            verify(telegramFacade, times(3)).downloadFile(tgFile);
        }
    }

    @Test
    void deveLancarExcecaoQuandoFalhaAoEscreverArquivoTemporario() {
        try (var mockedFiles = mockStatic(Files.class)) {
            Path tempPath = mock(Path.class);
            mockedFiles.when(() -> Files.createTempFile("audio", ".oga")).thenReturn(tempPath);
            mockedFiles
                    .when(() -> Files.write(tempPath, DATA))
                    .thenThrow(new IOException("Erro ao escrever arquivo"));

            File tgFile = mock(File.class);
            when(telegramFacade.getFile(FILE_ID)).thenReturn(tgFile);
            when(telegramFacade.downloadFile(tgFile)).thenReturn(DATA);

            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> service.baixarArquivo(FILE_ID))
                    .withMessage("Falha ao baixar arquivo após 3 tentativas");

            verify(telegramFacade, times(3)).getFile(FILE_ID);
            verify(telegramFacade, times(3)).downloadFile(tgFile);
        }
    }
}
