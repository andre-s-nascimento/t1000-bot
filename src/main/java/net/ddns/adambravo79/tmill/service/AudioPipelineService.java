/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.cache.TranscricaoCache;
import net.ddns.adambravo79.tmill.client.GroqClient;
import net.ddns.adambravo79.tmill.exception.AudioProcessingException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioPipelineService {

    private final AudioService audioService;
    private final GroqClient groqClient;
    private final TranscricaoCache transcricaoCache;
    private final TranscriptStoreService transcriptStoreService; // 🆕

    /**
     * Processa o fluxo completo de áudio, desde a conversão até a transcrição refinada.
     *
     * @param ogaFile arquivo de áudio recebido (formato OGA).
     * @param chatId identificador do chat para cache da transcrição.
     * @param userId identificador do usuário que enviou o áudio.
     * @param userName nome do usuário (primeiro + último, se houver).
     * @param callback função de retorno que recebe o texto transcrito e um indicador de refinamento.
     */
    public void processarFluxoAudio(
            File ogaFile,
            long chatId,
            long userId,
            String userName,
            BiConsumer<String, Boolean> callback) {
        log.info("Iniciando fluxo de processamento para: {}", ogaFile.getName());

        try {
            audioService
                    .converterParaWav(ogaFile)
                    .thenAccept(
                            wavFile -> {
                                try {
                                    String bruto = groqClient.transcrever(wavFile);
                                    callback.accept("🎙️ *Bruto:* \n_" + bruto + "_", false);

                                    String refinado = groqClient.refinarTexto(bruto);
                                    // 👇 Salva a transcrição refinada no banco SQLite
                                    transcriptStoreService.saveTranscript(
                                            chatId, userId, userName, refinado);
                                    transcricaoCache.salvar(chatId, refinado);
                                    callback.accept("✨ *Refinado:* \n" + refinado, true);

                                } catch (Exception e) {
                                    throw new CompletionException(
                                            new AudioProcessingException(
                                                    "Falha no pipeline de áudio para arquivo: "
                                                            + wavFile.getName(),
                                                    e));
                                } finally {
                                    deletarSilenciosamente(wavFile);
                                }
                            })
                    .exceptionally(
                            ex -> {
                                Throwable causa =
                                        (ex instanceof CompletionException && ex.getCause() != null)
                                                ? ex.getCause()
                                                : ex;
                                throw new CompletionException(
                                        new AudioProcessingException(
                                                "Erro inesperado no pipeline de áudio para arquivo:"
                                                        + " "
                                                        + ogaFile.getName(),
                                                causa));
                            })
                    .thenRun(() -> deletarSilenciosamente(ogaFile))
                    .join();

        } catch (CompletionException e) {
            Throwable causa = e.getCause() != null ? e.getCause() : e;
            if (causa instanceof AudioProcessingException ape) throw ape;
            throw new AudioProcessingException(
                    "Erro inesperado no pipeline de áudio para arquivo: " + ogaFile.getName(),
                    causa);
        }
    }

    public record ProcessedAudio(String bruto, String refinado) {}

    /**
     * Processa um arquivo de áudio OGA, converte para WAV, transcreve (Whisper) e refina (Llama).
     * Retorna um CompletableFuture com ambos os textos (bruto e refinado). Não envia mensagem alguma
     * – apenas processa e armazena internamente.
     *
     * @param ogaFile arquivo de áudio original (formato OGA)
     * @param chatId identificador do chat onde o áudio foi enviado (para salvar no banco)
     * @param userId ID do usuário que enviou o áudio
     * @param userName nome do usuário (primeiro + último, se houver)
     * @return futuro contendo {@link ProcessedAudio}
     */
    public CompletableFuture<ProcessedAudio> processarEArmazenar(
            File ogaFile, long chatId, long userId, String userName) {
        return audioService
                .converterParaWav(ogaFile)
                .thenApplyAsync(
                        wavFile -> {
                            try {
                                String bruto = groqClient.transcrever(wavFile);
                                String refinado = groqClient.refinarTexto(bruto);
                                return new ProcessedAudio(bruto, refinado);
                            } catch (Exception e) {
                                throw new CompletionException(e);
                            } finally {
                                deletarSilenciosamente(wavFile);
                            }
                        })
                .whenComplete((result, ex) -> deletarSilenciosamente(ogaFile));
    }

    private void deletarSilenciosamente(File file) {
        try {
            Files.delete(Path.of(file.getAbsolutePath()));
            log.debug("Arquivo temporário excluído: {}", file.getAbsolutePath());
        } catch (IOException ex) {
            log.warn("Não foi possível excluir arquivo temporário: {}", file.getAbsolutePath(), ex);
        }
    }
}
