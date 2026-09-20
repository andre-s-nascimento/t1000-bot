package net.ddns.adambravo79.tmill.controller.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.dto.AudioProcessedEvent;
import net.ddns.adambravo79.tmill.service.BotAnalyticsService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudioResultConsumer {

    private final TelegramFacade telegramFacade;
    private final BotAnalyticsService botAnalyticsService; // 💾 Injeção do nosso serviço NoSQL

    @KafkaListener(topics = "t1000.audio.processed", groupId = "t1000-bot-responses")
    public void handleProcessedAudio(@Payload AudioProcessedEvent event) {
        log.info(
                "📥 [Bot] Recebida resposta do Worker via Kafka. chatId={}, sucesso={}",
                event.chatId(),
                event.sucesso());

        if (event.sucesso()) {
            String mensagemSucesso =
                    "✅ *Áudio processado com sucesso!* (Duração: " + event.duration() + "s)";
            telegramFacade.enviarMensagemHtml(event.chatId(), mensagemSucesso);

            // 💾 Grava o log de sucesso estruturado no MongoDB
            botAnalyticsService.registrarLogInteracao(
                    event.chatId(),
                    event.senderId(),
                    event.senderName(),
                    "AUDIO_PROCESSED_SUCCESS",
                    "Áudio fileId=" + event.fileId() + " processado com sucesso.");

        } else {
            String mensagemErro =
                    String.format(
                            "❌ *Ops! Não consegui processar seu áudio.*\n\nMotivo: %s",
                            event.mensagemErro());
            telegramFacade.enviarMensagemHtml(event.chatId(), mensagemErro);

            // 💾 Grava o log de erro estruturado no MongoDB
            botAnalyticsService.registrarLogInteracao(
                    event.chatId(),
                    event.senderId(),
                    event.senderName(),
                    "AUDIO_PROCESSED_ERROR",
                    "Erro no worker para fileId=" + event.fileId() + ": " + event.mensagemErro());
        }
    }
}
