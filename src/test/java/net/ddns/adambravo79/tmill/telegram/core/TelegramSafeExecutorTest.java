/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.telegram.core;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

class TelegramSafeExecutorTest {

    @Test
    void deveExecutarComSucesso() throws Exception {
        TelegramSender sender = mock(TelegramSender.class);
        TelegramSafeExecutor executor = new TelegramSafeExecutor();

        executor.run(1L, sender, () -> sender.enviar(1L, "ok"));

        verify(sender).enviar(1L, "ok");
    }

    @Test
    void deveCapturarExcecaoTelegramApi() throws Exception {
        TelegramSender sender = mock(TelegramSender.class);
        TelegramSafeExecutor executor = new TelegramSafeExecutor();

        executor.run(
                1L,
                sender,
                () -> {
                    throw new org.telegram.telegrambots.meta.exceptions.TelegramApiException(
                            "erro");
                });

        verify(sender).enviar(1L, "⚠️ Erro ao enviar mensagem. Tente novamente.");
    }

    @Test
    void deveCapturarExcecaoGenerica() throws Exception {
        TelegramSender sender = mock(TelegramSender.class);
        TelegramSafeExecutor executor = new TelegramSafeExecutor();

        executor.run(
                1L,
                sender,
                () -> {
                    throw new RuntimeException("erro");
                });

        verify(sender).enviar(1L, "⚠️ Erro inesperado.");
    }
}
