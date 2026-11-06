/* (c) 2026 | 04/11/2026 */
package net.ddns.adambravo79.tmill.telegram.handler;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;

import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.service.BotAnalyticsService;
import net.ddns.adambravo79.tmill.service.IdeasLoggerService;
import net.ddns.adambravo79.tmill.telegram.core.GroupAuthorizationService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;
import net.ddns.adambravo79.tmill.telegram.util.TelegramUtils;
import net.ddns.adambravo79.tmill.util.LogSanitizer;

/**
 * Handler especializado para o comando {@code t1000 anotar ideia <texto>}.
 *
 * <h2>Responsabilidades</h2>
 *
 * <ol>
 *   <li>Extrair o texto da ideia após o prefixo.
 *   <li>Validar que a ideia não está vazia.
 *   <li>Persistir a ideia em dois destinos:
 *       <ul>
 *         <li>{@link IdeasLoggerService} — arquivo texto diário ({@code logs/ideas_YYYY-MM-DD.txt})
 *         <li>{@link BotAnalyticsService} — MongoDB ({@code user_ideas})
 *       </ul>
 *   <li>Notificar o {@code owner} via Telegram com os detalhes da ideia.
 *   <li>Confirmar o registro ao usuário que enviou o comando.
 * </ol>
 *
 * <h2>Formato aceito</h2>
 *
 * <pre>
 * t1000 anotar ideia: Melhorar o bot
 * t1000 anotar ideia Fazer café
 * t1000 anotar ideia： (chinês) — rejeitado como vazio
 * </pre>
 *
 * <p>O prefixo {@code :} ou {@code ：} (dois-pontos ASCII ou chinês) é removido opcionalmente
 * antes de processar o texto, permitindo que o usuário digite ambos os formatos.
 *
 * <h2>Feature flags</h2>
 *
 * <p>Esta funcionalidade <b>não possui</b> feature flag própria. Sempre está disponível.
 *
 * <h2>Métricas</h2>
 *
 * <p>Emite {@code comando_anotar_ideia} via {@link MetricsService} quando o comando é reconhecido
 * (mesmo em caso de erro de validação, para rastrear tentativas).
 */
@Slf4j
@Component
public class IdeaHandler extends AbstractCommandHandler {

    private static final String COMANDO_ANOTAR_IDEIA = "comando_anotar_ideia";

    /** Prefixo canônico do comando. */
    public static final String PREFIXO = "t1000 anotar ideia";

    private final IdeasLoggerService ideasLogger;
    private final BotAnalyticsService botAnalyticsService;
    private final MetricsService metricsService;

    @Value("${telegram.owner.id:0}")
    private long ownerId;

    public IdeaHandler(
            TelegramFacade telegramFacade,
            GroupAuthorizationService authService,
            TelegramUtils utils,
            IdeasLoggerService ideasLogger,
            BotAnalyticsService botAnalyticsService,
            MetricsService metricsService) {
        super(telegramFacade, authService, utils);
        this.ideasLogger = ideasLogger;
        this.botAnalyticsService = botAnalyticsService;
        this.metricsService = metricsService;
    }

    // =========================================================================
    // CONTRATO
    // =========================================================================

    @Override
    public String getPrefixo() {
        return PREFIXO;
    }

    @Override
    protected void handleCommand(Update update, String textoNormalizado) {
        Message message = extrairMensagem(update);
        String rawText = textoBruto(update);

        // Extrai a ideia usando o TEXTO BRUTO (preserva maiúsculas/acentos originais)
        String idea = extrairIdeia(rawText);

        // 1. Validação: ideia vazia ou nula
        if (idea == null || idea.isEmpty()) {
            sendPlain(chatId(update), BotMessages.IDEIA_DIGITE_APOS_COMANDO);
            metricsService.success(COMANDO_ANOTAR_IDEIA);
            return;
        }

        // 2. Remove dois-pontos inicial (ASCII ou chinês)
        idea = removerDoisPontos(idea);

        // 3. Validação: após remover dois-pontos, ainda tem conteúdo?
        if (idea.isEmpty()) {
            sendPlain(chatId(update), BotMessages.IDEIA_VAZIA);
            metricsService.success(COMANDO_ANOTAR_IDEIA);
            return;
        }

        // 4. Salva em ambos os destinos
        salvarIdeia(update, message, idea);

        // 5. Notifica owner (se configurado)
        notificarOwner(update, message, idea);

        // 6. Confirma ao usuário
        sendPlain(chatId(update), BotMessages.IDEIA_REGISTRADA);

        metricsService.success(COMANDO_ANOTAR_IDEIA);

        log.info(
                "💡 Ideia registrada: userId={} chatId={} chars={}",
                LogSanitizer.sanitizeId(userId(update)),
                LogSanitizer.sanitizeId(chatId(update)),
                idea.length());
    }

