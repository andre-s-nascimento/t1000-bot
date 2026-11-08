package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
}
