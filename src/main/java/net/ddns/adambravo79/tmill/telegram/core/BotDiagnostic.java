package net.ddns.adambravo79.tmill.telegram.core;

import org.springframework.stereotype.Component;

import io.ksilisk.telegrambot.core.executor.TelegramBotExecutor;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class BotDiagnostic {

    private final TelegramBotExecutor executor;

    @PostConstruct
    public void check() {
        log.info("🤖 TelegramBotExecutor: {}", executor);
        // Tenta obter informações do bot
        try {
            var me = executor.execute(new com.pengrad.telegrambot.request.GetMe());
            if (me.isOk()) {
                log.info("✅ Bot conectado: @{}", me.user().username());
            } else {
                log.error("❌ Falha ao conectar: {}", me.description());
            }
        } catch (Exception e) {
            log.error("❌ Erro ao verificar bot", e);
        }
    }
}
