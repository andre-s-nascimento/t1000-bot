package net.ddns.adambravo79.tmill.service.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.dto.AudioReceivedEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudioEventPublisher {

    private static final String TOPIC_RECEIVED = "t1000.audio.received";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(AudioReceivedEvent event) {
        try {
            log.info("📤 [Kafka Producer] Enviando evento... fileId={}", event.fileId());

            kafkaTemplate.send(TOPIC_RECEIVED, event.fileId(), event).get();

            log.info("✅ [Kafka Producer] Broker confirmou gravação do fileId={}", event.fileId());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            log.error(
                    "❌ [Kafka Producer] Thread interrompida durante publicação! fileId={}",
                    event.fileId(),
                    e);

            throw new IllegalStateException("Thread interrompida ao publicar evento no Kafka", e);

        } catch (Exception e) {
            log.error(
                    "❌ [Kafka Producer] Falha crítica ao publicar no Kafka! fileId={}",
                    event.fileId(),
                    e);

            throw new IllegalStateException(
                    "Falha ao publicar evento no Kafka: " + event.fileId(), e);
        }
    }
}
