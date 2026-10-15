package net.ddns.adambravo79.tmill.controller.kafka;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import net.ddns.adambravo79.tmill.dto.AudioProcessedEvent;
import net.ddns.adambravo79.tmill.service.BotAnalyticsService;
import net.ddns.adambravo79.tmill.service.feature.FeatureFlagAdminService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AudioResultConsumerTest {

    @Mock private TelegramFacade telegramFacade;
    @Mock private BotAnalyticsService botAnalyticsService;
    @Mock private MetricsService metricsService;
    @Mock private FeatureFlagAdminService featureFlags;

    @InjectMocks private AudioResultConsumer audioResultConsumer;

    // =========================
    // SUCESSO
    // =========================

    @BeforeEach
    void setUp() {
        // Default: audit desabilitado (default do projeto)
        lenient().when(featureFlags.isEnabled("kafka.audit.enabled")).thenReturn(false);
    }

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
        verify(botAnalyticsService, times(1))
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

        verify(metricsService, times(1)).success("audio_transcricao");
        verify(metricsService, never()).error(anyString());
        verify(botAnalyticsService, times(1))
                .registrarLogInteracao(anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("handleProcessedAudio: audit habilitado → registra KAFKA_AUDIT no sucesso")
    void deveRegistrarAuditQuandoSucessoEAuditHabilitado() {
        when(featureFlags.isEnabled("kafka.audit.enabled")).thenReturn(true);

        AudioProcessedEvent event =
                new AudioProcessedEvent("file123", 12345L, 999L, "André", true, null, 180);

        audioResultConsumer.handleProcessedAudio(event);

        // Log padrão (AUDIO_PROCESSED_SUCCESS) + audit (KAFKA_AUDIT) = 2 chamadas
        verify(botAnalyticsService, times(2))
                .registrarLogInteracao(anyLong(), anyLong(), anyString(), anyString(), anyString());

        // Confirma que o audit foi emitido com a ação correta
        verify(botAnalyticsService)
                .registrarLogInteracao(
                        eq(12345L),
                        eq(999L),
                        eq("André"),
                        eq("KAFKA_AUDIT"),
                        contains("event=audio_processed"));
    }

    @Test
    @DisplayName("handleProcessedAudio: audit habilitado → registra KAFKA_AUDIT na falha")
    void deveRegistrarAuditQuandoFalhaEAuditHabilitado() {
        when(featureFlags.isEnabled("kafka.audit.enabled")).thenReturn(true);

        AudioProcessedEvent event =
                new AudioProcessedEvent("file123", 12345L, 999L, "André", false, "FFmpeg crash", 0);

        audioResultConsumer.handleProcessedAudio(event);

        verify(botAnalyticsService)
                .registrarLogInteracao(
                        eq(12345L),
                        eq(999L),
                        eq("André"),
                        eq("KAFKA_AUDIT"),
                        argThat(
                                content ->
                                        content.contains("fileId=file123")
                                                && content.contains("success=false")
                                                && content.contains(
                                                        "action=AUDIO_PROCESSED_ERROR")));
    }

    @Test
    @DisplayName("handleProcessedAudio: audit desabilitado → apenas log padrão")
    void naoDeveRegistrarAuditQuandoDesabilitado() {
        when(featureFlags.isEnabled("kafka.audit.enabled")).thenReturn(false);

        AudioProcessedEvent event =
                new AudioProcessedEvent("file123", 12345L, 999L, "André", true, null, 180);

        audioResultConsumer.handleProcessedAudio(event);

        // Apenas o log padrão (AUDIO_PROCESSED_SUCCESS), sem KAFKA_AUDIT
        verify(botAnalyticsService, times(1))
                .registrarLogInteracao(anyLong(), anyLong(), anyString(), anyString(), anyString());
        verify(botAnalyticsService, never())
                .registrarLogInteracao(
                        anyLong(), anyLong(), anyString(), eq("KAFKA_AUDIT"), anyString());
    }

    @Test
    @DisplayName("handleProcessedAudio: falha no audit não propaga")
    void falhaNoAudit_naoPropaga() {
        when(featureFlags.isEnabled("kafka.audit.enabled")).thenReturn(true);

        doThrow(new RuntimeException("Mongo down"))
                .when(botAnalyticsService)
                .registrarLogInteracao(
                        eq(12345L), eq(999L), eq("André"), eq("KAFKA_AUDIT"), anyString());

        AudioProcessedEvent event =
                new AudioProcessedEvent("file123", 12345L, 999L, "André", true, null, 180);

        assertThatCode(() -> audioResultConsumer.handleProcessedAudio(event))
                .doesNotThrowAnyException();

        verify(telegramFacade).enviarMensagemHtml(anyLong(), anyString());
        verify(metricsService).success("audio_transcricao");
    }
}
