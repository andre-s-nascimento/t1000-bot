package net.ddns.adambravo79.tmill.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.ksilisk.telegrambot.core.executor.TelegramBotExecutor;

class TelegramExecutorConfigTest {

    @Test
    void telegramBotExecutor_deveCriarBean() {
        TelegramExecutorConfig config = new TelegramExecutorConfig();
        ReflectionTestUtils.setField(config, "botToken", "123:ABC");
        ReflectionTestUtils.setField(config, "connectTimeoutSeconds", 30);
        ReflectionTestUtils.setField(config, "readTimeoutSeconds", 180);
        ReflectionTestUtils.setField(config, "writeTimeoutSeconds", 180);
        ReflectionTestUtils.setField(config, "callTimeoutSeconds", 300);

        TelegramBotExecutor executor = config.telegramBotExecutor();

        assertThat(executor).isNotNull();
    }
}
