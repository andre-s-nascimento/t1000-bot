/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.telegram.util;

import java.util.concurrent.Callable;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Componente que implementa uma política de retry com backoff linear.
 *
 * <p>Características: - Máximo de 3 tentativas. - Backoff linear de 1 segundo multiplicado pelo
 * número da tentativa. - Retenta em casos de timeout, rate limit (HTTP 429) ou {@link
 * java.io.IOException}.
 *
 * <p>Usado para aumentar a resiliência em chamadas externas (ex.: API do Telegram).
 */
@Slf4j
@Component
public class RetryPolicy {

    private static final int MAX_RETRIES = 3;
    private static final long BACKOFF_MS = 1000;

    /**
     * Executa uma ação com política de retry.
     *
     * @param action ação a ser executada.
     * @param <T> tipo de retorno da ação.
     * @return resultado da ação se bem-sucedida.
     * @throws Exception se todas as tentativas falharem ou se não for elegível para retry.
     */
    public <T> T execute(Callable<T> action) throws Exception {
        int attempt = 0;

        while (true) {
            try {
                return action.call();

            } catch (Exception e) {
                attempt++;

                if (!shouldRetry(e) || attempt >= MAX_RETRIES) {
                    throw e;
                }

                log.warn("Retry attempt {} devido a {}", attempt, e.getMessage());

                Thread.sleep(BACKOFF_MS * attempt); // backoff linear simples
            }
        }
    }

    /**
     * Determina se uma exceção é elegível para retry.
     *
     * @param e exceção lançada.
     * @return {@code true} se deve tentar novamente, {@code false} caso contrário.
     */
    private boolean shouldRetry(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";

        return msg.contains("429") // rate limit
                || msg.toLowerCase().contains("timeout")
                || e instanceof java.io.IOException;
    }
}
