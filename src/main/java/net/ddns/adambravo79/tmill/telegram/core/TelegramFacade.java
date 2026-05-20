/* (c) 2026 | 19/05/2026 */
package net.ddns.adambravo79.tmill.telegram.core;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Fachada para interação com a API do Telegram.
 *
 * <p>Principais responsabilidades: - Enviar mensagens de texto com suporte a Markdown. - Enviar
 * fotos com legenda. - Enviar mensagens com botões interativos. - Obter e baixar arquivos do
 * Telegram.
 *
 * <p>Utiliza {@link TelegramSafeExecutor} para garantir execução segura com fallback em caso de
 * falha.
 */
@Slf4j
@Component
public class TelegramFacade {

    private static final String HTML = "HTML";

    private final TelegramClient telegramClient;
    private final TelegramSafeExecutor safeExecutor;

    public TelegramFacade(TelegramClient telegramClient, TelegramSafeExecutor safeExecutor) {
        this.telegramClient = telegramClient;
        this.safeExecutor = safeExecutor;
    }

    /** Envia mensagem de fallback sem parseMode, usada em caso de falha. */
    private void enviarFallback(long chatId, String mensagem) throws TelegramApiException {
        var fallback = SendMessage.builder().chatId(String.valueOf(chatId)).text(mensagem).build();

        telegramClient.execute(fallback);
    }

    /**
     * Envia uma mensagem de texto com suporte a Markdown.
     *
     * @param chatId identificador do chat.
     * @param texto conteúdo da mensagem.
     */
    public void enviarMensagem(long chatId, String texto) {
        safeExecutor.run(
                chatId,
                this::enviarFallback,
                () -> {
                    var msg =
                            SendMessage.builder()
                                    .chatId(String.valueOf(chatId))
                                    .text(texto)
                                    .parseMode(HTML)
                                    .build();

                    telegramClient.execute(msg);
                });
    }

    /**
     * Envia uma foto com legenda.
     *
     * @param chatId identificador do chat.
     * @param url URL da imagem.
     * @param legenda legenda da foto.
     */
    public void enviarFoto(long chatId, String url, String legenda) {
        log.debug("Enviando foto para chatId={} com legenda (escapada?): {}", chatId, legenda);
        safeExecutor.run(
                chatId,
                this::enviarFallback,
                () -> {
                    var photo =
                            SendPhoto.builder()
                                    .chatId(String.valueOf(chatId))
                                    .photo(new InputFile(url))
                                    .caption(legenda)
                                    // .parseMode(MARKDOWN)
                                    .build();

                    telegramClient.execute(photo);
                });
    }

    /**
     * Envia uma mensagem com botões interativos.
     *
     * @param chatId identificador do chat.
     * @param texto conteúdo da mensagem.
     * @param markup teclado inline com botões.
     */
    public void enviarComBotoes(long chatId, String texto, InlineKeyboardMarkup markup) {
        safeExecutor.run(
                chatId,
                this::enviarFallback,
                () -> {
                    var msg =
                            SendMessage.builder()
                                    .chatId(String.valueOf(chatId))
                                    .text(texto)
                                    .replyMarkup(markup)
                                    .parseMode(HTML)
                                    .build();

                    telegramClient.execute(msg);
                });
    }

    /**
     * Obtém metadados de um arquivo do Telegram.
     *
     * @param getFile requisição de arquivo.
     * @return metadados do arquivo.
     * @throws TelegramApiException em caso de falha.
     */
    public org.telegram.telegrambots.meta.api.objects.File getFile(GetFile getFile)
            throws TelegramApiException {
        return telegramClient.execute(getFile);
    }

    /**
     * Faz o download de um arquivo do Telegram para o sistema local.
     *
     * @param tgFile metadados do arquivo.
     * @return arquivo baixado.
     * @throws TelegramApiException em caso de falha.
     */
    public java.io.File downloadFile(org.telegram.telegrambots.meta.api.objects.File tgFile)
            throws TelegramApiException {
        return telegramClient.downloadFile(tgFile);
    }

    // NOVO: edita uma mensagem existente (útil para remover botões após clique)
    public void editarMensagem(long chatId, int messageId, String novoTexto) {
        safeExecutor.run(
                chatId,
                this::enviarFallback,
                () -> {
                    var editMsg =
                            EditMessageText.builder()
                                    .chatId(String.valueOf(chatId))
                                    .messageId(messageId)
                                    .text(novoTexto)
                                    .build();
                    telegramClient.execute(editMsg);
                });
    }

