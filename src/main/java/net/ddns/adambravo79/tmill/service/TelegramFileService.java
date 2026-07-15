package net.ddns.adambravo79.tmill.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;

@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramFileService {

    private final TelegramFacade telegramFacade;

    public java.io.File baixarArquivo(String fileId) {
        int maxAttempts = 3;
        long backoffMs = 1000;
        Exception lastException = null;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                log.debug(
                        "Baixando arquivo fileId={}, tentativa {}/{}",
                        fileId,
                        attempt + 1,
                        maxAttempts);
                return baixarComUmaTentativa(fileId);
            } catch (Exception e) {
                lastException = e;
                log.warn(
                        "Tentativa {} falhou para fileId={}: {}",
                        attempt + 1,
                        fileId,
                        e.getMessage());
                if (attempt < maxAttempts - 1) {
                    try {
                        Thread.sleep(backoffMs * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Download interrompido", ie);
                    }
                }
            }
        }
        throw new RuntimeException(
                "Falha ao baixar arquivo após " + maxAttempts + " tentativas", lastException);
    }

    private java.io.File baixarComUmaTentativa(String fileId) {
        com.pengrad.telegrambot.model.File tgFile = telegramFacade.getFile(fileId);
        byte[] data = telegramFacade.downloadFile(tgFile);
        try {
            Path tempFile = Files.createTempFile("audio", ".oga");
            Files.write(tempFile, data);
            log.info("Arquivo baixado: {}", tempFile);
            return tempFile.toFile();
        } catch (IOException e) {
            throw new RuntimeException("Falha ao salvar arquivo temporário", e);
        }
    }
}
