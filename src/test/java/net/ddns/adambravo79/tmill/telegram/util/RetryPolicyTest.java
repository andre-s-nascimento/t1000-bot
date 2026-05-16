/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.telegram.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import lombok.SneakyThrows;

class RetryPolicyTest {

    @Test
    @SneakyThrows
    void deveExecutarComSucessoSemRetry() {
        RetryPolicy policy = new RetryPolicy();

        int result = policy.execute(() -> 42);

        assertThat(result).isEqualTo(42);
    }

    @Test
    @SneakyThrows
    void deveExecutarComRetryAteSucesso() {
        RetryPolicy policy = new RetryPolicy();

        final int[] tentativas = {0};
        int result =
                policy.execute(
                        () -> {
                            tentativas[0]++;
                            if (tentativas[0] < 2) {
                                // lançar IOException para acionar retry
                                throw new java.io.IOException("timeout");
                            }
                            return 99;
                        });

        assertThat(result).isEqualTo(99);
        assertThat(tentativas[0]).isEqualTo(2);
    }

    @Test
    @SneakyThrows
    void deveFalharDepoisDeRetries() {
        RetryPolicy policy = new RetryPolicy();

        try {
            policy.execute(
                    () -> {
                        throw new RuntimeException("sempre falha");
                    });
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("sempre falha");
        }
    }
}
