package net.ddns.adambravo79.tmill.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GroqRateLimitExceptionTest {

    private static final String MENSAGEM = "Rate limit excedido";
    private static final Throwable CAUSA = new RuntimeException("Causa raiz");

    @Test
    void construtorComMensagem_deveCriarExcecao() {
        GroqRateLimitException ex = new GroqRateLimitException(MENSAGEM);

        assertThat(ex.getMessage()).isEqualTo(MENSAGEM);
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void construtorComMensagemECausa_deveCriarExcecao() {
        GroqRateLimitException ex = new GroqRateLimitException(MENSAGEM, CAUSA);

        assertThat(ex.getMessage()).isEqualTo(MENSAGEM);
        assertThat(ex.getCause()).isEqualTo(CAUSA);
    }

    @Test
    void deveSerInstanciaDeRuntimeException() {
        GroqRateLimitException ex = new GroqRateLimitException(MENSAGEM);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void deveSerInstanciaDeGroqRateLimitException() {
        GroqRateLimitException ex = new GroqRateLimitException(MENSAGEM, CAUSA);
        assertThat(ex).isInstanceOf(GroqRateLimitException.class);
    }
}
