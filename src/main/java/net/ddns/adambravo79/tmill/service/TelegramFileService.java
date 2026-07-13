package net.ddns.adambravo79.tmill.service;

import java.io.IOException;
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

    public java.io.File baixarArquivo(String fileId) {
        File tgFile = telegramFacade.getFile(fileId);
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
