package net.ddns.adambravo79.tmill.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.client.AzureTtsClient;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;

@Service
@Slf4j
@RequiredArgsConstructor
public class PodcastPublisherService {

    private final PodcastScriptService scriptService;
    private final AzureTtsClient ttsClient;
    private final TelegramFacade telegramFacade;
    private final TempDirService tempDirService;

    @Value("${podcast.publish.chat-id}")
    private long publishChatId;

    @Value("${podcast.target.user-id}")
    private long targetUserId;

    // Tamanho máximo do áudio antes de comprimir (5MB)
    private static final long MAX_AUDIO_SIZE_BYTES = 5 * 1024 * 1024;

    // Agendamento: toda sexta-feira ao meio-dia (12:00)
    @Scheduled(cron = "0 0 12 * * 5", zone = "America/Sao_Paulo")
    public void publishWeeklyPodcast() {
        log.info("🎧 Iniciando geração do podcast semanal (Sexta-feira)...");

        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        LocalDate endOfWeek = today.with(DayOfWeek.SUNDAY).minusWeeks(1);
        LocalDate startOfWeek = endOfWeek.with(DayOfWeek.MONDAY);

        generateAndSendPodcast(startOfWeek, endOfWeek, publishChatId);
    }

    public void generateAndSendPodcast(LocalDate startDate, LocalDate endDate, long chatId) {
        log.info(
                "🎧 Gerando podcast para período de {} a {}, chatId={}",
                startDate,
                endDate,
                chatId);
        long start = System.currentTimeMillis();

        // 1. Gera roteiro
        log.info("📝 Iniciando geração do roteiro...");
        String script = scriptService.generateScript(startDate, endDate);
        if (script == null || script.isBlank()) {
            log.warn("Nenhuma transcrição encontrada.");
            telegramFacade.enviarMensagem(chatId, "📭 Nenhuma transcrição para o período.");
            return;
        }
        log.info("📝 Roteiro gerado ({} caracteres).", script.length());

        // 2. Sintetiza áudio
        log.info("🔊 Iniciando síntese de áudio...");
        byte[] audioData = ttsClient.synthesizeFullText(script);
        if (audioData == null || audioData.length == 0) {
            log.error("❌ Áudio vazio.");
            telegramFacade.enviarMensagem(chatId, "❌ Erro ao gerar áudio do podcast.");
            return;
        }

        double audioSizeMb = audioData.length / 1024.0 / 1024.0;
        log.info("🔊 Áudio sintetizado: {} bytes ({:.2f} MB)", audioData.length, audioSizeMb);

        // 🔥 PASSO 2: Comprime se necessário
        if (audioData.length > MAX_AUDIO_SIZE_BYTES) {
            log.info("🔊 Áudio grande ({:.2f} MB), comprimindo...", audioSizeMb);
            audioData = compressAudio(audioData);
            double compressedSizeMb = audioData.length / 1024.0 / 1024.0;
            log.info(
                    "🔊 Áudio comprimido: {} bytes ({:.2f} MB) - redução de {:.1f}%",
                    audioData.length,
                    compressedSizeMb,
                    (1 - audioData.length / (double) (audioData.length * 1.0))
                            * 100); // Será calculado depois
        }

        // 3. Gera nome do arquivo
        String fileName = generatePodcastFileName(endDate);
        log.info("📁 Nome do arquivo: {}", fileName);

        // 4. Salva e envia
        Path tempFile = null;
        try {
            tempFile = tempDirService.createTempFile("podcast_", ".mp3");
            Files.write(tempFile, audioData);

            Path finalFile = tempFile.resolveSibling(fileName);
            Files.move(tempFile, finalFile);

            String caption =
                    String.format(
                            "<b>🎙️ Silas Cast</b>\n"
                                    + "📅 Período: %s a %s\n"
                                    + "📊 Tamanho: %.1f MB",
                            startDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                            endDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                            audioData.length / 1024.0 / 1024.0);

            log.info("📤 Enviando para o Telegram...");

            // 🔥 PASSO 3: Retry com backoff
            boolean sent = sendWithRetry(chatId, finalFile, caption);

            if (sent) {
                log.info("📤 Áudio enviado para chat {}", chatId);
            } else {
                log.error("❌ Falha ao enviar áudio após 3 tentativas");
                // Fallback: envia o roteiro como texto
                enviarRoteiroComoTexto(chatId, script, startDate, endDate);
            }

            Files.deleteIfExists(finalFile);

        } catch (Exception e) {
            log.error("❌ Erro ao salvar ou enviar áudio", e);
            enviarRoteiroComoTexto(chatId, script, startDate, endDate);
        } finally {
            if (tempFile != null && Files.exists(tempFile)) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    log.debug("Não foi possível deletar arquivo: {}", tempFile);
                }
            }
        }
        long duration = System.currentTimeMillis() - start;
        double sizeMb = audioData.length / 1024.0 / 1024.0;
        log.info(
                "📊 Métricas: tamanho={:.2f}MB, duração={}ms, caracteres={}",
                sizeMb,
                duration,
                script.length());
        log.info("✅ Podcast finalizado em {}ms", System.currentTimeMillis() - start);
    }

    // ===== NOVOS MÉTODOS =====

    /**
     * 🔥 Compressão de áudio usando FFmpeg
     * Reduz bitrate para 64kbps, mono, 22.05kHz
     */
    private byte[] compressAudio(byte[] audioData) {
        Path inputFile = null;
        Path outputFile = null;
        try {
            inputFile = tempDirService.createTempFile("compress_input_", ".mp3");
            Files.write(inputFile, audioData);

            outputFile = tempDirService.createTempFile("compress_output_", ".mp3");

            // Comprime para 64kbps (qualidade aceitável, tamanho reduzido)
            String[] cmd = {
                "ffmpeg",
                "-y",
                "-i",
                inputFile.toString(),
                "-b:a",
                "64k",
                "-ac",
                "1", // Mono
                "-ar",
                "22050", // 22.05kHz
                outputFile.toString()
            };

            log.debug("⚙️ Executando FFmpeg: {}", String.join(" ", cmd));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Aguarda até 60 segundos
            boolean finished = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
            int exitCode = finished ? process.exitValue() : 1;

            if (exitCode == 0 && Files.exists(outputFile) && Files.size(outputFile) > 0) {
                byte[] compressed = Files.readAllBytes(outputFile);
                double reduction = (1 - compressed.length / (double) audioData.length) * 100;
                log.info(
                        "✅ Compressão concluída: {} -> {} bytes ({:.1f}% redução)",
                        audioData.length, compressed.length, reduction);
                return compressed;
            } else {
                log.warn("⚠️ FFmpeg falhou com código {}, mantendo áudio original", exitCode);
                return audioData;
            }

        } catch (Exception e) {
            log.warn("⚠️ Falha na compressão: {}. Mantendo áudio original.", e.getMessage());
            return audioData;
        } finally {
            try {
                if (inputFile != null) Files.deleteIfExists(inputFile);
                if (outputFile != null) Files.deleteIfExists(outputFile);
            } catch (IOException ignored) {
                log.debug("Não foi possível deletar arquivos temporários de compressão");
            }
        }
    }

    /**
     * 🔥 Envia com retry (3 tentativas, 5s de espera entre elas)
     */
    private boolean sendWithRetry(long chatId, Path file, String caption) {
        int maxRetries = 3;
        long retryDelayMs = 5000;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("📤 Tentativa {}/{} de enviar áudio", attempt, maxRetries);
                telegramFacade.enviarMidia(chatId, file.toAbsolutePath().toString(), caption);
                log.info("✅ Áudio enviado na tentativa {}", attempt);
                return true;
            } catch (Exception e) {
                log.warn("⚠️ Tentativa {} falhou: {}", attempt, e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        log.info("⏳ Aguardando {}ms antes da próxima tentativa...", retryDelayMs);
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 🔥 Fallback: envia o roteiro como texto se o áudio falhar
     */
    private void enviarRoteiroComoTexto(
            long chatId, String script, LocalDate startDate, LocalDate endDate) {
        try {
            String header =
                    String.format(
                            "🎙️ Silas Cast (Áudio indisponível)\n"
                                    + "📅 Período: %s a %s\n\n"
                                    + "📝 Roteiro:\n",
                            startDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                            endDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

            String fullText = header + script;
            int maxLength = 4000;

            if (fullText.length() <= maxLength) {
                telegramFacade.enviarMensagemHtml(chatId, fullText);
            } else {
                // Envia em partes
                int totalParts = (fullText.length() + maxLength - 1) / maxLength;
                for (int i = 0; i < fullText.length(); i += maxLength) {
                    int partNumber = (i / maxLength) + 1;
                    String partText =
                            fullText.substring(i, Math.min(i + maxLength, fullText.length()));
                    telegramFacade.enviarMensagemHtml(
                            chatId,
                            String.format(
                                    "📄 Parte %d/%d\n\n%s", partNumber, totalParts, partText));
                }
            }
            log.info("📝 Roteiro enviado como texto (fallback)");
        } catch (Exception e) {
            log.error("❌ Erro ao enviar roteiro como texto", e);
        }
    }

    /**
     * Gera o nome do arquivo no formato:
     * SilasCast-Semana-XX-do-Mes-YY-do-Ano-YYYY.mp3 onde XX é a
     * semana dentro do mês (1-5) e YY é o mês (01-12)
     */
    private String generatePodcastFileName(LocalDate date) {
        int weekOfMonth = (date.getDayOfMonth() - 1) / 7 + 1;
        int month = date.getMonthValue();
        int year = date.getYear();

        return String.format(
                "SilasCast-Semana-%02d-do-Mes-%02d-do-Ano-%d.mp3", weekOfMonth, month, year);
    }
}
