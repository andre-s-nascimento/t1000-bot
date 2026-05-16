/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import net.ddns.adambravo79.tmill.exception.TelegramFileException;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;

class TelegramFileServiceTest {

    private static final String FILE_ID = "file-id";

    @Test
    void deveBaixarArquivoComSucesso() throws Exception {
        TelegramFacade facade = mock(TelegramFacade.class);
        TelegramFileService service = new TelegramFileService(facade);

        File temp = Files.createTempFile("audio", ".tmp").toFile();

        org.telegram.telegrambots.meta.api.objects.File tgFile =
                new org.telegram.telegrambots.meta.api.objects.File();

        when(facade.getFile(any(GetFile.class))).thenReturn(tgFile);
        when(facade.downloadFile(tgFile)).thenReturn(temp);

        File result = service.baixarArquivo(FILE_ID);

        assertThat(result).isNotNull().exists();
    }

    @Test
    void deveLancarExcecaoQuandoDownloadRetornaNull() throws Exception {
        TelegramFacade facade = mock(TelegramFacade.class);
        TelegramFileService service = new TelegramFileService(facade);

        org.telegram.telegrambots.meta.api.objects.File tgFile =
                new org.telegram.telegrambots.meta.api.objects.File();

        when(facade.getFile(any(GetFile.class))).thenReturn(tgFile);
        when(facade.downloadFile(tgFile)).thenReturn(null);

        assertThatThrownBy(() -> service.baixarArquivo(FILE_ID))
                .isInstanceOf(TelegramFileException.class)
                .hasMessageContaining("Arquivo não encontrado");
    }

    @Test
    void deveLancarExcecaoQuandoApiFalhar() throws Exception {
        TelegramFacade facade = mock(TelegramFacade.class);
        TelegramFileService service = new TelegramFileService(facade);

        // Agora simulamos a exceção correta que a implementação captura
        when(facade.getFile(any(GetFile.class))).thenThrow(new TelegramApiException("erro"));

        assertThatThrownBy(() -> service.baixarArquivo(FILE_ID))
                .isInstanceOf(TelegramFileException.class)
                .hasMessageContaining("Falha ao baixar arquivo: erro");
    }
}
