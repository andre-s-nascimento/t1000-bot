package net.ddns.adambravo79.tmill.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.test.context.ActiveProfiles;

import net.ddns.adambravo79.tmill.dto.AudioReceivedEvent;
import net.ddns.adambravo79.tmill.service.kafka.AudioEventPublisher;

@ActiveProfiles("test")
class KafkaAndDatabaseIntegrationTest extends BaseIntegrationTest {

    private static final String TOPIC_RECEIVED = "t1000.audio.received";

    @Autowired private AudioEventPublisher audioEventPublisher;

    @Test
    void publishesAudioReceivedEventToKafka() {
        assertThat(audioEventPublisher).isNotNull();

        AudioReceivedEvent event =
                new AudioReceivedEvent(
                        "integration-test-file-123",
                        123456789L,
                        987654321L,
                        "Test User",
                        0L,
                        "BRUTO",
                        0);

        audioEventPublisher.publish(event);

        try (Consumer<String, AudioReceivedEvent> consumer = createConsumer()) {
            consumer.subscribe(Collections.singletonList(TOPIC_RECEIVED));

            ConsumerRecord<String, AudioReceivedEvent> receivedRecord =
                    waitForEvent(consumer, event.fileId());

            assertThat(receivedRecord).isNotNull();
            assertThat(receivedRecord.key()).isEqualTo(event.fileId());
            assertThat(receivedRecord.value()).isNotNull();

            AudioReceivedEvent receivedEvent = receivedRecord.value();

            assertThat(receivedEvent.fileId()).isEqualTo(event.fileId());
            assertThat(receivedEvent.chatId()).isEqualTo(event.chatId());
            assertThat(receivedEvent.userId()).isEqualTo(event.userId());
            assertThat(receivedEvent.userName()).isEqualTo(event.userName());
            assertThat(receivedEvent.groupId()).isEqualTo(event.groupId());
            assertThat(receivedEvent.tipoFluxo()).isEqualTo(event.tipoFluxo());
            assertThat(receivedEvent.duration()).isEqualTo(event.duration());
        }
    }

    private Consumer<String, AudioReceivedEvent> createConsumer() {
        Properties properties = new Properties();

        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(
                ConsumerConfig.GROUP_ID_CONFIG, "t1000-integration-test-" + System.nanoTime());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JacksonJsonDeserializer.class);
        properties.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "net.ddns.adambravo79.tmill.dto");
        properties.put(
                JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, AudioReceivedEvent.class.getName());

        return new KafkaConsumer<>(properties);
    }

    private ConsumerRecord<String, AudioReceivedEvent> waitForEvent(
            Consumer<String, AudioReceivedEvent> consumer, String expectedFileId) {

        long timeout = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();

        while (System.currentTimeMillis() < timeout) {
            var records = consumer.poll(Duration.ofMillis(500));

            for (ConsumerRecord<String, AudioReceivedEvent> record : records) {
                if (expectedFileId.equals(record.key())) {
                    return record;
                }
            }
        }

        throw new AssertionError(
                "Evento não foi recebido pelo Kafka dentro do timeout: fileId=" + expectedFileId);
    }
}
