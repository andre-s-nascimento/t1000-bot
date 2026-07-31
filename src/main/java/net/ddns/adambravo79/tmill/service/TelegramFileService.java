package net.ddns.adambravo79.tmill.service;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Service;

import com.pengrad.telegrambot.model.File;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramFileService {

    private final TelegramFacade telegramFacade;
    private final TempDirService tempDirService;

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 1000;

    /**
     * Baixa um arquivo do Telegram com retry em caso de timeout.
     *
     * @param fileId identificador do arquivo no Telegram
     * @return arquivo baixado (temporário)
     * @throws RuntimeException se todas as tentativas falharem
     */
    public java.io.File baixarArquivo(String fileId) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                log.debug(
                        "Baixando arquivo fileId={}, tentativa {}/{}",
                        fileId,
                        attempt,
                        MAX_ATTEMPTS);
                return baixarComUmaTentativa(fileId);
            } catch (Exception e) {
                lastException = e;
                log.warn("Tentativa {} falhou para fileId={}: {}", attempt, fileId, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    long sleepMs = INITIAL_BACKOFF_MS * attempt;
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Download interrompido", ie);
                    }
                }
            }
        }
        throw new RuntimeException(
                "Falha ao baixar arquivo após " + MAX_ATTEMPTS + " tentativas", lastException);
    }

    private java.io.File baixarComUmaTentativa(String fileId) {
        File tgFile = telegramFacade.getFile(fileId);
        byte[] data = telegramFacade.downloadFile(tgFile);
        try {
            Path tempFile = tempDirService.createTempFile("audio", ".oga");
            Files.write(tempFile, data);
            log.info("Arquivo baixado: {}", tempFile);
            return tempFile.toFile();
        } catch (Exception e) {
            throw new RuntimeException("Falha ao salvar arquivo temporário", e);
        }
    }
}
