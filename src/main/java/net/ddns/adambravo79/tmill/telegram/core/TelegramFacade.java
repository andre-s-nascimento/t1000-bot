package net.ddns.adambravo79.tmill.telegram.core;

import java.io.IOException;
import java.net.URL;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.model.File;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.*;
import com.pengrad.telegrambot.response.GetFileResponse;

import io.ksilisk.telegrambot.core.executor.TelegramBotExecutor;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.telegram.exception.TelegramFileException;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramFacade {

    private final TelegramBotExecutor executor;
    private final TelegramSafeExecutor safeExecutor;

    @Value("${telegram.bot.token}")
    private String botToken;

    @PostConstruct
    public void init() {
        log.info("🔑 Token carregado: {}", botToken.substring(0, 4) + "...");
    }

    public void enviarMensagem(long chatId, String texto) {
        safeExecutor.run(
                chatId,
                this::enviarFallback,
                () -> executor.execute(new SendMessage(chatId, texto)));
    }

    public void enviarMensagemHtml(long chatId, String texto) {
        safeExecutor.run(
                chatId,
                this::enviarFallback,
                () -> executor.execute(new SendMessage(chatId, texto).parseMode(ParseMode.HTML)));
    }

    public void enviarFotoHtml(long chatId, String url, String legenda) {
        safeExecutor.run(
                chatId,
                this::enviarFallback,
                () ->
                        executor.execute(
                                new SendPhoto(chatId, url)
                                        .caption(legenda)
                                        .parseMode(ParseMode.HTML)));
    }

    public void enviarComBotoesHtml(long chatId, String texto, InlineKeyboardMarkup markup) {
        safeExecutor.run(
                chatId,
                this::enviarFallback,
                () ->
                        executor.execute(
                                new SendMessage(chatId, texto)
                                        .parseMode(ParseMode.HTML)
                                        .replyMarkup(markup)));
    }

    public void editarMensagemHtml(long chatId, int messageId, String novoTexto) {
        safeExecutor.run(
                chatId,
                this::enviarFallback,
                () ->
                        executor.execute(
                                new EditMessageText(chatId, messageId, novoTexto)
                                        .parseMode(ParseMode.HTML)));
    }

    public void editarMensagem(long chatId, int messageId, String novoTexto) {
        safeExecutor.run(
                chatId,
                this::enviarFallback,
                () -> executor.execute(new EditMessageText(chatId, messageId, novoTexto)));
    }

    public void answerCallbackQuery(String callbackQueryId, String mensagem, boolean showAlert) {
        safeExecutor.run(
                0L,
                (id, msg) -> log.debug("Fallback para answerCallbackQuery"),
                () ->
                        executor.execute(
                                new AnswerCallbackQuery(callbackQueryId)
                                        .text(mensagem)
                                        .showAlert(showAlert)));
    }

    public void enviarMidia(long chatId, String filePathOrUrl, String caption) {
        safeExecutor.run(
                chatId,
                (id, msg) -> enviarMensagem(id, msg),
                () -> {
                    try {
                        String lower = filePathOrUrl.toLowerCase();
                        if (lower.endsWith(".mp4")
                                || lower.endsWith(".mov")
                                || lower.endsWith(".avi")) {
                            executor.execute(
                                    new SendVideo(chatId, filePathOrUrl)
                                            .caption(caption)
                                            .parseMode(ParseMode.HTML));
                        } else if (lower.endsWith(".gif")) {
                            executor.execute(
                                    new SendAnimation(chatId, filePathOrUrl)
                                            .caption(caption)
                                            .parseMode(ParseMode.HTML));
                        } else if (lower.endsWith(".jpg")
                                || lower.endsWith(".jpeg")
                                || lower.endsWith(".png")) {
                            executor.execute(
                                    new SendPhoto(chatId, filePathOrUrl)
                                            .caption(caption)
                                            .parseMode(ParseMode.HTML));
                        } else {
                            // Se não reconhecer, tenta como foto (fallback)
                            executor.execute(
                                    new SendPhoto(chatId, filePathOrUrl)
                                            .caption(caption)
                                            .parseMode(ParseMode.HTML));
                        }
                    } catch (Exception e) {
                        log.warn(
                                "⚠️ Falha ao enviar mídia para chatId {}: {}. Enviando apenas"
                                        + " texto.",
                                chatId,
                                e.getMessage());
                        enviarMensagem(chatId, caption);
                    }
                });
    }

    // Fallback simples (sem parse mode)
    private void enviarFallback(long chatId, String texto) {
        try {
            executor.execute(new SendMessage(chatId, texto));
        } catch (Exception e) {
            log.error("Falha no fallback", e);
        }
    }

    // Obtém metadados do arquivo
    public File getFile(String fileId) {
        GetFile getFile = new GetFile(fileId);
        GetFileResponse response = executor.execute(getFile);
        if (response.isOk()) {
            return response.file();
        }
        throw new TelegramFileException("Falha ao obter arquivo: " + response.description(), null);
    }

    /**
     * Baixa o arquivo usando a URL pública do Telegram. O {@link TelegramBotExecutor} não expõe
     * {@code downloadFile}, então fazemos manualmente.
     */
    public byte[] downloadFile(File file) {
        String filePath = file.filePath();
        String url = "https://api.telegram.org/file/bot" + botToken + "/" + filePath;
        try (var is = new URL(url).openStream()) {
            return is.readAllBytes();
        } catch (IOException e) {
            throw new TelegramFileException("Erro ao baixar arquivo: " + filePath, e);
        }
    }
}