    // NOVO: responde a um callback query (para evitar timeout e dar feedback visual)
    public void answerCallbackQuery(String callbackQueryId, String mensagem, boolean showAlert) {
        safeExecutor.run(
                0L,
                (id, msg) ->
                        log.debug(
                                "Fallback silencioso para answerCallbackQuery (callback expirado):"
                                        + " {}",
                                msg),
                () -> {
                    try {
                        var answer =
                                AnswerCallbackQuery.builder()
                                        .callbackQueryId(callbackQueryId)
                                        .text(mensagem)
                                        .showAlert(showAlert)
                                        .build();
                        telegramClient.execute(answer);
                    } catch (TelegramApiException e) {
                        String errMsg = e.getMessage();
                        if (errMsg != null && errMsg.contains("query is too old")) {
                            log.debug(
                                    "Callback query expirado – nenhuma ação necessária. queryId={}",
                                    callbackQueryId);
                            return; // não repassa exceção
                        }
                        throw e; // outras exceções vão para o safeExecutor
                    }
                });
    }

    // Novo:
    public void enviarMensagemSemMarkdown(long chatId, String texto) {
        safeExecutor.run(
                chatId,
                this::enviarFallback,
                () -> {
                    var msg =
                            SendMessage.builder()
                                    .chatId(String.valueOf(chatId))
                                    .text(texto)
                                    // sem .parseMode
                                    .build();
                    telegramClient.execute(msg);
                });
    }

    public void enviarComBotoesSemParse(long chatId, String texto, InlineKeyboardMarkup markup) {
        safeExecutor.run(
                chatId,
                this::enviarFallback,
                () -> {
                    var msg =
                            SendMessage.builder()
                                    .chatId(String.valueOf(chatId))
                                    .text(texto)
                                    .replyMarkup(markup)
                                    .build();
                    telegramClient.execute(msg);
                });
    }

    public void enviarMensagemHtml(long chatId, String texto) {
        safeExecutor.run(
                chatId,
                this::enviarFallback,
                () -> {
                    var msg =
                            SendMessage.builder()
                                    .chatId(String.valueOf(chatId))
                                    .text(texto)
                                    .parseMode(HTML)
                                    .build();
                    telegramClient.execute(msg);
                });
    }

    // NOVO: enviar foto com legenda em HTML
    public void enviarFotoHtml(long chatId, String url, String legenda) {
        safeExecutor.run(
                chatId,
                this::enviarFallback,
                () -> {
                    var photo =
                            SendPhoto.builder()
                                    .chatId(String.valueOf(chatId))
                                    .photo(new InputFile(url))
                                    .caption(legenda)
                                    .parseMode(HTML) // ← ESSENCIAL
                                    .build();
                    telegramClient.execute(photo);
                });
    }

    // NOVO: enviar mensagem com botões em HTML
    public void enviarComBotoesHtml(long chatId, String texto, InlineKeyboardMarkup markup) {
        safeExecutor.run(
                chatId,
                this::enviarFallback,
                () -> {
                    var msg =
                            SendMessage.builder()
                                    .chatId(String.valueOf(chatId))
                                    .text(texto)
                                    .replyMarkup(markup)
                                    .parseMode(HTML)
                                    .build();
                    telegramClient.execute(msg);
                });
    }

    public void editarMensagemHtml(long chatId, int messageId, String novoTexto) {
        safeExecutor.run(
                chatId,
                this::enviarFallback,
                () -> {
                    var editMsg =
                            EditMessageText.builder()
                                    .chatId(String.valueOf(chatId))
                                    .messageId(messageId)
                                    .text(novoTexto)
                                    .parseMode(HTML)
                                    .build();
                    telegramClient.execute(editMsg);
                });
    }

    // net.ddns.adambravo79.tmill.telegram.core.TelegramFacade.java
    public void enviarAnimacao(long chatId, String animationUrl, String caption) {
        safeExecutor.run(
                chatId,
                this::enviarFallback,
                () -> {
                    // Cria o objeto de animação
                    SendAnimation animation =
                            SendAnimation.builder()
                                    .chatId(String.valueOf(chatId))
                                    .animation(new InputFile(animationUrl))
                                    .caption(caption)
                                    .parseMode("HTML") // ou "MarkdownV2", conforme sua preferência
                                    .build();
                    telegramClient.execute(animation);
                });
    }
}
