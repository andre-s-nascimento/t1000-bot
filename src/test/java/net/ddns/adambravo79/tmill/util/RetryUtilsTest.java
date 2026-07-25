package net.ddns.adambravo79.tmill.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.*;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class RetryUtilsTest {

    // ===================== SUCESSO NA PRIMEIRA TENTATIVA =====================

    @Test
    void withExponentialBackoff_quandoSucesso_retornaResultado() {
        RetryUtils.RetryableAction<String> action = () -> "sucesso";

        String result = RetryUtils.withExponentialBackoff(action, 3, 10, 100, "teste");

        assertThat(result).isEqualTo("sucesso");
    }

    // ===================== RETRY APÓS EXCEÇÕES =====================

    @Test
    void withExponentialBackoff_quandoRateLimitELogoSucesso_fazRetry() {
        AtomicInteger attempts = new AtomicInteger(0);
        RetryUtils.RetryableAction<String> action =
                () -> {
                    if (attempts.incrementAndGet() < 2) {
                        throw mockTooManyRequests();
                    }
                    return "sucesso";
                };

        String result = RetryUtils.withExponentialBackoff(action, 3, 10, 100, "teste");

        assertThat(result).isEqualTo("sucesso");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void withExponentialBackoff_quandoExcecaoGenerica_lancaRuntimeException() {
        RetryUtils.RetryableAction<String> action =
                () -> {
                    throw new IllegalArgumentException("Erro qualquer");
                };

        assertThatThrownBy(() -> RetryUtils.withExponentialBackoff(action, 3, 10, 100, "teste"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Falha em teste após 1 tentativas")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    // ===================== ESTOURAR NÚMERO MÁXIMO DE TENTATIVAS =====================

    @Test
    void withExponentialBackoff_quandoRateLimitSempre_lancaRuntimeException() {
        RetryUtils.RetryableAction<String> action =
                () -> {
                    throw mockTooManyRequests();
                };

        assertThatThrownBy(() -> RetryUtils.withExponentialBackoff(action, 2, 10, 100, "teste"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Falha em teste após 2 tentativas")
                .hasCauseInstanceOf(HttpClientErrorException.TooManyRequests.class);
    }

    // ===================== EXTRAÇÃO DE TEMPO DE ESPERA DA MENSAGEM =====================

    @ParameterizedTest
    @MethodSource("extractWaitTimeProvider")
    void extractWaitTime_deveExtrairCorretamente(String message, long fallback, long expected)
            throws Exception {
        java.lang.reflect.Method method =
                RetryUtils.class.getDeclaredMethod(
                        "extractWaitTime",
                        HttpClientErrorException.TooManyRequests.class,
                        long.class);
        method.setAccessible(true);

        HttpClientErrorException.TooManyRequests ex =
                mock(HttpClientErrorException.TooManyRequests.class);
        when(ex.getMessage()).thenReturn(message);

        long result = (long) method.invoke(null, ex, fallback);
        assertThat(result).isEqualTo(expected);
    }

    static Stream<Arguments> extractWaitTimeProvider() {
        return Stream.of(
                Arguments.of("try again in 2.5s", 1000L, 3000L),
                Arguments.of("Too Many Requests", 1500L, 1500L),
                Arguments.of(null, 2000L, 2000L),
                Arguments.of("try again in 0.5s", 500L, 1000L));
    }

    // ===================== SLEEP COM INTERRUPÇÃO =====================

    @Test
    void sleep_quandoInterrompido_lancaRuntimeException() throws Exception {
        java.lang.reflect.Method sleepMethod =
                RetryUtils.class.getDeclaredMethod("sleep", long.class);
        sleepMethod.setAccessible(true);

        Thread.currentThread().interrupt();

        Throwable thrown = catchThrowable(() -> sleepMethod.invoke(null, 100L));
        assertThat(thrown).isInstanceOf(InvocationTargetException.class);
        assertThat(thrown.getCause())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Thread interrompida durante retry");
    }

    // ===================== TESTE DE BACKOFF EXPONENCIAL (mínimo) =====================

    @Test
    void withExponentialBackoff_deveRespeitarMaxDelay() {
        AtomicInteger attempts = new AtomicInteger(0);
        RetryUtils.RetryableAction<String> action =
                () -> {
                    attempts.incrementAndGet();
                    throw mockTooManyRequests();
                };

        assertThatThrownBy(() -> RetryUtils.withExponentialBackoff(action, 3, 1000, 50, "teste"))
                .isInstanceOf(RuntimeException.class);

        assertThat(attempts.get()).isEqualTo(3);
    }

    // ===================== HELPER =====================

    private static HttpClientErrorException.TooManyRequests mockTooManyRequests() {
        HttpClientErrorException.TooManyRequests ex =
                mock(HttpClientErrorException.TooManyRequests.class);
        when(ex.getStatusCode()).thenReturn(HttpStatus.TOO_MANY_REQUESTS);
        return ex;
    }
}
