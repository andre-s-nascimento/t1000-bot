/* (c) 2026 | 04/11/2026 */
package net.ddns.adambravo79.tmill.telegram.handler;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.telegram.core.GroupAuthorizationService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.TelegramUtils;
import net.ddns.adambravo79.tmill.util.LogSanitizer;

/**
 * Superclasse abstrata para todos os handlers de comandos do bot.
 *
 * <h2>Objetivo</h2>
 *
 * <p>Centraliza responsabilidades comuns a todos os handlers, eliminando duplicação e garantindo
 * que nenhum comando seja executado sem passar pelas verificações obrigatórias.
 *
 * <h2>Responsabilidades centralizadas</h2>
 *
 * <ul>
 *   <li><b>Autorização:</b> to do handler verifica {@link GroupAuthorizationService#isAuthorized}
 *       antes de executar qualquer lógica de negócio.
 *   <li><b>Extração de contexto:</b> métodos utilitários para obter {@code chatId}, {@code userId},
 *       {@code userName}, texto normalizado e mensagem.
 *   <li><b>Envio de mensagens:</b> wrappers seguros para {@code enviarMensagemHtml},
 *       {@code enviarMensagem}, {@code enviarComBotoesHtml} e {@code enviarFotoHtml} que tratam
 *       erros HTTP, de rede e runtime.
 *   <li><b>Tratamento de erros:</b> wrapper {@link #executarComProtecao} que captura exceções,
 *       loga e (opcionalmente) notifica o usuário.
 * </ul>
 *
 * <h2>Contrato para subclasses</h2>
 *
 * <ol>
 *   <li>Anotar-se com {@code @Component}.
 *   <li>Chamar {@link #processarComando} (ou {@link #executarComProtecao}) como ponto de entrada.
 *   <li>Implementar {@link #handleCommand}.
 *   <li>Sobrescrever {@link #getPrefixo} para indicar qual prefixo de texto ativa o handler.
 * </ol>
 *
 * <h2>Thread-safety</h2>
 *
 * <p>As dependências injetadas são singletons stateless (ou thread-safe). Nenhum estado mutável é
 * mantido nesta classe, portanto é segura para uso concorrente em virtual threads.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractCommandHandler {

    /** Limite máximo de caracteres por mensagem do Telegram (com margem de segurança). */
    protected static final int TELEGRAM_MSG_LIMIT = 4000;

    /** Timezone de negócio (Brasília). */
    protected static final ZoneId BRAZIL_ZONE = ZoneId.of(BotMessages.BRAZIL_ZONE);

    protected final TelegramFacade telegramFacade;
    protected final GroupAuthorizationService authService;
    protected final TelegramUtils utils;

    // =========================================================================
    // CONTRATO PARA SUBCLASSES
    // =========================================================================

    public abstract String getPrefixo();

    protected abstract void handleCommand(Update update, String textoNormalizado);

    // =========================================================================
    // PONTO DE ENTRADA (Template Method)
    // =========================================================================

    public void processarComando(Update update) {
        Message message = extrairMensagem(update);
        if (message == null) {
            log.debug("processarComando chamado com update/message nulo — ignorando.");
            return;
        }

        if (!authService.isAuthorized(update)) {
            log.warn(
                    "⛔ [{}] Update recusado por autorização (chatId={})",
                    getClass().getSimpleName(),
                    message.chat().id());
            return;
        }

        String textoNormalizado = normalizarTexto(message.text());
        executarComProtecao(update, textoNormalizado);
    }

    protected void executarComProtecao(Update update, String textoNormalizado) {
        long chatId = chatId(update);
        try {
            handleCommand(update, textoNormalizado);
        } catch (HttpClientErrorException e) {
            log.error(
                    "❌ [{}] Erro HTTP do Telegram (chatId={}, status={})",
                    getClass().getSimpleName(),
                    LogSanitizer.sanitizeId(chatId),
                    e.getStatusCode(),
                    e);
            notificarErro(chatId, mensagemErroUsuario());
        } catch (ResourceAccessException e) {
            log.error(
                    "❌ [{}] Falha de conectividade (chatId={})",
                    getClass().getSimpleName(),
                    LogSanitizer.sanitizeId(chatId),
                    e);
            notificarErro(chatId, mensagemErroUsuario());
        } catch (RuntimeException e) {
            log.error(
                    "❌ [{}] Erro inesperado (chatId={})",
                    getClass().getSimpleName(),
                    LogSanitizer.sanitizeId(chatId),
                    e);
            notificarErro(chatId, mensagemErroUsuario());
        }
    }

    protected String mensagemErroUsuario() {
        return BotMessages.ERRO_GENERICO;
    }

    // =========================================================================
    // EXTRAÇÃO DE CONTEXTO
    // =========================================================================

    protected Message extrairMensagem(Update update) {
        return update != null ? update.message() : null;
    }

    protected long chatId(Update update) {
        return update.message().chat().id();
    }

    protected User usuario(Update update) {
        return update.message().from();
    }

    protected long userId(Update update) {
        return usuario(update).id();
    }

    protected String userName(Update update) {
        return utils.buildFullName(usuario(update));
    }

    protected boolean isGroupChat(Update update) {
        Chat.Type type = update.message().chat().type();
        return type == Chat.Type.group || type == Chat.Type.supergroup;
    }

    protected String textoBruto(Update update) {
        String text = update.message().text();
        return text != null ? text : "";
    }

    protected String normalizarTexto(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.trim().toLowerCase();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized
                .replace("t-1000", "t1000")
                .replaceAll("\\s+", " ") // ← colapsa espaços internos
                .trim();
    }

    protected String argumentoApos(String textoNormalizado, String prefixo) {
        if (textoNormalizado == null || prefixo == null) {
            return "";
        }
        if (!textoNormalizado.startsWith(prefixo)) {
            return "";
        }
        return textoNormalizado.substring(prefixo.length()).trim();
    }

    // =========================================================================
    // ENVIO SEGURO (wrappers)
    // =========================================================================

    protected void sendHtml(long chatId, String texto) {
        if (texto == null || texto.isBlank()) {
            log.warn(
                    "[{}] sendHtml chamado com texto vazio (chatId={})",
                    getClass().getSimpleName(),
                    LogSanitizer.sanitizeId(chatId));
            return;
        }
        try {
            if (texto.length() > TELEGRAM_MSG_LIMIT) {
                utils.splitMessage(texto, TELEGRAM_MSG_LIMIT)
                        .forEach(parte -> telegramFacade.enviarMensagemHtml(chatId, parte));
            } else {
                telegramFacade.enviarMensagemHtml(chatId, texto);
            }
        } catch (HttpClientErrorException e) {
            log.error(
                    "❌ [{}] Erro HTTP ao enviar mensagem HTML (chatId={}, status={})",
                    getClass().getSimpleName(),
                    LogSanitizer.sanitizeId(chatId),
                    e.getStatusCode(),
                    e);
        } catch (ResourceAccessException e) {
            log.error(
                    "❌ [{}] Falha de conectividade ao enviar mensagem (chatId={})",
                    getClass().getSimpleName(),
                    LogSanitizer.sanitizeId(chatId),
                    e);
        } catch (RuntimeException e) {
            log.error(
                    "❌ [{}] Erro inesperado ao enviar mensagem HTML (chatId={})",
                    getClass().getSimpleName(),
                    LogSanitizer.sanitizeId(chatId),
                    e);
        }
    }

    protected void sendPlain(long chatId, String texto) {
        if (texto == null || texto.isBlank()) {
            log.warn(
                    "[{}] sendPlain chamado com texto vazio (chatId={})",
                    getClass().getSimpleName(),
                    LogSanitizer.sanitizeId(chatId));
            return;
        }
        try {
            if (texto.length() > TELEGRAM_MSG_LIMIT) {
                utils.splitMessage(texto, TELEGRAM_MSG_LIMIT)
                        .forEach(parte -> telegramFacade.enviarMensagem(chatId, parte));
            } else {
                telegramFacade.enviarMensagem(chatId, texto);
            }
        } catch (HttpClientErrorException e) {
            log.error(
                    "❌ [{}] Erro HTTP ao enviar mensagem plain (chatId={}, status={})",
                    getClass().getSimpleName(),
                    LogSanitizer.sanitizeId(chatId),
                    e.getStatusCode(),
                    e);
        } catch (ResourceAccessException e) {
            log.error(
                    "❌ [{}] Falha de conectividade ao enviar mensagem plain (chatId={})",
                    getClass().getSimpleName(),
                    LogSanitizer.sanitizeId(chatId),
                    e);
        } catch (RuntimeException e) {
            log.error(
                    "❌ [{}] Erro inesperado ao enviar mensagem plain (chatId={})",
                    getClass().getSimpleName(),
                    LogSanitizer.sanitizeId(chatId),
                    e);
        }
    }

    protected void sendWithButtons(long chatId, String texto, InlineKeyboardMarkup markup) {
        try {
            telegramFacade.enviarComBotoesHtml(chatId, texto, markup);
        } catch (HttpClientErrorException e) {
            log.error(
                    "❌ [{}] Erro HTTP ao enviar mensagem com botões (chatId={}, status={})",
                    getClass().getSimpleName(),
                    LogSanitizer.sanitizeId(chatId),
                    e.getStatusCode(),
                    e);
        } catch (ResourceAccessException e) {
            log.error(
                    "❌ [{}] Falha de conectividade ao enviar botões (chatId={})",
                    getClass().getSimpleName(),
                    LogSanitizer.sanitizeId(chatId),
                    e);
        } catch (RuntimeException e) {
            // Cobre exceções inesperadas do TelegramFacade (ex.: parsing interno)
            log.error(
                    "❌ [{}] Erro inesperado ao enviar mensagem com botões (chatId={})",
                    getClass().getSimpleName(),
                    LogSanitizer.sanitizeId(chatId),
                    e);
        }
    }

    protected void sendPhoto(long chatId, String fotoUrl, String legenda) {
        try {
            telegramFacade.enviarFotoHtml(chatId, fotoUrl, legenda);
        } catch (HttpClientErrorException e) {
            log.error(
                    "❌ [{}] Erro HTTP ao enviar foto (chatId={}, status={})",
                    getClass().getSimpleName(),
                    LogSanitizer.sanitizeId(chatId),
                    e.getStatusCode(),
                    e);
        } catch (ResourceAccessException e) {
            log.error(
                    "❌ [{}] Falha de conectividade ao enviar foto (chatId={})",
                    getClass().getSimpleName(),
                    LogSanitizer.sanitizeId(chatId),
                    e);
        } catch (RuntimeException e) {
            log.error(
                    "❌ [{}] Erro inesperado ao enviar foto (chatId={})",
                    getClass().getSimpleName(),
                    LogSanitizer.sanitizeId(chatId),
                    e);
        }
    }

    // =========================================================================
    // HELPERS DE DATA/HORA
    // =========================================================================

    protected LocalDate hoje() {
        return LocalDate.now(BRAZIL_ZONE);
    }

    protected LocalTime agora() {
        return LocalTime.now(BRAZIL_ZONE);
    }

    protected boolean isValidUrl(String url) {
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

    // =========================================================================
    // HELPERS PRIVADOS
    // =========================================================================

    private void notificarErro(long chatId, String mensagem) {
        if (mensagem == null || mensagem.isBlank()) {
            return;
        }
        try {
            telegramFacade.enviarMensagem(chatId, mensagem);
        } catch (Exception e) {
            log.warn(
                    "Falha ao notificar usuário sobre erro (chatId={}): {}",
                    LogSanitizer.sanitizeId(chatId),
                    e.getMessage());
        }
    }
}
