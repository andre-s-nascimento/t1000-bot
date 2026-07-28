package net.ddns.adambravo79.tmill.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.client.GroqClient;
import net.ddns.adambravo79.tmill.exception.AudioProcessingException;
import net.ddns.adambravo79.tmill.exception.GroqRateLimitException;
import net.ddns.adambravo79.tmill.service.cache.ChatTranscriptionCache;

/**
 * Serviço responsável pelo pipeline completo de processamento de áudio: conversão OGA → WAV,
 * transcrição via Whisper (Groq) e refinamento via Llama.
 *
 * <p>Exception handling strategy:
 *
 * <ul>
 *   <li>{@link AudioProcessingException} — erro de negócio no pipeline (conversão, transcrição).
 *   <li>{@link GroqRateLimitException} — rate limit do Groq; retry com backoff exponencial.
 *   <li>{@link CompletionException} — wrapper para exceções em CompletableFuture.
 *   <li>Erros fatais (Error, InterruptedException) — NUNCA engolidos; sempre repropagados.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AudioPipelineService {

    private static final int MAX_RETRIES = 4;
    private static final long BASE_RETRY_DELAY_MS = 2000L;

    // 🔧 Correção S8786: expressão mais eficiente, sem backtracking excessivo
    private static final Pattern RATE_LIMIT_PATTERN =
            Pattern.compile("try again in (\\d+(?:\\.\\d+)?)s");

    private final AudioService audioService;
    private final GroqClient groqClient;
    private final ChatTranscriptionCache chatTranscriptionCache;
    private final TranscriptStoreService transcriptStoreService;

    /**
     * Processa o fluxo completo de áudio com callback para envio de mensagens.
     *
     * @param ogaFile arquivo de áudio recebido (formato OGA); não deve ser nulo
     * @param chatId identificador do chat para cache da transcrição
     * @param userId identificador do usuário que enviou o áudio
     * @param userName nome do usuário (primeiro + último, se houver)
     * @param callback função de retorno que recebe o texto transcrito e um indicador de refinamento
     * @throws IllegalArgumentException se ogaFile ou callback forem nulos
     * @throws AudioProcessingException se houver falha no pipeline de áudio
     * @throws GroqRateLimitException se o rate limit persistir após todas as tentativas
     * @throws CompletionException se houver erro inesperado na execução assíncrona
     */
    public void processarFluxoAudio(
            File ogaFile,
            long chatId,
            long userId,
            String userName,
            BiConsumer<String, Boolean> callback) {

        validateInput(ogaFile, callback);
        log.info("Iniciando fluxo de processamento para: {}", ogaFile.getName());

        try {
            audioService
                    .converterParaWav(ogaFile)
                    .thenAccept(
                            wavFile ->
                                    processarTranscricao(
                                            wavFile, chatId, userId, userName, callback))
                    .exceptionally(this::handlePipelineException)
                    .thenRun(() -> deletarSilenciosamente(ogaFile))
                    .join();

        } catch (CompletionException e) {
            Throwable causa = unwrapCause(e);
            rethrowIfFatal(causa);

            if (causa instanceof AudioProcessingException ape) {
                throw ape;
            }
            if (causa instanceof GroqRateLimitException grle) {
                throw grle;
            }
            throw new AudioProcessingException(
                    "Erro inesperado no pipeline de áudio para arquivo: " + ogaFile.getName(),
                    causa);
        }
    }

    /**
     * Processa um arquivo de áudio OGA, converte para WAV, transcreve e refina. Não envia mensagens —
     * apenas processa e armazena internamente.
     *
     * @param ogaFile arquivo de áudio original (formato OGA); não deve ser nulo
     * @param chatId identificador do chat onde o áudio foi enviado
     * @param userId ID do usuário que enviou o áudio
     * @param userName nome do usuário
     * @return {@link CompletableFuture} contendo {@link ProcessedAudio}
     * @throws IllegalArgumentException se ogaFile for nulo
     */
    public CompletableFuture<ProcessedAudio> processarEArmazenar(
            File ogaFile, long chatId, long userId, String userName) {

        if (ogaFile == null) {
            throw new IllegalArgumentException("ogaFile não pode ser nulo");
        }

        return audioService
                .converterParaWav(ogaFile)
                .thenApplyAsync(this::transcreverERefinar)
                .whenComplete(
                        (result, ex) -> {
                            // Cleanup do arquivo OGA independente de sucesso ou falha
                            deletarSilenciosamente(ogaFile);
                            if (ex != null) {
                                log.error(
                                        "Falha no processamento assíncrono para chatId={}",
                                        chatId,
                                        ex);
                            }
                        });
    }

    // ======================== MÉTODOS PRIVADOS ========================

    /** Validação de entrada síncrona — falha rápido. */
    private void validateInput(File ogaFile, BiConsumer<String, Boolean> callback) {
        if (ogaFile == null) {
            throw new IllegalArgumentException("ogaFile não pode ser nulo");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback não pode ser nulo");
        }
    }

    /**
     * Pipeline de transcrição + refinamento + persistência. Executado dentro do thenAccept do
     * CompletableFuture.
     */
    private void processarTranscricao(
            File wavFile,
            long chatId,
            long userId,
            String userName,
            BiConsumer<String, Boolean> callback) {

        try {
            // --- Transcrição bruta ---
            String bruto = groqClient.transcrever(wavFile);
            callback.accept("🎙️ *Bruto:* \n_" + bruto + "_", false);

            // --- Refinamento com retry ---
            String refinado = retryRefinamento(bruto);

            // --- Persistência ---
            transcriptStoreService.saveTranscript(chatId, userId, userName, refinado);
            chatTranscriptionCache.salvar(chatId, refinado);

            callback.accept("✨ *Refinado:* \n" + refinado, true);

        } catch (HttpClientErrorException e) {
            throw new CompletionException(
                    new AudioProcessingException(
                            "Falha na comunicação com Groq para arquivo: " + wavFile.getName(), e));
        } catch (AudioProcessingException | GroqRateLimitException e) {
            // Já é nossa exceção de domínio — repassar sem wrap
            throw new CompletionException(e);
        } catch (RuntimeException e) {
            // Erros inesperados de runtime (NPE, etc.) — log + wrap
            log.error("Erro inesperado de runtime no pipeline para {}", wavFile.getName(), e);
            throw new CompletionException(
                    new AudioProcessingException(
                            "Erro inesperado no pipeline de áudio para arquivo: "
                                    + wavFile.getName(),
                            e));
        } finally {
            deletarSilenciosamente(wavFile);
        }
    }

    /**
     * Transcreve e refina um arquivo WAV, retornando ambos os textos. (Parâmetros chatId, userId,
     * userName foram removidos por não serem utilizados)
     */
    private ProcessedAudio transcreverERefinar(File wavFile) {
        try {
            String bruto = groqClient.transcrever(wavFile);
            String refinado = retryRefinamento(bruto);
            return new ProcessedAudio(bruto, refinado);
        } catch (HttpClientErrorException e) {
            throw new CompletionException(
                    new AudioProcessingException(
                            "Falha na comunicação com Groq para arquivo: " + wavFile.getName(), e));
        } catch (AudioProcessingException | GroqRateLimitException e) {
            throw new CompletionException(e);
        } catch (RuntimeException e) {
            log.error("Erro inesperado de runtime no processamento para {}", wavFile.getName(), e);
            throw new CompletionException(
                    new AudioProcessingException(
                            "Erro inesperado no processamento de áudio: " + wavFile.getName(), e));
        } finally {
            deletarSilenciosamente(wavFile);
        }
    }

    /**
     * Retry com backoff para refinamento, tratando rate limit 429 do Groq.
     *
     * @param textoBruto texto transcrito a ser refinado
     * @return texto refinado
     * @throws GroqRateLimitException se o rate limit persistir após MAX_RETRIES tentativas
     */
    private String retryRefinamento(String textoBruto) {
        Exception lastException = null;

        for (int tentativa = 0; tentativa < MAX_RETRIES; tentativa++) {
            try {
                return groqClient.refinarTexto(textoBruto);

            } catch (HttpClientErrorException.TooManyRequests e) {
                lastException = e;
                long waitMs = calcularWaitTime(e, tentativa);
                log.warn(
                        "Rate limit Groq (429), aguardando {}ms (tentativa {}/{})",
                        waitMs,
                        tentativa + 1,
                        MAX_RETRIES);

                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new GroqRateLimitException(
                            "Thread interrompida durante backoff de rate limit", ie);
                }

            } catch (HttpClientErrorException e) {
                // Outro erro HTTP (4xx/5xx) — não faz sentido retry
                throw new AudioProcessingException(
                        "Erro HTTP do Groq no refinamento: " + e.getStatusCode(), e);
            }
        }

        throw new GroqRateLimitException(
                "Falha no refinamento após " + MAX_RETRIES + " tentativas", lastException);
    }

    /**
     * Calcula o tempo de espera para retry: - Se o Groq informar tempo específico no header/body, usa
     * esse valor + margem - Senão, usa backoff exponencial: delay * (tentativa + 1)
     */
    private long calcularWaitTime(HttpClientErrorException.TooManyRequests e, int tentativa) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("try again in")) {
            try {
                Matcher matcher = RATE_LIMIT_PATTERN.matcher(msg);
                if (matcher.find()) {
                    double waitSeconds = Double.parseDouble(matcher.group(1));
                    return (long) (waitSeconds * 1000) + 500;
                }
            } catch (NumberFormatException nfe) {
                log.warn("Não foi possível parsear tempo de espera do Groq: '{}'", msg);
            }
        }
        return BASE_RETRY_DELAY_MS * (tentativa + 1);
    }

    /**
     * Handler central de exceções do pipeline. Desempacota CompletionException e repropaga como
     * AudioProcessingException.
     */
    private Void handlePipelineException(Throwable ex) {
        Throwable causa = unwrapCause(ex);
        rethrowIfFatal(causa);

        if (causa instanceof AudioProcessingException || causa instanceof GroqRateLimitException) {
            throw (RuntimeException) causa;
        }

        throw new CompletionException(
                new AudioProcessingException("Erro inesperado no pipeline de áudio", causa));
    }

    /** Desempacota CompletionException para obter a causa raiz. */
    private Throwable unwrapCause(Throwable ex) {
        return (ex instanceof CompletionException && ex.getCause() != null) ? ex.getCause() : ex;
    }

    /**
     * Repropaga erros fatais (Error, InterruptedException) sem engoli-los. Estes NUNCA devem ser
     * tratados como exceções de negócio.
     */
    private void rethrowIfFatal(Throwable t) {
        // 🔧 Correção S6201: uso de pattern matching para instanceof
        if (t instanceof Error error) {
            throw error;
        }
        if (t instanceof InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new CompletionException("Thread interrompida", ie);
        }
    }

    /** Deleta arquivo temporário silenciosamente. Falhas são logadas mas não propagadas. */
    private void deletarSilenciosamente(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        try {
            Files.delete(Path.of(file.getAbsolutePath()));
            log.debug("Arquivo temporário excluído: {}", file.getAbsolutePath());
        } catch (IOException ex) {
            log.warn("Não foi possível excluir arquivo temporário: {}", file.getAbsolutePath(), ex);
        }
    }

    // ======================== RECORDS / DTOs ========================

    public record ProcessedAudio(String bruto, String refinado) {}
}
