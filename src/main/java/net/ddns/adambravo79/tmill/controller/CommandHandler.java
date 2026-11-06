/* (c) 2026 | 04/11/2026 */
package net.ddns.adambravo79.tmill.controller;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.MessageEntity;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.model.AutoResponseOverride;
import net.ddns.adambravo79.tmill.service.AutoResponseService;
import net.ddns.adambravo79.tmill.service.BotAnalyticsService;
import net.ddns.adambravo79.tmill.service.MessageStoreService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.handler.BirthdayHandler;
import net.ddns.adambravo79.tmill.telegram.handler.HelpCommandHandler;
import net.ddns.adambravo79.tmill.telegram.handler.IdeaHandler;
import net.ddns.adambravo79.tmill.telegram.handler.MovieHandler;
import net.ddns.adambravo79.tmill.telegram.handler.SalaVipHandler;
import net.ddns.adambravo79.tmill.telegram.handler.TorrentHandler;
import net.ddns.adambravo79.tmill.telegram.handler.WorldCupHandler;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;
import net.ddns.adambravo79.tmill.telegram.util.TelegramUtils;
import net.ddns.adambravo79.tmill.util.LogSanitizer;

/**
 * Roteador de mensagens de texto do bot.
 *
 * <p>Responsabilidades:
 *
 * <ol>
 *   <li>Pré-processamento comum a toda mensagem:
 *       <ul>
 *         <li>Registro no analytics
 *         <li>Salvamento no DB (apenas para não-comandos)
 *         <li>Detecção de spoiler
 *         <li>Auto-resposta (apenas para não-comandos)
 *       </ul>
 *   <li>Roteamento para o handler especializado
 *   <li>Fallback: comando não reconhecido
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommandHandler {

    private static final String PREFIXO_BASE = "t1000";
    private static final String PREFIXO_BASE_HIFEN = "t-1000";

    private final BotAnalyticsService botAnalyticsService;
    private final MessageStoreService messageStoreService;
    private final AutoResponseService autoResponseService;
    private final TelegramFacade telegramFacade;
    private final TelegramUtils utils;
    private final MetricsService metricsService;

    private final BirthdayHandler birthdayHandler;
    private final HelpCommandHandler helpCommandHandler;
    private final IdeaHandler ideaHandler;
    private final MovieHandler movieHandler;
    private final SalaVipHandler salaVipHandler;
    private final WorldCupHandler worldCupHandler;
    private final TorrentHandler torrentHandler;

    // =========================================================================
    // PONTO DE ENTRADA
    // =========================================================================

    public void handleTextUpdate(Update update) {
        Message message = update.message();
        if (message == null || message.text() == null) {
            log.debug("handleTextUpdate chamado sem mensagem/texto — ignorando.");
            return;
        }

        long chatId = message.chat().id();
        String rawText = message.text();
        String text = rawText.trim().toLowerCase();

        registrarConversa(update, chatId, rawText);

        boolean isCommand = isCommand(text);

        if (!isCommand) {
            boolean ignoreInDigest = hasSpoiler(message);
            messageStoreService.saveMessage(
                    chatId,
                    message.from().id(),
                    utils.buildFullName(message.from()),
                    rawText,
                    ignoreInDigest);

            Optional<AutoResponseOverride> auto =
                    autoResponseService.getResponseRule(message.from().id(), rawText);
            if (auto.isPresent()) {
                sendAutoResponse(chatId, message.from(), auto.get());
                return;
            }
        }

        String normalized = normalizarTexto(text);
        boolean roteado = rotear(update, normalized);

        if (!roteado && isCommand) {
            sendPlain(chatId, BotMessages.COMANDO_NAO_RECONHECIDO);
            if (rawText.contains("http://") || rawText.contains("https://")) {
                log.warn(
                        "🔗 Link não processado: '{}' (chatId={})",
                        LogSanitizer.sanitize(rawText),
                        LogSanitizer.sanitizeId(chatId));
            }
        }
    }

    // =========================================================================
    // ROTEAMENTO
    // =========================================================================

    /**
     * Roteia o texto normalizado para o handler especializado.
     *
     * <p>Usa **constantes estáticas** dos prefixos (não métodos de instância dos handlers), para
     * garantir determinismo — mesmo que um handler mockado retorne `null` ou `""` em algum
     * getter, o roteamento continua funcionando.
     *
     * @param update update do Telegram
     * @param normalized texto normalizado (lowercase, sem barra, espaços colapsados)
     * @return {@code true} se algum handler foi acionado
     */
    private boolean rotear(Update update, String normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return false;
        }

        // 1. start
        if (normalized.equals("start") || normalized.startsWith("start ")) {
            helpCommandHandler.processarComando(update);
            return true;
        }

        // 2. anotar ideia
        if (normalized.startsWith(IdeaHandler.PREFIXO)) {
            ideaHandler.processarComando(update);
            return true;
        }

        // 3. buscar / estreias / lancamentos
        if (normalized.startsWith(MovieHandler.PREFIXO_BUSCAR)
                || normalized.startsWith(MovieHandler.PREFIXO_ESTREIAS)
                || normalized.startsWith(MovieHandler.PREFIXO_LANCAMENTOS)) {
            movieHandler.processarComando(update);
            return true;
        }

        // 4. jogos / copa / resultados
        if (normalized.startsWith(WorldCupHandler.PREFIXO_JOGOS)
                || normalized.startsWith(WorldCupHandler.PREFIXO_COPA)
                || normalized.startsWith(WorldCupHandler.PREFIXO_RESULTADOS)) {
            worldCupHandler.processarComando(update);
            return true;
        }

        // 5. aniversario
        if (isBirthdayCommand(normalized)) {
            birthdayHandler.processarComando(update);
            return true;
        }

        // 6. sala vip — usa constantes estáticas (NÃO getPrefixoAlternativo mockável)
        if (normalized.startsWith(SalaVipHandler.PREFIXO)
                || normalized.startsWith(SalaVipHandler.PREFIXO_ALT)) {
            salaVipHandler.processarComando(update);
            return true;
        }

        // 🆕 Torrent
        if (normalized.startsWith(TorrentHandler.PREFIXO)) {
            torrentHandler.processarComando(update);
            return true;
        }

        return false;
    }

    private boolean isBirthdayCommand(String normalized) {
        return normalized.startsWith("t1000 registrar aniversario")
                || normalized.startsWith("t1000 registrar aniversário")
                || normalized.startsWith("t1000 anotar aniversario")
                || normalized.startsWith("t1000 anotar aniversário")
                || normalized.startsWith("t1000 aniversario")
                || normalized.startsWith("t1000 aniversário");
    }

    // =========================================================================
    // PRÉ-PROCESSAMENTO
    // =========================================================================

    private void registrarConversa(Update update, long chatId, String rawText) {
        try {
            botAnalyticsService.registrarConversa(
                    chatId,
                    update.message().from().id(),
                    utils.buildFullName(update.message().from()),
                    "TEXT",
                    rawText,
                    "Processado via CommandHandler");
        } catch (Exception e) {
            log.warn(
                    "Falha ao registrar conversa no analytics (chatId={}): {}",
                    LogSanitizer.sanitizeId(chatId),
                    e.getMessage());
        }
    }

    private boolean isCommand(String text) {
        return text.startsWith(PREFIXO_BASE)
                || text.startsWith(PREFIXO_BASE_HIFEN)
                || text.startsWith("/");
    }

    /**
     * Normaliza o texto:
     *
     * <ol>
     *   <li>remove barra inicial
     *   <li>normaliza {@code t-1000} → {@code t1000}
     *   <li>colapsa espaços múltiplos em um só
     * </ol>
     */
    private String normalizarTexto(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String normalized = text;
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.replace(PREFIXO_BASE_HIFEN, PREFIXO_BASE).replaceAll("\\s+", " ").trim();
    }

    private boolean hasSpoiler(Message message) {
        MessageEntity[] entities = message.entities();
        if (entities == null) {
            return false;
        }
        for (MessageEntity entity : entities) {
            if (entity.type() == MessageEntity.Type.spoiler) {
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // AUTO-RESPOSTA
    // =========================================================================

    private void sendAutoResponse(long chatId, User user, AutoResponseOverride response) {
        String userMention = utils.buildUserMention(user);
        String finalMsg = userMention + ", " + response.response();
        String animation = response.animation();

        if (animation != null && !animation.isBlank()) {
            if (isValidUrl(animation)) {
                boolean enviado = telegramFacade.enviarMidia(chatId, animation, finalMsg);
                if (!enviado) {
                    log.warn(
                            "⚠️ Falha ao enviar animação, tentando texto (chatId={})",
                            LogSanitizer.sanitizeId(chatId));
                    sendHtmlSafe(chatId, finalMsg);
                }
            } else {
                log.warn(
                        "⚠️ Animation inválida para chatId={}: '{}'. Enviando apenas texto.",
                        LogSanitizer.sanitizeId(chatId),
                        LogSanitizer.sanitize(animation));
                sendHtmlSafe(chatId, finalMsg);
            }
        } else {
            sendHtmlSafe(chatId, finalMsg);
        }

        log.info(
                "✅ Auto-resposta enviada (chatId={}) animation={}",
                LogSanitizer.sanitizeId(chatId),
                animation != null && !animation.isBlank());
        metricsService.success("auto_response_disparada");
    }

    private void sendHtmlSafe(long chatId, String texto) {
        try {
            telegramFacade.enviarMensagemHtml(chatId, texto);
        } catch (Exception e) {
            log.error(
                    "❌ Falha ao enviar mensagem HTML (chatId={})",
                    LogSanitizer.sanitizeId(chatId),
                    e);
        }
    }

    private void sendPlain(long chatId, String texto) {
        try {
            telegramFacade.enviarMensagem(chatId, texto);
        } catch (Exception e) {
            log.error(
                    "❌ Falha ao enviar mensagem plain (chatId={})",
                    LogSanitizer.sanitizeId(chatId),
                    e);
        }
    }

    private boolean isValidUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            java.net.URI uri = new java.net.URI(url);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank();
        } catch (java.net.URISyntaxException e) {
            return false;
        }
    }
}
