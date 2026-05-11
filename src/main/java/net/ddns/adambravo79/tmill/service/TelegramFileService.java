/* (c) 2026 | 11/05/2026 */
package net.ddns.adambravo79.tmill.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.exception.TelegramFileException;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;

/**
 * Serviço responsável por baixar arquivos do Telegram a partir de um fileId.
 *
 * <p>Principais responsabilidades: - Consultar metadados do arquivo via {@link TelegramFacade}. -
 * Realizar o download do arquivo para o sistema local. - Garantir que nunca retorne {@code null},
 * lançando {@link TelegramFileException} em caso de falha.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramFileService {

    private final TelegramFacade telegramFacade;

    /**
     * Baixa um arquivo do Telegram dado o fileId.
     *
     * @param fileId identificador único do arquivo no Telegram.
     * @return {@link File} baixado e armazenado localmente.
     * @throws TelegramFileException em caso de falha no download ou se o arquivo não existir.
     */
    public File baixarArquivo(String fileId) {
        int maxTentativas = 3;
        int tentativa = 0;
        long backoffMs = 1000; // 1 segundo

        while (tentativa < maxTentativas) {
            try {
                log.debug(
                        "Baixando arquivo do Telegram fileId={}, tentativa {}/{}",
                        fileId,
                        tentativa + 1,
                        maxTentativas);

                // 1. Obtém metadados do arquivo
                org.telegram.telegrambots.meta.api.objects.File tgFile =
                        telegramFacade.getFile(new GetFile(fileId));

                // 2. Download para um arquivo temporário
                File tempFile = telegramFacade.downloadFile(tgFile);

                if (tempFile == null || !tempFile.exists()) {
                    throw new TelegramFileException(
                            "Arquivo não encontrado após download: " + fileId, null);
                }

                // 3. Cria um arquivo definitivo com extensão .oga
                String destFileName = tempFile.getAbsolutePath().replace(".tmp", ".oga");
                File destFile = new File(destFileName);

                // 4. Move o arquivo temporário para o destino final (com sobrescrita)
                Path source = tempFile.toPath();
                Path target = destFile.toPath();
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);

                log.info(
                        "Arquivo baixado com sucesso: {} -> {}",
                        tempFile.getName(),
                        destFile.getName());
                return destFile;

            } catch (TelegramApiException e) {
                boolean isTimeout =
                        e.getMessage() != null
                                && (e.getMessage().contains("timeout")
                                        || e.getMessage().contains("SocketTimeoutException"));

                if (isTimeout && tentativa < maxTentativas - 1) {
                    long espera = backoffMs * (tentativa + 1);
                    log.warn(
                            "⏱️ Timeout no download (tentativa {}/{}), aguardando {}ms antes de"
                                    + " tentar novamente. fileId={}",
                            tentativa + 1,
                            maxTentativas,
                            espera,
                            fileId);
                    try {
                        Thread.sleep(espera);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new TelegramFileException("Download interrompido", ie);
                    }
                    tentativa++;
                } else {
                    log.error("❌ Erro na API do Telegram ao baixar arquivo fileId={}", fileId, e);
                    throw new TelegramFileException(
                            "Falha ao baixar arquivo do Telegram: " + e.getMessage(), e);
                }

            } catch (IOException e) {
                log.error("❌ Erro de I/O ao mover arquivo fileId={}", fileId, e);
                throw new TelegramFileException(
                        "Erro ao salvar arquivo baixado: " + e.getMessage(), e);
            }
        }

        throw new TelegramFileException(
                "Não foi possível baixar o arquivo após " + maxTentativas + " tentativas", null);
    }
}
