/* (c) 2026 | 04/10/2026 */
package net.ddns.adambravo79.tmill.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.client.AzureTtsClient;
import net.ddns.adambravo79.tmill.service.config.AudioConfigService;
import net.ddns.adambravo79.tmill.service.feature.FeatureFlagAdminService;
import net.ddns.adambravo79.tmill.service.prompt.PromptRegistryService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;

@Service
@Slf4j
@RequiredArgsConstructor
public class PodcastPublisherService implements SchedulingConfigurer {

    private final PodcastScriptService scriptService;
    private final AzureTtsClient ttsClient;
    private final TelegramFacade telegramFacade;
    private final TempDirService tempDirService;
    private final MetricsService metricsService;
    private final PromptRegistryService promptRegistryService;
    private final AudioConfigService audioConfig;
    private final FeatureFlagAdminService featureFlags;

    @Value("${podcast.publish.chat-id}")
    private long publishChatId;

    @Value("${podcast.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${podcast.retry.delay-ms:5000}")
    private long retryDelayMs;

    // Constante para formatação de data
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(
                this::publishWeeklyPodcast,
                ctx ->
                        new CronTrigger(
                                        promptRegistryService.getPodcastCron(),
                                        ZoneId.of("America/Sao_Paulo"))
                                .nextExecution(ctx));
    }

    /**
     * Publicação semanal agendada via cron.
     *
     * <p><b>Feature flag:</b> se {@code podcast.enabled} estiver desabilitada, este método apenas
     * loga e retorna sem fazer nada. O admin ainda pode disparar manualmente via {@link
     * #generateAndSendPodcast(LocalDate, LocalDate, long)} (endpoints {@code /admin/test-podcast},
     * {@code /admin/podcast/resend}).
     */
    public void publishWeeklyPodcast() {
        if (!featureFlags.isEnabled("podcast.enabled")) {
            log.info("🎧 Podcast desabilitado por feature flag — pulando publicação semanal.");
            return;
        }

        log.info("🎧 Iniciando geração do podcast semanal (Sexta-feira)...");

        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        LocalDate endOfWeek = today.with(DayOfWeek.SUNDAY).minusWeeks(1);
        LocalDate startOfWeek = endOfWeek.with(DayOfWeek.MONDAY);

        generateAndSendPodcast(startOfWeek, endOfWeek, publishChatId);
    }

    // =========================================================================
    // SOBRECARGA 1: dia inteiro (3 args)
    // =========================================================================

    /**
     * Gera e envia o podcast para um intervalo de dias (00:00 → 23:59).
     *
     * <p>Usado pelos endpoints administrativos e pelo cron semanal. NÃO valida feature flags —
     * chamadas administrativas explícitas devem funcionar independentemente de {@code
     * podcast.enabled}.
     */
    public void generateAndSendPodcast(LocalDate startDate, LocalDate endDate, long chatId) {
        generateAndSendPodcast(startDate, endDate, chatId, null, null);
    }

    // =========================================================================
    // SOBRECARGA 2: intervalo preciso (5 args)
    // =========================================================================

    /**
     * Gera e envia o podcast respeitando um intervalo preciso de {@link LocalDateTime}.
     *
     * <p>Se {@code from} e {@code to} forem nulos, cai no comportamento antigo (dia inteiro). Usado
     * pelo endpoint de reenvio por sexta-feira para respeitar o intervalo {@code sexta 12:00 →
     * sexta 11:59:59}.
     */
    public void generateAndSendPodcast(
            LocalDate startDate,
            LocalDate endDate,
            long chatId,
            LocalDateTime from,
            LocalDateTime to) {

        log.info(
                "🎧 Gerando podcast para período de {} a {} (chatId={})",
                (from != null ? from : startDate),
                (to != null ? to : endDate),
                chatId);
        long start = System.currentTimeMillis();

        // 1. Gera roteiro
        log.info("📝 Iniciando geração do roteiro...");
        String script = scriptService.generateScript(startDate, endDate, from, to);
        if (script == null || script.isBlank()) {
            log.warn("Nenhuma transcrição encontrada.");
            telegramFacade.enviarMensagem(chatId, "📭 Nenhuma transcrição para o período.");
            metricsService.error("podcast_sem_roteiro");
            return;
        }
        log.info("📝 Roteiro gerado ({} caracteres).", script.length());

        // 2. Sintetiza e avalia necessidade de compressão do áudio
        byte[] audioData = generateAndProcessAudio(script, chatId, startDate, endDate);

        if (audioData.length == 0) {
            return; // O fluxo foi encerrado (com fallback disparado) nos sub-métodos
        }

        // 3 e 4. Prepara arquivo, salva e envia
        saveAndSendPodcast(audioData, script, startDate, endDate, chatId);

        long duration = System.currentTimeMillis() - start;
        double sizeMb = audioData.length / 1024.0 / 1024.0;
        log.info(
                "📊 Métricas: tamanho={}MB, duração={}ms, caracteres={}",
                String.format("%.2f", sizeMb),
                duration,
                script.length());
        log.info("✅ Podcast finalizado em {}ms", System.currentTimeMillis() - start);
    }

    // =========================================================================
    // ETAPAS INTERNAS
    // =========================================================================

    private byte[] generateAndProcessAudio(
            String script, long chatId, LocalDate startDate, LocalDate endDate) {
        log.info("🔊 Iniciando síntese de áudio...");
        byte[] audioData;
        try {
            audioData = ttsClient.synthesizeFullText(script);
        } catch (Exception e) {
            log.error("❌ Exceção na síntese do áudio", e);
            metricsService.error("podcast_tts_vazio");
            enviarRoteiroComoTexto(chatId, script, startDate, endDate);
            return new byte[0];
        }

        if (audioData == null || audioData.length == 0) {
            log.error("❌ Áudio vazio.");
            telegramFacade.enviarMensagem(chatId, "❌ Erro ao gerar áudio do podcast.");
            metricsService.error("podcast_tts_vazio");
            return new byte[0];
        }

        return checkAndCompressAudio(audioData);
    }

    private byte[] checkAndCompressAudio(byte[] audioData) {
        AudioConfigService.PodcastAudioConfig cfg = audioConfig.getPodcast();
        long maxBytes = cfg.maxAudioSizeBytes();

        double audioSizeMb = audioData.length / 1024.0 / 1024.0;
        log.info(
                "🔊 Áudio sintetizado: {} bytes ({} MB)",
                audioData.length,
                String.format("%.2f", audioSizeMb));

        if (audioData.length <= maxBytes) {
            metricsService.success("podcast_compressao_pulada");
            return audioData;
        }

        log.info(
                "🔊 Áudio grande ({} MB > {} MB), comprimindo...",
                String.format("%.2f", audioSizeMb),
                String.format("%.2f", maxBytes / 1024.0 / 1024.0));

        long originalSize = audioData.length;
        byte[] compressedData = compressAudio(audioData);

        if (compressedData.length < originalSize) {
            double reduction = (1 - compressedData.length / (double) originalSize) * 100;
            double compressedSizeMb = compressedData.length / 1024.0 / 1024.0;
            log.info(
                    "🔊 Áudio comprimido: {} bytes ({} MB) - redução de {}%",
                    compressedData.length,
                    String.format("%.2f", compressedSizeMb),
                    String.format("%.1f", reduction));
            metricsService.success("podcast_compressao_ok");
            return compressedData;
        }

        log.warn("⚠️ FFmpeg não reduziu o tamanho. Mantendo original.");
        metricsService.error("podcast_compressao_falha");
        return audioData;
    }

    private void saveAndSendPodcast(
            byte[] audioData, String script, LocalDate startDate, LocalDate endDate, long chatId) {
        String fileName = generatePodcastFileName(endDate);
        log.info("📁 Nome do arquivo: {}", fileName);

        Path tempFile = null;
        try {
            tempFile = tempDirService.createTempFile("podcast_", ".mp3");
            Files.write(tempFile, audioData);

            Path finalFile = tempFile.resolveSibling(fileName);
            Files.move(tempFile, finalFile);

            String caption =
                    String.format(
                            """
                            <b>🎙️ Silas Cast</b>
                            📅 Período: %s a %s
                            📊 Tamanho: %.1f MB\
                            """,
                            startDate.format(DATE_FORMATTER),
                            endDate.format(DATE_FORMATTER),
                            audioData.length / 1024.0 / 1024.0);

            log.info("📤 Enviando para o Telegram...");

            boolean sent = sendWithRetry(chatId, finalFile, caption);

            if (sent) {
                log.info("📤 Áudio enviado para chat {}", chatId);
                metricsService.success("podcast_publicado");
            } else {
                log.error("❌ Falha ao enviar áudio após {} tentativas", maxRetryAttempts);
                metricsService.error("podcast_falha_envio");
                enviarRoteiroComoTexto(chatId, script, startDate, endDate);
            }

            Files.deleteIfExists(finalFile);

        } catch (Exception e) {
            log.error("❌ Erro ao salvar ou enviar áudio", e);
            metricsService.error("podcast_falha_envio");
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
    }

    // ===== COMPRESSÃO =====

    @SuppressWarnings(
            "java:S4036") // Ignorado propositalmente: uso do FFMPEG via PATH da imagem Docker
    private byte[] compressAudio(byte[] audioData) {
        Path inputFile = null;
        Path outputFile = null;
        try {
            inputFile = tempDirService.createTempFile("compress_input_", ".mp3");
            Files.write(inputFile, audioData);

            outputFile = tempDirService.createTempFile("compress_output_", ".mp3");

            AudioConfigService.TtsConfig ttsCfg = audioConfig.getTts();

            String[] cmd = {
                "ffmpeg",
                "-y",
                "-i",
                inputFile.toString(),
                "-b:a",
                ttsCfg.compressBitrate(),
                "-ac",
                String.valueOf(ttsCfg.compressChannels()),
                "-ar",
                String.valueOf(ttsCfg.compressSampleRate()),
                outputFile.toString()
            };

            log.debug("⚙️ Executando FFmpeg: {}", String.join(" ", cmd));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            long timeoutSeconds = audioConfig.getPodcast().compressTimeoutSeconds();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            int exitCode = finished ? process.exitValue() : 1;

            if (exitCode == 0 && Files.exists(outputFile) && Files.size(outputFile) > 0) {
                byte[] compressed = Files.readAllBytes(outputFile);
                double reduction = (1 - compressed.length / (double) audioData.length) * 100;
                log.info(
                        "✅ Compressão concluída: {} -> {} bytes ({}% redução)",
                        audioData.length, compressed.length, String.format("%.1f", reduction));
                return compressed;
            }

            log.warn("⚠️ FFmpeg falhou com código {}, mantendo áudio original", exitCode);
            return audioData;

        } catch (InterruptedException e) { // Captura específica de interrupção (SonarQube)
            log.warn("⚠️ Compressão interrompida. Mantendo áudio original.");
            Thread.currentThread().interrupt(); // Restaura flag da Thread
            return audioData;
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
     * Tenta enviar o áudio até {@code maxRetryAttempts} vezes.
     *
     * <p><b>KI-001:</b> só faz retry se {@link TelegramFacade#enviarMidia} retornar {@code false} ou
     * lançar exceção.
     */
    private boolean sendWithRetry(long chatId, Path file, String caption) {
        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            log.info("📤 Tentativa {}/{} de enviar áudio", attempt, maxRetryAttempts);
            try {
                boolean enviado =
                        telegramFacade.enviarMidia(
                                chatId, file.toAbsolutePath().toString(), caption);
                if (enviado) {
                    log.info("✅ Áudio enviado na tentativa {}", attempt);
                    return true;
                }
                log.warn("⚠️ Tentativa {} retornou false (envio falhou silenciosamente)", attempt);
            } catch (Exception e) {
                log.warn("⚠️ Tentativa {} lançou exceção: {}", attempt, e.getMessage());
            }

            if (attempt < maxRetryAttempts) {
                try {
                    log.info("⏳ Aguardando {}ms antes da próxima tentativa...", retryDelayMs);
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    // ===== FALLBACK DE TEXTO =====

    private void enviarRoteiroComoTexto(
            long chatId, String script, LocalDate startDate, LocalDate endDate) {
        try {
            String header =
                    String.format(
                            """
                            🎙️ Silas Cast (Áudio indisponível)
                            📅 Período: %s a %s

                            📝 Roteiro:
                            """,
                            startDate.format(DATE_FORMATTER), endDate.format(DATE_FORMATTER));

            String fullText = header + script;
            int maxLength = 4000;

            if (fullText.length() <= maxLength) {
                telegramFacade.enviarMensagemHtml(chatId, fullText);
            } else {
                int totalParts = (fullText.length() + maxLength - 1) / maxLength;
                for (int i = 0; i < fullText.length(); i += maxLength) {
                    int partNumber = (i / maxLength) + 1;
                    String partText =
                            fullText.substring(i, Math.min(i + maxLength, fullText.length()));
                    telegramFacade.enviarMensagemHtml(
                            chatId,
                            String.format(
                                    "📄 Parte %d/%d%n%n%s", partNumber, totalParts, partText));
                }
            }
            log.info("📝 Roteiro enviado como texto (fallback)");
            metricsService.success("podcast_fallback_texto");
        } catch (Exception e) {
            log.error("❌ Erro ao enviar roteiro como texto", e);
        }
    }

    private String generatePodcastFileName(LocalDate date) {
        int weekOfMonth = (date.getDayOfMonth() - 1) / 7 + 1;
        int month = date.getMonthValue();
        int year = date.getYear();

        return String.format(
                "SilasCast-Semana-%02d-do-Mes-%02d-do-Ano-%d.mp3", weekOfMonth, month, year);
    }
}
