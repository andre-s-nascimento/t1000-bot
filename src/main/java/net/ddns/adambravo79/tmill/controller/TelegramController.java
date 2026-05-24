/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.controller;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import net.ddns.adambravo79.tmill.cache.TranscriptionCacheEntry;
import net.ddns.adambravo79.tmill.cache.TranscriptionCacheService;
import net.ddns.adambravo79.tmill.dto.AudioRequest;
import net.ddns.adambravo79.tmill.exception.MovieNotFoundException;
import net.ddns.adambravo79.tmill.model.MovieOrchestrationResponse;
import net.ddns.adambravo79.tmill.model.MovieRecord;
import net.ddns.adambravo79.tmill.model.MovieSearchResponse;
import net.ddns.adambravo79.tmill.service.*;
import net.ddns.adambravo79.tmill.service.AudioPipelineService.ProcessedAudio;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.core.TelegramSafeExecutor;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramController implements LongPollingUpdateConsumer {

    private static final String T1000 = "t1000";
    private static final String START = "/start";
    private static final String TRANSCRICAO_REFINADA = "✨ Transcrição Refinada:\n";
    private static final String TRANSCRICAO_BRUTA = "🎙️ Transcrição Bruta:\n";
    private static final DateTimeFormatter FORMATTER_BR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final String TRANS_BRUTO = "trans_bruto";
    private static final String TRANS_REFINADO_PREFIX = "trans_refinado|";
    private static final String TRANS_REFINADO = "trans_refinado";
    private static final String TRANS_BRUTO_PREFIX = "trans_bruto|";

    private final MovieService movieService;
    private final AudioPipelineService audioService;
    private final TelegramFileService fileService;
    private final TelegramFacade telegramFacade;
    private final TelegramSafeExecutor safeExecutor;
    private final UserInteractionLogger userLogger;
    private final IdeasLogger ideasLogger;
    private final MessageStoreService messageStoreService;
    private final TranscriptStoreService transcriptStoreService;
    private final TranscriptionCacheService transcriptionCacheService;
    private final AutoResponseService autoResponseService;

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
                    int before = pendingGroupAudio.size();
                    // Remove apenas tokens com mais de 7 dias (604800000 ms)
                    pendingGroupAudio
                            .entrySet()
                            .removeIf(entry -> now - entry.getValue().timestamp() > 604800000);
                    int after = pendingGroupAudio.size();
                    if (before != after) {
                        log.info(
                                "🧹 Cache de tokens limpo: {} entradas removidas, {} restantes",
                                before - after,
                                after);
                    }
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
            processarCallback(update);
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

    private void processarCallback(Update update) {
        var callback = update.getCallbackQuery();
        if (callback == null || callback.getMessage() == null) {
            log.warn("⚠️ CallbackQuery ou message nulo, ignorando update");
            return;
        }
        var from = callback.getFrom();
        if (from != null) {
            userLogger.logUser(from.getId(), buildFullName(from), "callback:" + callback.getData());
        }
        long cbChatId = callback.getMessage().getChatId();
        log.info("🔘 Callback recebido chatId={} data={}", cbChatId, callback.getData());
        safeExecutor.run(cbChatId, telegramFacade::enviarMensagem, () -> tratarCallback(callback));
    }

    // =========================
    // 📝 TEXTO
    // =========================

    private void tratarTexto(Message message, long chatId) {
        String texto = message.getText().toLowerCase().trim();
        log.debug("🔎 Processando texto chatId={} texto='{}'", chatId, texto);

        if (texto.equals(START)) {
            enviarBoasVindas(chatId, message.getFrom().getFirstName());
            return;
        }

        // Salva mensagem apenas se NÃO for comando
        if (!texto.startsWith(T1000) && !texto.startsWith(START)) {
            messageStoreService.saveMessage(
                    chatId, message.getFrom().getId(), buildFullName(message.getFrom()), texto);
        }

        // ========== RESPOSTAS AUTOMÁTICAS ==========
        if (!texto.startsWith("T1000") && !texto.startsWith(START)) {
            Optional<AutoResponseOverride> autoResponse =
                    autoResponseService.getResponseRule(message.getFrom().getId(), texto);
            if (autoResponse.isPresent()) {
                AutoResponseOverride response = autoResponse.get();
                String userMention = buildUserMention(message.getFrom());
                String finalMsg = userMention + ", " + response.getResponse();
                if (response.getAnimation() != null && !response.getAnimation().isBlank()) {
                    telegramFacade.enviarMidia(chatId, response.getAnimation(), finalMsg);
                } else {
                    telegramFacade.enviarMensagemHtml(chatId, finalMsg);
                }
                return;
            }
        }
        // ==========================================
        // Opção: enviar diretamente no privado do usuário (descomente e comente as linhas
        // abaixo)
        // long privateChatId = message.getFrom().getId();
        // if (rule.getAnimation() != null && !rule.getAnimation().isBlank()) {
        //     telegramFacade.enviarAnimacao(privateChatId, rule.getAnimation(),
        // responseText);
        // } else {
        //     telegramFacade.enviarMensagemHtml(privateChatId, responseText);
        // }
        // return;

        // 🔥 Normalização para comandos com hífen
        String normalized = texto.replace("t-1000", T1000);

        // Comando "anotar ideia"
        if (normalized.startsWith("t1000 anotar ideia")) {
            log.info("✅ Comando 't1000 anotar ideia' detectado");
            tratarAnotarIdeia(message, chatId, texto);
            return;
        }

        // 🔥 Comando "buscar" – extrai o termo após "t1000 buscar"
        if (normalized.startsWith("t1000 buscar")) {
            // Remove o prefixo "t1000 buscar" (case‑insensitive) e espaços
            String termo = texto.replaceFirst("(?i)^t-?1000 buscar\\s*", "").trim();
            if (termo.isEmpty()) {
                telegramFacade.enviarMensagem(
                        chatId, "❓ Digite um filme para buscar após o comando.");
                return;
            }
            log.info("✅ Comando 't1000 buscar' detectado");
            tratarBuscarFilme(chatId, termo);
        }
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

    private void tratarAnotarIdeia(Message message, long chatId, String texto) {
        log.info("📝 Comando 'anotar ideia' recebido: {}", texto);
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
        long userId = from.getId();
        String userName = buildFullName(from);
        String chatName = resolverNomeChat(message);

        ideasLogger.saveIdea(userId, userName, chatId, idea, chatName);

        String adminMsg =
                """
        💡 <b>Nova ideia</b>
        📝 <i>%s</i>
        👤 <b>Usuário:</b> <a href="tg://user?id=%d">%s</a>
        📍 <b>Local:</b> %s
        🕒 %s
        """
                        .formatted(
                                escapeHtml(idea),
                                userId,
                                escapeHtml(userName),
                                escapeHtml(chatName),
                                LocalDateTime.now().format(FORMATTER_BR));

        telegramFacade.enviarMensagemHtml(ownerId, adminMsg);
        telegramFacade.enviarMensagemHtml(
                chatId, "✅ Ideia registrada! Obrigado pela contribuição.");
    }

    private void tratarBuscarFilme(long chatId, String texto) {
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
            enviarFilmeUnico(chatId, busca.results().get(0).id());
            return;
        }

        log.info("🧐 Vários resultados: {} total={}", chatId, busca.results().size());
        enviarOpcoesDesambiguacao(chatId, busca.results());
    }

    // =========================
    // 🎬 FILMES
    // =========================

    private void enviarFilmeUnico(long chatId, Long movieId) {
        MovieOrchestrationResponse resposta = movieService.buscarPorId(movieId);
        log.info("✅ Filme único chatId={} movieId={}", chatId, movieId);
        exibirRespostaFilme(chatId, resposta);
    }

    private void enviarFilmeUnicoCallback(long chatId, Long movieId, int messageId) {
        MovieOrchestrationResponse resposta = movieService.buscarPorId(movieId);
        log.info("✅ Callback de filme chatId={} movieId={}", chatId, movieId);
        exibirRespostaFilme(chatId, resposta);
        String newText = "✅ Filme selecionado: " + resposta.textoFormatado().split("\n")[0];
        telegramFacade.editarMensagemHtml(chatId, messageId, newText);
    }

    private void exibirRespostaFilme(long chatId, MovieOrchestrationResponse resposta) {
        String fotoUrl = resposta.urlFoto();
        if (fotoUrl != null
                && !fotoUrl.isBlank()
                && (fotoUrl.startsWith("http://") || fotoUrl.startsWith("https://"))) {
            telegramFacade.enviarFotoHtml(chatId, fotoUrl, resposta.textoFormatado());
        } else {
            telegramFacade.enviarMensagemHtml(
                    chatId, resposta.textoFormatado() + "\n\n_(sem imagem)_");
        }
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

        if (fileSize > maxAudioSizeMb * 1024L * 1024L) {
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

        if (isGroupChat(message)) {
            processarAudioGrupo(message, chatId, fileId);
            return;
        }
        processarAudioPrivado(message, chatId, fileId);
    }

    private void processarAudioGrupo(Message message, long chatId, String fileId) {
        long senderId = message.getFrom().getId();
        String senderName = buildFullName(message.getFrom());
        int duration =
                message.hasVoice()
                        ? message.getVoice().getDuration()
                        : message.getAudio().getDuration();

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
                        (result, ex) ->
                                handleResultadoGrupo(
                                        result,
                                        ex,
                                        chatId,
                                        fileId,
                                        senderId,
                                        senderName,
                                        duration));
    }

    private void processarAudioPrivado(Message message, long chatId, String fileId) {
        long userId = message.getFrom().getId();
        String userName = buildFullName(message.getFrom());
        File file = fileService.baixarArquivo(fileId);

        audioService.processarFluxoAudio(
                file,
                chatId,
                userId,
                userName,
                (texto, isUltima) -> handleRespostaPrivado(chatId, texto));
    }

    private void handleResultadoGrupo(
            ProcessedAudio result,
            Throwable ex,
            long chatId,
            String fileId,
            long senderId,
            String senderName,
            int duration) {

        if (ex != null || result == null) {
            log.error("Falha no pré-processamento do áudio", ex);
            telegramFacade.enviarMensagem(chatId, "❌ Erro ao processar o áudio. Tente novamente.");
            return;
        }

        transcriptionCacheService.put(fileId, result.bruto(), result.refinado());
        transcriptStoreService.saveTranscriptWithRaw(
                chatId, senderId, senderName, result.bruto(), result.refinado());

        String token = gerarToken(fileId);
        log.info("🔑 Token {} gerado para fileId={} (expira em 7 dias)", token, fileId);
        pendingGroupAudio.put(
                token,
                new AudioRequest(fileId, chatId, System.currentTimeMillis(), senderId, senderName));

        enviarBotoesGrupo(chatId, senderName, duration, token);
    }

    private void handleRespostaPrivado(long chatId, String texto) {
        if (texto.length() > telegramMessageLimit) {
            dividirMensagem(texto, telegramMessageLimit)
                    .forEach(parte -> telegramFacade.enviarMensagemSemMarkdown(chatId, parte));
            return;
        }
        telegramFacade.enviarMensagemSemMarkdown(chatId, texto);
    }

    private String gerarToken(String fileId) {
        String token =
                Long.toHexString(System.nanoTime())
                        + Integer.toHexString(fileId.hashCode() & 0xFFFF);
        return token.length() > 20 ? token.substring(0, 20) : token;
    }

    private void enviarBotoesGrupo(long chatId, String senderName, int duration, String token) {
        long minutos = duration / 60;
        long segundos = duration % 60;
        String duracao = String.format("%dmin e %ds", minutos, segundos);
        String hint = duration > 300 ? ", praticamente um SilasCast 🗣" : "";

        String mensagem =
                """
        🎧 Áudio de <b>%s</b> (%s%s) processado!

        Clique num botão para receber a transcrição no seu privado:
        """
                        .formatted(escapeHtml(senderName), duracao, hint);

        InlineKeyboardMarkup markup =
                InlineKeyboardMarkup.builder()
                        .keyboard(
                                List.of(
                                        new InlineKeyboardRow(
                                                InlineKeyboardButton.builder()
                                                        .text("🎙️ Transcrição Bruta")
                                                        .callbackData(TRANS_BRUTO_PREFIX + token)
                                                        .build(),
                                                InlineKeyboardButton.builder()
                                                        .text("✨ Transcrição Refinada")
                                                        .callbackData(TRANS_REFINADO_PREFIX + token)
                                                        .build())))
                        .build();

        telegramFacade.enviarComBotoesHtml(chatId, mensagem, markup);
    }

    // =========================
    // 🔘 CALLBACK
    // =========================

    private void tratarCallback(CallbackQuery cb) {
        String data = cb.getData();
        long chatId = cb.getMessage().getChatId();
        log.debug("🔘 callback chatId={} data={}", chatId, data);

        if (data.startsWith(TRANS_BRUTO_PREFIX) || data.startsWith(TRANS_REFINADO_PREFIX)) {
            tratarCallbackTranscricaoComToken(cb, data);
            return;
        }

        if (data.startsWith("id:")) {
            long id = Long.parseLong(data.replace("id:", ""));
            enviarFilmeUnicoCallback(chatId, id, cb.getMessage().getMessageId());
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

        String fileId = request.fileId();
        TranscriptionCacheEntry cached = transcriptionCacheService.get(fileId);
        if (cached != null) {
            // Cache hit: envia o resultado imediatamente (sem chamar Groq)
            entregarTranscricaoCache(userId, tipo, cached);
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

        telegramFacade.answerCallbackQuery(
                callback.getId(), "Processando áudio... enviarei no privado.", false);

        long groupId = request.groupId();
        CompletableFuture.runAsync(
                () -> processarAudioAsync(callback, tipo, fileId, request, userId, groupId));
    }

    private void entregarTranscricaoCache(
            long userId, String tipo, TranscriptionCacheEntry cached) {
        String texto =
                TRANS_BRUTO.equals(tipo) ? cached.getTextoBruto() : cached.getTextoRefinado();
        String prefixo = TRANS_BRUTO.equals(tipo) ? TRANSCRICAO_BRUTA : TRANSCRICAO_REFINADA;
        enviarTranscricao(userId, prefixo + texto); // ← reutiliza enviarTranscricao
        log.info("✅ Transcrição entregue via cache para userId={} tipo={}", userId, tipo);
    }

    // =========================
    // 🔄 ASYNC — TRANSCRIÇÃO
    // =========================

    private void processarAudioAsync(
            CallbackQuery callback,
            String tipo,
            String fileId,
            AudioRequest request,
            long userId,
            long groupId) {
        try {
            String mensagem = transcreverAudio(fileId, tipo, request);
            enviarTranscricao(userId, mensagem);
            log.info("✅ Transcrição enviada para userId={} tipo={}", userId, tipo);
        } catch (Exception e) {
            log.error("Erro ao processar áudio para userId={} fileId={}", userId, fileId, e);
            tratarErroTranscricao(e, callback, userId, groupId);
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
                    if ((TRANS_BRUTO.equals(tipo) && !isUltimaMsg)
                            || (TRANS_REFINADO.equals(tipo) && isUltimaMsg)) {
                        resultado[0] = texto;
                    }
                });

        if (resultado[0] == null) throw new IllegalStateException("Nenhum texto produzido");

        String prefixo = TRANS_BRUTO.equals(tipo) ? TRANSCRICAO_BRUTA : TRANSCRICAO_REFINADA;
        return prefixo + resultado[0];
    }

    private void enviarTranscricao(long userId, String mensagem) {
        if (mensagem.length() > telegramMessageLimit) {
            dividirMensagem(mensagem, telegramMessageLimit)
                    .forEach(parte -> telegramFacade.enviarMensagemSemMarkdown(userId, parte));
        } else {
            telegramFacade.enviarMensagemSemMarkdown(userId, mensagem);
        }
    }

    private void tratarErroTranscricao(
            Exception e, CallbackQuery callback, long userId, long groupId) {
        String errorMsg = e.getMessage();
        boolean isForbidden =
                errorMsg != null
                        && errorMsg.contains("403")
                        && errorMsg.contains("can't initiate conversation");

        if (isForbidden && groupId != 0) {
            avisarUsuarioSemPrivado(callback, userId, groupId);
        } else {
            try {
                telegramFacade.enviarMensagem(userId, "❌ Erro ao processar áudio: " + errorMsg);
            } catch (Exception ex) {
                log.error("Falha ao enviar mensagem de erro para userId {}", userId, ex);
            }
        }
    }

    private void avisarUsuarioSemPrivado(CallbackQuery callback, long userId, long groupId) {
        String warnKey = groupId + "_" + userId;
        if (!warnedUsersFor403.add(warnKey)) {
            log.debug("Usuário {} já avisado sobre 403, suprimindo novo aviso.", userId);
            return;
        }
        try {
            User from = callback.getFrom();
            String userMention =
                    from.getUserName() != null ? "@" + from.getUserName() : buildFullName(from);
            String aviso =
                    """
          ⚠️ %s, você precisa iniciar uma conversa com o bot no privado antes de receber transcrições.
          👉 Envie /start para @%s no seu chat privado e tente novamente.\
          """
                            .formatted(userMention, botUsername);
            telegramFacade.enviarMensagem(groupId, aviso);
        } catch (Exception ex) {
            log.error("Falha ao enviar aviso de 403 para o grupo {}", groupId, ex);
        }
    }

    // =========================
    // 🛠️ UTILITÁRIOS
    // =========================

    private boolean isGroupChat(Message message) {
        var chat = message.getChat();
        return chat.isGroupChat() || chat.isSuperGroupChat() || chat.isChannelChat();
    }

    private String resolverNomeChat(Message message) {
        return (message.getChat().isGroupChat() || message.getChat().isSuperGroupChat())
                ? message.getChat().getTitle()
                : "privado";
    }

    private String buildFullName(User user) {
        if (user == null) return "";
        String lastName = user.getLastName();
        return lastName != null && !lastName.isBlank()
                ? user.getFirstName() + " " + lastName
                : user.getFirstName();
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

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String buildUserMention(User user) {
        if (user == null) return "Usuário";
        String name = user.getFirstName();
        if (user.getLastName() != null && !user.getLastName().isBlank()) {
            name += " " + user.getLastName();
        }
        // Escapa aspas e caracteres especiais para HTML
        String escapedName = name.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return String.format("<a href=\"tg://user?id=%d\">@%s</a>", user.getId(), escapedName);
    }
}
