package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.SimpleTriggerContext;
import org.springframework.test.util.ReflectionTestUtils;

import net.ddns.adambravo79.tmill.service.config.JsonConfigLoader;
import net.ddns.adambravo79.tmill.service.feature.FeatureFlagAdminService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;

@ExtendWith(MockitoExtension.class)
class WeeklyReminderServiceTest {

    @Mock private TelegramFacade telegramFacade;
    @Mock private JsonConfigLoader jsonConfigLoader;
    @Mock private FeatureFlagAdminService featureFlags;

    @InjectMocks private WeeklyReminderService service;

    private static final long GROUP_100 = -100L;
    private static final long GROUP_200 = -200L;
    private static final String MEDIA_FILE = "/app/media/video.mp4";

    @BeforeEach
    void setUp() {
        Set<Long> newAllowedGroups = new HashSet<>();
        ReflectionTestUtils.setField(service, "allowedGroups", newAllowedGroups);

        // Default: feature flag habilitada (cron roda normalmente)
        lenient().when(featureFlags.isEnabled("weekly.reminder.enabled")).thenReturn(true);
    }

    // =========================================================================
    // 🆕 FEATURE FLAG — weekly.reminder.enabled
    // =========================================================================

    @Test
    @DisplayName("sendWednesdayReminder: flag 'weekly.reminder.enabled' desabilitada → pula")
    void sendWednesdayReminder_flagDesabilitada_pula() {
        when(featureFlags.isEnabled("weekly.reminder.enabled")).thenReturn(false);
        ReflectionTestUtils.setField(service, "allowedGroups", Set.of(GROUP_100));
        ReflectionTestUtils.setField(service, "mediaFilePath", MEDIA_FILE);

        service.sendWednesdayReminder();

        verifyNoInteractions(telegramFacade);
    }

    @Test
    @DisplayName("sendWednesdayReminder: flag 'weekly.reminder.enabled' habilitada → envia")
    void sendWednesdayReminder_flagHabilitada_envia() {
        when(featureFlags.isEnabled("weekly.reminder.enabled")).thenReturn(true);
        ReflectionTestUtils.setField(service, "allowedGroups", Set.of(GROUP_100));
        ReflectionTestUtils.setField(service, "mediaFilePath", MEDIA_FILE);

        service.sendWednesdayReminder();

        verify(telegramFacade).enviarMidia(eq(GROUP_100), eq(MEDIA_FILE), contains("quatro horas"));
    }

    @Test
    @DisplayName("sendReminderToChat: NÃO valida feature flag (comando explícito do admin)")
    void sendReminderToChat_naoValidaFeatureFlag() {
        // Stub intencionalmente órfão: o teste PROVA que sendReminderToChat não consulta a flag.
        lenient().when(featureFlags.isEnabled("weekly.reminder.enabled")).thenReturn(false);
        ReflectionTestUtils.setField(service, "mediaFilePath", MEDIA_FILE);

        service.sendReminderToChat(GROUP_100);

        verify(telegramFacade).enviarMidia(eq(GROUP_100), eq(MEDIA_FILE), anyString());
        // Documenta a intenção: a flag não foi consultada
        verify(featureFlags, never()).isEnabled("weekly.reminder.enabled");
    }

    // =========================================================================
    // TESTES DE INICIALIZAÇÃO
    // =========================================================================

    @Test
    void init_deveCarregarGruposDaPropriedade() {
        String allowedChatsStr = "-100123,-100456";
        ReflectionTestUtils.setField(service, "allowedChatsStr", allowedChatsStr);
        ReflectionTestUtils.setField(service, "mediaFilePath", MEDIA_FILE);

        service.init();

        @SuppressWarnings("unchecked")
        Set<Long> allowedGroups =
                (Set<Long>) ReflectionTestUtils.getField(service, "allowedGroups");
        assertThat(allowedGroups).containsExactlyInAnyOrder(-100123L, -100456L);
        verifyNoInteractions(telegramFacade);
    }

    @Test
    void init_deveIgnorarIdsPositivos() {
        String allowedChatsStr = "-100123,12345,-100456";
        ReflectionTestUtils.setField(service, "allowedChatsStr", allowedChatsStr);

        service.init();

        @SuppressWarnings("unchecked")
        Set<Long> allowedGroups =
                (Set<Long>) ReflectionTestUtils.getField(service, "allowedGroups");
        assertThat(allowedGroups).containsExactlyInAnyOrder(-100123L, -100456L);
    }

    @Test
    void init_deveIgnorarIdsInvalidos() {
        String allowedChatsStr = "-100123,abc,-100456";
        ReflectionTestUtils.setField(service, "allowedChatsStr", allowedChatsStr);

        service.init();

        @SuppressWarnings("unchecked")
        Set<Long> allowedGroups =
                (Set<Long>) ReflectionTestUtils.getField(service, "allowedGroups");
        assertThat(allowedGroups).containsExactlyInAnyOrder(-100123L, -100456L);
    }

    @Test
    void init_deveLogarQuandoNaoHaGrupos() {
        ReflectionTestUtils.setField(service, "allowedChatsStr", "");

        service.init();

        @SuppressWarnings("unchecked")
        Set<Long> allowedGroups =
                (Set<Long>) ReflectionTestUtils.getField(service, "allowedGroups");
        assertThat(allowedGroups).isEmpty();
        verifyNoInteractions(telegramFacade);
    }

    // =========================================================================
    // ENVIO (sendWednesdayReminder)
    // =========================================================================

