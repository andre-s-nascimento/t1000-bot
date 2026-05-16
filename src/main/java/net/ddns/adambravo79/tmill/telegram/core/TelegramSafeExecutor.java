/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.telegram.core;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import lombok.extern.slf4j.Slf4j;

/**
 * Executor seguro para ações do Telegram.
 *
 * <p>Responsável por: - Executar ações contra a API do Telegram. - Tratar exceções conhecidas e
 * inesperadas. - Aplicar fallback para garantir que o usuário receba feedback mesmo em caso de
 * falha.
 *
 * <p>Usado em conjunto com {@link TelegramFacade} para envio de mensagens, fotos e botões.
 */
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
    public void run(Long chatId, TelegramSender fallback, TelegramAction action) {
        try {
            action.run();

        } catch (TelegramApiException e) {
            String msg = e.getMessage();
            boolean isTransient =
                    msg != null
                            && (msg.contains("timeout")
                                    || msg.contains("429")
                                    || msg.contains("Too Many Requests")
                                    || msg.contains("SocketTimeoutException"));

            if (isTransient) {
                log.warn(
                        "⚠️ Telegram erro transitório (timeout/rate‑limit) chatId={}: {}",
                        chatId,
                        msg);
            } else {
                log.error("❌ telegram_error chatId={} msg={}", chatId, msg, e);
            }

            try {
                fallback.enviar(chatId, "⚠️ Erro ao enviar mensagem. Tente novamente.");
            } catch (Exception fallbackError) {
                log.error("fallback_error chatId={}", chatId, fallbackError);
            }

        } catch (Exception e) {
            log.error("❌ unexpected_error chatId={}", chatId, e);
            try {
                fallback.enviar(chatId, "⚠️ Erro inesperado.");
            } catch (Exception fallbackError) {
                log.error("fallback_error chatId={}", chatId, fallbackError);
            }
        }
    }
}
