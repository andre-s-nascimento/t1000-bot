package net.ddns.adambravo79.tmill.telegram.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TelegramApiExceptionTest {

    private static final String MENSAGEM = "Erro na API do Telegram";
    private static final Throwable CAUSA = new RuntimeException("Causa raiz");

    @Test
    void construtorComMensagem_deveCriarExcecao() {
        TelegramApiException ex = new TelegramApiException(MENSAGEM);

        assertThat(ex.getMessage()).isEqualTo(MENSAGEM);
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void construtorComMensagemECausa_deveCriarExcecao() {
        TelegramApiException ex = new TelegramApiException(MENSAGEM, CAUSA);

        assertThat(ex.getMessage()).isEqualTo(MENSAGEM);
        assertThat(ex.getCause()).isEqualTo(CAUSA);
    }

    @Test
    void deveSerInstanciaDeTelegramBotException() {
        TelegramApiException ex = new TelegramApiException(MENSAGEM);
        assertThat(ex).isInstanceOf(TelegramBotException.class);
    }

    @Test
    void deveSerInstanciaDeRuntimeException() {
        TelegramApiException ex = new TelegramApiException(MENSAGEM, CAUSA);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
