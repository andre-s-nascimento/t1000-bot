/**********************************************************
 * ARQUIVO: ./src/main/java/net/ddns/adambravo79/tmill/controller/CommandHandler.java
 **********************************************************/

package net.ddns.adambravo79.tmill.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.exception.MovieNotFoundException;
import net.ddns.adambravo79.tmill.model.AutoResponseOverride;
import net.ddns.adambravo79.tmill.model.MovieOrchestrationResponse;
import net.ddns.adambravo79.tmill.model.MovieRecord;
import net.ddns.adambravo79.tmill.model.MovieSearchResponse;
import net.ddns.adambravo79.tmill.service.*;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.TelegramUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommandHandler {

    private static final int MAX_LOG_LENGTH = 200;

    private static String sanitizeForLog(String input) {
        if (input == null) return "null";
        String sanitized = input.replaceAll("\s+", " ").trim();
        if (sanitized.length() > MAX_LOG_LENGTH) {
            sanitized = sanitized.substring(0, MAX_LOG_LENGTH) + "...";
        }
        return sanitized;
    }

    private final MovieService movieService;
    private final AutoResponseService autoResponseService;
    private final WeeklyReleasesService weeklyReleasesService;
    private final WorldCupSchedulerService worldCupSchedulerService;
    private final IdeasLogger ideasLogger;
    private final MessageStoreService messageStoreService;
    private final TelegramFacade telegramFacade;
    private final TelegramUtils utils;

    @Value("${telegram.owner.id:0}")
    private long ownerId;

    @Value("${telegram.message.limit:4000}")
    private int telegramMessageLimit;

    @Value("${worldcup.enabled:false}")
    private boolean worldcupEnabled;

    // Método principal chamado pelo TextCommandHandler
    public void handleTextUpdate(Update update) {
        Message message = update.message();
        long chatId = message.chat().id();
        String rawText = message.text();
        String text = rawText.trim().toLowerCase();

        // Remove barra inicial
        if (text.startsWith("/")) {
            text = text.substring(1);
        }

        // Comando /start – já tratado pelo StartCommandHandler, mas mantemos fallback
        if (text.equals("start")) {
            sendWelcome(chatId, message.from().firstName());
            return;
        }

        // Salva mensagem (exceto comandos)
        boolean isCommand = text.startsWith("t1000") || text.startsWith("t-1000");
        if (!isCommand) {
            messageStoreService.saveMessage(
                    chatId, message.from().id(), utils.buildFullName(message.from()), rawText);
        }

        // Auto-respostas (se não for comando)
        if (!isCommand) {
            Optional<AutoResponseOverride> auto =
                    autoResponseService.getResponseRule(message.from().id(), rawText);
            if (auto.isPresent()) {
                sendAutoResponse(chatId, message.from(), auto.get());
                return;
            }
        }

        // Normaliza comando (t-1000 -> t1000)
        String normalized = text.replace("t-1000", "t1000");

        // --- Roteamento de comandos ---
        if (normalized.startsWith("t1000 anotar ideia")) {
            String idea = rawText.replaceFirst("(?i)^t1000\s+anotar\s+ideia\s*", "").trim();
            handleAnotarIdeia(message, chatId, idea);
            return;
        }
        if (normalized.startsWith("t1000 buscar")) {
            String termo = rawText.replaceFirst("(?i)^t1000\s+buscar\s*", "").trim();
            handleBuscarFilme(chatId, termo);
            return;
        }
        if (normalized.startsWith("t1000 estreias da semana")
                || normalized.startsWith("t1000 lancamentos")) {
            handleEstreias(chatId);
            return;
        }
        if (normalized.contains("t1000 jogos") || normalized.contains("t1000 copa")) {
            handleJogosCopa(chatId, rawText);
            return;
        }
        if (normalized.startsWith("t1000 resultados")) {
            handleResultados(chatId, rawText);
            return;
        }

        // Se for comando não reconhecido
        if (isCommand) {
            telegramFacade.enviarMensagem(chatId, BotMessages.COMANDO_NAO_RECONHECIDO);
            // Logar link se houver
            if (rawText.contains("http://") || rawText.contains("https://")) {
                log.warn(
                        "🔗 Link não processado: '{}' (chatId={})",
                        sanitizeForLog(rawText),
                        chatId);
            }
        }
    }

    // ========================= Métodos privados para cada comando =========================

    private void sendWelcome(long chatId, String firstName) {
        String saudacao =
                String.format(
                        "🤖 Olá, <b>%s</b>! Eu sou o <b>Tmill Bot</b>, o robô de metal líquido das"
                            + " transcrições e buscas.\n\n"
                            + "📌 <b>O que posso fazer?</b>\n"
                            + "🎬 Buscar filmes: <code>t1000 buscar &lt;nome do filme&gt;</code>\n"
                            + "🎙️ Transcrever áudios: envie uma mensagem de voz ou áudio.\n\n"
                            + "💡 <b>Em grupos/canais:</b>\n"
                            + "Ao enviar um áudio, aparecerão botões para você escolher a"
                            + " transcrição bruta ou refinada.\n\n"
                            + "💡 Anotar sugestões: <code>t1000 anotar ideia Achar os pais adotivos"
                            + " do John Connor...</code>\n\n"
                            + "Desenvolvido com 🧠 e ☕ Java 21 + Spring Boot.",
                        utils.escapeHtml(firstName));
        telegramFacade.enviarMensagemHtml(chatId, saudacao);
    }

    private void sendAutoResponse(long chatId, User user, AutoResponseOverride response) {
        String userMention = utils.buildUserMention(user);
        String finalMsg = userMention + ", " + response.response();
        String animation = response.animation();

        if (animation != null && !animation.isBlank()) {
            // Verifica se a URL tem um host válido (para HTTP/HTTPS)
            if (isValidUrl(animation)) {
                telegramFacade.enviarMidia(chatId, animation, finalMsg);
            } else {
                log.warn(
                        "⚠️ Animation inválida para chatId {}: '{}'. Enviando apenas texto.",
                        chatId,
                        animation);
                telegramFacade.enviarMensagemHtml(chatId, finalMsg);
            }
        } else {
            telegramFacade.enviarMensagemHtml(chatId, finalMsg);
        }
    }

    private boolean isValidUrl(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            java.net.URI uri = new java.net.URI(url);
            String scheme = uri.getScheme();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                return uri.getHost() != null && !uri.getHost().isBlank();
            }
            return false; // URLs com outros esquemas não são suportadas para envio de mídia
        } catch (java.net.URISyntaxException e) {
            return false;
        }
    }

    private void handleAnotarIdeia(Message message, long chatId, String idea) {
        log.info("📝 Comando 'anotar ideia' recebido: {}", sanitizeForLog(idea));
        if (idea.isEmpty()) {
            telegramFacade.enviarMensagem(chatId, BotMessages.IDEIA_DIGITE_APOS_COMANDO);
            return;
        }
        if (idea.startsWith(":") || idea.startsWith("：")) {
            idea = idea.substring(1).trim();
        }
        if (idea.isEmpty()) {
            telegramFacade.enviarMensagem(chatId, BotMessages.IDEIA_VAZIA);
            return;
        }

        User from = message.from();
        long userId = from.id();
        String userName = utils.buildFullName(from);
        String chatName = utils.getChatName(message);

        ideasLogger.saveIdea(userId, userName, chatId, idea, chatName);

        String adminMsg =
                String.format(
                        "💡 <b>Nova ideia</b>\n"
                                + "📝 <i>%s</i>\n"
                                + "👤 <b>Usuário:</b> %s\n"
                                + "📍 <b>Local:</b> %s\n"
                                + "🕒 %s",
                        utils.escapeHtml(idea),
                        utils.buildUserMention(from),
                        utils.escapeHtml(chatName),
                        LocalDateTime.now()
                                .format(
                                        DateTimeFormatter.ofPattern(
                                                BotMessages.FMT_DD_MM_YYYY_HH_MM)));
        telegramFacade.enviarMensagemHtml(ownerId, adminMsg);
        telegramFacade.enviarMensagemHtml(chatId, BotMessages.IDEIA_REGISTRADA);
    }

    private void handleBuscarFilme(long chatId, String nome) {
        if (nome.length() < 3) {
            telegramFacade.enviarMensagem(chatId, BotMessages.BUSCA_TERM_CURTO);
            return;
        }
        if (nome.length() > 100) {
            telegramFacade.enviarMensagem(chatId, BotMessages.BUSCA_TERM_LONGO);
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
            telegramFacade.enviarMensagem(chatId, "❌ " + BotMessages.FILME_NAO_ENCONTRADO);
            return;
        }

        if (busca.results().size() == 1) {
            enviarFilmeUnico(chatId, busca.results().get(0).id());
            return;
        }

        // Múltiplos resultados → enviar opções com botões
        enviarOpcoesDesambiguacao(chatId, busca.results());
    }

    private void enviarFilmeUnico(long chatId, Long movieId) {
        MovieOrchestrationResponse resposta = movieService.buscarPorId(movieId);
        log.info("✅ Filme único chatId={} movieId={}", chatId, movieId);
        exibirRespostaFilme(chatId, resposta);
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
        List<InlineKeyboardButton[]> rows = new ArrayList<>();
        List<InlineKeyboardButton> currentRow = new ArrayList<>();
        for (int i = 0; i < resultados.size() && i < 10; i++) {
            var filme = resultados.get(i);
            String ano =
                    (filme.releaseDate() != null && filme.releaseDate().length() >= 4)
                            ? " (" + filme.releaseDate().substring(0, 4) + ")"
                            : " (S/A)";
            currentRow.add(
                    new InlineKeyboardButton(filme.title() + ano).callbackData("id:" + filme.id()));
            if ((i + 1) % 2 == 0 || (i + 1) == resultados.size()) {
                rows.add(currentRow.toArray(new InlineKeyboardButton[0]));
                currentRow.clear();
            }
        }
        InlineKeyboardMarkup markup =
                new InlineKeyboardMarkup(rows.toArray(new InlineKeyboardButton[0][]));
        telegramFacade.enviarComBotoesHtml(
                chatId, "🧐 Encontrei vários resultados. Qual você quer?", markup);
    }

    private void handleEstreias(long chatId) {
        String response = weeklyReleasesService.getWeeklyReleasesMessage();
        telegramFacade.enviarMensagemHtml(chatId, response);
    }

    private void handleJogosCopa(long chatId, String rawText) {
        if (!worldcupEnabled) {
            telegramFacade.enviarMensagem(chatId, BotMessages.WORLD_CUP_FINISHED);
            return;
        }
        LocalDate today = LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE));
        worldCupSchedulerService.sendMatchesToChat(chatId, today);
    }

    private void handleResultados(long chatId, String rawText) {
        if (!worldcupEnabled) {
            telegramFacade.enviarMensagem(chatId, BotMessages.WORLD_CUP_FINISHED);
            return;
        }

        // Remove o prefixo "t1000 resultados" e limpa palavras comuns
        String param = rawText.replaceFirst("(?i)^t1000\s+resultados\s+", "").trim();
        String cleanedParam = param.replaceAll("(?i)\b(do|dia|de|da|as|os|dias)\b", " ").trim();

        LocalDate date;
        if (cleanedParam.isEmpty()) {
            date = LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE));
            log.info("📅 Comando 'resultados' sem data, usando hoje: {}", date);
        } else {
            date = parseDateParam(cleanedParam);
            log.info("📅 Comando 'resultados' com data: {} -> {}", cleanedParam, date);
        }

        if (date != null) {
            worldCupSchedulerService.sendResultsToChat(chatId, date);
        } else {
            telegramFacade.enviarMensagem(chatId, BotMessages.DATA_INVALIDA);
        }
    }

    // Utilitário de parse de data (copiado do controller original)
    private LocalDate parseDateParam(String param) {
        if (param == null || param.isBlank()) return null;
        LocalDate today = LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE));
        String lower = param.toLowerCase().trim();
        if (lower.equals("hoje") || lower.equals("de hoje")) return today;
        if (lower.equals("ontem") || lower.equals("de ontem")) return today.minusDays(1);

        String cleaned = param.replaceAll("(?i)\\b(do|dia|de|da|as|os|dias)\\b", " ").trim();

        Pattern pattern =
                Pattern.compile("\\b(\\d{1,2}[/-]\\d{2}(?:[/-]\\d{4})?|\\d{4}-\\d{2}-\\d{2})\\b");
        java.util.regex.Matcher matcher = pattern.matcher(cleaned);
        if (matcher.find()) {
            String dateStr = matcher.group(1).trim();
            try {
                if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    return LocalDate.parse(dateStr);
                }
                if (dateStr.matches("\\d{1,2}[/-]\\d{2}")) {
                    DateTimeFormatter fmt =
                            new DateTimeFormatterBuilder()
                                    .appendPattern(dateStr.contains("/") ? "dd/MM" : "dd-MM")
                                    .parseDefaulting(java.time.temporal.ChronoField.YEAR, 2026)
                                    .toFormatter();
                    return LocalDate.parse(dateStr, fmt);
                }
            } catch (DateTimeParseException ignored) {
                // fall through
            }
        }
        try {
            return LocalDate.parse(param);
        } catch (DateTimeParseException ignored) {
        }
        try {
            DateTimeFormatter fmt =
                    new DateTimeFormatterBuilder()
                            .appendPattern("dd/MM")
                            .parseDefaulting(java.time.temporal.ChronoField.YEAR, 2026)
                            .toFormatter();
            return LocalDate.parse(param, fmt);
        } catch (DateTimeParseException ignored) {
        }
        try {
            DateTimeFormatter fmt =
                    new DateTimeFormatterBuilder()
                            .appendPattern("dd-MM")
                            .parseDefaulting(java.time.temporal.ChronoField.YEAR, 2026)
                            .toFormatter();
            return LocalDate.parse(param, fmt);
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }
}
