package net.ddns.adambravo79.tmill.telegram.core;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

class TelegramSafeExecutorTest {

    private final TelegramSafeExecutor executor = new TelegramSafeExecutor();

    @Test
    void deveExecutarAcaoComSucesso() throws Exception {
        TelegramSender sender = mock(TelegramSender.class);
        TelegramSafeExecutor.ThrowingRunnable action =
                mock(TelegramSafeExecutor.ThrowingRunnable.class);

        executor.run(123L, sender, action);

        verify(action).run();
        verify(sender, never()).enviar(anyLong(), anyString());
    }

    @Test
    void deveChamarFallbackEmErro() throws Exception {
        TelegramSender sender = mock(TelegramSender.class);
        TelegramSafeExecutor.ThrowingRunnable action =
                mock(TelegramSafeExecutor.ThrowingRunnable.class);
        doThrow(new RuntimeException("erro")).when(action).run();

        executor.run(123L, sender, action);

        verify(action).run();
        verify(sender).enviar(eq(123L), contains("Erro ao processar"));
    }

    @Test
    void deveLogarErroNoFallback() throws Exception {
        TelegramSender sender = mock(TelegramSender.class);
        doThrow(new RuntimeException("fallback falhou"))
                .when(sender)
                .enviar(anyLong(), anyString());

        TelegramSafeExecutor.ThrowingRunnable action =
                mock(TelegramSafeExecutor.ThrowingRunnable.class);
        doThrow(new RuntimeException("erro")).when(action).run();

        // Apenas verifica que não lança exceção (o erro é logado)
        executor.run(123L, sender, action);

        verify(sender).enviar(eq(123L), contains("Erro ao processar"));
    }
}
