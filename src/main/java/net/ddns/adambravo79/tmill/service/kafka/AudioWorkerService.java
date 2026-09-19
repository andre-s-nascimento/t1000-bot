package net.ddns.adambravo79.tmill.service.kafka;

import java.io.File;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.dto.AudioProcessedEvent;
import net.ddns.adambravo79.tmill.dto.AudioReceivedEvent;
import net.ddns.adambravo79.tmill.service.AudioPipelineService;
import net.ddns.adambravo79.tmill.service.TelegramFileService;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioWorkerService {

    private final TelegramFileService fileService;
    private final AudioPipelineService audioPipeline;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC_PROCESSED = "t1000.audio.processed";

    // Usamos um novo groupId ("t1000-workers-v4") para ignorar qualquer histórico de leitura
    @KafkaListener(topics = "t1000.audio.received", groupId = "t1000-workers-v4")
    public void consumeAudioRequest(@Payload AudioReceivedEvent event) {
        log.info(
                "🚀 [SUCESSO!] Worker capturou o evento. FileId: {}, Usuário: {}",
                event.fileId(),
                event.userName());

        File audioFile = null;
        int duration = event.duration();
        try {
            // 1. Baixa o arquivo do Telegram de forma autônoma no Worker
            audioFile = fileService.baixarArquivo(event.fileId());
            if (audioFile == null || !audioFile.exists()) {
                log.error("❌ [Kafka Worker] Arquivo não encontrado para fileId={}", event.fileId());
                publicarResposta(event, false, "Arquivo temporário não encontrado.", 0);
                return;
            }

            // 2. Processa o fluxo de áudio (Whisper + Llama)
            audioPipeline.processarFluxoAudio(
                    audioFile,
                    event.groupId() != 0 ? event.groupId() : event.chatId(),
                    event.userId(),
                    event.userName(),
                    (texto, isUltima) -> {
                        if (Boolean.TRUE.equals(isUltima)) {
                            log.info(
                                    "✅ [Kafka Worker] Transcrição concluída para chatId={}",
                                    event.chatId());
                            // Aqui você pode disparar o evento de sucesso para o bot postar os
                            // botões ou o
                            // texto
                        }
                    });

            publicarResposta(event, true, null, duration);

        } catch (Exception e) {
            log.error(
                    "❌ [Kafka Worker] Erro crítico ao processar áudio fileId={}",
                    event.fileId(),
                    e);
            publicarResposta(event, false, e.getMessage(), 0);
        } finally {
            if (audioFile != null && audioFile.exists()) {
                audioFile.delete();
            }
        }
    }

    private void publicarResposta(
            AudioReceivedEvent request, boolean sucesso, String erro, int duration) {
        var responseEvent =
                new AudioProcessedEvent(
                        request.fileId(),
                        request.chatId(),
                        request.userId(),
                        request.userName(),
                        sucesso,
                        erro,
                        duration);
        kafkaTemplate.send(TOPIC_PROCESSED, responseEvent.fileId(), responseEvent);
    }
}
