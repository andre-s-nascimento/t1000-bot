package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BirthdaySchedulerTest {

    @Mock private BirthdayService birthdayService;
    @InjectMocks private BirthdayScheduler scheduler;

    @Test
    @DisplayName("dispararParabens chama birthdayService com dia/mês de hoje")
    void dispararParabens_chamaServiceComDataAtual() {
        when(birthdayService.enviarParabensPara(anyInt(), anyInt())).thenReturn(2);

        scheduler.dispararParabens();

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
        assertThatCode(() -> scheduler.dispararParabens()).doesNotThrowAnyException();
    }
}
