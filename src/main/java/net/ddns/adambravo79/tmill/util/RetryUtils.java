package net.ddns.adambravo79.tmill.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.client.HttpClientErrorException;

import lombok.extern.slf4j.Slf4j;

/**
 * Utilitário para retry com backoff exponencial. Centraliza a lógica de retry usada em serviços que
 * não podem usar @Retryable.
 */
@Slf4j
public final class RetryUtils {

    private RetryUtils() {}

    @FunctionalInterface
    public interface RetryableAction<T> {
        T execute() throws Exception;
    }

    /**
     * Executa uma ação com retry e backoff exponencial. Trata especificamente
     * HttpClientErrorException.TooManyRequests (429).
     *
     * @param action ação a ser executada
     * @param maxRetries número máximo de tentativas
     * @param baseDelayMs delay base em ms
     * @param maxDelayMs delay máximo em ms
     * @param context descrição do contexto (para logs)
     * @return resultado da ação
     */
    public static <T> T withExponentialBackoff(
            RetryableAction<T> action,
            int maxRetries,
            long baseDelayMs,
            long maxDelayMs,
            String context) {

        Exception lastException = null;
        for (int i = 0; i < maxRetries; i++) {
            try {
                return action.execute();
            } catch (HttpClientErrorException.TooManyRequests e) {
                lastException = e;
                long waitMs = extractWaitTime(e, baseDelayMs * (i + 1));
                waitMs = Math.min(waitMs, maxDelayMs);
                log.warn(
                        "Rate limit em {} (tentativa {}/{}), aguardando {}ms",
                        context,
                        i + 1,
                        maxRetries,
                        waitMs);
                sleep(waitMs);
            } catch (Exception e) {
                throw new RuntimeException(
                        "Falha em " + context + " após " + (i + 1) + " tentativas", e);
            }
        }
        throw new RuntimeException(
                "Falha em " + context + " após " + maxRetries + " tentativas", lastException);
    }

    private static long extractWaitTime(
            HttpClientErrorException.TooManyRequests e, long fallbackMs) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("try again in")) {
            try {
                Pattern pattern = Pattern.compile("try again in (\\d+\\.?\\d*)s");
                Matcher matcher = pattern.matcher(msg);
                if (matcher.find()) {
                    double waitSeconds = Double.parseDouble(matcher.group(1));
                    return (long) (waitSeconds * 1000) + 500;
                }
            } catch (Exception ignored) {
            }
        }
        return fallbackMs;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrompida durante retry", e);
        }
    }
}
