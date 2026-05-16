/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.service;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.exception.AudioProcessingException;

/**
 * Serviço técnico para manipulação de arquivos de áudio via FFmpeg.
 *
 * <p>Responsável por converter arquivos recebidos em formato OGA para WAV, garantindo
 * compatibilidade com o pipeline de transcrição. Otimizado para rodar dentro de container Docker na
 * OCI.
 *
 * <p>Em caso de falha, lança {@link AudioProcessingException}.
 */
@Slf4j
@Service
public class AudioService {

    /**
     * Converte um arquivo OGA para WAV utilizando FFmpeg.
     *
     * <p>Configurações aplicadas: - Taxa de amostragem: 16 kHz - Canal único (mono)
     *
     * @param ogaFile arquivo de áudio em formato OGA.
     * @return {@link CompletableFuture} contendo o arquivo WAV convertido.
     * @throws AudioProcessingException em caso de falha na conversão ou timeout.
     */
    @Async
    public CompletableFuture<File> converterParaWav(File ogaFile) {
        File wavFile = new File(ogaFile.getAbsolutePath().replace(".oga", ".wav"));

        try {
            ProcessBuilder pb =
                    new ProcessBuilder(
                            "ffmpeg",
                            "-y",
                            "-i",
                            ogaFile.getAbsolutePath(),
                            "-ar",
                            "16000",
                            "-ac",
                            "1",
                            wavFile.getAbsolutePath());

            pb.redirectErrorStream(true);

            log.info("FFmpeg: Iniciando conversão de {}...", ogaFile.getName());

            Process p = startProcess(pb);

            boolean finished = p.waitFor(30, TimeUnit.SECONDS);

            if (finished && p.exitValue() == 0) {
                log.info("FFmpeg: Conversão concluída com sucesso.");
                return CompletableFuture.completedFuture(wavFile);
            }

            log.error("FFmpeg: Falha na conversão ou timeout.");
            return CompletableFuture.failedFuture(new AudioProcessingException("FFmpeg falhou"));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 🔥 obrigatório para restaurar estado da thread
            return CompletableFuture.failedFuture(
                    new AudioProcessingException("Processo interrompido", e));

        } catch (Exception e) {
            log.error("Erro crítico no AudioService", e);
            return CompletableFuture.failedFuture(new AudioProcessingException("FFmpeg falhou", e));
        }
    }

    /**
     * Método protegido para iniciar o processo do FFmpeg.
     *
     * <p>Permite sobrescrita em testes unitários.
     *
     * @param pb {@link ProcessBuilder} configurado para execução do FFmpeg.
     * @return instância de {@link Process}.
     * @throws IOException em caso de falha ao iniciar o processo.
     */
    protected Process startProcess(ProcessBuilder pb) throws IOException {
        return pb.start();
    }
}
