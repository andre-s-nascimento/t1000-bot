package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;

@ExtendWith(MockitoExtension.class)
class WeeklyReminderServiceTest {

    @Mock private TelegramFacade telegramFacade;

    @InjectMocks private WeeklyReminderService service;

    @BeforeEach
    void setUp() {
        Set<Long> newAllowedGroups = new HashSet<>();
        ReflectionTestUtils.setField(service, "allowedGroups", newAllowedGroups);
    }

    // =========================
    // TESTES DE INICIALIZAÇÃO
    // =========================

    @Test
    void init_deveCarregarGruposDaPropriedade() {
        String allowedChatsStr = "-100123,-100456";
        ReflectionTestUtils.setField(service, "allowedChatsStr", allowedChatsStr);
        ReflectionTestUtils.setField(service, "mediaFilePath", "/app/media/video.mp4");

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

    // =========================
    // TESTES DE ENVIO (sendWednesdayReminder)
    // =========================

    @Test
    void sendWednesdayReminder_deveEnviarParaTodosOsGruposComMidia() {
        Set<Long> allowedGroups = Set.of(-100L, -200L);
        ReflectionTestUtils.setField(service, "allowedGroups", allowedGroups);
        ReflectionTestUtils.setField(service, "mediaFilePath", "/app/media/video.mp4");

        service.sendWednesdayReminder();

        verify(telegramFacade)
                .enviarMidia(eq(-100L), eq("/app/media/video.mp4"), contains("quatro horas"));
        verify(telegramFacade)
                .enviarMidia(eq(-200L), eq("/app/media/video.mp4"), contains("quatro horas"));
        verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
    }

    @Test
    void sendWednesdayReminder_deveEnviarSemMidiaQuandoArquivoVazio() {
        Set<Long> allowedGroups = Set.of(-100L);
        ReflectionTestUtils.setField(service, "allowedGroups", allowedGroups);
        ReflectionTestUtils.setField(service, "mediaFilePath", "");

        service.sendWednesdayReminder();

        verify(telegramFacade).enviarMensagemHtml(eq(-100L), contains("quatro horas"));
        verify(telegramFacade, never()).enviarMidia(anyLong(), anyString(), anyString());
    }

    @Test
    void sendWednesdayReminder_deveEnviarSemMidiaQuandoArquivoNull() {
        Set<Long> allowedGroups = Set.of(-100L);
        ReflectionTestUtils.setField(service, "allowedGroups", allowedGroups);
        ReflectionTestUtils.setField(service, "mediaFilePath", null);

        service.sendWednesdayReminder();

        verify(telegramFacade).enviarMensagemHtml(eq(-100L), contains("quatro horas"));
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
        Set<Long> allowedGroups = Set.of(-100L);
        ReflectionTestUtils.setField(service, "allowedGroups", allowedGroups);
        ReflectionTestUtils.setField(service, "mediaFilePath", "/app/media/video.mp4");

        doThrow(new RuntimeException("Erro no envio"))
                .when(telegramFacade)
                .enviarMidia(eq(-100L), anyString(), anyString());

        service.sendWednesdayReminder();

        verify(telegramFacade).enviarMidia(eq(-100L), eq("/app/media/video.mp4"), anyString());
        // Não lança exceção, apenas loga o erro
    }

    // =========================
    // TESTES DE ENVIO (sendReminderToChat)
    // =========================

    @Test
    void sendReminderToChat_deveEnviarParaChatComMidia() {
        long chatId = 12345L;
        ReflectionTestUtils.setField(service, "mediaFilePath", "/app/media/video.mp4");

        service.sendReminderToChat(chatId);

        verify(telegramFacade)
                .enviarMidia(eq(chatId), eq("/app/media/video.mp4"), contains("quatro horas"));
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
        ReflectionTestUtils.setField(service, "mediaFilePath", "/app/media/video.mp4");

        doThrow(new RuntimeException("Erro no envio"))
                .when(telegramFacade)
                .enviarMidia(eq(chatId), anyString(), anyString());

        service.sendReminderToChat(chatId);

        verify(telegramFacade).enviarMidia(eq(chatId), eq("/app/media/video.mp4"), anyString());
        // Não lança exceção, apenas loga o erro
    }
}
