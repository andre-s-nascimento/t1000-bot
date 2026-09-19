package net.ddns.adambravo79.tmill.controller.kafka;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import net.ddns.adambravo79.tmill.dto.AudioProcessedEvent;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;

@ExtendWith(MockitoExtension.class)
class AudioResultConsumerTest {

    @Mock private TelegramFacade telegramFacade;

    @InjectMocks private AudioResultConsumer audioResultConsumer;

    @Test
    void deveEnviarBotoesOuMensagemQuandoSucesso() {
        // Arrange
        AudioProcessedEvent event =
                new AudioProcessedEvent("file123", 12345L, 999L, "André", true, null, 180);

        // Act
        audioResultConsumer.handleProcessedAudio(event);

        // Assert
        // Verifique aqui o método exato que você usa para enviar os botões vistos no print
        // Exemplo genérico:
        verify(telegramFacade).enviarMensagemHtml(eq(12345L), anyString());
    }

    @Test
    void deveEnviarMensagemDeErroQuandoFalhar() {
        // Arrange
        AudioProcessedEvent event =
                new AudioProcessedEvent("file123", 12345L, 999L, "André", false, "FFmpeg crash", 0);

        // Act
        audioResultConsumer.handleProcessedAudio(event);

        // Assert
        verify(telegramFacade).enviarMensagemHtml(eq(12345L), contains("Motivo: FFmpeg crash"));
    }
}
