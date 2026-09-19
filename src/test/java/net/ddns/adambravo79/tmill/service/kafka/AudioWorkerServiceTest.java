package net.ddns.adambravo79.tmill.service.kafka;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;

import net.ddns.adambravo79.tmill.dto.AudioReceivedEvent;
import net.ddns.adambravo79.tmill.service.AudioPipelineService;
import net.ddns.adambravo79.tmill.service.TelegramFileService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AudioWorkerServiceTest {

    @Mock private TelegramFileService fileService;
    @Mock private AudioPipelineService audioPipeline;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks private AudioWorkerService audioWorkerService;

    @Test
    void deveConsumirMensagemSemLancarExcecao() {
        AudioReceivedEvent event =
                new AudioReceivedEvent(
                        "AwACAgEAAyEFAATcv8y", -1003703557250L, 12345L, "André", 0L, "AMBOS", 0);

        // ✅ CORREÇÃO: usar um mock de File em vez de "new File(...)"
        File mockFile = org.mockito.Mockito.mock(File.class);
        when(mockFile.exists()).thenReturn(true);
        when(fileService.baixarArquivo(event.fileId())).thenReturn(mockFile);

        assertThatCode(() -> audioWorkerService.consumeAudioRequest(event))
                .doesNotThrowAnyException();

        verify(fileService).baixarArquivo(event.fileId());
        verify(audioPipeline)
                .processarFluxoAudio(
                        eq(mockFile), anyLong(), eq(event.userId()), eq(event.userName()), any());
        verify(kafkaTemplate).send(eq("t1000.audio.processed"), eq(event.fileId()), any());
    }

    @Test
    void quandoArquivoNaoExiste_devePublicarErro() {
        AudioReceivedEvent event =
                new AudioReceivedEvent(
                        "AwACAgEAAyEFAATcv8y", -1003703557250L, 12345L, "André", 0L, "AMBOS", 0);

        when(fileService.baixarArquivo(event.fileId())).thenReturn(null);

        audioWorkerService.consumeAudioRequest(event);

        verify(kafkaTemplate).send(eq("t1000.audio.processed"), eq(event.fileId()), any());
        verifyNoInteractions(audioPipeline);
    }
}
