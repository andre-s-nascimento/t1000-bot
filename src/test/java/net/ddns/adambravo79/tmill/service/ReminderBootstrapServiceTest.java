package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import net.ddns.adambravo79.tmill.dto.ReminderJson;
import net.ddns.adambravo79.tmill.model.Reminder;
import net.ddns.adambravo79.tmill.repository.ReminderRepository;
import net.ddns.adambravo79.tmill.service.config.JsonConfigLoader;

@ExtendWith(MockitoExtension.class)
class ReminderBootstrapServiceTest {

    @Mock private ReminderRepository repository;
    @Mock private ReminderService reminderService;
    @Mock private JsonConfigLoader jsonConfigLoader;
    @Mock private Environment environment;

    @InjectMocks private ReminderBootstrapService service;

    @Test
    void bootstrapDesabilitado_naoFazNada() {
        ReflectionTestUtils.setField(service, "bootstrapEnabled", false);
        service.run(null);
        verifyNoInteractions(repository, reminderService, jsonConfigLoader);
    }

    @Test
    void tabelaNaoVazia_naoFazNada() {
        ReflectionTestUtils.setField(service, "bootstrapEnabled", true);
        when(repository.count()).thenReturn(5);

        service.run(null);

        verify(repository).count();
        verifyNoInteractions(reminderService, jsonConfigLoader);
    }

    @Test
    void jsonAusente_naoFazNada() {
        ReflectionTestUtils.setField(service, "bootstrapEnabled", true);
        ReflectionTestUtils.setField(service, "remindersFile", "classpath:reminders.json");
        when(repository.count()).thenReturn(0);
        when(jsonConfigLoader.loadConfig(anyString(), eq(ReminderJson.class), any()))
                .thenReturn(Optional.empty());

        service.run(null);

        verifyNoInteractions(reminderService);
    }

    @Test
    void listaVazia_naoFazNada() {
        ReflectionTestUtils.setField(service, "bootstrapEnabled", true);
        ReflectionTestUtils.setField(service, "remindersFile", "classpath:reminders.json");
        when(repository.count()).thenReturn(0);
        when(jsonConfigLoader.loadConfig(anyString(), eq(ReminderJson.class), any()))
                .thenReturn(Optional.of(ReminderJson.empty()));

        service.run(null);

        verifyNoInteractions(reminderService);
    }

    @Test
    void sucesso_importa() {
        ReflectionTestUtils.setField(service, "bootstrapEnabled", true);
        ReflectionTestUtils.setField(service, "remindersFile", "classpath:reminders.json");
        Reminder r =
                new Reminder(
                        "a",
                        "A",
                        true,
                        "0 0 * * * *",
                        "America/Sao_Paulo",
                        "msg",
                        null,
                        List.of(),
                        List.of());
        when(repository.count()).thenReturn(0);
        when(jsonConfigLoader.loadConfig(anyString(), eq(ReminderJson.class), any()))
                .thenReturn(Optional.of(ReminderJson.of(List.of(r))));

        service.run(null);

        verify(reminderService).importAll(List.of(r), false);
    }

