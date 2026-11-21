/* (c) 2026 */
package net.ddns.adambravo79.tmill.telegram.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.model.File;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.*;
import com.pengrad.telegrambot.response.BaseResponse;
import com.pengrad.telegrambot.response.GetFileResponse;

import io.ksilisk.telegrambot.core.executor.TelegramBotExecutor;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.service.config.AudioConfigService;
import net.ddns.adambravo79.tmill.telegram.exception.TelegramFileException;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;
import net.ddns.adambravo79.tmill.util.LogSanitizer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramFacade {

    private final TelegramBotExecutor executor;
    private final TelegramSafeExecutor safeExecutor;
    private final MetricsService metricsService;
    private final AudioConfigService audioConfig;

    @Value("${telegram.bot.token}")
    private String botToken;

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
                "⏱️ Timeouts do httpClient interno (download): connect={}s, read={}s, write={}s",
                connectTimeout,
                readTimeout,
                writeTimeout);

        this.httpClient =
                new OkHttpClient.Builder()
                        .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                        .readTimeout(readTimeout, TimeUnit.SECONDS)
                        .writeTimeout(writeTimeout, TimeUnit.SECONDS)
                        .callTimeout(writeTimeout * 2L, TimeUnit.SECONDS)
                        .build();
    }

    public <T extends BaseRequest<T, R>, R extends BaseResponse> R executar(
            BaseRequest<T, R> request) {
        return executor.execute(request);
    }

    public void enviarMensagem(long chatId, String texto) {
        safeExecutor.run(
                chatId,
                this::enviarFallback,
                () -> {
                    executor.execute(new SendMessage(chatId, texto));
                    metricsService.success("telegram_enviar_mensagem");
                });
    }

    public void enviarMensagemHtml(long chatId, String texto) {
        safeExecutor.run(
                chatId,
                this::enviarFallback,
                () -> {
                    executor.execute(new SendMessage(chatId, texto).parseMode(ParseMode.HTML));
                    metricsService.success("telegram_enviar_mensagem_html");
                });
    }

    public void enviarFotoHtml(long chatId, String url, String legenda) {
        safeExecutor.run(
                chatId,
                this::enviarFallback,
                () -> {
                    executor.execute(
                            new SendPhoto(chatId, url).caption(legenda).parseMode(ParseMode.HTML));
                    metricsService.success("telegram_enviar_foto");
                });
    }

    public void enviarComBotoesHtml(long chatId, String texto, InlineKeyboardMarkup markup) {
        safeExecutor.run(
                chatId,
                this::enviarFallback,
                () -> {
                    executor.execute(
                            new SendMessage(chatId, texto)
                                    .parseMode(ParseMode.HTML)
                                    .replyMarkup(markup));
                    metricsService.success("telegram_enviar_mensagem_com_botoes_html");
                });
    }

    public void editarMensagemHtml(long chatId, int messageId, String novoTexto) {
        safeExecutor.run(
                chatId,
                this::enviarFallback,
                () -> {
                    executor.execute(
                            new EditMessageText(chatId, messageId, novoTexto)
                                    .parseMode(ParseMode.HTML));
                    metricsService.success("telegram_editar_mensagem_html");
                });
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

    /**
     * Envia mídia (URL ou arquivo local).
     *
     * <p>Retorna {@code true} se o envio da mídia foi confirmado pelo Telegram, {@code false}
     * caso contrário (mídia falhou E fallback de texto também falhou, ou mídia falhou mas fallback
     * foi enviado — nesse caso retorna {@code true} porque algo chegou ao usuário).
     */
    public boolean enviarMidia(long chatId, String filePathOrUrl, String caption) {
        boolean[] sucesso = {false};

        safeExecutor.run(
                chatId,
                this::enviarFallback,
                () -> {
                    try {
                        if (filePathOrUrl.startsWith("http://")
                                || filePathOrUrl.startsWith("https://")) {
                            sucesso[0] = sendMediaByUrl(chatId, filePathOrUrl, caption);
                        } else {
                            java.io.File file = new java.io.File(filePathOrUrl);
                            if (file.exists()) {
                                sucesso[0] = sendMediaByFile(chatId, file, caption);
                            } else {
                                log.warn(
                                        "⚠️ Arquivo não existe: {} — tentando como URL/file_id",
                                        LogSanitizer.sanitize(filePathOrUrl));
                                sucesso[0] = sendMediaByUrl(chatId, filePathOrUrl, caption);
                            }
                        }

                        if (!sucesso[0]) {
                            log.warn(
                                    "⚠️ Envio de mídia falhou para chatId={} — enviando fallback"
                                            + " formatado",
                                    LogSanitizer.sanitizeId(chatId));
                            enviarMensagemHtml(chatId, caption);
                            sucesso[0] = true;
                        }
                    } catch (Exception e) {
                        log.warn(
                                "⚠️ Exceção ao enviar mídia para chatId={}: {}",
                                LogSanitizer.sanitizeId(chatId),
                                LogSanitizer.sanitize(e.getMessage()));
                        try {
                            enviarMensagemHtml(chatId, caption);
                            sucesso[0] = true;
                        } catch (Exception fallbackErr) {
                            log.error(
                                    "❌ Fallback também falhou: {}",
                                    LogSanitizer.sanitize(fallbackErr.getMessage()));
                        }
                    }
                });

        return sucesso[0];
    }

    // =========================================================================
    // MÉTODOS AUXILIARES PRIVADOS — envio de mídia
    // =========================================================================

    private boolean sendMediaByUrl(long chatId, String url, String caption) {
        String lower = url.toLowerCase();
        try {
            BaseResponse response;
            if (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".avi")) {
                response =
                        executor.execute(
                                new SendVideo(chatId, url)
                                        .caption(caption)
                                        .parseMode(ParseMode.HTML));
            } else if (lower.endsWith(".gif")) {
                response =
                        executor.execute(
                                new SendAnimation(chatId, url)
                                        .caption(caption)
                                        .parseMode(ParseMode.HTML));
            } else if (lower.endsWith(".jpg")
                    || lower.endsWith(".jpeg")
                    || lower.endsWith(".png")) {
                response =
                        executor.execute(
                                new SendPhoto(chatId, url)
                                        .caption(caption)
                                        .parseMode(ParseMode.HTML));
            } else {
                response =
                        executor.execute(
                                new SendPhoto(chatId, url)
                                        .caption(caption)
                                        .parseMode(ParseMode.HTML));
            }
            return response != null && response.isOk();
        } catch (Exception e) {
            log.error("❌ Erro no envio de mídia por URL: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean sendMediaByFile(long chatId, java.io.File file, String caption) {
        String lower = file.getName().toLowerCase();
        long sizeBytes = file.length();
        try {
            BaseResponse response;
            if (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".avi")) {
                response =
                        executor.execute(
                                new SendVideo(chatId, file)
                                        .caption(caption)
                                        .parseMode(ParseMode.HTML));
            } else if (lower.endsWith(".gif")) {
                response =
                        executor.execute(
                                new SendAnimation(chatId, file)
                                        .caption(caption)
                                        .parseMode(ParseMode.HTML));
            } else if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".oga")) {
                response = sendAudioOrDocument(chatId, file, caption, sizeBytes);
            } else if (lower.endsWith(".jpg")
                    || lower.endsWith(".jpeg")
                    || lower.endsWith(".png")) {
                response =
                        executor.execute(
                                new SendPhoto(chatId, file)
                                        .caption(caption)
                                        .parseMode(ParseMode.HTML));
            } else {
                response =
                        executor.execute(
                                new SendDocument(chatId, file)
                                        .caption(caption)
                                        .parseMode(ParseMode.HTML));
            }
            return response != null && response.isOk();
        } catch (Exception e) {
            log.error("❌ Erro no envio de mídia por arquivo: {}", e.getMessage(), e);
            return false;
        }
    }

    private BaseResponse sendAudioOrDocument(
            long chatId, java.io.File file, String caption, long sizeBytes) {
        long thresholdBytes = audioConfig.getTelegram().useDocumentWhenOverMb() * 1024L * 1024L;

        if (sizeBytes > thresholdBytes) {
            log.info(
                    "📎 Áudio de {} MB acima do limite de {} MB — enviando como DOCUMENTO",
                    String.format("%.2f", sizeBytes / 1024.0 / 1024.0),
                    audioConfig.getTelegram().useDocumentWhenOverMb());
            return executor.execute(
                    new SendDocument(chatId, file).caption(caption).parseMode(ParseMode.HTML));
        }

        return executor.execute(
                new SendAudio(chatId, file).caption(caption).parseMode(ParseMode.HTML));
    }

    // =========================================================================
    // FALLBACK / UTILITÁRIOS
    // =========================================================================

    private void enviarFallback(long chatId, String texto) {
        try {
            executor.execute(new SendMessage(chatId, texto));
        } catch (Exception e) {
            log.error("Falha no fallback: {}", LogSanitizer.sanitize(e.getMessage()));
        }
    }

    public File getFile(String fileId) {
        GetFile getFile = new GetFile(fileId);
        GetFileResponse response = executor.execute(getFile);
        if (response.isOk()) {
            return response.file();
        }
        throw new TelegramFileException("Falha ao obter arquivo: " + response.description(), null);
    }

    public byte[] downloadFile(File file) {
        String filePath = file.filePath();
        String url = "https://api.telegram.org/file/bot" + botToken + "/" + filePath;

        Request request = new Request.Builder().url(url).build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new TelegramFileException(
                        "Erro ao baixar arquivo: " + filePath + ", HTTP status: " + response.code(),
                        null);
            }
            try (InputStream is = response.body().byteStream()) {
                return is.readAllBytes();
            }
        } catch (IOException e) {
            throw new TelegramFileException("Erro ao baixar arquivo: " + filePath, e);
        }
    }

    private static String maskToken(String token) {
        if (token == null) {
            return "***";
        }
        int length = token.length();
        if (length < 8) {
            return "***";
        }
        return token.substring(0, 4) + "..." + token.substring(length - 4);
    }
}
