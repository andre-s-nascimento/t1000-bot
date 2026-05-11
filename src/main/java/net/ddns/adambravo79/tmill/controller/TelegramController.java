/* (c) 2026 | 09/05/2026 */
package net.ddns.adambravo79.tmill.controller;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.*;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.cache.TranscricaoCache;
import net.ddns.adambravo79.tmill.cache.TranscriptionCacheEntry;
import net.ddns.adambravo79.tmill.cache.TranscriptionCacheService;
import net.ddns.adambravo79.tmill.client.BloggerClient;
import net.ddns.adambravo79.tmill.dto.AudioRequest;
import net.ddns.adambravo79.tmill.exception.MovieNotFoundException;
import net.ddns.adambravo79.tmill.model.MovieRecord;
import net.ddns.adambravo79.tmill.model.MovieSearchResponse;
import net.ddns.adambravo79.tmill.service.*;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.core.TelegramSafeExecutor;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramController implements LongPollingUpdateConsumer {

    private final MovieService movieService;
    private final AudioPipelineService audioService;
    private final BloggerClient bloggerClient;
    private final TranscricaoCache cache;
    private final TelegramFileService fileService;
    private final TelegramFacade telegramFacade;
    private final TelegramSafeExecutor safeExecutor;
    private final UserInteractionLogger userLogger;
    private final IdeasLogger ideasLogger;
    private final MessageStoreService messageStoreService;
    private final TranscriptStoreService transcriptStoreService;
    private final TranscriptionCacheService transcriptionCacheService;

    @Value("${t1000.features.transcription-enabled:false}")
    private boolean transcriptionEnabled;

    @Value("${telegram.owner.id:0}")
    private long ownerId;

    @Value("${t1000.audio.max-size-mb:20}")
    private int maxAudioSizeMb;

    @Value("${telegram.message.limit:4000}")
    private int telegramMessageLimit;

    @Value("${telegram.bot.name:@t1000paneleiro_bot}")
    private String botUsername;

    @Value("${bot.allowed-chats:}")
    private String allowedChatsStr;

    private final Set<Long> allowedGroups = new HashSet<>();
    private final Set<Long> warnedGroups = ConcurrentHashMap.newKeySet();
    private final Set<String> warnedUsersFor403 = ConcurrentHashMap.newKeySet();

    private final Map<String, AudioRequest> pendingGroupAudio = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void init() {
        if (allowedChatsStr != null && !allowedChatsStr.isBlank()) {
            for (String s : allowedChatsStr.split(",")) {
                try {
                    long id = Long.parseLong(s.trim());
                    if (id < 0) allowedGroups.add(id);
                    else log.warn("ID positivo ignorado (use apenas grupos): {}", id);
                } catch (NumberFormatException e) {
                    log.warn("Chat ID inválido: {}", s);
                }
            }
            log.info("📋 Grupos autorizados: {}", allowedGroups);
        } else {
            log.info("📋 Nenhum grupo autorizado configurado – todos os grupos serão ignorados.");
        }

        cleaner.scheduleAtFixedRate(
                () -> {
                    long now = System.currentTimeMillis();
                    pendingGroupAudio
                            .entrySet()
                            .removeIf(entry -> now - entry.getValue().timestamp() > 3600000);
                    log.debug("🧹 Cache de tokens limpo. Tamanho: {}", pendingGroupAudio.size());
                },
                1,
                1,
                TimeUnit.HOURS);
    }

    @Override
    public void consume(List<Update> updates) {
        if (updates == null || updates.isEmpty()) return;
        log.info("📩 Recebidos {} updates", updates.size());
        updates.parallelStream().forEach(this::processarUpdate);
    }

    public void consume(Update update) {
        if (update != null) {
            log.info("📩 Update único");
            processarUpdate(update);
        }
    }

    private boolean isChatAllowed(long chatId) {
        if (chatId > 0) return true;
        if (allowedGroups.isEmpty()) return true;
        return allowedGroups.contains(chatId);
    }

    private void processarUpdate(Update update) {
        Long chatId = null;
        if (update.hasMessage()) chatId = update.getMessage().getChatId();
        else if (update.hasCallbackQuery())
            chatId = update.getCallbackQuery().getMessage().getChatId();
        if (chatId != null && !isChatAllowed(chatId)) {
            if (warnedGroups.add(chatId)) log.warn("⛔ Grupo não autorizado: {}", chatId);
            return;
        }

        if (update.hasCallbackQuery()) {
            var callback = update.getCallbackQuery();
            var from = callback != null ? callback.getFrom() : null;
            if (from != null) {
                userLogger.logUser(
                        from.getId(),
                        from.getFirstName()
                                + (from.getLastName() != null ? " " + from.getLastName() : ""),
                        "callback:" + callback.getData());
            }
            long cbChatId = callback.getMessage().getChatId();
            log.info("🔘 Callback recebido chatId={} data={}", cbChatId, callback.getData());
            safeExecutor.run(
                    cbChatId, telegramFacade::enviarMensagem, () -> tratarCallback(callback));
            return;
        }

        if (!update.hasMessage()) return;

        Message message = update.getMessage();
        long msgChatId = message.getChatId();
        if (message.hasVoice() || message.hasAudio()) {
            safeExecutor.run(
                    msgChatId,
                    telegramFacade::enviarMensagem,
                    () -> tratarFluxoAudio(message, msgChatId));
            return;
        }

        if (message.hasText()) {
            safeExecutor.run(
                    msgChatId,
                    telegramFacade::enviarMensagem,
                    () -> tratarTexto(message, msgChatId));
        }
    }

    private void tratarTexto(Message message, long chatId) {
        String texto = message.getText().toLowerCase().trim();
        log.debug("🔎 Processando texto chatId={} texto='{}'", chatId, texto);

        if (texto.equals("/start")) {
            enviarBoasVindas(chatId, message.getFrom().getFirstName());
            return;
        }

        // Dentro de tratarTexto, após o /start e antes de processar comandos, salve a mensagem:
        if (!texto.startsWith("t1000") && !texto.startsWith("/start")) {
            String userName =
                    message.getFrom().getFirstName()
                            + (message.getFrom().getLastName() != null
                                    ? " " + message.getFrom().getLastName()
                                    : "");
            messageStoreService.saveMessage(chatId, message.getFrom().getId(), userName, texto);
        }

        if (texto.startsWith("t1000 anotar ideia")) {
            String idea = texto.replace("t1000 anotar ideia", "").trim();
            if (idea.isEmpty()) {
                telegramFacade.enviarMensagem(
                        chatId,
                        "❓ Digite a ideia após o comando. Ex: `T1000 anotar ideia: fazer café`");
                return;
            }
            if (idea.startsWith(":") || idea.startsWith("：")) idea = idea.substring(1).trim();
            if (idea.isEmpty()) {
                telegramFacade.enviarMensagem(chatId, "❓ A ideia não pode ficar vazia.");
                return;
            }

            var from = message.getFrom();
            String userName =
                    from.getFirstName()
                            + (from.getLastName() != null ? " " + from.getLastName() : "");
            long userId = from.getId();
            String chatName =
                    (message.getChat().isGroupChat() || message.getChat().isSuperGroupChat())
                            ? message.getChat().getTitle()
                            : "privado";

            ideasLogger.saveIdea(userId, userName, chatId, idea, chatName);

            String adminMsg =
                    String.format(
                            "💡 <b>Nova ideia</b>\n"
                                    + "📝 <i>%s</i>\n"
                                    + "👤 <b>Usuário:</b> <a href=\"tg://user?id=%d\">%s</a>\n"
                                    + "📍 <b>Local:</b> %s\n"
                                    + "🕒 %s",
                            escapeHtml(idea),
                            userId,
                            escapeHtml(userName),
                            escapeHtml(chatName),
                            java.time.LocalDateTime.now()
                                    .format(
                                            java.time.format.DateTimeFormatter.ofPattern(
                                                    "dd/MM/yyyy HH:mm:ss")));
            telegramFacade.enviarMensagemHtml(ownerId, adminMsg);
            telegramFacade.enviarMensagemHtml(
                    chatId, "✅ Ideia registrada! Obrigado pela contribuição.");
            return;
        }

        if (!texto.startsWith("t1000 buscar")) return;

        String nome = texto.replace("t1000 buscar", "").trim();
        if (nome.length() < 3) {
            telegramFacade.enviarMensagem(chatId, "🔍 O termo deve ter pelo menos 3 caracteres.");
            return;
        }
        if (nome.length() > 100) {
            telegramFacade.enviarMensagem(
                    chatId, "🔍 O termo é muito longo (máx. 100 caracteres).");
            return;
        }

        MovieSearchResponse busca;
        try {
            busca = movieService.buscarFilme(nome);
        } catch (MovieNotFoundException e) {
            telegramFacade.enviarMensagem(chatId, "❌ " + e.getMessage());
            return;
        }

        if (busca == null || busca.results() == null || busca.results().isEmpty()) {
            telegramFacade.enviarMensagem(chatId, "❌ Filme não encontrado.");
            return;
        }

        if (busca.results().size() == 1) {
            var id = busca.results().get(0).id();
            var resposta = movieService.buscarPorId(id);
            log.info("✅ Filme único chatId={} movieId={}", chatId, id);
            String fotoUrl = resposta.urlFoto();
            if (fotoUrl != null
                    && !fotoUrl.isBlank()
                    && (fotoUrl.startsWith("http://") || fotoUrl.startsWith("https://"))) {
                telegramFacade.enviarFotoHtml(chatId, fotoUrl, resposta.textoFormatado());
            } else {
                telegramFacade.enviarMensagemHtml(
                        chatId, resposta.textoFormatado() + "\n\n_(sem imagem)_");
            }
            return;
        }

        log.info("🧐 Vários resultados: {} total={}", chatId, busca.results().size());
        enviarOpcoesDesambiguacao(chatId, busca.results());
    }

    private void enviarBoasVindas(long chatId, String firstName) {
        String saudacao =
                String.format(
                        """
            🤖 Olá, <b>%s</b>! Eu sou o <b>Tmill Bot</b>, o robô de metal líquido das transcrições e buscas.

            📌 <b>O que posso fazer?</b>
            🎬 Buscar filmes: <code>t1000 buscar &lt;nome do filme&gt;</code>
            🎙️ Transcrever áudios: envie uma mensagem de voz ou áudio.

            💡 <b>Em grupos/canais:</b>
            Ao enviar um áudio, aparecerão botões para você escolher a transcrição <b>bruta</b> (🎙️) ou <b>refinada</b> (✨). O resultado chegará no seu <b>chat privado</b>.

            💡 Dar sugestões de melhoria do Bot <code>t1000 anotar ideia Achar os pais adotivos do John Connor... </code>

            Desenvolvido com 🧠 e ☕ Java 21 + Spring Boot e bom humor.
            """,
                        escapeHtml(firstName));
        telegramFacade.enviarMensagemHtml(chatId, saudacao);
    }

    // =========================
    // 🎙️ AUDIO
    // =========================
    private void tratarFluxoAudio(Message message, long chatId) {
        if (!transcriptionEnabled) {
            log.warn("⚠️ Transcrição desativada chatId={}", chatId);
            telegramFacade.enviarMensagem(chatId, "🔇 Transcrição desativada.");
            return;
        }

        String fileId =
                message.hasVoice()
                        ? message.getVoice().getFileId()
                        : message.getAudio().getFileId();
        long fileSize =
                message.hasVoice()
                        ? message.getVoice().getFileSize()
                        : message.getAudio().getFileSize();
        long maxBytes = maxAudioSizeMb * 1024L * 1024L;

        if (fileSize > maxBytes) {
            log.warn(
                    "⚠️ Áudio muito grande chatId={} size={} bytes (limite {} MB)",
                    chatId,
                    fileSize,
                    maxAudioSizeMb);
            telegramFacade.enviarMensagem(
                    chatId,
                    "📂 O arquivo de áudio excede "
                            + maxAudioSizeMb
                            + " MB. Envie um arquivo menor.");
            return;
        }

        if (fileId == null || fileId.isEmpty()) {
            log.error("❌ fileId inválido chatId={}", chatId);
            telegramFacade.enviarMensagem(
                    chatId, "❌ Identificador do áudio inválido. Tente novamente.");
            return;
        }

        boolean isGroup = isGroupChat(message);

        // ========== GRUPO: pré‑processamento em background ==========
        if (isGroup) {
            final long senderId = message.getFrom().getId();
            final String firstName = message.getFrom().getFirstName();
            final String lastName = message.getFrom().getLastName();
            final String senderName = firstName + (lastName != null ? " " + lastName : "");
            final int duration =
                    message.hasVoice()
                            ? message.getVoice().getDuration()
                            : message.getAudio().getDuration();
            final long fixedChatId = chatId;
            final String fixedFileId = fileId;

            log.info(
                    "🎙️ Áudio recebido em grupo chatId={} fileId={} de {} duração={}s",
                    fixedChatId,
                    fixedFileId,
                    senderName,
                    duration);

            // Processa em background (baixa, converte, transcreve, refina)
            CompletableFuture.supplyAsync(() -> fileService.baixarArquivo(fixedFileId))
                    .thenCompose(
                            audioFile ->
                                    audioService.processarEArmazenar(
                                            audioFile, fixedChatId, senderId, senderName))
                    .whenComplete(
                            (result, ex) -> {
                                if (ex == null && result != null) {
                                    // Armazena no cache
                                    transcriptionCacheService.put(
                                            fixedFileId, result.bruto(), result.refinado());

                                    // Salva refinado no banco para o digest
                                    transcriptStoreService.saveTranscriptWithRaw(
                                            fixedChatId,
                                            senderId,
                                            senderName,
                                            result.bruto(),
                                            result.refinado());

                                    // Gera token curto
                                    String token =
                                            Long.toHexString(System.nanoTime())
                                                    + Integer.toHexString(
                                                            fixedFileId.hashCode() & 0xFFFF);
                                    if (token.length() > 20) token = token.substring(0, 20);
                                    pendingGroupAudio.put(
                                            token,
                                            new AudioRequest(
                                                    fixedFileId,
                                                    fixedChatId,
                                                    System.currentTimeMillis(),
                                                    senderId,
                                                    senderName));

                                    // Prepara mensagem com botões
                                    long minutos = duration / 60;
                                    long segundos = duration % 60;
                                    String duracaoFormatada =
                                            String.format("%dmin e %ds", minutos, segundos);
                                    String silasCastHint =
                                            (duration > 300)
                                                    ? ", praticamente um SilasCast 🗣"
                                                    : "";
                                    String mensagemBotoes =
                                            String.format(
                                                    "🎧 Áudio de <b>%s</b> (%s%s) processado!\n\n"
                                                            + "Clique num botão para receber a"
                                                            + " transcrição no seu privado:",
                                                    escapeHtml(senderName),
                                                    duracaoFormatada,
                                                    silasCastHint);

                                    InlineKeyboardMarkup markup =
                                            InlineKeyboardMarkup.builder()
                                                    .keyboard(
                                                            List.of(
                                                                    new InlineKeyboardRow(
                                                                            InlineKeyboardButton
                                                                                    .builder()
                                                                                    .text(
                                                                                            "🎙️ Transcrição"
                                                                                                + " Bruta")
                                                                                    .callbackData(
                                                                                            "trans_bruto|"
                                                                                                    + token)
                                                                                    .build(),
                                                                            InlineKeyboardButton
                                                                                    .builder()
                                                                                    .text(
                                                                                            "✨ Transcrição"
                                                                                                + " Refinada")
                                                                                    .callbackData(
                                                                                            "trans_refinado|"
                                                                                                    + token)
                                                                                    .build())))
                                                    .build();

                                    // Envia a mensagem com botões (agora que o processamento
                                    // terminou)
                                    telegramFacade.enviarComBotoesHtml(
                                            fixedChatId, mensagemBotoes, markup);
                                } else {
                                    log.error("Falha no pré‑processamento do áudio", ex);
                                    telegramFacade.enviarMensagem(
                                            fixedChatId,
                                            "❌ Erro ao processar o áudio. Tente novamente.");
                                }
                            });
            return;
        }

        // ========== CHAT PRIVADO: comportamento original (processamento imediato) ==========
        long userId = message.getFrom().getId();
        boolean isOwner = userId == ownerId;

        File file = fileService.baixarArquivo(fileId);
        String userName = message.getFrom().getFirstName();
        if (message.getFrom().getLastName() != null)
            userName += " " + message.getFrom().getLastName();

        audioService.processarFluxoAudio(
                file,
                chatId,
                userId,
                userName,
                (texto, isUltima) -> {
                    if (texto.length() > telegramMessageLimit) {
                        dividirMensagem(texto, telegramMessageLimit)
                                .forEach(
                                        parte ->
                                                telegramFacade.enviarMensagemSemMarkdown(
                                                        chatId, parte));
                        return;
                    }
                    if (isUltima && isOwner) {
                        enviarRespostaComBotoesBloggerHtml(chatId, texto);
                    } else {
                        telegramFacade.enviarMensagemSemMarkdown(chatId, texto);
                    }
                });
    }

    private boolean isGroupChat(Message message) {
        var chat = message.getChat();
        return chat.isGroupChat() || chat.isSuperGroupChat() || chat.isChannelChat();
    }

    private void enviarRespostaComBotoesBloggerHtml(long chatId, String texto) {
        cache.salvar(chatId, texto);
        InlineKeyboardMarkup markup =
                InlineKeyboardMarkup.builder()
                        .keyboard(
                                List.of(
                                        new InlineKeyboardRow(
                                                InlineKeyboardButton.builder()
                                                        .text("📝 Publicar")
                                                        .callbackData("blogger:publicar")
                                                        .build(),
                                                InlineKeyboardButton.builder()
                                                        .text("❌ Cancelar")
                                                        .callbackData("blogger:cancelar")
                                                        .build())))
                        .build();
        // 🔥 Usar HTML – o texto refinado pode conter pontuação especial, mas HTML não requer
        // escape
        telegramFacade.enviarComBotoesHtml(chatId, texto, markup);
    }

    private void enviarBotoesTranscricaoEmGrupo(
            long groupId, String fileId, String senderName, long senderId, int durationSeconds) {
        // Gera token curto
        String token =
                Long.toHexString(System.nanoTime())
                        + Integer.toHexString(fileId.hashCode() & 0xFFFF);
        if (token.length() > 20) token = token.substring(0, 20);

        pendingGroupAudio.put(
                token,
                new AudioRequest(
                        fileId, groupId, System.currentTimeMillis(), senderId, senderName));

        long minutos = durationSeconds / 60;
        long segundos = durationSeconds % 60;
        String duracaoFormatada = String.format("%dmin e %ds", minutos, segundos);
        String silasCastHint = (durationSeconds > 300) ? ", praticamente um SilasCast 🗣" : "";

        // 🔥 HTML em vez de MarkdownV2 – não precisa escapar '!'
        String mensagem =
                String.format(
                        "🎧 Áudio recebido de <b>%s</b>, com ⌛%s%s! \n\n"
                            + "Clique num botão abaixo para receber a transcrição 📝 desejada no"
                            + " seu chat privado 💬.",
                        escapeHtml(senderName), duracaoFormatada, silasCastHint);

        InlineKeyboardMarkup markup =
                InlineKeyboardMarkup.builder()
                        .keyboard(
                                List.of(
                                        new InlineKeyboardRow(
                                                InlineKeyboardButton.builder()
                                                        .text("🎙️ Transcrição Bruta")
                                                        .callbackData("trans_bruto|" + token)
                                                        .build(),
                                                InlineKeyboardButton.builder()
                                                        .text("✨ Transcrição Refinada")
                                                        .callbackData("trans_refinado|" + token)
                                                        .build())))
                        .build();

        // Usar o método HTML do facade
        telegramFacade.enviarComBotoesHtml(groupId, mensagem, markup);
    }

    // =========================
    // 🔘 CALLBACK
    // =========================

    private void tratarCallback(CallbackQuery cb) {
        String data = cb.getData();
        long chatId = cb.getMessage().getChatId();
        log.debug("🔘 callback chatId={} data={}", chatId, data);

        if (data.startsWith("trans_bruto|") || data.startsWith("trans_refinado|")) {
            tratarCallbackTranscricaoComToken(cb, data);
            return;
        }

        if (data.startsWith("id:")) {
            long id = Long.parseLong(data.replace("id:", ""));
            var resposta = movieService.buscarPorId(id);
            log.info("✅ Callback de filme chatId={} movieId={}", chatId, id);
            String fotoUrl = resposta.urlFoto();
            if (fotoUrl != null
                    && !fotoUrl.isBlank()
                    && (fotoUrl.startsWith("http://") || fotoUrl.startsWith("https://"))) {
                telegramFacade.enviarFotoHtml(chatId, fotoUrl, resposta.textoFormatado());
            } else {
                telegramFacade.enviarMensagemHtml(
                        chatId, resposta.textoFormatado() + "\n\n_(sem imagem)_");
            }
            int messageId = cb.getMessage().getMessageId();
            String newText = "✅ Filme selecionado: " + resposta.textoFormatado().split("\n")[0];
            telegramFacade.editarMensagemHtml(chatId, messageId, newText);
            return;
        }

        if ("blogger:cancelar".equals(data)) {
            cache.remover(chatId);
            telegramFacade.enviarMensagem(chatId, "❌ Publicação cancelada.");
            return;
        }

        if ("blogger:publicar".equals(data)) {
            String texto = cache.recuperar(chatId);
            if (texto == null) {
                telegramFacade.enviarMensagem(chatId, "⚠️ Nenhuma transcrição disponível.");
                return;
            }
            String url = bloggerClient.criarRascunho("Post automático", texto);
            if (url != null) {
                telegramFacade.enviarMensagem(chatId, "✅ Publicado: " + url);
                cache.remover(chatId);
            } else {
                telegramFacade.enviarMensagem(chatId, "❌ Falha ao publicar.");
            }
        }
    }

    private void tratarCallbackTranscricaoComToken(CallbackQuery callback, String data) {
        String[] parts = data.split("\\|", 2);
        if (parts.length < 2) {
            log.error("Callback malformado: {}", data);
            return;
        }
        String tipo = parts[0];
        String token = parts[1];

        long userId = callback.getFrom().getId();
        long chatId = callback.getMessage().getChatId();
        log.info(
                "📊 Clique no botão {} | userId={} | chatId={} | token={}",
                tipo,
                userId,
                chatId,
                token);

        AudioRequest request = pendingGroupAudio.get(token);
        if (request == null) {
            log.warn(
                    "Token inválido ou expirado: {} (userId={}, chatId={})", token, userId, chatId);
            telegramFacade.answerCallbackQuery(
                    callback.getId(), "Pedido expirado. Envie o áudio novamente.", true);
            return;
        }

        // Dentro de tratarCallbackTranscricaoComToken, após obter request e antes de processar
        String fileId = request.fileId();
        TranscriptionCacheEntry cached = transcriptionCacheService.get(fileId);
        if (cached != null) {
            // Cache hit: envia o resultado imediatamente (sem chamar Groq)
            String textoEscolhido =
                    "trans_bruto".equals(tipo) ? cached.getTextoBruto() : cached.getTextoRefinado();
            String prefixo =
                    tipo.equals("trans_bruto")
                            ? "🎙️ Transcrição Bruta:\n"
                            : "✨ Transcrição Refinada:\n";
            String mensagem = prefixo + textoEscolhido;
            // ... enviar mensagem (igual ao fluxo normal)
            if (mensagem.length() > telegramMessageLimit) {
                dividirMensagem(mensagem, telegramMessageLimit)
                        .forEach(parte -> telegramFacade.enviarMensagemSemMarkdown(userId, parte));
            } else {
                telegramFacade.enviarMensagemSemMarkdown(userId, mensagem);
            }
            log.info("✅ Transcrição entregue via cache para userId={} tipo={}", userId, tipo);
            return;
        }

        log.info(
                "📊 Clique no botão {} | clicador={} (id={}) | áudio enviado por: {} (id={}) |"
                        + " chatId={} | token={}",
                tipo,
                callback.getFrom().getFirstName(),
                callback.getFrom().getId(),
                request.senderName(),
                request.senderId(),
                chatId,
                token);

        long groupId = request.groupId();

        telegramFacade.answerCallbackQuery(
                callback.getId(), "Processando áudio... enviarei no privado.", false);

        CompletableFuture.runAsync(
                () -> {
                    try {
                        File audioFile = fileService.baixarArquivo(fileId);
                        final String[] resultado = {null};

                        audioService.processarFluxoAudio(
                                audioFile,
                                request.groupId(),
                                request.senderId(),
                                request.senderName(),
                                (texto, isUltima) -> {
                                    if ("trans_bruto".equals(tipo) && !isUltima)
                                        resultado[0] = texto;
                                    else if ("trans_refinado".equals(tipo) && isUltima)
                                        resultado[0] = texto;
                                });

                        if (resultado[0] == null)
                            throw new IllegalStateException("Nenhum texto produzido");

                        String prefixo =
                                tipo.equals("trans_bruto")
                                        ? "🎙️ Transcrição Bruta:\n"
                                        : "✨ Transcrição Refinada:\n";
                        String mensagem = prefixo + resultado[0];

                        if (mensagem.length() > telegramMessageLimit) {
                            dividirMensagem(mensagem, telegramMessageLimit)
                                    .forEach(
                                            parte ->
                                                    telegramFacade.enviarMensagemSemMarkdown(
                                                            userId, parte));
                        } else {
                            telegramFacade.enviarMensagemSemMarkdown(userId, mensagem);
                        }
                        log.info("✅ Transcrição enviada para userId={} tipo={}", userId, tipo);

                    } catch (Exception e) {
                        log.error(
                                "Erro ao processar áudio para userId={} fileId={}",
                                userId,
                                fileId,
                                e);
                        String errorMsg = e.getMessage();
                        boolean isForbidden =
                                errorMsg != null
                                        && errorMsg.contains("403")
                                        && errorMsg.contains("can't initiate conversation");

                        if (isForbidden && groupId != 0) {
                            String warnKey = groupId + "_" + userId;
                            if (warnedUsersFor403.add(warnKey)) {
                                try {
                                    User from = callback.getFrom();
                                    String userDisplayName = from.getFirstName();
                                    if (from.getLastName() != null
                                            && !from.getLastName().isBlank()) {
                                        userDisplayName += " " + from.getLastName();
                                    }
                                    String userMention =
                                            from.getUserName() != null
                                                    ? "@" + from.getUserName()
                                                    : userDisplayName;
                                    telegramFacade.enviarMensagem(
                                            groupId,
                                            "⚠️ "
                                                    + userMention
                                                    + ", você precisa iniciar uma conversa com o"
                                                    + " bot no privado antes de receber"
                                                    + " transcrições.\n"
                                                    + "👉 Envie /start para @"
                                                    + botUsername
                                                    + " no seu chat privado e tente novamente.");
                                } catch (Exception ex) {
                                    log.error(
                                            "Falha ao enviar aviso de 403 para o grupo {}",
                                            groupId,
                                            ex);
                                }
                            } else {
                                log.debug(
                                        "Usuário {} já avisado sobre 403, suprimindo novo aviso.",
                                        userId);
                            }
                        } else {
                            try {
                                telegramFacade.enviarMensagem(
                                        userId, "❌ Erro ao processar áudio: " + errorMsg);
                            } catch (Exception ex) {
                                log.error(
                                        "Falha ao enviar mensagem de erro para userId {}",
                                        userId,
                                        ex);
                            }
                        }
                    }
                });
    }

    private void editarMensagemGrupo(long chatId, int messageId, String novoTexto) {
        telegramFacade.editarMensagem(chatId, messageId, novoTexto);
    }

    private void enviarOpcoesDesambiguacao(long chatId, List<MovieRecord> resultados) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        InlineKeyboardRow current = new InlineKeyboardRow();
        for (int i = 0; i < resultados.size() && i < 10; i++) {
            var filme = resultados.get(i);
            String ano =
                    (filme.releaseDate() != null && filme.releaseDate().length() >= 4)
                            ? " (" + filme.releaseDate().substring(0, 4) + ")"
                            : " (S/A)";
            current.add(
                    InlineKeyboardButton.builder()
                            .text(filme.title() + ano)
                            .callbackData("id:" + filme.id())
                            .build());
            if ((i + 1) % 2 == 0 || (i + 1) == resultados.size()) {
                rows.add(current);
                current = new InlineKeyboardRow();
            }
        }
        var markup = InlineKeyboardMarkup.builder().keyboard(rows).build();
        telegramFacade.enviarComBotoesSemParse(
                chatId, "🧐 Encontrei vários resultados. Qual você quer?", markup);
    }

    private void enviarRespostaComBotoesBlogger(long chatId, String texto) {
        cache.salvar(chatId, texto);
        InlineKeyboardMarkup markup =
                InlineKeyboardMarkup.builder()
                        .keyboard(
                                List.of(
                                        new InlineKeyboardRow(
                                                InlineKeyboardButton.builder()
                                                        .text("📝 Publicar")
                                                        .callbackData("blogger:publicar")
                                                        .build(),
                                                InlineKeyboardButton.builder()
                                                        .text("❌ Cancelar")
                                                        .callbackData("blogger:cancelar")
                                                        .build())))
                        .build();
        telegramFacade.enviarComBotoes(chatId, texto, markup);
    }

    private String escapeMarkdown(String text) {
        return text.replace("_", "\\_")
                .replace("*", "\\*")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("~", "\\~")
                .replace("`", "\\`")
                .replace(">", "\\>")
                .replace("#", "\\#")
                .replace("+", "\\+")
                .replace("-", "\\-")
                .replace("=", "\\=")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace(".", "\\.")
                .replace("!", "\\!");
    }

    private List<String> dividirMensagem(String texto, int limite) {
        List<String> partes = new ArrayList<>();
        while (texto.length() > limite) {
            int corte = texto.lastIndexOf(" ", limite);
            if (corte <= 0) corte = limite;
            partes.add(texto.substring(0, corte));
            texto = texto.substring(corte).trim();
        }
        if (!texto.isEmpty()) partes.add(texto);
        return partes;
    }

    private String fecharMarkdown(String texto) {
        long count = texto.chars().filter(c -> c == '*').count();
        return (count % 2 != 0) ? texto + "*" : texto;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
