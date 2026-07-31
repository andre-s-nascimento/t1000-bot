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

    // Agendamento: toda sexta-feira ao meio-dia (12:00)
    @Scheduled(cron = "0 0 12 * * 5", zone = "America/Sao_Paulo")
    public void publishWeeklyPodcast() {
        log.info("🎧 Iniciando geração do podcast semanal (Sexta-feira)...");

        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        // Período: de segunda a domingo da semana passada (para pegar a semana completa)
        LocalDate endOfWeek = today.with(DayOfWeek.SUNDAY).minusWeeks(1);
        LocalDate startOfWeek = endOfWeek.with(DayOfWeek.MONDAY);

        // Gera e envia usando o método comum
        generateAndSendPodcast(startOfWeek, endOfWeek, publishChatId);
    }

    /** Método público para gerar e enviar o podcast sob demanda (usado pelo endpoint de teste). */
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
        log.info("🔊 Áudio sintetizado: {} bytes", audioData.length);

        // 3. Gera nome do arquivo
        String fileName = generatePodcastFileName(endDate);
        log.info("📁 Nome do arquivo: {}", fileName);

        // 4. Salva e envia
        Path tempFile = null;
        try {
            tempFile = tempDirService.createTempFile("podcast_", ".mp3");
            Files.write(tempFile, audioData);

            // Move para o nome final (opcional, mas podemos renomear)
            Path finalFile = tempFile.resolveSibling(fileName);
            Files.move(tempFile, finalFile);

            String caption =
                    String.format(
                            "<b>🎙️ Silas Cast</b>\n" + "📅 Período: %s a %s",
                            startDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                            endDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

            log.info("📤 Enviando para o Telegram...");
            telegramFacade.enviarMidia(chatId, finalFile.toAbsolutePath().toString(), caption);
            log.info("📤 Áudio enviado para chat {}", chatId);

            // Limpeza
            Files.deleteIfExists(finalFile);

        } catch (Exception e) {
            log.error("❌ Erro ao salvar ou enviar áudio", e);
            telegramFacade.enviarMensagem(chatId, "❌ Erro ao processar podcast: " + e.getMessage());
        } finally {
            if (tempFile != null && Files.exists(tempFile)) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // Falha ao deletar arquivo temporário – pode ser ignorado
                    log.debug("Não foi possível deletar arquivo: {}", tempFile);
                }
            }
        }
        log.info("✅ Podcast finalizado em {}ms", System.currentTimeMillis() - start);
    }

    /**
     * Gera o nome do arquivo no formato: SilasCast-Semana-XX-do-Mes-YY-do-Ano-YYYY.mp3 onde XX é a
     * semana dentro do mês (1-5) e YY é o mês (01-12)
     */
    private String generatePodcastFileName(LocalDate date) {
        // Calcula a semana dentro do mês (1 a 5)
        int weekOfMonth = (date.getDayOfMonth() - 1) / 7 + 1;
        int month = date.getMonthValue();
        int year = date.getYear();

        return String.format(
                "SilasCast-Semana-%02d-do-Mes-%02d-do-Ano-%d.mp3", weekOfMonth, month, year);
    }
}
