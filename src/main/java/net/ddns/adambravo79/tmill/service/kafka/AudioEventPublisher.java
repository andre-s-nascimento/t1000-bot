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

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC_RECEIVED = "t1000.audio.received";

    public void publish(AudioReceivedEvent event) {
        try {
            log.info("📤 [Kafka Producer] Enviando evento... fileId={}", event.fileId());

            // O .get() força a thread a esperar a confirmação oficial do Broker Kafka
            kafkaTemplate.send(TOPIC_RECEIVED, event.fileId(), event).get();

            log.info("✅ [Kafka Producer] Broker confirmou gravação do fileId={}", event.fileId());
        } catch (Exception e) {
            log.error("❌ [Kafka Producer] Falha crítica ao publicar no Kafka!", e);
        }
    }
}
