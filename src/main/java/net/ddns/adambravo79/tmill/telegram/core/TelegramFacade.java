package net.ddns.adambravo79.tmill.telegram.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.concurrent.TimeUnit;

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
import net.ddns.adambravo79.tmill.util.LogSanitizer;
import okhttp3.OkHttpClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramFacade {

    private final TelegramBotExecutor executor;
    private final TelegramSafeExecutor safeExecutor;

    @Value("${telegram.bot.token}")
    private String botToken;

    // 🔥 NOVAS CONFIGURAÇÕES DE TIMEOUT
    @Value("${telegram.bot.client.connect-timeout:60}")
    private int connectTimeout;

    @Value("${telegram.bot.client.read-timeout:120}")
    private int readTimeout;

    @Value("${telegram.bot.client.write-timeout:120}")
    private int writeTimeout;

    private OkHttpClient httpClient;

    @PostConstruct
    public void init() {
        log.info("🔑 Token carregado: {}", maskToken(botToken));
        log.info(
                "⏱️ Timeouts configurados: connect={}s, read={}s, write={}s",
                connectTimeout,
                readTimeout,
                writeTimeout);

        // 🔥 Cria cliente HTTP com timeouts configurados
        this.httpClient =
                new OkHttpClient.Builder()
                        .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                        .readTimeout(readTimeout, TimeUnit.SECONDS)
                        .writeTimeout(writeTimeout, TimeUnit.SECONDS)
                        .build();
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
                this::enviarMensagem,
                () -> {
                    try {
                        // 1. Verifica se é uma URL HTTP/HTTPS
                        if (filePathOrUrl.startsWith("http://")
                                || filePathOrUrl.startsWith("https://")) {
                            sendMediaByUrl(chatId, filePathOrUrl, caption);
                            return;
                        }

                        // 2. Tenta interpretar como arquivo local
                        java.io.File file = new java.io.File(filePathOrUrl);
                        if (file.exists()) {
                            sendMediaByFile(chatId, file, caption);
                            return;
                        }

                        // 3. Fallback: assume que é um file_id ou URL inválida – trata como URL
                        log.debug("Tratando '{}' como file_id ou string genérica", filePathOrUrl);
                        sendMediaByUrl(chatId, filePathOrUrl, caption);

                    } catch (Exception e) {
                        log.warn(
                                "⚠️ Falha ao enviar mídia para chatId {}: {}. Enviando apenas"
                                        + " texto.",
                                LogSanitizer.sanitizeId(chatId),
                                LogSanitizer.sanitize(e.getMessage()));
                        enviarMensagem(chatId, caption);
                    }
                });
    }

    // Métodos auxiliares privados

    private void sendMediaByUrl(long chatId, String url, String caption) {
        String lower = url.toLowerCase();
        if (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".avi")) {
            executor.execute(new SendVideo(chatId, url).caption(caption).parseMode(ParseMode.HTML));
        } else if (lower.endsWith(".gif")) {
            executor.execute(
                    new SendAnimation(chatId, url).caption(caption).parseMode(ParseMode.HTML));
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")) {
            executor.execute(new SendPhoto(chatId, url).caption(caption).parseMode(ParseMode.HTML));
        } else {
            // Fallback genérico: tenta enviar como foto
            executor.execute(new SendPhoto(chatId, url).caption(caption).parseMode(ParseMode.HTML));
        }
    }

    private void sendMediaByFile(long chatId, java.io.File file, String caption) {
        String lower = file.getName().toLowerCase();
        if (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".avi")) {
            executor.execute(
                    new SendVideo(chatId, file).caption(caption).parseMode(ParseMode.HTML));
        } else if (lower.endsWith(".gif")) {
            executor.execute(
                    new SendAnimation(chatId, file).caption(caption).parseMode(ParseMode.HTML));
        } else if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".oga")) {
            executor.execute(
                    new SendAudio(chatId, file).caption(caption).parseMode(ParseMode.HTML));
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")) {
            executor.execute(
                    new SendPhoto(chatId, file).caption(caption).parseMode(ParseMode.HTML));
        } else {
            // Para outros tipos, usa SendDocument
            executor.execute(
                    new SendDocument(chatId, file).caption(caption).parseMode(ParseMode.HTML));
        }
    }

    // Fallback simples (sem parse mode)
    private void enviarFallback(long chatId, String texto) {
        try {
            executor.execute(new SendMessage(chatId, texto));
        } catch (Exception e) {
            log.error("Falha no fallback: {}", LogSanitizer.sanitize(e.getMessage()));
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
     * Baixa o arquivo usando a URL pública do Telegram. O
     * {@link TelegramBotExecutor} não expõe
     * {@code downloadFile}, então fazemos manualmente.
     */
    public byte[] downloadFile(File file) {
        String filePath = file.filePath();
        String url = "https://api.telegram.org/file/bot" + botToken + "/" + filePath;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            // 🔥 Usa os timeouts configurados
            conn.setConnectTimeout(connectTimeout * 1000);
            conn.setReadTimeout(readTimeout * 1000);
            try (InputStream is = conn.getInputStream()) {
                return is.readAllBytes();
            }
        } catch (IOException e) {
            throw new TelegramFileException("Erro ao baixar arquivo: " + filePath, e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Mascara um token para exibição em logs. Exibe apenas os 4 primeiros e 4
     * últimos caracteres.
     */
    private static String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "***";
        }
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
}
