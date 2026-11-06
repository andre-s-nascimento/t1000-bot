/* (c) 2026 | 06/11/2026 */
package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import net.ddns.adambravo79.tmill.model.Reminder;
import net.ddns.adambravo79.tmill.service.feature.FeatureFlagAdminService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;

/**
 * Testes do {@link ReminderDispatcher}.
 *
 * <p>Cobre:
 *
 * <ul>
 *   <li>Feature flag {@code reminders.enabled}: on/off.
 *   <li>Reminder inexistente / desabilitado / sem chats: pulado, com métrica correta.
 *   <li>Envio sem mídia: usa {@code enviarMensagemHtml}.
 *   <li>Envio com mídia: usa {@code enviarMidia}.
 *   <li>Múltiplos chats: cada um é tentado independentemente (isolamento).
 *   <li>Chat ID inválido na lista: logado, restante é tentado.
 *   <li>Exceção por chat: não derruba os demais.
 *   <li>{@code dispatchToChat} (teste manual): não valida feature flag, retorna boolean.
 *   <li>Métricas: success, failure, skipped — conforme o cenário.
 * </ul>
 *
 * <p><b>Regra do {@code eq()}:</b> só é usado quando <b>algum</b> argumento do {@code verify} é
 * matcher ({@code any()}, {@code anyLong()}, {@code contains()}, etc.). Quando todos os argumentos
 * são literais, passa-se direto (Sonar {@code java:S6068}).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReminderDispatcherTest {

    private static final String FLAG_REMINDERS = "reminders.enabled";
    private static final String METRIC_SUCCESS = "reminder_dispatch_success";
    private static final String METRIC_FAILURE = "reminder_dispatch_failure";
    private static final String METRIC_SKIPPED = "reminder_dispatch_skipped";

    private static final String REMINDER_ID = "quatro-horas-quarta";
    private static final long CHAT_ID_A = -100123L;
    private static final long CHAT_ID_B = -100456L;
    private static final long CHAT_ID_TEST = 999L;
    private static final String MESSAGE = "<b>Lembrete!</b>";
    private static final String MEDIA_URL = "https://exemplo.com/video.mp4";
    private static final String CRON_VALIDO = "0 0 16 * * WED";
    private static final String TZ = "America/Sao_Paulo";

    @Mock private ReminderService reminderService;
    @Mock private TelegramFacade telegramFacade;
    @Mock private FeatureFlagAdminService featureFlags;
    @Mock private MetricsService metricsService;

    private ReminderDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher =
                new ReminderDispatcher(
                        reminderService, telegramFacade, featureFlags, metricsService);

        // Default: flag habilitada
        lenient().when(featureFlags.isEnabled(FLAG_REMINDERS)).thenReturn(true);
    }

    // =========================================================================
    // FIXTURES
    // =========================================================================

    private Reminder reminder(boolean enabled, String mediaUrl, long... chatIds) {
        List<String> chats = java.util.Arrays.stream(chatIds).mapToObj(String::valueOf).toList();
        return new Reminder(
                REMINDER_ID,
                "Quatro Horas de Quarta",
                enabled,
                CRON_VALIDO,
                TZ,
                MESSAGE,
                mediaUrl,
                chats,
                List.of());
    }

    private void stubGetById(Reminder reminder) {
        when(reminderService.getById(REMINDER_ID)).thenReturn(Optional.of(reminder));
    }

    private void stubGetByIdEmpty() {
        when(reminderService.getById(REMINDER_ID)).thenReturn(Optional.empty());
    }

    // =========================================================================
    // FEATURE FLAG
    // =========================================================================

    @Nested
    @DisplayName("Feature flag 'reminders.enabled'")
    class FeatureFlag {

        @Test
        @DisplayName("flag off: dispatch retorna cedo, sem tocar no service/telegram")
        void flagOffPula() {
            when(featureFlags.isEnabled(FLAG_REMINDERS)).thenReturn(false);

            dispatcher.dispatch(REMINDER_ID);

            verifyNoInteractions(telegramFacade);
            verify(reminderService, never()).getById(anyString());
            verify(metricsService).success(METRIC_SKIPPED);
            verify(metricsService, never()).success(METRIC_SUCCESS);
            verify(metricsService, never()).error(anyString());
        }

        @Test
        @DisplayName("flag on: prossegue normalmente")
        void flagOnProssegue() {
            stubGetById(reminder(true, null, CHAT_ID_A));

            dispatcher.dispatch(REMINDER_ID);

            // ✅ todos os args literais → sem eq()
            verify(telegramFacade).enviarMensagemHtml(CHAT_ID_A, MESSAGE);
        }

        @Test
        @DisplayName("dispatchToChat NÃO valida feature flag")
        void dispatchToChatIgnoraFlag() {
            when(featureFlags.isEnabled(FLAG_REMINDERS)).thenReturn(false);
            stubGetById(reminder(true, null, CHAT_ID_A));

            boolean ok = dispatcher.dispatchToChat(REMINDER_ID, CHAT_ID_TEST);

            assertThat(ok).isTrue();
            // ✅ todos os args literais → sem eq()
            verify(telegramFacade).enviarMensagemHtml(CHAT_ID_TEST, MESSAGE);
            // Flag nem foi consultada
            verify(featureFlags, never()).isEnabled(FLAG_REMINDERS);
        }
    }

    // =========================================================================
    // REMINDER INEXISTENTE / DESABILITADO / SEM CHATS
    // =========================================================================

    @Nested
    @DisplayName("Casos de borda do dispatch")
    class BordasDispatch {

        @Test
        @DisplayName("reminder inexistente: registra failure")
        void inexistenteFailure() {
            stubGetByIdEmpty();

            dispatcher.dispatch(REMINDER_ID);

            verifyNoInteractions(telegramFacade);
            verify(metricsService).error(METRIC_FAILURE);
            verify(metricsService, never()).success(METRIC_SUCCESS);
        }

        @Test
        @DisplayName("reminder desabilitado: registra skipped")
        void desabilitadoSkipped() {
            stubGetById(reminder(false, null, CHAT_ID_A));

            dispatcher.dispatch(REMINDER_ID);

            verifyNoInteractions(telegramFacade);
            verify(metricsService).success(METRIC_SKIPPED);
            verify(metricsService, never()).success(METRIC_SUCCESS);
            verify(metricsService, never()).error(anyString());
        }

        @Test
        @DisplayName("reminder sem chats: registra failure")
        void semChatsFailure() {
            stubGetById(reminder(true, null /* sem chatIds */));

            dispatcher.dispatch(REMINDER_ID);

            verifyNoInteractions(telegramFacade);
            verify(metricsService).error(METRIC_FAILURE);
        }
    }

    // =========================================================================
    // ENVIO SEM MÍDIA
    // =========================================================================

    @Nested
    @DisplayName("Envio sem mídia")
    class SemMidia {

        @Test
        @DisplayName("1 chat: chama enviarMensagemHtml, sucesso, métrica success")
        void umChat() {
            stubGetById(reminder(true, null, CHAT_ID_A));

            dispatcher.dispatch(REMINDER_ID);

            // ✅ todos os args literais → sem eq()
            verify(telegramFacade).enviarMensagemHtml(CHAT_ID_A, MESSAGE);
            verify(telegramFacade, never()).enviarMidia(anyLong(), anyString(), anyString());
            verify(metricsService).success(METRIC_SUCCESS);
        }

        @Test
        @DisplayName("3 chats: envia para todos, métrica success")
        void tresChats() {
            stubGetById(reminder(true, null, CHAT_ID_A, CHAT_ID_B, -100789L));

            dispatcher.dispatch(REMINDER_ID);

            // ✅ eq(CHAT_ID) + anyString(): matcher misturado → eq() obrigatório
            verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID_A), anyString());
            verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID_B), anyString());
            verify(telegramFacade).enviarMensagemHtml(eq(-100789L), anyString());
            verify(metricsService).success(METRIC_SUCCESS);
        }

        @Test
        @DisplayName("mediaUrl em branco: trata como sem mídia")
        void mediaUrlEmBranco() {
            stubGetById(reminder(true, "   ", CHAT_ID_A));

            dispatcher.dispatch(REMINDER_ID);

            // ✅ todos os args literais → sem eq()
            verify(telegramFacade).enviarMensagemHtml(CHAT_ID_A, MESSAGE);
            verify(telegramFacade, never()).enviarMidia(anyLong(), anyString(), anyString());
        }
    }

    // =========================================================================
    // ENVIO COM MÍDIA
    // =========================================================================

    @Nested
    @DisplayName("Envio com mídia")
    class ComMidia {

        @Test
        @DisplayName("mídia OK: chama enviarMidia, métrica success")
        void midiaOk() {
            stubGetById(reminder(true, MEDIA_URL, CHAT_ID_A));
            when(telegramFacade.enviarMidia(CHAT_ID_A, MEDIA_URL, MESSAGE)).thenReturn(true);

            dispatcher.dispatch(REMINDER_ID);

            // ✅ todos os args literais → sem eq()
            verify(telegramFacade).enviarMidia(CHAT_ID_A, MEDIA_URL, MESSAGE);
            verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
            verify(metricsService).success(METRIC_SUCCESS);
        }

        @Test
        @DisplayName("mídia falha (retorna false): registra failure")
        void midiaFalha() {
            stubGetById(reminder(true, MEDIA_URL, CHAT_ID_A));
            when(telegramFacade.enviarMidia(anyLong(), anyString(), anyString())).thenReturn(false);

            dispatcher.dispatch(REMINDER_ID);

            // ✅ todos os args literais → sem eq()
            verify(telegramFacade).enviarMidia(CHAT_ID_A, MEDIA_URL, MESSAGE);
            verify(metricsService).error(METRIC_FAILURE);
            verify(metricsService, never()).success(METRIC_SUCCESS);
        }

        @Test
        @DisplayName("mídia falha com exceção: registra failure, não propaga")
        void midiaFalhaComExcecao() {
            stubGetById(reminder(true, MEDIA_URL, CHAT_ID_A));
            doThrow(new RuntimeException("boom"))
                    .when(telegramFacade)
                    .enviarMidia(anyLong(), anyString(), anyString());

            assertThatCode(() -> dispatcher.dispatch(REMINDER_ID)).doesNotThrowAnyException();

            verify(metricsService).error(METRIC_FAILURE);
        }
    }

    // =========================================================================
    // ISOLAMENTO POR CHAT
    // =========================================================================

    @Nested
    @DisplayName("Isolamento por chat")
    class Isolamento {

        @Test
        @DisplayName("falha em A não impede B")
        void falhaEmAUmNaoImpedeB() {
            stubGetById(reminder(true, null, CHAT_ID_A, CHAT_ID_B));
            // A falha, B sucede
            doThrow(new RuntimeException("A falhou"))
                    .doNothing()
                    .when(telegramFacade)
                    .enviarMensagemHtml(anyLong(), anyString());

            dispatcher.dispatch(REMINDER_ID);

            verify(telegramFacade, times(2)).enviarMensagemHtml(anyLong(), anyString());
            verify(metricsService).error(METRIC_FAILURE);
        }

        @Test
        @DisplayName("chat ID inválido (não numérico): ignorado, resto é enviado")
        void chatIdInvalido() {
            Reminder r =
                    new Reminder(
                            REMINDER_ID,
                            "x",
                            true,
                            CRON_VALIDO,
                            TZ,
                            MESSAGE,
                            null,
                            List.of("abc", String.valueOf(CHAT_ID_A)),
                            List.of());
            stubGetById(r);

            dispatcher.dispatch(REMINDER_ID);

            // ✅ todos os args literais → sem eq()
            verify(telegramFacade).enviarMensagemHtml(CHAT_ID_A, MESSAGE);
            verify(telegramFacade, times(1)).enviarMensagemHtml(anyLong(), anyString());
        }

        @Test
        @DisplayName("todos os chats inválidos: registra failure")
        void todosChatsInvalidos() {
            Reminder r =
                    new Reminder(
                            REMINDER_ID,
                            "x",
                            true,
                            CRON_VALIDO,
                            TZ,
                            MESSAGE,
                            null,
                            List.of("abc", "xyz", ""),
                            List.of());
            stubGetById(r);

            dispatcher.dispatch(REMINDER_ID);

            verifyNoInteractions(telegramFacade);
            verify(metricsService).error(METRIC_FAILURE);
        }
    }

    // =========================================================================
    // DISPATCH TO CHAT (teste manual pelo admin)
    // =========================================================================

    @Nested
    @DisplayName("dispatchToChat() (teste manual)")
    class DispatchToChat {

        @Test
        @DisplayName("reminder existe, envio OK: retorna true, métrica success")
        void sucesso() {
            stubGetById(reminder(true, null, CHAT_ID_A));

            boolean ok = dispatcher.dispatchToChat(REMINDER_ID, CHAT_ID_TEST);

            assertThat(ok).isTrue();
            // ✅ todos os args literais → sem eq()
            verify(telegramFacade).enviarMensagemHtml(CHAT_ID_TEST, MESSAGE);
            verify(metricsService).success(METRIC_SUCCESS);
        }

        @Test
        @DisplayName("reminder inexistente: retorna false, sem tocar no telegram")
        void inexistente() {
            stubGetByIdEmpty();

            boolean ok = dispatcher.dispatchToChat(REMINDER_ID, CHAT_ID_TEST);

            assertThat(ok).isFalse();
            verifyNoInteractions(telegramFacade);
            verify(metricsService, never()).success(anyString());
            verify(metricsService, never()).error(anyString());
        }

        @Test
        @DisplayName("reminder desabilitado: ainda assim envia (é ação explícita do admin)")
        void desabilitadoEnvia() {
            stubGetById(reminder(false, null, CHAT_ID_A));

            boolean ok = dispatcher.dispatchToChat(REMINDER_ID, CHAT_ID_TEST);

            assertThat(ok).isTrue();
            // ✅ todos os args literais → sem eq()
            verify(telegramFacade).enviarMensagemHtml(CHAT_ID_TEST, MESSAGE);
        }

        @Test
        @DisplayName("envio falha: retorna false, métrica failure")
        void falha() {
            stubGetById(reminder(true, null, CHAT_ID_A));
            doThrow(new RuntimeException("boom"))
                    .when(telegramFacade)
                    .enviarMensagemHtml(anyLong(), anyString());

            boolean ok = dispatcher.dispatchToChat(REMINDER_ID, CHAT_ID_TEST);

            assertThat(ok).isFalse();
            verify(metricsService).error(METRIC_FAILURE);
        }

        @Test
        @DisplayName("com mídia: usa enviarMidia")
        void comMidia() {
            stubGetById(reminder(true, MEDIA_URL, CHAT_ID_A));
            when(telegramFacade.enviarMidia(CHAT_ID_TEST, MEDIA_URL, MESSAGE)).thenReturn(true);

            boolean ok = dispatcher.dispatchToChat(REMINDER_ID, CHAT_ID_TEST);

            assertThat(ok).isTrue();
            // ✅ todos os args literais → sem eq()
            verify(telegramFacade).enviarMidia(CHAT_ID_TEST, MEDIA_URL, MESSAGE);
        }

        @Test
        @DisplayName("com mídia que falha: retorna false")
        void comMidiaQueFalha() {
            stubGetById(reminder(true, MEDIA_URL, CHAT_ID_A));
            when(telegramFacade.enviarMidia(anyLong(), anyString(), anyString())).thenReturn(false);

            boolean ok = dispatcher.dispatchToChat(REMINDER_ID, CHAT_ID_TEST);

            assertThat(ok).isFalse();
            verify(metricsService).error(METRIC_FAILURE);
        }
    }

    // =========================================================================
    // MÉTRICAS — contagem
    // =========================================================================

    @Nested
    @DisplayName("Métricas — contagem em múltiplos dispatch")
    class Metricas {

        @Test
        @DisplayName("3 dispatch com sucesso: 3 success")
        void tresSucessos() {
            stubGetById(reminder(true, null, CHAT_ID_A));

            dispatcher.dispatch(REMINDER_ID);
            dispatcher.dispatch(REMINDER_ID);
            dispatcher.dispatch(REMINDER_ID);

            verify(metricsService, times(3)).success(METRIC_SUCCESS);
            verify(metricsService, never()).error(anyString());
        }

        @Test
        @DisplayName("flag off 2×: 2 skipped")
        void doisSkipped() {
            when(featureFlags.isEnabled(FLAG_REMINDERS)).thenReturn(false);

            dispatcher.dispatch(REMINDER_ID);
            dispatcher.dispatch(REMINDER_ID);

            verify(metricsService, times(2)).success(METRIC_SKIPPED);
        }

        @Test
        @DisplayName("1 sucesso + 1 inexistente: 1 success + 1 failure")
        void misto() {
            // 1ª chamada: reminder existe
            when(reminderService.getById(REMINDER_ID))
                    .thenReturn(Optional.of(reminder(true, null, CHAT_ID_A)))
                    // 2ª chamada: inexistente
                    .thenReturn(Optional.empty());

            dispatcher.dispatch(REMINDER_ID);
            dispatcher.dispatch(REMINDER_ID);

            verify(metricsService).success(METRIC_SUCCESS);
            verify(metricsService).error(METRIC_FAILURE);
        }
    }

    // =========================================================================
    // PARAMETRIZADO — variações de chat IDs válidos
    // =========================================================================

    @Nested
    @DisplayName("Chat IDs válidos parametrizados")
    class ChatIdsValidos {

        @ParameterizedTest(name = "[{index}] chatId={0}")
        @ValueSource(
                strings = {
                    "-1001234567890", // grupo típico
                    "-100", // grupo
                    "12345", // usuário
                    "0", // zero (válido do ponto de vista de parse)
                    "9223372036854775807" // Long.MAX_VALUE
                })
        @DisplayName("Chat IDs parseáveis são enviados")
        void chatIdParseavel(String chatId) {
            Reminder r =
                    new Reminder(
                            REMINDER_ID,
                            "x",
                            true,
                            CRON_VALIDO,
                            TZ,
                            MESSAGE,
                            null,
                            List.of(chatId),
                            List.of());
            stubGetById(r);

            dispatcher.dispatch(REMINDER_ID);

            // ✅ anyLong() + literal → eq() obrigatório no literal
            verify(telegramFacade, times(1)).enviarMensagemHtml(anyLong(), eq(MESSAGE));
            verify(metricsService).success(METRIC_SUCCESS);
        }
    }

    // =========================================================================
    // CASO INTEGRADO
    // =========================================================================

    @Nested
    @DisplayName("Caso integrado (feature flag + isolamento + métrica)")
    class Integrado {

        @Test
        @DisplayName("3 chats, 1 falha: metric failure, mas os 3 são tentados")
        void tresChatsUmaFalha() {
            stubGetById(reminder(true, null, CHAT_ID_A, CHAT_ID_B, -100789L));
            doThrow(new RuntimeException("A falhou"))
                    .doNothing()
                    .doNothing()
                    .when(telegramFacade)
                    .enviarMensagemHtml(anyLong(), anyString());

            dispatcher.dispatch(REMINDER_ID);

            // ✅ anyLong() + literal → eq() obrigatório no literal
            verify(telegramFacade, times(3)).enviarMensagemHtml(anyLong(), eq(MESSAGE));
            verify(metricsService).error(METRIC_FAILURE);
            verify(metricsService, never()).success(METRIC_SUCCESS);
        }

        @Test
        @DisplayName("mensagem com HTML não é sanitizada (ParseMode.HTML do Telegram)")
        void mensagemComHtmlPassaDireto() {
            String htmlMessage = "<b>Negrito</b> <i>itálico</i> <code>code</code>";
            Reminder r =
                    new Reminder(
                            REMINDER_ID,
                            "x",
                            true,
                            CRON_VALIDO,
                            TZ,
                            htmlMessage,
                            null,
                            List.of(String.valueOf(CHAT_ID_A)),
                            List.of());
            stubGetById(r);

            dispatcher.dispatch(REMINDER_ID);

            // ✅ todos os args literais → sem eq()
            verify(telegramFacade).enviarMensagemHtml(CHAT_ID_A, htmlMessage);
        }
    }
}
