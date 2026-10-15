package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

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

import net.ddns.adambravo79.tmill.service.config.JsonConfigLoader;

@ExtendWith(MockitoExtension.class)
class BirthdaySchedulerTest {

    @Mock private BirthdayService birthdayService;
    @Mock private JsonConfigLoader jsonConfigLoader;
    @InjectMocks private BirthdayScheduler service;

    @Test
    @DisplayName("dispararParabens chama birthdayService com dia/mês de hoje")
    void dispararParabens_chamaServiceComDataAtual() {
        when(birthdayService.enviarParabensPara(anyInt(), anyInt())).thenReturn(2);

        service.dispararParabens();

        ArgumentCaptor<Integer> diaCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> mesCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(birthdayService).enviarParabensPara(diaCaptor.capture(), mesCaptor.capture());

        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        assertThat(diaCaptor.getValue()).isEqualTo(hoje.getDayOfMonth());
        assertThat(mesCaptor.getValue()).isEqualTo(hoje.getMonthValue());
    }

    @Test
    @DisplayName("dispararParabens com exceção do service apenas loga")
    void dispararParabens_comExcecao_naoLanca() {
        when(birthdayService.enviarParabensPara(anyInt(), anyInt()))
                .thenThrow(new RuntimeException("boom"));

        // Não deve lançar
        assertThatCode(() -> service.dispararParabens()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("configureTasks: deve registrar as tasks e invocar getCron cobrindo a lambda")
    void configureTasks_deveRegistrarAsTasksECobrirLambda() {
        // Preparamos o mock do jsonConfigLoader com todas as chaves possíveis
        // para que este teste seja reaproveitável em todas as classes
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

        // Aciona o método que registra as tarefas
        service.configureTasks(taskRegistrar);

        // Captura as triggers que foram adicionadas
        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(taskRegistrar, atLeastOnce())
                .addTriggerTask(any(Runnable.class), triggerCaptor.capture());

        // Aciona a lambda de cada trigger para cobrir o código do getCron()
        // 🔧 FIX: Usar SimpleTriggerContext em vez de mock(TriggerContext.class)
        SimpleTriggerContext ctx = new SimpleTriggerContext();
        for (Trigger trigger : triggerCaptor.getAllValues()) {
            trigger.nextExecution(ctx);
        }
    }
}
