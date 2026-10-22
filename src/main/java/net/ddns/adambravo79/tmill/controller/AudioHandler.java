/* (c) 2026 | 22/07/2026 */
package net.ddns.adambravo79.tmill.controller;

import static net.ddns.adambravo79.tmill.constant.BotMessages.AUDIO_TOO_LARGE;
import static net.ddns.adambravo79.tmill.constant.BotMessages.ERRO_PROCESSAR_AUDIO_CALLBACK;
import static net.ddns.adambravo79.tmill.constant.BotMessages.TOKEN_EXPIRADO;
import static net.ddns.adambravo79.tmill.constant.BotMessages.TRANSCRIPTION_DISABLED;
import static net.ddns.adambravo79.tmill.constant.BotMessages.USUARIO_PRECISA_INICIAR_BOT;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.dto.AudioProcessedEvent;
import net.ddns.adambravo79.tmill.dto.AudioReceivedEvent;
import net.ddns.adambravo79.tmill.dto.AudioRequest;
import net.ddns.adambravo79.tmill.exception.AudioProcessingException;
import net.ddns.adambravo79.tmill.model.TranscriptionCacheEntry;
import net.ddns.adambravo79.tmill.service.AudioPipelineService;
import net.ddns.adambravo79.tmill.service.TelegramFileService;
import net.ddns.adambravo79.tmill.service.cache.FileTranscriptionCacheService;
import net.ddns.adambravo79.tmill.service.config.AudioConfigService;
import net.ddns.adambravo79.tmill.service.feature.FeatureFlagAdminService;
import net.ddns.adambravo79.tmill.service.kafka.AudioEventPublisher;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;
import net.ddns.adambravo79.tmill.telegram.util.TelegramUtils;

