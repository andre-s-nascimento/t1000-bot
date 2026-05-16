/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
        long backoffMs = 1000;

        while (tentativa < maxTentativas) {
            try {
                log.debug(
                        "Baixando arquivo fileId={}, tentativa {}/{}",
                        fileId,
                        tentativa + 1,
                        maxTentativas);
                return baixarArquivoComUmaTentativa(fileId);
            } catch (TelegramApiException e) {
                if (isTimeoutError(e) && tentativa < maxTentativas - 1) {
                    aguardarBackoff(tentativa, backoffMs);
                    tentativa++;
                } else {
                    log.error("❌ Erro na API do Telegram fileId={}", fileId, e);
                    throw new TelegramFileException(
                            "Falha ao baixar arquivo: " + e.getMessage(), e);
                }
            } catch (IOException e) {
                log.error("❌ Erro de I/O ao mover arquivo fileId={}", fileId, e);
                throw new TelegramFileException("Erro ao salvar arquivo: " + e.getMessage(), e);
            }
        }
        throw new TelegramFileException(
                "Não foi possível baixar após " + maxTentativas + " tentativas", null);
    }

    private boolean isTimeoutError(TelegramApiException e) {
        String msg = e.getMessage();
        return msg != null && (msg.contains("timeout") || msg.contains("SocketTimeoutException"));
    }

    private File baixarArquivoComUmaTentativa(String fileId)
            throws TelegramApiException, IOException {
        org.telegram.telegrambots.meta.api.objects.File tgFile =
                telegramFacade.getFile(new GetFile(fileId));
        File tempFile = telegramFacade.downloadFile(tgFile);
        if (tempFile == null || !tempFile.exists()) {
            throw new TelegramFileException(
                    "Arquivo não encontrado após download: " + fileId, null);
        }
        // Renomeia para .oga
        String destFileName = tempFile.getAbsolutePath().replace(".tmp", ".oga");
        File destFile = new File(destFileName);
        Files.move(tempFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        log.info("Arquivo baixado com sucesso: {} -> {}", tempFile.getName(), destFile.getName());
        return destFile;
    }

    private void aguardarBackoff(int tentativaAtual, long backoffMs) throws TelegramFileException {
        long espera = backoffMs * (tentativaAtual + 1);
        log.warn("⏱️ Timeout, aguardando {}ms antes de tentar novamente", espera);
        try {
            Thread.sleep(espera);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new TelegramFileException("Download interrompido", ie);
        }
    }
}