    @Test
    void excecao_capturada() {
        ReflectionTestUtils.setField(service, "bootstrapEnabled", true);
        ReflectionTestUtils.setField(service, "remindersFile", "classpath:reminders.json");
        when(repository.count()).thenReturn(0);
        when(jsonConfigLoader.loadConfig(anyString(), eq(ReminderJson.class), any()))
                .thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> service.run(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("bootstrap resolve ${VAR} simples no mediaUrl")
    void bootstrap_resolvePlaceholderSimples() {
        ReflectionTestUtils.setField(service, "bootstrapEnabled", true);
        ReflectionTestUtils.setField(service, "remindersFile", "classpath:reminders.json");

        Reminder r = reminderComMediaUrl("${QUATRO_HORAS_QUARTA_FILE}");
        when(repository.count()).thenReturn(0);
        when(jsonConfigLoader.loadConfig(anyString(), eq(ReminderJson.class), any()))
                .thenReturn(Optional.of(ReminderJson.of(List.of(r))));
        when(environment.getProperty("QUATRO_HORAS_QUARTA_FILE", ""))
                .thenReturn("https://exemplo.com/video.mp4");

        service.run(null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Reminder>> captor = ArgumentCaptor.forClass(List.class);
        verify(reminderService).importAll(captor.capture(), eq(false));

        Reminder resolvido = captor.getValue().get(0);
        assertThat(resolvido.mediaUrl()).isEqualTo("https://exemplo.com/video.mp4");
    }

    @Test
    @DisplayName("bootstrap resolve ${VAR:default} quando VAR está ausente")
    void bootstrap_resolvePlaceholderComDefault() {
        ReflectionTestUtils.setField(service, "bootstrapEnabled", true);
        ReflectionTestUtils.setField(service, "remindersFile", "classpath:reminders.json");

        Reminder r =
                reminderComMediaUrl("${QUATRO_HORAS_QUARTA_FILE:https://fallback.com/video.mp4}");
        when(repository.count()).thenReturn(0);
        when(jsonConfigLoader.loadConfig(anyString(), eq(ReminderJson.class), any()))
                .thenReturn(Optional.of(ReminderJson.of(List.of(r))));
        // VAR ausente → retorna o default
        when(environment.getProperty("QUATRO_HORAS_QUARTA_FILE", "https://fallback.com/video.mp4"))
                .thenReturn("https://fallback.com/video.mp4");

        service.run(null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Reminder>> captor = ArgumentCaptor.forClass(List.class);
        verify(reminderService).importAll(captor.capture(), eq(false));

        assertThat(captor.getValue().get(0).mediaUrl()).isEqualTo("https://fallback.com/video.mp4");
    }

    @Test
    @DisplayName("bootstrap substitui ${VAR} ausente por vazio e loga warn")
    void bootstrap_placeholderAusenteSemDefault_ficaVazio() {
        ReflectionTestUtils.setField(service, "bootstrapEnabled", true);
        ReflectionTestUtils.setField(service, "remindersFile", "classpath:reminders.json");

        Reminder r = reminderComMediaUrl("${VAR_INEXISTENTE}");
        when(repository.count()).thenReturn(0);
        when(jsonConfigLoader.loadConfig(anyString(), eq(ReminderJson.class), any()))
                .thenReturn(Optional.of(ReminderJson.of(List.of(r))));
        when(environment.getProperty("VAR_INEXISTENTE", "")).thenReturn("");

        service.run(null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Reminder>> captor = ArgumentCaptor.forClass(List.class);
        verify(reminderService).importAll(captor.capture(), eq(false));

        assertThat(captor.getValue().get(0).mediaUrl()).isEmpty();
    }

    @Test
    @DisplayName("bootstrap NÃO mexe em mediaUrl sem placeholder (retorna o mesmo record)")
    void bootstrap_semPlaceholder_naoAltera() {
        ReflectionTestUtils.setField(service, "bootstrapEnabled", true);
        ReflectionTestUtils.setField(service, "remindersFile", "classpath:reminders.json");

        Reminder r = reminderComMediaUrl("https://exemplo.com/direto.mp4");
        when(repository.count()).thenReturn(0);
        when(jsonConfigLoader.loadConfig(anyString(), eq(ReminderJson.class), any()))
                .thenReturn(Optional.of(ReminderJson.of(List.of(r))));

        service.run(null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Reminder>> captor = ArgumentCaptor.forClass(List.class);
        verify(reminderService).importAll(captor.capture(), eq(false));

        // Sem placeholder → não consulta Environment e mantém o valor original
        assertThat(captor.getValue().get(0).mediaUrl()).isEqualTo("https://exemplo.com/direto.mp4");
        verify(environment, never()).getProperty(anyString(), anyString());
    }

    @Test
    @DisplayName("bootstrap resolve ${VAR} em múltiplos campos (name, message, mediaUrl)")
    void bootstrap_resolveEmMultiplosCampos() {
        ReflectionTestUtils.setField(service, "bootstrapEnabled", true);
        ReflectionTestUtils.setField(service, "remindersFile", "classpath:reminders.json");

        Reminder r =
                new Reminder(
                        "quatro-horas-quarta",
                        "${APP_NAME}",
                        true,
                        "0 0 16 * * WED",
                        "America/Sao_Paulo",
                        "Mensagem ${VERSAO}",
                        "${QUATRO_HORAS_QUARTA_FILE}",
                        List.of("-100"),
                        List.of());
        when(repository.count()).thenReturn(0);
        when(jsonConfigLoader.loadConfig(anyString(), eq(ReminderJson.class), any()))
                .thenReturn(Optional.of(ReminderJson.of(List.of(r))));

        when(environment.getProperty("APP_NAME", "")).thenReturn("T-1000 Bot");
        when(environment.getProperty("VERSAO", "")).thenReturn("2.11.0");
        when(environment.getProperty("QUATRO_HORAS_QUARTA_FILE", ""))
                .thenReturn("https://exemplo.com/video.mp4");

        service.run(null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Reminder>> captor = ArgumentCaptor.forClass(List.class);
        verify(reminderService).importAll(captor.capture(), eq(false));

        Reminder resolvido = captor.getValue().get(0);
        assertThat(resolvido.name()).isEqualTo("T-1000 Bot");
        assertThat(resolvido.message()).isEqualTo("Mensagem 2.11.0");
        assertThat(resolvido.mediaUrl()).isEqualTo("https://exemplo.com/video.mp4");
    }

    @Test
    @DisplayName("bootstrap lida com mediaUrl null sem quebrar")
    void bootstrap_mediaUrlNull_naoQuebra() {
        ReflectionTestUtils.setField(service, "bootstrapEnabled", true);
        ReflectionTestUtils.setField(service, "remindersFile", "classpath:reminders.json");

        Reminder r = reminderComMediaUrl(null);
        when(repository.count()).thenReturn(0);
        when(jsonConfigLoader.loadConfig(anyString(), eq(ReminderJson.class), any()))
                .thenReturn(Optional.of(ReminderJson.of(List.of(r))));

        service.run(null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Reminder>> captor = ArgumentCaptor.forClass(List.class);
        verify(reminderService).importAll(captor.capture(), eq(false));

        assertThat(captor.getValue().get(0).mediaUrl()).isNull();
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private static Reminder reminderComMediaUrl(String mediaUrl) {
        return new Reminder(
                "quatro-horas-quarta",
                "Quatro Horas de Quarta",
                true,
                "0 0 16 * * WED",
                "America/Sao_Paulo",
                "<b>Msg</b>",
                mediaUrl,
                List.of("-100"),
                List.of());
    }
}
