/* (c) 2026 | 22/07/2026 */
package net.ddns.adambravo79.tmill.controller;

import static net.ddns.adambravo79.tmill.constant.BotMessages.AUDIO_TOO_LARGE;
import static net.ddns.adambravo79.tmill.constant.BotMessages.ERRO_PROCESSAR_AUDIO;
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
import net.ddns.adambravo79.tmill.dto.AudioRequest;
import net.ddns.adambravo79.tmill.exception.AudioProcessingException;
import net.ddns.adambravo79.tmill.model.TranscriptionCacheEntry;
import net.ddns.adambravo79.tmill.service.AudioPipelineService;
import net.ddns.adambravo79.tmill.service.TelegramFileService;
import net.ddns.adambravo79.tmill.service.TranscriptStoreService;
import net.ddns.adambravo79.tmill.service.cache.FileTranscriptionCacheService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
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

    private final TelegramFileService fileService;
    private final AudioPipelineService audioService;
    private final FileTranscriptionCacheService cacheService;
    private final TranscriptStoreService transcriptStore;
    private final TelegramFacade telegramFacade;
    private final TelegramUtils utils;

    @Value("${t1000.audio.max-size-mb:20}")
    private int maxSizeMb;

    @Value("${t1000.features.transcription-enabled:false}")
    private boolean transcriptionEnabled;

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
        if (!transcriptionEnabled) {
            safeSendMessage(update.message().chat().id(), TRANSCRIPTION_DISABLED);
            return;
        }

        Message message = update.message();
        long chatId = message.chat().id();

        String fileId;
        long fileSize;
        int duration = 0;

        if (message.audio() != null) {
            fileId = message.audio().fileId();
            fileSize = message.audio().fileSize();
            duration = message.audio().duration();
        } else if (message.voice() != null) {
            fileId = message.voice().fileId();
            fileSize = message.voice().fileSize();
            duration = message.voice().duration();
        } else {
            log.warn("handleAudioUpdate chamado sem áudio ou voz no update.");
            return;
        }

        if (fileSize > maxSizeMb * 1024L * 1024L) {
            log.warn(
                    "⚠️ Áudio muito grande chatId={} size={} bytes (limite {} MB)",
                    chatId,
                    fileSize,
                    maxSizeMb);
            safeSendMessage(chatId, String.format(AUDIO_TOO_LARGE, maxSizeMb));
            return;
        }

        boolean isGroup = isGroupChat(message);

        if (isGroup) {
            processGroupAudio(message, chatId, fileId, duration);
        } else {
            processPrivateAudio(message, chatId, fileId);
        }
    }

    // ========================= PRIVADO =========================

    private void processPrivateAudio(Message message, long chatId, String fileId) {
        long userId = message.from().id();
        String userName = utils.buildFullName(message.from());
        File file = fileService.baixarArquivo(fileId);

        audioService.processarFluxoAudio(
                file,
                chatId,
                userId,
                userName,
                (texto, isUltima) -> {
                    if (Boolean.TRUE.equals(isUltima)) {
                        safeSendMessage(chatId, texto);
                    }
                });
    }

    // ========================= GRUPO =========================

    private void processGroupAudio(Message message, long chatId, String fileId, int duration) {
        long senderId = message.from().id();
        String senderName = utils.buildFullName(message.from());

        log.info(
                "🎙️ Áudio recebido em grupo chatId={} fileId={} de {} duração={}s",
                chatId,
                fileId,
                senderName,
                duration);

        CompletableFuture.supplyAsync(() -> fileService.baixarArquivo(fileId))
                .thenCompose(
                        audio ->
                                audioService.processarEArmazenar(
                                        audio, chatId, senderId, senderName))
                .whenComplete(
                        (result, ex) -> {
                            if (ex != null) {
                                handleGroupAudioFailure(chatId, ex);
                                return;
                            }
                            if (result == null) {
                                log.error(
                                        "Resultado nulo do processamento de áudio chatId={}",
                                        chatId);
                                safeSendMessage(chatId, ERRO_PROCESSAR_AUDIO);
                                return;
                            }

                            cacheService.put(fileId, result.bruto(), result.refinado());
                            transcriptStore.saveTranscriptWithRaw(
                                    chatId,
                                    senderId,
                                    senderName,
                                    result.bruto(),
                                    result.refinado());

                            String token = gerarToken(fileId);
                            log.info(
                                    "🔑 Token {} gerado para fileId={} (expira em 7 dias)",
                                    token,
                                    fileId);
                            pendingRequests.put(
                                    token,
                                    new AudioRequest(
                                            fileId,
                                            chatId,
                                            System.currentTimeMillis(),
                                            senderId,
                                            senderName));

                            safeSendButtons(chatId, senderName, duration, token);
                        });
    }

    private void handleGroupAudioFailure(long chatId, Throwable ex) {
        Throwable causa = unwrapCause(ex);
        rethrowIfFatal(causa);

        if (causa instanceof AudioProcessingException) {
            log.error("Falha no pipeline de áudio para chatId={}", chatId, causa);
        } else {
            log.error("Erro inesperado no pré-processamento do áudio chatId={}", chatId, causa);
        }
        safeSendMessage(chatId, ERRO_PROCESSAR_AUDIO);
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
        long chatId = callback.message().chat().id();

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
        String texto = tipo.equals(TRANS_BRUTO) ? cached.textoBruto() : cached.textoRefinado();
        String prefixo =
                tipo.equals(TRANS_BRUTO) ? "🎙️ Transcrição Bruta:\n" : "✨ Transcrição Refinada:\n";
        safeSendTranscription(userId, prefixo + texto, groupId);
        log.info("✅ Transcrição entregue via cache para userId={} tipo={}", userId, tipo);
    }

    private void processarAudioCallback(
            long userId, String tipo, String fileId, AudioRequest request, long groupId) {
        try {
            String mensagem = transcreverAudio(fileId, tipo, request);
            safeSendTranscription(userId, mensagem, groupId);
            log.info("✅ Transcrição enviada para userId={} tipo={}", userId, tipo);
        } catch (AudioProcessingException e) {
            log.error("Erro no pipeline de áudio para userId={} fileId={}", userId, fileId, e);
            tratarErroTranscricao(e, userId, groupId);
        } catch (HttpClientErrorException e) {
            log.error(
                    "Erro HTTP do Telegram para userId={} fileId={}: {}",
                    userId,
                    fileId,
                    e.getStatusCode(),
                    e);
            tratarErroTranscricao(e, userId, groupId);
        } catch (ResourceAccessException e) {
            log.error("Falha de conectividade para userId={} fileId={}", userId, fileId, e);
            safeSendMessage(userId, ERRO_PROCESSAR_AUDIO_CALLBACK + "Falha de conectividade.");
        } catch (RuntimeException e) {
            log.error(
                    "Erro inesperado ao processar áudio para userId={} fileId={}",
                    userId,
                    fileId,
                    e);
            tratarErroTranscricao(e, userId, groupId);
        } finally {
            pendingRequests.entrySet().removeIf(entry -> entry.getValue().fileId().equals(fileId));
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
     */
    private void safeSendTranscription(long userId, String mensagem, long groupId) {
        try {
            if (mensagem.length() > telegramMessageLimit) {
                utils.splitMessage(mensagem, telegramMessageLimit)
                        .forEach(parte -> telegramFacade.enviarMensagem(userId, parte));
            } else {
                telegramFacade.enviarMensagem(userId, mensagem);
            }
            log.info("📤 Transcrição enviada para userId={}", userId);
        } catch (HttpClientErrorException.Forbidden e) {
            log.warn(
                    "Usuário {} não iniciou o bot (Forbidden), notificando no grupo {}",
                    userId,
                    groupId);
            if (groupId != 0) {
                safeSendMessage(groupId, USUARIO_PRECISA_INICIAR_BOT);
            }
        } catch (HttpClientErrorException e) {
            log.error(
                    "Erro HTTP {} ao enviar transcrição para userId={}",
                    e.getStatusCode(),
                    userId,
                    e);
            safeSendMessage(userId, ERRO_PROCESSAR_AUDIO_CALLBACK + "Erro de comunicação.");
        } catch (ResourceAccessException e) {
            log.error("Falha de conectividade ao enviar transcrição para userId={}", userId);
            safeSendMessage(userId, ERRO_PROCESSAR_AUDIO_CALLBACK + "Falha de conectividade.");
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
                    String.format(
                            "🎧 Áudio de <b>%s</b> (%s%s) processado!\n\n"
                                    + "Clique num botão para receber a transcrição no seu privado:",
                            utils.escapeHtml(senderName), duracao, hint);

            InlineKeyboardMarkup markup =
                    new InlineKeyboardMarkup(
                            new InlineKeyboardButton[] {
                                new InlineKeyboardButton("🎙️ Transcrição Bruta")
                                        .callbackData(TRANS_BRUTO + "|" + token),
                                new InlineKeyboardButton("✨ Transcrição Refinada")
                                        .callbackData(TRANS_REFINADO + "|" + token)
                            });

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

    /** Desempacota CompletionException para obter a causa raiz. */
    private Throwable unwrapCause(Throwable ex) {
        return (ex instanceof java.util.concurrent.CompletionException && ex.getCause() != null)
                ? ex.getCause()
                : ex;
    }

    /** Repropaga erros fatais (Error, InterruptedException) sem engoli-los. */
    private void rethrowIfFatal(Throwable t) {
        if (t instanceof Error) {
            throw (Error) t;
        }
        if (t instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrompida", t);
        }
    }
}
