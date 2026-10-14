package net.ddns.adambravo79.tmill.controller.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.dto.AudioProcessedEvent;
import net.ddns.adambravo79.tmill.service.BotAnalyticsService;
import net.ddns.adambravo79.tmill.service.feature.FeatureFlagAdminService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;

/**
 * Consumer de eventos do tópico {@code t1000.audio.processed}.
 *
 * <p>Envia a mensagem de sucesso/erro para o chat de origem. Opcionalmente, registra um audit
 * trail no MongoDB via {@link BotAnalyticsService}.
 *
 * <p><b>Feature flag:</b> {@code kafka.audit.enabled}. Quando habilitada, cada evento processado
 * gera um registro em {@code interaction_logs} com a ação {@code KAFKA_AUDIO_PROCESSED}. Quando
 * desabilitada (default), apenas o envio normal acontece.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AudioResultConsumer {

    private static final String FLAG_KAFKA_AUDIT = "kafka.audit.enabled";

    private final TelegramFacade telegramFacade;
    private final BotAnalyticsService botAnalyticsService;
    private final MetricsService metricsService;
    private final FeatureFlagAdminService featureFlags;

    @KafkaListener(topics = "t1000.audio.processed", groupId = "t1000-bot-responses")
    public void handleProcessedAudio(@Payload AudioProcessedEvent event) {
        log.info(
                "📥 [Bot] Recebida resposta do Worker via Kafka. chatId={}, sucesso={}",
                event.chatId(),
                event.sucesso());

        if (event.sucesso()) {
            handleSuccess(event);
        } else {
            handleFailure(event);
        }
    }

    // =========================================================================
    // SUCESSO
    // =========================================================================

    private void handleSuccess(AudioProcessedEvent event) {
        String mensagemSucesso =
                "✅ *Áudio processado com sucesso!* (Duração: " + event.duration() + "s)";
        telegramFacade.enviarMensagemHtml(event.chatId(), mensagemSucesso);

        // 💾 Log estruturado no MongoDB (sempre — parte do fluxo normal)
        botAnalyticsService.registrarLogInteracao(
                event.chatId(),
                event.senderId(),
                event.senderName(),
                "AUDIO_PROCESSED_SUCCESS",
                "Áudio fileId=" + event.fileId() + " processado com sucesso.");

        metricsService.success("audio_transcricao");
        audit(event, "AUDIO_PROCESSED_SUCCESS");
    }

    // =========================================================================
    // FALHA
    // =========================================================================

    private void handleFailure(AudioProcessedEvent event) {
        String mensagemErro =
                "❌ *Ops! Não consegui processar seu áudio.*\n\nMotivo: " + event.mensagemErro();
        telegramFacade.enviarMensagemHtml(event.chatId(), mensagemErro);

        // 💾 Log estruturado no MongoDB (sempre — parte do fluxo normal)
        botAnalyticsService.registrarLogInteracao(
                event.chatId(),
                event.senderId(),
                event.senderName(),
                "AUDIO_PROCESSED_ERROR",
                "Erro no worker para fileId=" + event.fileId() + ": " + event.mensagemErro());

        metricsService.error("audio_transcricao");
        audit(event, "AUDIO_PROCESSED_ERROR");
    }

    // =========================================================================
    // AUDIT TRAIL (feature flag)
    // =========================================================================

    /**
     * Registra um audit trail adicional no MongoDB quando {@code kafka.audit.enabled} está
     * habilitada.
     *
     * <p>Diferença em relação ao log padrão ({@code registrarLogInteracao}): o audit usa uma
     * ação distinta ({@code KAFKA_AUDIT}) e um formato de conteúdo voltado a rastreamento — útil
     * para correlação com logs do broker e análise de volume de eventos.
     *
     * <p>Falhas no audit <b>não</b> afetam o fluxo normal — são logadas e engolidas.
     */
    private void audit(AudioProcessedEvent event, String actionType) {
        if (!featureFlags.isEnabled(FLAG_KAFKA_AUDIT)) {
            log.debug("📴 Kafka audit desabilitado por feature flag — pulando.");
            return;
        }

        try {
            String content =
                    String.format(
                            "event=audio_processed fileId=%s chatId=%d senderId=%d success=%s"
                                    + " duration=%d action=%s",
                            event.fileId(),
                            event.chatId(),
                            event.senderId(),
                            event.sucesso(),
                            event.duration(),
                            actionType);

            botAnalyticsService.registrarLogInteracao(
                    event.chatId(), event.senderId(), event.senderName(), "KAFKA_AUDIT", content);

            log.debug("📝 Kafka audit registrado: fileId={}", event.fileId());
        } catch (Exception e) {
            log.warn("⚠️ Falha ao registrar audit Kafka (não afeta fluxo): {}", e.getMessage());
        }
    }
}
