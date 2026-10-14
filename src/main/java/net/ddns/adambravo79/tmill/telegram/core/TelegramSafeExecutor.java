package net.ddns.adambravo79.tmill.telegram.core;

import java.io.IOException;

import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.TelegramException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.service.feature.FeatureFlagAdminService;

/**
 * Executor seguro para chamadas ao Telegram.
 *
 * <p>Envolve uma ação ({@link ThrowingRunnable}) em try/catch e, em caso de erro, delega para um
 * {@link TelegramSender} de fallback que envia uma mensagem genérica ao usuário.
 *
 * <p><b>Feature flag:</b> o fallback pode ser suprimido em runtime via {@code
 * telegram.error-fallback.enabled}. Quando desabilitado, o erro é logado mas nenhuma mensagem
 * substituta é enviada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramSafeExecutor {

    private static final String FALLBACK_MESSAGE = "⚠️ Erro ao processar. Tente novamente.";

    private static final String FLAG_ERROR_FALLBACK = "telegram.error-fallback.enabled";

    private final FeatureFlagAdminService featureFlags;

    public void run(Long chatId, TelegramSender fallback, ThrowingRunnable action) {
        try {
            action.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("❌ Ação interrompida chatId={}", chatId, e);
        } catch (IOException | TelegramException | RuntimeException e) {
            log.error("❌ Erro ao executar ação do Telegram chatId={}", chatId, e);

            if (!featureFlags.isEnabled(FLAG_ERROR_FALLBACK)) {
                log.debug("📴 Fallback suprimido por feature flag — apenas log.");
                return;
            }

            enviarFallback(chatId, fallback);
        }
    }

    /**
     * Tenta enviar a mensagem de fallback. Erros são logados mas não propagados — o fallback é a
     * última linha de defesa.
     *
     * <p>Captura {@link TelegramException} porque {@link TelegramSender#enviar(long, String)} a
     * declara como <b>checked exception</b> (não é {@link RuntimeException}).
     */
    private void enviarFallback(Long chatId, TelegramSender fallback) {
        try {
            fallback.enviar(chatId, FALLBACK_MESSAGE);
        } catch (TelegramException fallbackError) {
            log.error("❌ Fallback também falhou chatId={}", chatId, fallbackError);
        }
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws IOException, InterruptedException, TelegramException;
    }
}
