/* (c) 2026 | 04/11/2026 */
package net.ddns.adambravo79.tmill.telegram.handler;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.model.Update;

import io.ksilisk.telegrambot.core.handler.update.command.CommandUpdateHandler;
import io.ksilisk.telegrambot.core.update.Updates;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.telegram.core.GroupAuthorizationService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.TelegramUtils;
import net.ddns.adambravo79.tmill.util.LogSanitizer;

/**
 * Handler para os comandos de ajuda e boas-vindas.
 *
 * <h2>Comandos tratados</h2>
 *
 * <ul>
 *   <li>{@code /help} e {@code /ajuda} → central de ajuda formatada
 *   <li>{@code /start} → saudação personalizada com o nome do usuário
 * </ul>
 *
 * <h2>Dupla interface</h2>
 *
 * <p>Este handler implementa <b>duas interfaces</b>:
 *
 * <ol>
 *   <li>{@link CommandUpdateHandler} (ksilisk) — para comandos com barra ({@code /help},
 *       {@code /start}, {@code /ajuda}). O ksilisk roteia automaticamente quando o texto começa
 *       com {@code /}.
 *   <li>{@link AbstractCommandHandler} (nossa hierarquia) — para casos em que o comando chega
 *       como texto sem barra (ex.: usuário digita "start" puro). O roteador {@code CommandHandler}
 *       delega para {@link #processarComando}.
 * </ol>
 *
 * <p>Ambos os caminhos executam a mesma lógica via {@link #enviarAjuda} e {@link #enviarWelcome}.
 *
 * <h2>Autorização</h2>
 *
 * <p>A verificação de autorização é feita em <b>ambos</b> os caminhos:
 *
 * <ul>
 *   <li>No caminho ksilisk: via {@link #handle} (que chama {@code authService.isAuthorized}).
 *   <li>No caminho do roteador: via {@link AbstractCommandHandler#processarComando} (superclasse).
 * </ul>
 *
 * <h2>Sem métricas</h2>
 *
 * <p>Este handler <b>não emite</b> métricas de comando. {@code /help} e {@code /start} são
 * comandos de "sistema" (não representam uso de feature), então poluir o dashboard com eles não
 * agrega valor.
 */
@Slf4j
@Component
public class HelpCommandHandler extends AbstractCommandHandler implements CommandUpdateHandler {

    /** Comandos com barra tratados pelo ksilisk. */
    private static final Set<String> COMANDOS_BARRA = Set.of("/help", "/ajuda", "/start");

    /** Prefixo canônico para o roteador (quando chega como texto sem barra). */
    public static final String PREFIXO_START = "start";

    public HelpCommandHandler(
            TelegramFacade telegramFacade,
            GroupAuthorizationService authService,
            TelegramUtils utils) {
        super(telegramFacade, authService, utils);
    }

    // =========================================================================
    // CONTRATO Ksilisk (comandos com barra)
    // =========================================================================

    @Override
    public Set<String> commands() {
        return COMANDOS_BARRA;
    }

    @Override
    public void handle(Update update) {
        // O ksilisk já validou que é um comando com barra
        if (!authService.isAuthorized(update)) {
            log.warn(
                    "⛔ [HelpCommandHandler] Update recusado por autorização (chatId={})",
                    LogSanitizer.sanitizeId(Updates.chatId(update)));
            return;
        }

        String texto = textoBruto(update).trim().toLowerCase();

        if (texto.equals("/start") || texto.equals(PREFIXO_START)) {
            enviarWelcome(update);
        } else {
            enviarAjuda(update);
        }
    }

    // =========================================================================
    // CONTRATO AbstractCommandHandler (texto sem barra, via roteador)
    // =========================================================================

    @Override
    public String getPrefixo() {
        return PREFIXO_START;
    }

    @Override
    protected void handleCommand(Update update, String textoNormalizado) {
        // Chamado pelo roteador quando o texto (normalizado) começa com "start"
        // (ex.: usuário digitou "Start" ou "START" sem barra)
        if (textoNormalizado.equals(PREFIXO_START) || textoNormalizado.startsWith("start ")) {
            enviarWelcome(update);
        } else {
            // Comando veio como texto mas não é exatamente "start" — trata como help
            enviarAjuda(update);
        }
    }

    // =========================================================================
    // AÇÕES COMPARTILHADAS
    // =========================================================================

    /**
     * Envia a saudação de boas-vindas (usada por {@code /start}).
     *
     * <p>Personaliza a mensagem com o primeiro nome do usuário (escapado para HTML).
     *
     * @param update update do Telegram
     */
    public void handleWelcome(Update update) {
        if (!authService.isAuthorized(update)) {
            log.warn(
                    "⛔ [HelpCommandHandler.handleWelcome] Update recusado por autorização"
                            + " (chatId={})",
                    LogSanitizer.sanitizeId(chatId(update)));
            return;
        }
        enviarWelcome(update);
    }

    /**
     * Envia a central de ajuda.
     *
     * @param update update do Telegram
     */
    public void handleHelp(Update update) {
        if (!authService.isAuthorized(update)) {
            log.warn(
                    "⛔ [HelpCommandHandler.handleHelp] Update recusado por autorização"
                            + " (chatId={})",
                    LogSanitizer.sanitizeId(chatId(update)));
            return;
        }
        enviarAjuda(update);
    }

    // =========================================================================
    // IMPLEMENTAÇÃO PRIVADA
    // =========================================================================

    private void enviarWelcome(Update update) {
        long chatId = chatId(update);
        String firstName = usuario(update).firstName();
        if (firstName == null || firstName.isBlank()) {
            firstName = "amigo(a)";
        }

        String saudacao = buildWelcomeMessage(firstName);
        sendHtml(chatId, saudacao);
    }

    private void enviarAjuda(Update update) {
        sendHtml(chatId(update), BotMessages.HELP_TEXT);
    }

    /**
     * Monta a mensagem de boas-vindas com o nome do usuário.
     *
     * <p>Extraído para método separado para permitir testes diretos da formatação, sem depender de
     * mocks do TelegramFacade.
     *
     * @param firstName primeiro nome do usuário (nunca nulo/vazio; fallback "amigo(a)")
     * @return mensagem HTML formatada
     */
    String buildWelcomeMessage(String firstName) {
        String nomeEscapado = utils.escapeHtml(firstName);
        return """
        🤖 Olá, <b>%s</b>! Eu sou o <b>Tmill Bot</b>, o robô de metal líquido das transcrições e buscas.

        📌 <b>O que posso fazer?</b>
        🎬 Buscar filmes: <code>t1000 buscar &lt;nome do filme&gt;</code>
        🍿 Consultar Sala VIP: <code>t1000 salavip 119</code> ou <code>t1000 salavip tem filme &lt;nome&gt;</code>
        🎙️ Transcrever áudios: envie uma mensagem de voz ou áudio.

        💡 <b>Em grupos/canais:</b>
        Ao enviar um áudio, aparecerão botões para você escolher a transcrição bruta ou refinada.

        💡 Anotar sugestões: <code>t1000 anotar ideia Achar os pais adotivos do John Connor...</code>
        🎂 Registrar aniversário: <code>t1000 anotar aniversário 05/10</code>

        Desenvolvido com 🧠 e ☕ Java 21 + Spring Boot.
        """
                .formatted(nomeEscapado);
    }
}