    @Test
    void sendWednesdayReminder_deveEnviarParaTodosOsGruposComMidia() {
        Set<Long> allowedGroups = Set.of(GROUP_100, GROUP_200);
        ReflectionTestUtils.setField(service, "allowedGroups", allowedGroups);
        ReflectionTestUtils.setField(service, "mediaFilePath", MEDIA_FILE);

        service.sendWednesdayReminder();

        verify(telegramFacade).enviarMidia(eq(GROUP_100), eq(MEDIA_FILE), contains("quatro horas"));
        verify(telegramFacade).enviarMidia(eq(GROUP_200), eq(MEDIA_FILE), contains("quatro horas"));
        verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
    }

    @Test
    void sendWednesdayReminder_deveEnviarSemMidiaQuandoArquivoVazio() {
        Set<Long> allowedGroups = Set.of(GROUP_100);
        ReflectionTestUtils.setField(service, "allowedGroups", allowedGroups);
        ReflectionTestUtils.setField(service, "mediaFilePath", "");

        service.sendWednesdayReminder();

        verify(telegramFacade).enviarMensagemHtml(eq(GROUP_100), contains("quatro horas"));
        verify(telegramFacade, never()).enviarMidia(anyLong(), anyString(), anyString());
    }

    @Test
    void sendWednesdayReminder_deveEnviarSemMidiaQuandoArquivoNull() {
        Set<Long> allowedGroups = Set.of(GROUP_100);
        ReflectionTestUtils.setField(service, "allowedGroups", allowedGroups);
        ReflectionTestUtils.setField(service, "mediaFilePath", null);

        service.sendWednesdayReminder();

        verify(telegramFacade).enviarMensagemHtml(eq(GROUP_100), contains("quatro horas"));
        verify(telegramFacade, never()).enviarMidia(anyLong(), anyString(), anyString());
    }

    @Test
    void sendWednesdayReminder_deveIgnorarSeNaoHaGrupos() {
        Set<Long> allowedGroups = new HashSet<>();
        ReflectionTestUtils.setField(service, "allowedGroups", allowedGroups);

        service.sendWednesdayReminder();

        verifyNoInteractions(telegramFacade);
    }

    @Test
    void sendWednesdayReminder_deveCapturarExcecaoElogar() {
        Set<Long> allowedGroups = Set.of(GROUP_100);
        ReflectionTestUtils.setField(service, "allowedGroups", allowedGroups);
        ReflectionTestUtils.setField(service, "mediaFilePath", MEDIA_FILE);

        doThrow(new RuntimeException("Erro no envio"))
                .when(telegramFacade)
                .enviarMidia(eq(GROUP_100), anyString(), anyString());

        service.sendWednesdayReminder();

        verify(telegramFacade).enviarMidia(eq(GROUP_100), eq(MEDIA_FILE), anyString());
        // Não lança exceção, apenas loga o erro
    }

    // =========================================================================
    // ENVIO (sendReminderToChat)
    // =========================================================================

    @Test
    void sendReminderToChat_deveEnviarParaChatComMidia() {
        long chatId = 12345L;
        ReflectionTestUtils.setField(service, "mediaFilePath", MEDIA_FILE);

        service.sendReminderToChat(chatId);

        verify(telegramFacade).enviarMidia(eq(chatId), eq(MEDIA_FILE), contains("quatro horas"));
        verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
    }

    @Test
    void sendReminderToChat_deveEnviarSemMidiaQuandoArquivoVazio() {
        long chatId = 12345L;
        ReflectionTestUtils.setField(service, "mediaFilePath", "");

        service.sendReminderToChat(chatId);

        verify(telegramFacade).enviarMensagemHtml(eq(chatId), contains("quatro horas"));
        verify(telegramFacade, never()).enviarMidia(anyLong(), anyString(), anyString());
    }

    @Test
    void sendReminderToChat_deveIgnorarQuandoChatIdNull() {
        service.sendReminderToChat(null);

        verifyNoInteractions(telegramFacade);
    }

    @Test
    void sendReminderToChat_deveCapturarExcecaoElogar() {
        long chatId = 12345L;
        ReflectionTestUtils.setField(service, "mediaFilePath", MEDIA_FILE);

        doThrow(new RuntimeException("Erro no envio"))
                .when(telegramFacade)
                .enviarMidia(eq(chatId), anyString(), anyString());

        service.sendReminderToChat(chatId);

        verify(telegramFacade).enviarMidia(eq(chatId), eq(MEDIA_FILE), anyString());
        // Não lança exceção, apenas loga o erro
    }

    // =========================================================================
    // CONFIGURE TASKS
    // =========================================================================

    @Test
    @DisplayName("configureTasks: deve registrar as tasks e invocar getCron cobrindo a lambda")
    void configureTasks_deveRegistrarAsTasksECobrirLambda() {
        lenient()
                .when(jsonConfigLoader.loadConfig(anyString(), eq(Map.class), anyString()))
                .thenReturn(
                        Optional.of(
                                Map.of(
                                        "cron", "0 0 12 * * *",
                                        "updateCron", "0 0 12 * * *",
                                        "hourlyCron", "0 0 12 * * *",
                                        "weeklyCron", "0 0 12 * * *",
                                        "morningCron", "0 0 12 * * *",
                                        "eveningCron", "0 0 12 * * *",
                                        "noonCron", "0 0 12 * * *",
                                        "checkCron", "0 0 12 * * *",
                                        "cleanCron", "0 0 12 * * *")));

        ScheduledTaskRegistrar taskRegistrar = mock(ScheduledTaskRegistrar.class);

        service.configureTasks(taskRegistrar);

        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(taskRegistrar, atLeastOnce())
                .addTriggerTask(any(Runnable.class), triggerCaptor.capture());

        SimpleTriggerContext ctx = new SimpleTriggerContext();
        for (Trigger trigger : triggerCaptor.getAllValues()) {
            trigger.nextExecution(ctx);
        }
    }
}
