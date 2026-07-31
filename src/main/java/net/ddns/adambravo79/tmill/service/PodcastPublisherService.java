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
    private final AzureTtsClient ttsClient; // ✅ injetado
    private final TelegramFacade telegramFacade;

    @Value("${podcast.publish.chat-id}")
    private long publishChatId;

    @Value("${podcast.target.user-id}")
    private long targetUserId;

    @Scheduled(cron = "${podcast.schedule.cron:0 0 22 * * 0}", zone = "America/Sao_Paulo")
    public void publishWeeklyPodcast() {
        log.info("🎧 Iniciando geração do podcast semanal...");

        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        LocalDate endOfWeek = today.with(DayOfWeek.SUNDAY).minusWeeks(1);
        LocalDate startOfWeek = endOfWeek.with(DayOfWeek.MONDAY);

        // 1. Gera roteiro
        String script = scriptService.generateScript(startOfWeek, endOfWeek);
        if (script == null || script.isBlank()) {
            log.info("📭 Nenhuma mensagem do usuário {} na semana passada.", targetUserId);
            return;
        }

        // 2. Sintetiza todo o áudio (já faz divisão e concatenação internamente)
        byte[] audioData = ttsClient.synthesizeFullText(script);
        if (audioData == null || audioData.length == 0) {
            log.error("❌ Nenhum áudio gerado.");
            return;
        }

        // 3. Salva em arquivo temporário único
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("podcast_semanal_", ".mp3");
            Files.write(tempFile, audioData);

            String caption =
                    String.format(
                            """
              🎙️ **Podcast Semanal do Silas**
              📅 Semana de %s a %s
              ⏱️ Duração: ~%d segundos
              """,
                            startOfWeek.format(DateTimeFormatter.ofPattern("dd/MM")),
                            endOfWeek.format(DateTimeFormatter.ofPattern("dd/MM")),
                            audioData.length / 16000 // estimativa
                            );

            // 4. Publica no Telegram
            telegramFacade.enviarMidia(
                    publishChatId, tempFile.toAbsolutePath().toString(), caption);
            log.info("✅ Podcast publicado com sucesso!");

        } catch (IOException e) {
            log.error("❌ Erro ao salvar ou publicar áudio.", e);
        } finally {
            // 5. Limpeza
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    log.warn("Não foi possível deletar arquivo temporário: {}", tempFile);
                }
            }
        }
    }
}
