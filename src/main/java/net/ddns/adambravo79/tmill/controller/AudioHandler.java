package net.ddns.adambravo79.tmill.controller;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.cache.TranscriptionCacheEntry;
import net.ddns.adambravo79.tmill.cache.TranscriptionCacheService;
import net.ddns.adambravo79.tmill.dto.AudioRequest;
import net.ddns.adambravo79.tmill.service.AudioPipelineService;
import net.ddns.adambravo79.tmill.service.TelegramFileService;
import net.ddns.adambravo79.tmill.service.TranscriptStoreService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.TelegramUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudioHandler {

    private static final String TRANS_BRUTO = "trans_bruto";
    private static final String TRANS_REFINADO = "trans_refinado";

    private final TelegramFileService fileService;
    private final AudioPipelineService audioService;
    private final TranscriptionCacheService cacheService;
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

    public void handleAudioUpdate(Update update) {
        if (!transcriptionEnabled) {
            telegramFacade.enviarMensagem(
                    update.message().chat().id(), "🔇 Transcrição desativada.");
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
            telegramFacade.enviarMensagem(
                    chatId,
                    "📂 O arquivo de áudio excede " + maxSizeMb + " MB. Envie um arquivo menor.");
            return;
        }

        boolean isGroup =
                message.chat().type() == com.pengrad.telegrambot.model.Chat.Type.group
                        || message.chat().type()
                                == com.pengrad.telegrambot.model.Chat.Type.supergroup;

        if (isGroup) {
            processGroupAudio(message, chatId, fileId, duration);
        } else {
            processPrivateAudio(message, chatId, fileId);
        }
    }

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
                        handleRespostaPrivado(chatId, texto);
                    }
                });
    }

    private void handleRespostaPrivado(long chatId, String texto) {
        if (texto.length() > telegramMessageLimit) {
            utils.splitMessage(texto, telegramMessageLimit)
                    .forEach(parte -> telegramFacade.enviarMensagem(chatId, parte));
        } else {
            telegramFacade.enviarMensagem(chatId, texto);
        }
    }

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
                            if (ex != null || result == null) {
                                log.error("Falha no pré-processamento do áudio", ex);
                                telegramFacade.enviarMensagem(
                                        chatId, "❌ Erro ao processar o áudio. Tente novamente.");
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

                            enviarBotoesGrupo(chatId, senderName, duration, token);
                        });
    }

    @SuppressWarnings("unused")
    private void enviarBotoesGrupo(long chatId, String senderName, int duration, String token) {
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
    }

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
            telegramFacade.answerCallbackQuery(
                    callback.id(), "Pedido expirado. Envie o áudio novamente.", true);
            return;
        }

        String fileId = request.fileId();
        TranscriptionCacheEntry cached = cacheService.get(fileId);
        if (cached != null) {
            entregarTranscricaoCache(userId, tipo, cached);
            pendingRequests.remove(token);
            return;
        }

        log.info("Cache miss para fileId={}, processando novamente.", fileId);
        telegramFacade.answerCallbackQuery(
                callback.id(), "Processando áudio... enviarei no privado.", false);

        long groupId = request.groupId();
        CompletableFuture.runAsync(
                () -> processarAudioCallback(userId, tipo, fileId, request, groupId));
    }

    private void entregarTranscricaoCache(
            long userId, String tipo, TranscriptionCacheEntry cached) {
        String texto =
                tipo.equals(TRANS_BRUTO) ? cached.getTextoBruto() : cached.getTextoRefinado();
        String prefixo =
                tipo.equals(TRANS_BRUTO) ? "🎙️ Transcrição Bruta:\n" : "✨ Transcrição Refinada:\n";
        enviarTranscricao(userId, prefixo + texto);
        log.info("✅ Transcrição entregue via cache para userId={} tipo={}", userId, tipo);
    }

    private void processarAudioCallback(
            long userId, String tipo, String fileId, AudioRequest request, long groupId) {
        try {
            String mensagem = transcreverAudio(fileId, tipo, request);
            enviarTranscricao(userId, mensagem);
            log.info("✅ Transcrição enviada para userId={} tipo={}", userId, tipo);
        } catch (Exception e) {
            log.error("Erro ao processar áudio para userId={} fileId={}", userId, fileId, e);
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
            throw new IllegalStateException("Nenhum texto produzido");
        }

        String prefixo =
                tipo.equals(TRANS_BRUTO) ? "🎙️ Transcrição Bruta:\n" : "✨ Transcrição Refinada:\n";
        return prefixo + resultado[0];
    }

    private void enviarTranscricao(long userId, String mensagem) {
        if (mensagem.length() > telegramMessageLimit) {
            utils.splitMessage(mensagem, telegramMessageLimit)
                    .forEach(parte -> telegramFacade.enviarMensagem(userId, parte));
        } else {
            telegramFacade.enviarMensagem(userId, mensagem);
        }
    }

    private void tratarErroTranscricao(Exception e, long userId, long groupId) {
        String errorMsg = e.getMessage();
        boolean isForbidden =
                errorMsg != null
                        && errorMsg.contains("403")
                        && errorMsg.contains("can't initiate conversation");

        if (isForbidden && groupId != 0) {
            telegramFacade.enviarMensagem(
                    groupId,
                    "⚠️ Usuário precisa iniciar conversa com o bot no privado para receber"
                            + " transcrições.");
        } else {
            try {
                telegramFacade.enviarMensagem(userId, "❌ Erro ao processar áudio: " + errorMsg);
            } catch (Exception ex) {
                log.error("Falha ao enviar mensagem de erro para userId {}", userId, ex);
            }
        }
    }

    private String gerarToken(String fileId) {
        String token =
                Long.toHexString(System.nanoTime())
                        + Integer.toHexString(fileId.hashCode() & 0xFFFF);
        return token.length() > 20 ? token.substring(0, 20) : token;
    }

    private void cleanExpiredTokens() {
        long now = System.currentTimeMillis();
        int before = pendingRequests.size();
        pendingRequests
                .entrySet()
                .removeIf(entry -> now - entry.getValue().timestamp() > 604800000);
        int after = pendingRequests.size();
        if (before != after) {
            log.info(
                    "🧹 Cache de tokens limpo: {} entradas removidas, {} restantes",
                    before - after,
                    after);
        }
    }
}
