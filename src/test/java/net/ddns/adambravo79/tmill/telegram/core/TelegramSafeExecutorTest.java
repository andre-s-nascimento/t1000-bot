package net.ddns.adambravo79.tmill.telegram.core;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import net.ddns.adambravo79.tmill.service.feature.FeatureFlagAdminService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelegramSafeExecutorTest {

    @Mock private FeatureFlagAdminService featureFlags;

    private TelegramSafeExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new TelegramSafeExecutor(featureFlags);

        // Default: fallback habilitado (mantém os testes existentes passando)
        lenient().when(featureFlags.isEnabled("telegram.error-fallback.enabled")).thenReturn(true);
    }

    // =========================================================================
    // SUCESSO — ação não lança
    // =========================================================================

    @Test
    void deveExecutarAcaoComSucesso() throws Exception {
        TelegramSender sender = mock(TelegramSender.class);
        TelegramSafeExecutor.ThrowingRunnable action =
                mock(TelegramSafeExecutor.ThrowingRunnable.class);

        executor.run(123L, sender, action);

        verify(action).run();
        verify(sender, never()).enviar(anyLong(), anyString());
    }

    // =========================================================================
    // FALLBACK — ação lança
    // =========================================================================

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

    // =========================================================================
    // 🆕 FEATURE FLAG — telegram.error-fallback.enabled
    // =========================================================================

    @Test
    @DisplayName("fallback desabilitado: erro é logado mas nenhuma mensagem é enviada")
    void fallbackDesabilitado_naoEnviaMensagem() throws Exception {
        when(featureFlags.isEnabled("telegram.error-fallback.enabled")).thenReturn(false);

        TelegramSender sender = mock(TelegramSender.class);
        TelegramSafeExecutor.ThrowingRunnable action =
                mock(TelegramSafeExecutor.ThrowingRunnable.class);
        doThrow(new RuntimeException("erro")).when(action).run();

        executor.run(123L, sender, action);

        verify(action).run();
        verify(sender, never()).enviar(anyLong(), anyString());
    }

    @Test
    @DisplayName("fallback habilitado: erro envia mensagem genérica")
    void fallbackHabilitado_enviaMensagem() throws Exception {
        when(featureFlags.isEnabled("telegram.error-fallback.enabled")).thenReturn(true);

        TelegramSender sender = mock(TelegramSender.class);
        TelegramSafeExecutor.ThrowingRunnable action =
                mock(TelegramSafeExecutor.ThrowingRunnable.class);
        doThrow(new RuntimeException("erro")).when(action).run();

        executor.run(123L, sender, action);

        verify(sender).enviar(eq(123L), contains("Erro ao processar"));
    }

    @Test
    @DisplayName("fallback desabilitado + ação ok: não consulta a flag")
    void acaoOk_naoConsultaFlag() throws Exception {
        TelegramSender sender = mock(TelegramSender.class);
        TelegramSafeExecutor.ThrowingRunnable action =
                mock(TelegramSafeExecutor.ThrowingRunnable.class);

        executor.run(123L, sender, action);

        verify(action).run();
        // Como não houve erro, a flag nunca é consultada
        verify(featureFlags, never()).isEnabled("telegram.error-fallback.enabled");
    }
}
