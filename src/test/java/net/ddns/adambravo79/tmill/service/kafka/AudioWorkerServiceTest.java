package net.ddns.adambravo79.tmill.service.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import net.ddns.adambravo79.tmill.dto.AudioProcessedEvent;
import net.ddns.adambravo79.tmill.dto.AudioReceivedEvent;
import net.ddns.adambravo79.tmill.service.AudioPipelineService;
import net.ddns.adambravo79.tmill.service.TelegramFileService;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AudioWorkerServiceTest {

    @Mock private TelegramFileService fileService;
    @Mock private AudioPipelineService audioPipeline;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private MetricsService metricsService;

    private AudioWorkerService service;

    private static final String FILE_ID = "AwACAgEAAyEFAATcv8y";
    private static final long CHAT_ID = -1003703557250L;
    private static final long USER_ID = 12345L;
    private static final String USER_NAME = "André";

    @BeforeEach
    void setUp() {
        service = new AudioWorkerService(fileService, audioPipeline, kafkaTemplate, metricsService);
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private AudioReceivedEvent eventoPadrao() {
        return new AudioReceivedEvent(FILE_ID, CHAT_ID, USER_ID, USER_NAME, 0L, "AMBOS", 0);
    }

    private File mockFileValido() {
        File f = mock(File.class);
        when(f.exists()).thenReturn(true);
        return f;
    }

    private SendResult<String, Object> mockSendResult() {
        // 👇 Usa o construtor REAL do RecordMetadata (sem mock)
        TopicPartition topicPartition =
                new org.apache.kafka.common.TopicPartition("t1000.audio.processed", 0);

        org.apache.kafka.clients.producer.RecordMetadata recordMetadata =
                new RecordMetadata(
                        topicPartition,
                        /* baseOffset */ 14L,
                        /* batchIndex */ 0,
                        /* lastOffset */ 14L,
                        /* serializedKeySize */ 10,
                        /* serializedValueSize */ 100);

        // SendResult real com metadados reais
        return new SendResult<>(
                new ProducerRecord<>("t1000.audio.processed", "key", "value"), recordMetadata);
    }

    // ============================================================
    // SUCESSO
    // ============================================================

    @Test
    @DisplayName(
            "Fluxo completo com sucesso: registra 'audio_worker_sucesso' e"
                    + " 'audio_worker_resposta_publicada'")
    void fluxoCompleto_registraMetricas() {
        AudioReceivedEvent event = eventoPadrao();
        File mockFile = mockFileValido();

        when(fileService.baixarArquivo(FILE_ID)).thenReturn(mockFile);

        // Simula o pipeline invocando o callback final (isUltima = true)
        doAnswer(
                        inv -> {
                            BiConsumer<String, Boolean> cb = inv.getArgument(4);
                            cb.accept("Texto final", true);
                            return null;
                        })
                .when(audioPipeline)
                .processarFluxoAudio(eq(mockFile), anyLong(), eq(USER_ID), eq(USER_NAME), any());

        // Simula send bem-sucedido
        when(kafkaTemplate.send(eq("t1000.audio.processed"), eq(FILE_ID), any()))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        assertThatCode(() -> service.consumeAudioRequest(event)).doesNotThrowAnyException();

        verify(fileService).baixarArquivo(FILE_ID);
        verify(audioPipeline)
                .processarFluxoAudio(eq(mockFile), anyLong(), eq(USER_ID), eq(USER_NAME), any());
        verify(kafkaTemplate).send(eq("t1000.audio.processed"), eq(FILE_ID), any());

        verify(metricsService).success("audio_worker_sucesso");
        verify(metricsService).success("audio_worker_resposta_publicada");
        verify(metricsService, never()).error(anyString());
    }

    @Test
    @DisplayName("Sucesso: usa groupId quando presente; senão, chatId")
    void sucesso_usaGroupIdQuandoPresente() {
        AudioReceivedEvent event =
                new AudioReceivedEvent(FILE_ID, CHAT_ID, USER_ID, USER_NAME, -999L, "AMBOS", 0);
        File mockFile = mockFileValido();

        when(fileService.baixarArquivo(FILE_ID)).thenReturn(mockFile);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        service.consumeAudioRequest(event);

        verify(audioPipeline)
                .processarFluxoAudio(eq(mockFile), eq(-999L), eq(USER_ID), eq(USER_NAME), any());
    }

    @Test
    @DisplayName("Sucesso: quando groupId é 0, usa chatId")
    void sucesso_usaChatIdQuandoGroupIdZero() {
        AudioReceivedEvent event =
                new AudioReceivedEvent(FILE_ID, CHAT_ID, USER_ID, USER_NAME, 0L, "AMBOS", 0);
        File mockFile = mockFileValido();

        when(fileService.baixarArquivo(FILE_ID)).thenReturn(mockFile);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        service.consumeAudioRequest(event);

        verify(audioPipeline)
                .processarFluxoAudio(eq(mockFile), eq(CHAT_ID), eq(USER_ID), eq(USER_NAME), any());
    }

    // ============================================================
    // ARQUIVO NÃO ENCONTRADO
    // ============================================================

    @Test
    @DisplayName("Arquivo null: registra 'audio_worker_arquivo_nao_encontrado'")
    void arquivoNull_registraMetricaEspecifica() {
        AudioReceivedEvent event = eventoPadrao();
        when(fileService.baixarArquivo(FILE_ID)).thenReturn(null);

        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        service.consumeAudioRequest(event);

        verify(metricsService).error("audio_worker_arquivo_nao_encontrado");
        verify(metricsService, never()).success("audio_worker_sucesso");
        verifyNoInteractions(audioPipeline);
        verify(kafkaTemplate).send(eq("t1000.audio.processed"), eq(FILE_ID), any());
    }

    @Test
    @DisplayName("Arquivo existe=false: registra 'audio_worker_arquivo_nao_encontrado'")
    void arquivoNaoExiste_registraMetricaEspecifica() {
        AudioReceivedEvent event = eventoPadrao();
        File mockFile = mock(File.class);
        when(mockFile.exists()).thenReturn(false);
        when(fileService.baixarArquivo(FILE_ID)).thenReturn(mockFile);

        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        service.consumeAudioRequest(event);

        verify(metricsService).error("audio_worker_arquivo_nao_encontrado");
        verifyNoInteractions(audioPipeline);
    }

    // ============================================================
    // ERRO CRÍTICO NO PIPELINE
    // ============================================================

    @Test
    @DisplayName("Exceção no pipeline: registra 'audio_worker_erro' e publica falha")
    void excecaoNoPipeline_registraErro() {
        AudioReceivedEvent event = eventoPadrao();
        File mockFile = mockFileValido();

        when(fileService.baixarArquivo(FILE_ID)).thenReturn(mockFile);
        doThrow(new RuntimeException("Falha no pipeline"))
                .when(audioPipeline)
                .processarFluxoAudio(any(), anyLong(), anyLong(), anyString(), any());

        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        service.consumeAudioRequest(event);

        verify(metricsService).error("audio_worker_erro");
        verify(metricsService, never()).success("audio_worker_sucesso");
        verify(kafkaTemplate).send(eq("t1000.audio.processed"), eq(FILE_ID), any());
    }

    @Test
    @DisplayName("Exceção no download: registra 'audio_worker_erro'")
    void excecaoNoDownload_registraErro() {
        AudioReceivedEvent event = eventoPadrao();
        when(fileService.baixarArquivo(FILE_ID))
                .thenThrow(new RuntimeException("Falha no download"));

        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        service.consumeAudioRequest(event);

        verify(metricsService).error("audio_worker_erro");
        verifyNoInteractions(audioPipeline);
    }

    // ============================================================
    // FALHA NA PUBLICAÇÃO DA RESPOSTA
    // ============================================================

    @Test
    @DisplayName("Falha no send do Kafka: registra 'audio_worker_resposta_falha'")
    void falhaNoSendKafka_registraErro() {
        AudioReceivedEvent event = eventoPadrao();
        File mockFile = mockFileValido();

        when(fileService.baixarArquivo(FILE_ID)).thenReturn(mockFile);
        doAnswer(
                        inv -> {
                            BiConsumer<String, Boolean> cb = inv.getArgument(4);
                            cb.accept("Texto", true);
                            return null;
                        })
                .when(audioPipeline)
                .processarFluxoAudio(any(), anyLong(), anyLong(), anyString(), any());

        // send devolve future completada excepcionalmente
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Broker down"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failed);

        service.consumeAudioRequest(event);

        verify(metricsService).success("audio_worker_sucesso");
        verify(metricsService).error("audio_worker_resposta_falha");
        verify(metricsService, never()).success("audio_worker_resposta_publicada");
    }

    // ============================================================
    // MÉTRICAS — contagem em múltiplos eventos
    // ============================================================

    @Test
    @DisplayName("3 eventos bem-sucedidos: 3 'audio_worker_sucesso' e 3 'resposta_publicada'")
    void tresEventosSucesso() {
        File mockFile = mockFileValido();
        when(fileService.baixarArquivo(FILE_ID)).thenReturn(mockFile);
        doAnswer(
                        inv -> {
                            BiConsumer<String, Boolean> cb = inv.getArgument(4);
                            cb.accept("Texto", true);
                            return null;
                        })
                .when(audioPipeline)
                .processarFluxoAudio(any(), anyLong(), anyLong(), anyString(), any());
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        service.consumeAudioRequest(eventoPadrao());
        service.consumeAudioRequest(eventoPadrao());
        service.consumeAudioRequest(eventoPadrao());

        verify(metricsService, times(3)).success("audio_worker_sucesso");
        verify(metricsService, times(3)).success("audio_worker_resposta_publicada");
        verify(metricsService, never()).error(anyString());
    }

    @Test
    @DisplayName("2 sucessos + 1 falha: métricas corretas")
    void doisSucessosUmaFalha() {
        File mockFile = mockFileValido();
        when(fileService.baixarArquivo(FILE_ID)).thenReturn(mockFile);

        // 1º e 2º: sucesso
        doAnswer(
                        inv -> {
                            BiConsumer<String, Boolean> cb = inv.getArgument(4);
                            cb.accept("Texto", true);
                            return null;
                        })
                .doAnswer(
                        inv -> {
                            BiConsumer<String, Boolean> cb = inv.getArgument(4);
                            cb.accept("Texto", true);
                            return null;
                        })
                // 3º: falha
                .doThrow(new RuntimeException("boom"))
                .when(audioPipeline)
                .processarFluxoAudio(any(), anyLong(), anyLong(), anyString(), any());

        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        service.consumeAudioRequest(eventoPadrao());
        service.consumeAudioRequest(eventoPadrao());
        service.consumeAudioRequest(eventoPadrao());

        verify(metricsService, times(2)).success("audio_worker_sucesso");
        verify(metricsService, times(1)).error("audio_worker_erro");
    }

    // ============================================================
    // CLEANUP DO ARQUIVO
    // ============================================================

    @Test
    @DisplayName("Arquivo é deletado após processamento (sucesso)")
    void arquivoDeletadoEmSucesso() {
        AudioReceivedEvent event = eventoPadrao();
        File mockFile = mockFileValido();
        when(fileService.baixarArquivo(FILE_ID)).thenReturn(mockFile);

        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        service.consumeAudioRequest(event);

        verify(mockFile).delete();
    }

    @Test
    @DisplayName("Arquivo é deletado após processamento (falha)")
    void arquivoDeletadoEmFalha() {
        AudioReceivedEvent event = eventoPadrao();
        File mockFile = mockFileValido();
        when(fileService.baixarArquivo(FILE_ID)).thenReturn(mockFile);

        doThrow(new RuntimeException("boom"))
                .when(audioPipeline)
                .processarFluxoAudio(any(), anyLong(), anyLong(), anyString(), any());

        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        service.consumeAudioRequest(event);

        verify(mockFile).delete();
    }

    // ============================================================
    // VERIFICAÇÃO DA CARGA ÚTIL PUBLICADA
    // ============================================================

    @Test
    @DisplayName("Payload publicado contém fileId, chatId, userId e senderName corretos")
    void payloadPublicadoContemDados() {
        AudioReceivedEvent event = eventoPadrao();
        File mockFile = mockFileValido();
        when(fileService.baixarArquivo(FILE_ID)).thenReturn(mockFile);

        doAnswer(
                        inv -> {
                            BiConsumer<String, Boolean> cb = inv.getArgument(4);
                            cb.accept("Texto", true);
                            return null;
                        })
                .when(audioPipeline)
                .processarFluxoAudio(any(), anyLong(), anyLong(), anyString(), any());

        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        service.consumeAudioRequest(event);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate)
                .send(eq("t1000.audio.processed"), eq(FILE_ID), payloadCaptor.capture());

        Object payload = payloadCaptor.getValue();
        // Verifica que é um AudioProcessedEvent com os campos corretos
        AudioProcessedEvent processed = (AudioProcessedEvent) payload;
        assertThat(processed.fileId()).isEqualTo(FILE_ID);
        assertThat(processed.chatId()).isEqualTo(CHAT_ID);
        assertThat(processed.senderId()).isEqualTo(USER_ID);
        assertThat(processed.senderName()).isEqualTo(USER_NAME);
        assertThat(processed.sucesso()).isTrue();
    }

    @Test
    @DisplayName("Payload de erro contém mensagem de erro")
    void payloadErroContemMensagem() {
        AudioReceivedEvent event = eventoPadrao();
        File mockFile = mockFileValido();
        when(fileService.baixarArquivo(FILE_ID)).thenReturn(mockFile);

        doThrow(new RuntimeException("Falha X"))
                .when(audioPipeline)
                .processarFluxoAudio(any(), anyLong(), anyLong(), anyString(), any());

        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        service.consumeAudioRequest(event);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("t1000.audio.processed"), eq(FILE_ID), captor.capture());

        AudioProcessedEvent processed = (AudioProcessedEvent) captor.getValue();
        assertThat(processed.sucesso()).isFalse();
        assertThat(processed.mensagemErro()).contains("Falha X");
    }
}