    // =========================================================================
    // EXTRAÇÃO E VALIDAÇÃO
    // =========================================================================

    /**
     * Extrai a ideia do texto bruto, removendo o prefixo (case-insensitive).
     *
     * <p>Usa o texto <b>bruto</b> (não o normalizado) para preservar maiúsculas, acentos e
     * pontuação — o usuário digita "Fazer café" e é isso que deve ser salvo, não "fazer café".
     *
     * <p>A regex {@code (?i)^t1000\s+anotar\s+ideia\s*} é ancorada no início, então um texto como
     * "minha ideia é t1000 anotar ideia X" NÃO é processado.
     *
     * @param rawText texto bruto do usuário
     * @return a ideia (pode ser vazia se o usuário só digitou o comando), ou {@code null} se o
     *     texto não começar com o prefixo
     */
    private String extrairIdeia(String rawText) {
        if (rawText == null) {
            return null;
        }
        String idea = rawText.replaceFirst("(?i)^t1000\\s+anotar\\s+ideia\\s*", "").trim();
        // Se a string não mudou, o prefixo não estava presente → não é nosso comando
        if (idea.equals(rawText)) {
            return null;
        }
        return idea;
    }

    /**
     * Remove o primeiro caractere se for dois-pontos (ASCII {@code :} ou chinês {@code ：}).
     *
     * <p>Ex.: {@code ":Fazer café"} → {@code "Fazer café"}, {@code "：Fazer café"} → {@code
     * "Fazer café"}.
     *
     * @param texto texto original (não-nulo)
     * @return texto sem dois-pontos inicial, com trim aplicado
     */
    private String removerDoisPontos(String texto) {
        if (texto.startsWith(":") || texto.startsWith("：")) {
            return texto.substring(1).trim();
        }
        return texto;
    }

    // =========================================================================
    // PERSISTÊNCIA
    // =========================================================================

    /**
     * Salva a ideia em ambos os destinos (arquivo + MongoDB).
     *
     * <p>Cada salvamento é independente: se o arquivo falhar, o MongoDB ainda é tentado (e
     * vice-versa). Erros são logados mas não propagados — o usuário já recebeu o feedback de
     * sucesso assim que {@code salvarIdeia} retornar.
     */
    private void salvarIdeia(Update update, Message message, String idea) {
        User from = usuario(update);
        long userId = from.id();
        String userName = userName(update);
        long chatId = chatId(update);
        String chatName = utils.getChatName(message);

        // 1. Arquivo texto (diário)
        try {
            ideasLogger.saveIdea(userId, userName, chatId, idea, chatName);
        } catch (Exception e) {
            log.error(
                    "❌ Falha ao salvar ideia em arquivo (userId={})",
                    LogSanitizer.sanitizeId(userId),
                    e);
        }

        // 2. MongoDB
        try {
            botAnalyticsService.salvarIdeia(
                    userId, idea, List.of("telegram", "ideia"), "BACKLOG_TECNICO");
        } catch (Exception e) {
            log.error(
                    "❌ Falha ao salvar ideia no MongoDB (userId={})",
                    LogSanitizer.sanitizeId(userId),
                    e);
        }
    }

    // =========================================================================
    // NOTIFICAÇÃO DO OWNER
    // =========================================================================

    /**
     * Envia a ideia para o owner via Telegram, com formatação HTML.
     *
     * <p>Se {@code ownerId} for 0 (não configurado), não envia nada.
     */
    private void notificarOwner(Update update, Message message, String idea) {
        if (ownerId == 0) {
            log.debug("ownerId não configurado — pulando notificação");
            return;
        }

        String chatName = utils.getChatName(message);
        String timestamp =
                LocalDateTime.now(BRAZIL_ZONE)
                        .format(DateTimeFormatter.ofPattern(BotMessages.FMT_DD_MM_YYYY_HH_MM));

        String adminMsg =
                """
                💡 <b>Nova ideia</b>
                📝 <i>%s</i>
                👤 <b>Usuário:</b> %s
                📍 <b>Local:</b> %s
                🕒 %s
                """
                        .formatted(
                                utils.escapeHtml(idea),
                                utils.buildUserMention(usuario(update)),
                                utils.escapeHtml(chatName),
                                timestamp);

        sendHtml(ownerId, adminMsg);
    }
}
