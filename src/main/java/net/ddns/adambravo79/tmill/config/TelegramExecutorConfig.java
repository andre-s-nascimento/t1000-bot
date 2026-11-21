package net.ddns.adambravo79.tmill.config;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.response.BaseResponse;

import io.ksilisk.telegrambot.core.executor.TelegramBotExecutor;
import okhttp3.OkHttpClient;

@Configuration
public class TelegramExecutorConfig {

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.client.connect-timeout:30}")
    private int connectTimeoutSeconds;

    @Value("${telegram.bot.client.read-timeout:180}")
    private int readTimeoutSeconds;

    @Value("${telegram.bot.client.write-timeout:180}")
    private int writeTimeoutSeconds;

    @Value("${telegram.bot.client.call-timeout:300}")
    private int callTimeoutSeconds;

    @Bean
    public TelegramBotExecutor telegramBotExecutor() {
        OkHttpClient httpClient =
                new OkHttpClient.Builder()
                        .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
                        .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
                        .writeTimeout(writeTimeoutSeconds, TimeUnit.SECONDS)
                        .callTimeout(callTimeoutSeconds, TimeUnit.SECONDS)
                        .build();

        TelegramBot bot = new TelegramBot.Builder(botToken).okHttpClient(httpClient).build();

        return new TelegramBotExecutor() {
            @Override
            public <T extends BaseRequest<T, R>, R extends BaseResponse> R execute(
                    BaseRequest<T, R> request) {
                return bot.execute(request);
            }
        };
    }
}
