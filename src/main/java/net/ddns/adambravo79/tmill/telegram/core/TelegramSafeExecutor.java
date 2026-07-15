package net.ddns.adambravo79.tmill.telegram.core;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TelegramSafeExecutor {

    /**
     * Executa uma ação contra a API do Telegram com fallback em caso de falha.
     *
     * @param chatId identificador único do chat.
     * @param fallback executor de fallback para enviar mensagem alternativa.
     * @param action ação principal a ser executada.
     */
    public void run(Long chatId, TelegramSender fallback, ThrowingRunnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.error("❌ Erro ao executar ação do Telegram chatId={}", chatId, e);
            try {
                fallback.enviar(chatId, "⚠️ Erro ao processar. Tente novamente.");
            } catch (Exception fallbackError) {
                log.error("❌ Fallback também falhou chatId={}", chatId, fallbackError);
            }
        }
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
