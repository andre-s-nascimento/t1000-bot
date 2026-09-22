package net.ddns.adambravo79.tmill.controller.kafka;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import net.ddns.adambravo79.tmill.dto.AudioProcessedEvent;
import net.ddns.adambravo79.tmill.service.BotAnalyticsService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;

@ExtendWith(MockitoExtension.class)
class AudioResultConsumerTest {

    @Mock private TelegramFacade telegramFacade;
    @Mock private BotAnalyticsService botAnalyticsService;
    @Mock private MetricsService metricsService;

    @InjectMocks private AudioResultConsumer audioResultConsumer;

    // =========================
    // SUCESSO
    // =========================

    @Test
    @DisplayName("Evento de sucesso envia mensagem, registra log e incrementa métrica de sucesso")
    void deveEnviarMensagemERegistrarMetricaQuandoSucesso() {
        AudioProcessedEvent event =
                new AudioProcessedEvent("file123", 12345L, 999L, "André", true, null, 180);

        audioResultConsumer.handleProcessedAudio(event);

        // Mensagem para o chat
        verify(telegramFacade)
                .enviarMensagemHtml(eq(12345L), contains("Áudio processado com sucesso"));

        // Log estruturado no MongoDB
        verify(botAnalyticsService)
                .registrarLogInteracao(
                        eq(12345L),
                        eq(999L),
                        eq("André"),
                        eq("AUDIO_PROCESSED_SUCCESS"),
                        anyString());

        // Métrica
        verify(metricsService).success("audio_transcricao");
        verify(metricsService, never()).error("audio_transcricao");
    }

    @Test
    @DisplayName("Evento de sucesso inclui duração na mensagem")
    void deveIncluirDuracaoNaMensagemDeSucesso() {
        AudioProcessedEvent event =
                new AudioProcessedEvent("file123", 12345L, 999L, "André", true, null, 240);

        audioResultConsumer.handleProcessedAudio(event);

        verify(telegramFacade).enviarMensagemHtml(eq(12345L), contains("Duração: 240s"));
    }

    // =========================
    // ERRO
    // =========================

    @Test
    @DisplayName("Evento de falha envia mensagem, registra log e incrementa métrica de erro")
    void deveEnviarMensagemDeErroERegistrarMetricaQuandoFalhar() {
        AudioProcessedEvent event =
                new AudioProcessedEvent("file123", 12345L, 999L, "André", false, "FFmpeg crash", 0);

        audioResultConsumer.handleProcessedAudio(event);

        verify(telegramFacade).enviarMensagemHtml(eq(12345L), contains("Motivo: FFmpeg crash"));

        verify(botAnalyticsService)
                .registrarLogInteracao(
                        eq(12345L),
                        eq(999L),
                        eq("André"),
                        eq("AUDIO_PROCESSED_ERROR"),
                        contains("FFmpeg crash"));

        verify(metricsService).error("audio_transcricao");
        verify(metricsService, never()).success("audio_transcricao");
    }

    // =========================
    // CASOS DE BORDA
    // =========================

    @Test
    @DisplayName("Mensagem de erro nula ainda é enviada com 'Motivo: null'")
    void eventoDeErro_comMensagemNula_aindaEnvia() {
        AudioProcessedEvent event =
                new AudioProcessedEvent("file123", 12345L, 999L, "André", false, null, 0);

        audioResultConsumer.handleProcessedAudio(event);

        verify(telegramFacade).enviarMensagemHtml(eq(12345L), anyString());
        verify(metricsService).error("audio_transcricao");
    }

    @Test
    @DisplayName("Evento de sucesso não deve chamar error em hipótese alguma")
    void sucesso_nuncaChamaError() {
        AudioProcessedEvent event =
                new AudioProcessedEvent("file123", 12345L, 999L, "André", true, null, 60);

        audioResultConsumer.handleProcessedAudio(event);

        verify(metricsService, never()).error(anyString());
    }

    @Test
    @DisplayName("Evento de falha não deve chamar success em hipótese alguma")
    void falha_nuncaChamaSuccess() {
        AudioProcessedEvent event =
                new AudioProcessedEvent("file123", 12345L, 999L, "André", false, "erro", 0);

        audioResultConsumer.handleProcessedAudio(event);

        verify(metricsService, never()).success(anyString());
    }

    @Test
    @DisplayName("Cada evento registra exatamente 1 métrica (nem mais, nem menos)")
    void cadaEvento_registraExatamenteUmaMetrica() {
        AudioProcessedEvent event =
                new AudioProcessedEvent("file123", 12345L, 999L, "André", true, null, 60);

        audioResultConsumer.handleProcessedAudio(event);

        verify(metricsService, org.mockito.Mockito.times(1)).success("audio_transcricao");
        verify(metricsService, never()).error(anyString());
        verify(botAnalyticsService, org.mockito.Mockito.times(1))
                .registrarLogInteracao(anyLong(), anyLong(), anyString(), anyString(), anyString());
    }
}
