/* (c) 2026 | 29/09/2026 */
package net.ddns.adambravo79.tmill.service.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import net.ddns.adambravo79.tmill.dto.AudioReceivedEvent;

@ExtendWith(MockitoExtension.class)
class AudioEventPublisherTest {

    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    private AudioEventPublisher publisher;

    private static final String FILE_ID = "test-file-id";
    private static final AudioReceivedEvent EVENT =
            new AudioReceivedEvent(FILE_ID, 123L, 456L, "User", 0L, "AMBOS", 0);

    @BeforeEach
    void setUp() {
        publisher = new AudioEventPublisher(kafkaTemplate);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("publish com sucesso envia para o tópico correto com o payload correto")
    void publish_success() {
        SendResult<String, Object> result = mock(SendResult.class);
        when(kafkaTemplate.send(eq("t1000.audio.received"), eq(FILE_ID), any()))
                .thenReturn(CompletableFuture.completedFuture(result));

        assertThatCode(() -> publisher.publish(EVENT)).doesNotThrowAnyException();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("t1000.audio.received"), eq(FILE_ID), captor.capture());
        assertThat(captor.getValue()).isEqualTo(EVENT);
    }

    @Test
    @DisplayName("publish propaga IllegalStateException quando a thread é interrompida")
    void publish_interrupted() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(new CompletableFuture<>());

        Thread.currentThread().interrupt();

        try {
            assertThatThrownBy(() -> publisher.publish(EVENT))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Thread interrompida");
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("publish propaga IllegalStateException quando o Kafka falha")
    void publish_kafkaFailure() {
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Kafka down"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failed);

        assertThatThrownBy(() -> publisher.publish(EVENT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Falha ao publicar evento no Kafka")
                .hasRootCauseInstanceOf(RuntimeException.class);
    }
}
