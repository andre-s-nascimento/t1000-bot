package net.ddns.adambravo79.tmill.service.kafka;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import net.ddns.adambravo79.tmill.dto.AudioReceivedEvent;

@ExtendWith(MockitoExtension.class)
class AudioEventPublisherTest {

    @Mock private KafkaTemplate<String, AudioReceivedEvent> kafkaTemplate;

    @InjectMocks private AudioEventPublisher audioEventPublisher;

    @Test
    void devePublicarEventoNoTopicoCorretoComAChaveFileId() {
        // Arrange
        String fileId = "AwACAgEAAyEFAATcv8y";
        AudioReceivedEvent event =
                new AudioReceivedEvent(fileId, -1003703557250L, 12345L, "André", 0L, "AMBOS", 0);

        String expectedTopic = "t1000.audio.received";

        // Act
        audioEventPublisher.publish(event);

        // Assert
        // Verifica se o template do Kafka foi chamado exatamente 1 vez com o tópico, chave e
        // payload
        // corretos
        verify(kafkaTemplate, times(1)).send(expectedTopic, fileId, event);
    }
}