/**
 * Handler de áudio para o bot Telegram. Processa áudios em chats privados e grupos, com cache e
 * botões de callback.
 *
 * <p>Exception handling strategy:
 *
 * <ul>
 *   <li>{@link AudioProcessingException} — erro no pipeline de áudio; notifica usuário.
 *   <li>{@link HttpClientErrorException.Forbidden} — usuário não iniciou bot; notifica no grupo.
 *   <li>{@link HttpClientErrorException} — outros erros HTTP do Telegram.
 *   <li>{@link ResourceAccessException} — falha de conectividade.
 *   <li>Erros fatais (Error, InterruptedException) — NUNCA engolidos.
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AudioHandler {

    private static final String TRANS_BRUTO = "trans_bruto";
    private static final String TRANS_REFINADO = "trans_refinado";
    private static final long TOKEN_EXPIRATION_MS = 604800000L; // 7 dias
    private static final int TOKEN_MAX_LENGTH = 20;

    // 🔧 FIX (KI-001): chaves de métrica centralizadas
    private static final String METRIC_TRANSCRICAO_ENTREGUE = "transcricao_entregue";
    private static final String METRIC_TRANSCRICAO_FALHA = "transcricao_falha";

    private final TelegramFileService fileService;
    private final AudioPipelineService audioService;
    private final FileTranscriptionCacheService cacheService;
    private final TelegramFacade telegramFacade;
    private final TelegramUtils utils;
    private final AudioEventPublisher audioEventPublisher;
    private final FeatureFlagAdminService featureFlags;
    private final AudioConfigService audioConfig;
    private final MetricsService metricsService;

    @Value("${telegram.message.limit:4000}")
    private int telegramMessageLimit;

    private final ConcurrentHashMap<String, AudioRequest> pendingRequests =
            new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void init() {
        cleaner.scheduleAtFixedRate(this::cleanExpiredTokens, 1, 1, TimeUnit.HOURS);
        log.info("🧹 AudioHandler: cleaner agendado para remover tokens expirados.");
    }

    // ========================= HANDLER PRINCIPAL =========================

    public void handleAudioUpdate(Update update) {
        if (!featureFlags.isEnabled("transcription.enabled")) {
            safeSendMessage(update.message().chat().id(), TRANSCRIPTION_DISABLED);
            return;
        }

        Message message = update.message();
        long chatId = message.chat().id();

        AudioMetadata meta = extractAudioMetadata(message);
        if (meta == null) {
            log.warn("handleAudioUpdate chamado sem áudio ou voz no update.");
            return;
        }

        if (isAudioTooLarge(chatId, meta.fileSize())) {
            return;
        }

        if (isGroupChat(message)) {
            processGroupAudio(message, chatId, meta.fileId(), meta.duration());
        } else {
            processPrivateAudio(message, chatId, meta.fileId(), meta.duration());
        }
    }

    /** Metadados extraídos de um áudio ou voz do Telegram. */
    private record AudioMetadata(String fileId, long fileSize, int duration) {}

    /**
     * Extrai fileId, fileSize e duration de um {@link Message}, seja áudio ou voz.
     *
     * @return os metadados, ou {@code null} se a mensagem não contiver áudio/voz.
     */
    private AudioMetadata extractAudioMetadata(Message message) {
        if (message.audio() != null) {
            return new AudioMetadata(
                    message.audio().fileId(),
                    message.audio().fileSize(),
                    message.audio().duration());
        }
        if (message.voice() != null) {
            return new AudioMetadata(
                    message.voice().fileId(),
                    message.voice().fileSize(),
                    message.voice().duration());
        }
        return null;
    }

    /**
     * Verifica se o áudio excede o limite configurado e, em caso afirmativo, notifica o usuário.
     *
     * @return {@code true} se o áudio for grande demais (e o usuário já foi notificado).
     */
    private boolean isAudioTooLarge(long chatId, long fileSize) {
        int maxSizeMb = audioConfig.getTelegram().audioMaxSizeMb();
        if (fileSize <= maxSizeMb * 1024L * 1024L) {
            return false;
        }
        log.warn(
                "⚠️ Áudio muito grande chatId={} size={} bytes (limite {} MB)",
                chatId,
                fileSize,
                maxSizeMb);
        safeSendMessage(chatId, String.format(AUDIO_TOO_LARGE, maxSizeMb));
        return true;
    }

    // ========================= PRIVADO =========================

    private void processPrivateAudio(Message message, long chatId, String fileId, int duration) {
        long userId = message.from().id();
        String userName = utils.buildFullName(message.from());

        log.info("🎙️ Enfileirando áudio privado chatId={} fileId={}", chatId, fileId);

        AudioReceivedEvent event =
                new AudioReceivedEvent(fileId, chatId, userId, userName, 0L, "AMBOS", duration);
        audioEventPublisher.publish(event);

        safeSendMessage(
                chatId,
                "🎧 Seu áudio foi colocado na fila de processamento. Enviarei a transcrição em"
                        + " instantes!");
    }

    // ========================= GRUPO =========================

    private void processGroupAudio(Message message, long chatId, String fileId, int duration) {
        long senderId = message.from().id();
        String senderName = utils.buildFullName(message.from());

        log.info("🎙️ Enfileirando áudio de grupo chatId={} fileId={}", chatId, fileId);

        AudioReceivedEvent event =
                new AudioReceivedEvent(
                        fileId,
                        chatId,
                        senderId,
                        senderName,
                        chatId,
                        "PRE_PROCESSAMENTO_GRUPO",
                        duration);
        audioEventPublisher.publish(event);

        safeSendMessage(chatId, "🎧 Áudio de grupo recebido e enfileirado na Skynet!");
    }

    @KafkaListener(topics = "t1000.audio.processed", groupId = "t1000-bot-responses")
    public void onAudioProcessed(AudioProcessedEvent event) {
        if (!event.sucesso()) {
            safeSendMessage(
                    event.chatId(), "❌ Falha ao processar seu áudio: " + event.mensagemErro());
            return;
        }

        log.info("✅ Bot recebeu confirmação de processamento: fileId={}", event.fileId());

        String token = gerarToken(event.fileId());

        pendingRequests.put(
                token,
                new AudioRequest(
                        event.fileId(),
                        event.chatId(),
                        System.currentTimeMillis(),
                        event.senderId(),
                        event.senderName()));
        safeSendButtons(event.chatId(), event.senderName(), event.duration(), token);
    }

    // ========================= CALLBACK =========================

    public void handleTranscriptionCallback(CallbackQuery callback, String data) {
        String[] parts = data.split("\\|", 2);
        if (parts.length < 2) {
            log.error("Callback malformado: {}", data);
            return;
        }

        String tipo = parts[0];
        String token = parts[1];
        long userId = callback.from().id();

        var msg = callback.maybeInaccessibleMessage();
        if (msg == null) {
            log.warn("Callback sem mensagem acessível (message may have been deleted)");
            safeAnswerCallback(callback.id(), "Mensagem original não disponível", true);
            return;
        }
        long chatId = msg.chat().id();

        log.info(
                "📊 Clique no botão {} | userId={} | chatId={} | token={}",
                tipo,
                userId,
                chatId,
                token);

        AudioRequest request = pendingRequests.get(token);
        if (request == null) {
            log.warn(
                    "Token inválido ou expirado: {} (userId={}, chatId={})", token, userId, chatId);
            safeAnswerCallback(callback.id(), TOKEN_EXPIRADO, true);
            return;
        }

        String fileId = request.fileId();
        long groupId = request.groupId();
        TranscriptionCacheEntry cached = cacheService.get(fileId);
        if (cached != null) {
            entregarTranscricaoCache(userId, tipo, cached, groupId);
            return;
        }

        log.info("Cache miss para fileId={}, processando novamente.", fileId);
        safeAnswerCallback(callback.id(), "Processando áudio... enviarei no privado.", false);
        CompletableFuture.runAsync(
                () -> processarAudioCallback(userId, tipo, fileId, request, groupId));
    }

    private void entregarTranscricaoCache(
            long userId, String tipo, TranscriptionCacheEntry cached, long groupId) {

        String texto = selecionarTexto(tipo, cached);

        if (texto == null || texto.isBlank()) {
            entregarFallbackOuErro(userId, tipo, cached, groupId);
            return;
        }

        entregarTextoNormal(userId, tipo, texto, groupId);
    }

    /**
     * Seleciona o texto do cache de acordo com o tipo solicitado.
     *
     * @return o texto correspondente ao {@code tipo}, possivelmente {@code null} ou em branco.
     */
    private String selecionarTexto(String tipo, TranscriptionCacheEntry cached) {
        return tipo.equals(TRANS_BRUTO) ? cached.textoBruto() : cached.textoRefinado();
    }

    /**
     * Entrega a transcrição quando o texto está presente e não vazio, emitindo a métrica
     * correspondente ao resultado real do envio.
     */
    private void entregarTextoNormal(long userId, String tipo, String texto, long groupId) {

        String prefixo =
                tipo.equals(TRANS_BRUTO) ? "🎙️ Transcrição Bruta:\n" : "✨ Transcrição Refinada:\n";

        boolean entregue = safeSendTranscription(userId, prefixo + texto, groupId);
        if (entregue) {
            metricsService.success(METRIC_TRANSCRICAO_ENTREGUE);
            log.info(
                    "✅ Transcrição entregue via cache para userId={} tipo={} ({} chars)",
                    userId,
                    tipo,
                    texto.length());
        } else {
            metricsService.error(METRIC_TRANSCRICAO_FALHA);
        }
    }

    /**
     * Trata o caso em que o texto solicitado está vazio: tenta o fallback bruto (quando o
     * solicitado era o refinado) ou reporta erro ao usuário.
     */
    private void entregarFallbackOuErro(
            long userId, String tipo, TranscriptionCacheEntry cached, long groupId) {

        log.warn(
                "⚠️ Transcrição {} VAZIA no cache para userId={}. Tentando fallback.",
                tipo,
                userId);

        if (tipo.equals(TRANS_REFINADO) && tentarFallbackBruto(userId, cached, groupId)) {
            return;
        }

        log.error("❌ Ambos bruto e refinado estão vazios para userId={}", userId);
        safeSendMessage(userId, "❌ Não consegui obter o texto da transcrição. Tente novamente.");
        metricsService.error(METRIC_TRANSCRICAO_FALHA);
    }

    /**
     * Tenta entregar a versão bruta como fallback quando a refinada está vazia.
     *
     * @return {@code true} se o fallback foi enviado (com sucesso ou falha), {@code false} se não
     *     havia texto bruto disponível.
     */
    private boolean tentarFallbackBruto(long userId, TranscriptionCacheEntry cached, long groupId) {

        String brutoFallback = cached.textoBruto();
        if (brutoFallback == null || brutoFallback.isBlank()) {
            return false;
        }

        log.info("🔄 Fallback: usando transcrição BRUTA no lugar da refinada.");
        String aviso =
                """
                ⚠️ _Não consegui refinar esta transcrição. Enviando a versão bruta:_

                """;
        boolean entregue = safeSendTranscription(userId, aviso + "🎙️ " + brutoFallback, groupId);

        if (entregue) {
            metricsService.success(METRIC_TRANSCRICAO_ENTREGUE);
        } else {
            metricsService.error(METRIC_TRANSCRICAO_FALHA);
        }
        return true;
    }

    private void processarAudioCallback(
            long userId, String tipo, String fileId, AudioRequest request, long groupId) {
        try {
            String mensagem = transcreverAudio(fileId, tipo, request);
            boolean entregue = safeSendTranscription(userId, mensagem, groupId);
            if (entregue) {
                metricsService.success(METRIC_TRANSCRICAO_ENTREGUE);
                log.info("✅ Transcrição enviada para userId={} tipo={}", userId, tipo);
            } else {
                metricsService.error(METRIC_TRANSCRICAO_FALHA);
                log.warn("⚠️ Transcrição NÃO entregue para userId={} tipo={}", userId, tipo);
            }
        } catch (AudioProcessingException e) {
            log.error("Erro no pipeline de áudio para userId={} fileId={}", userId, fileId, e);
            tratarErroTranscricao(e, userId, groupId);
            metricsService.error(METRIC_TRANSCRICAO_FALHA);
        } catch (HttpClientErrorException e) {
            log.error(
                    "Erro HTTP do Telegram para userId={} fileId={}: {}",
                    userId,
                    fileId,
                    e.getStatusCode(),
                    e);
            tratarErroTranscricao(e, userId, groupId);
            metricsService.error(METRIC_TRANSCRICAO_FALHA);
        } catch (ResourceAccessException e) {
            log.error("Falha de conectividade para userId={} fileId={}", userId, fileId, e);
            safeSendMessage(userId, ERRO_PROCESSAR_AUDIO_CALLBACK + "Falha de conectividade.");
            metricsService.error(METRIC_TRANSCRICAO_FALHA);
        } catch (RuntimeException e) {
            log.error(
                    "Erro inesperado ao processar áudio para userId={} fileId={}",
                    userId,
                    fileId,
                    e);
            tratarErroTranscricao(e, userId, groupId);
            metricsService.error(METRIC_TRANSCRICAO_FALHA);
        } finally {
            // 🔧 FIX: Não remover o token imediatamente após o processamento.
            // O token deve expirar apenas pelo cleaner (7 dias).
        }
    }

    private String transcreverAudio(String fileId, String tipo, AudioRequest request) {
        File audioFile = fileService.baixarArquivo(fileId);
        final String[] resultado = {null};

        audioService.processarFluxoAudio(
                audioFile,
                request.groupId(),
                request.senderId(),
                request.senderName(),
                (texto, isUltima) -> {
                    boolean isUltimaMsg = Boolean.TRUE.equals(isUltima);
                    if ((tipo.equals(TRANS_BRUTO) && !isUltimaMsg)
                            || (tipo.equals(TRANS_REFINADO) && isUltimaMsg)) {
                        resultado[0] = texto;
                    }
                });

        if (resultado[0] == null) {
            throw new IllegalStateException("Nenhum texto produzido pelo pipeline de áudio");
        }

        String prefixo =
                tipo.equals(TRANS_BRUTO) ? "🎙️ Transcrição Bruta:\n" : "✨ Transcrição Refinada:\n";
        return prefixo + resultado[0];
    }

    // ========================= ENVIO SEGURO =========================

    /** Envia mensagem para o usuário, tratando erros HTTP e de conectividade. */
    private void safeSendMessage(long chatId, String mensagem) {
        try {
            if (mensagem.length() > telegramMessageLimit) {
                utils.splitMessage(mensagem, telegramMessageLimit)
                        .forEach(parte -> telegramFacade.enviarMensagem(chatId, parte));
            } else {
                telegramFacade.enviarMensagem(chatId, mensagem);
            }
        } catch (HttpClientErrorException.Forbidden e) {
            log.warn("Usuário {} bloqueou o bot (Forbidden)", chatId);
        } catch (HttpClientErrorException e) {
            log.error(
                    "Erro HTTP {} ao enviar mensagem para chatId={}", e.getStatusCode(), chatId, e);
        } catch (ResourceAccessException e) {
            log.error("Falha de conectividade ao enviar mensagem para chatId={}", chatId);
        }
    }

    /**
     * Envia transcrição para o usuário, com tratamento específico de Forbidden (usuário não iniciou
     * bot).
     *
     * <p><b>KI-001 (fix):</b> retorna {@code true} se a mensagem foi entregue, {@code false} caso
     * contrário. Permite que o chamador emita métricas de sucesso/falha baseadas no resultado real.
     */
    private boolean safeSendTranscription(long userId, String mensagem, long groupId) {
        try {
            if (mensagem.length() > telegramMessageLimit) {
                utils.splitMessage(mensagem, telegramMessageLimit)
                        .forEach(parte -> telegramFacade.enviarMensagem(userId, parte));
            } else {
                telegramFacade.enviarMensagem(userId, mensagem);
            }
            log.info("📤 Transcrição enviada para userId={}", userId);
            return true;
        } catch (HttpClientErrorException.Forbidden e) {
            log.warn(
                    "Usuário {} não iniciou o bot (Forbidden), notificando no grupo {}",
                    userId,
                    groupId);
            if (groupId != 0) {
                safeSendMessage(groupId, USUARIO_PRECISA_INICIAR_BOT);
            }
            return false;
        } catch (HttpClientErrorException e) {
            log.error(
                    "Erro HTTP {} ao enviar transcrição para userId={}",
                    e.getStatusCode(),
                    userId,
                    e);
            safeSendMessage(userId, ERRO_PROCESSAR_AUDIO_CALLBACK + "Erro de comunicação.");
            return false;
        } catch (ResourceAccessException e) {
            log.error("Falha de conectividade ao enviar transcrição para userId={}", userId);
            safeSendMessage(userId, ERRO_PROCESSAR_AUDIO_CALLBACK + "Falha de conectividade.");
            return false;
        }
    }

    /** Envia botões de transcrição para o grupo. */
    private void safeSendButtons(long chatId, String senderName, int duration, String token) {
        try {
            long minutos = duration / 60;
            long segundos = duration % 60;
            String duracao = String.format("%dmin e %ds", minutos, segundos);
            String hint = duration > 300 ? ", praticamente um SilasCast 🗣" : "";

            String mensagem =
                    """
                    🎧 Áudio de <b>%s</b> (%s%s) processado!

                    Clique num botão para receber a transcrição no seu privado:
                    """
                            .formatted(utils.escapeHtml(senderName), duracao, hint);

            InlineKeyboardMarkup markup =
                    new InlineKeyboardMarkup(
                            new InlineKeyboardButton("🎙️ Transcrição Bruta")
                                    .callbackData(TRANS_BRUTO + "|" + token),
                            new InlineKeyboardButton("✨ Transcrição Refinada")
                                    .callbackData(TRANS_REFINADO + "|" + token));

            telegramFacade.enviarComBotoesHtml(chatId, mensagem, markup);
        } catch (HttpClientErrorException e) {
            log.error("Erro HTTP {} ao enviar botões para chatId={}", e.getStatusCode(), chatId, e);
        } catch (ResourceAccessException e) {
            log.error("Falha de conectividade ao enviar botões para chatId={}", chatId);
        }
    }

    /** Responde a um callback query de forma segura. */
    private void safeAnswerCallback(String callbackId, String text, boolean showAlert) {
        try {
            telegramFacade.answerCallbackQuery(callbackId, text, showAlert);
        } catch (HttpClientErrorException e) {
            log.warn("Erro HTTP {} ao responder callback {}", e.getStatusCode(), callbackId);
        } catch (ResourceAccessException e) {
            log.warn("Falha de conectividade ao responder callback {}", callbackId);
        }
    }

    // ========================= ERROS =========================

    private void tratarErroTranscricao(Exception e, long userId, long groupId) {
        String errorMsg = e.getMessage();
        boolean isForbidden =
                errorMsg != null
                        && errorMsg.contains("403")
                        && errorMsg.contains("can't initiate conversation");

        if (isForbidden && groupId != 0) {
            safeSendMessage(groupId, USUARIO_PRECISA_INICIAR_BOT);
        } else {
            safeSendMessage(userId, ERRO_PROCESSAR_AUDIO_CALLBACK + "Erro no processamento.");
        }
    }

    // ========================= UTILITÁRIOS =========================

    private boolean isGroupChat(Message message) {
        return message.chat().type() == com.pengrad.telegrambot.model.Chat.Type.group
                || message.chat().type() == com.pengrad.telegrambot.model.Chat.Type.supergroup;
    }

    private String gerarToken(String fileId) {
        String token =
                Long.toHexString(System.nanoTime())
                        + Integer.toHexString(fileId.hashCode() & 0xFFFF);
        return token.length() > TOKEN_MAX_LENGTH ? token.substring(0, TOKEN_MAX_LENGTH) : token;
    }

    private void cleanExpiredTokens() {
        long now = System.currentTimeMillis();
        int before = pendingRequests.size();
        pendingRequests
                .entrySet()
                .removeIf(entry -> now - entry.getValue().timestamp() > TOKEN_EXPIRATION_MS);
        int after = pendingRequests.size();
        if (before != after) {
            log.info(
                    "🧹 Cache de tokens limpo: {} entradas removidas, {} restantes",
                    before - after,
                    after);
        }
    }
}
