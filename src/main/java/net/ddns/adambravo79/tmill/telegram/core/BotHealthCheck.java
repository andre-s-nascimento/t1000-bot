package net.ddns.adambravo79.tmill.telegram.core;

import org.springframework.stereotype.Component;

import io.ksilisk.telegrambot.core.executor.TelegramBotExecutor;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class BotHealthCheck {
    private final TelegramBotExecutor executor;

    BotHealthCheck(TelegramBotExecutor executor) {
        this.executor = executor;
    }

    @PostConstruct
    public void check() {
        log.info("🤖 TelegramBotExecutor injetado? {}", executor != null);
    }
}
