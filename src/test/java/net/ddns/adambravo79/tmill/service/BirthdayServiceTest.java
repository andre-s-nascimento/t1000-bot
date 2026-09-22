package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;

import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.model.Birthday;
import net.ddns.adambravo79.tmill.repository.BirthdayRepository;
import net.ddns.adambravo79.tmill.telegram.core.GroupAuthorizationService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;

@ExtendWith(MockitoExtension.class)
class BirthdayServiceTest {

    @Mock private BirthdayRepository repository;
    @Mock private TelegramFacade telegramFacade;
    @Mock private GroupAuthorizationService groupAuthorizationService;
    @Mock private MetricsService metricsService;

    @InjectMocks private BirthdayService service;

    private static final String GIF = "https://example.com/gif.gif";

    private void setGif() {
        ReflectionTestUtils.setField(service, "gifUrl", GIF);
    }

    // =========================
    // REGISTRAR
    // =========================

    @Test
    @DisplayName("registrar: data válida chama upsert e não mexe em métricas")
    void registrar_dataValida_chamaUpsert() {
        String resp = service.registrar(1L, "Fulano", "05/10");

        verify(repository).upsert(1L, "Fulano", 5, 10);
        assertThat(resp).contains("05/10").contains("Aniversário registrado");
        verifyNoInteractions(metricsService);
    }

    @Test
    @DisplayName("registrar: data com hífen aceita")
    void registrar_dataComHifen_aceita() {
        service.registrar(1L, "Fulano", "05-10");
        verify(repository).upsert(1L, "Fulano", 5, 10);
        verifyNoInteractions(metricsService);
    }

    @Test
    @DisplayName("registrar: data sem zero à esquerda aceita")
    void registrar_dataSemZeroAEsquerda_aceita() {
        service.registrar(1L, "Fulano", "5/10");
        verify(repository).upsert(1L, "Fulano", 5, 10);
    }

    @Test
    @DisplayName("registrar: texto com data extrai a data")
    void registrar_dataComTextoExtra_extraiData() {
        service.registrar(1L, "Fulano", "meu aniversario é 05/10 valeu");
        verify(repository).upsert(1L, "Fulano", 5, 10);
    }

    @Test
    @DisplayName("registrar: texto vazio retorna formato inválido")
    void registrar_textoVazio_retornaFormatoInvalido() {
        String resp = service.registrar(1L, "Fulano", "");
        assertThat(resp).isEqualTo(BotMessages.ANIVERSARIO_FORMATO_INVALIDO);
        verifyNoInteractions(repository, metricsService);
    }

    @Test
    @DisplayName("registrar: texto nulo retorna formato inválido")
    void registrar_textoNulo_retornaFormatoInvalido() {
        String resp = service.registrar(1L, "Fulano", null);
        assertThat(resp).isEqualTo(BotMessages.ANIVERSARIO_FORMATO_INVALIDO);
        verifyNoInteractions(repository, metricsService);
    }

    @Test
    @DisplayName("registrar: texto sem data retorna formato inválido")
    void registrar_textoSemData_retornaFormatoInvalido() {
        String resp = service.registrar(1L, "Fulano", "qualquer coisa");
        assertThat(resp).isEqualTo(BotMessages.ANIVERSARIO_FORMATO_INVALIDO);
        verifyNoInteractions(repository, metricsService);
    }

    @Test
    @DisplayName("registrar: data inválida (31/02) retorna erro")
    void registrar_dataInvalida_retornaErro() {
        String resp = service.registrar(1L, "Fulano", "31/02");
        assertThat(resp).isEqualTo(BotMessages.ANIVERSARIO_DATA_INVALIDA);
        verifyNoInteractions(repository, metricsService);
    }

    @Test
    @DisplayName("registrar: mês 13 retorna erro")
    void registrar_mes13_retornaErro() {
        String resp = service.registrar(1L, "Fulano", "05/13");
        assertThat(resp).isEqualTo(BotMessages.ANIVERSARIO_DATA_INVALIDA);
    }

    @Test
    @DisplayName("registrar: dia 32 retorna erro")
    void registrar_dia32_retornaErro() {
        String resp = service.registrar(1L, "Fulano", "32/10");
        assertThat(resp).isEqualTo(BotMessages.ANIVERSARIO_DATA_INVALIDA);
    }

    @Test
    @DisplayName("registrar: 29/02 é aceito (ano bissexto)")
    void registrar_29Fevereiro_aceita() {
        service.registrar(1L, "Fulano", "29/02");
        verify(repository).upsert(1L, "Fulano", 29, 2);
    }

    // =========================
    // isDataValida
    // =========================

    @Test
    @DisplayName("isDataValida: casos-limite corretos")
    void isDataValida_casosLimite() {
        assertThat(service.isDataValida(1, 1)).isTrue();
        assertThat(service.isDataValida(31, 12)).isTrue();
        assertThat(service.isDataValida(29, 2)).isTrue();
        assertThat(service.isDataValida(30, 2)).isFalse();
        assertThat(service.isDataValida(31, 4)).isFalse();
        assertThat(service.isDataValida(31, 6)).isFalse();
        assertThat(service.isDataValida(31, 9)).isFalse();
        assertThat(service.isDataValida(31, 11)).isFalse();
        assertThat(service.isDataValida(0, 1)).isFalse();
        assertThat(service.isDataValida(1, 0)).isFalse();
        assertThat(service.isDataValida(1, 13)).isFalse();
    }

    // =========================
    // ENVIAR PARABÉNS — SUCESSO
    // =========================

    @Test
    @DisplayName("enviarParabensPara: privado + grupos registra 1 success")
    void enviarParabensPara_privadoEGrupos_registraSuccess() {
        setGif();
        Birthday b = new Birthday(1L, 100L, "Fulano", 5, 10, null);
        when(repository.findByDayAndMonth(5, 10)).thenReturn(List.of(b));
        when(groupAuthorizationService.getAllowedGroups()).thenReturn(Set.of(-100L, -200L));

        int enviados = service.enviarParabensPara(5, 10);

        assertThat(enviados).isEqualTo(1);
        verify(telegramFacade).enviarMidia(eq(100L), eq(GIF), contains("Fulano"));
        verify(telegramFacade).enviarMidia(eq(-100L), eq(GIF), contains("Fulano"));
        verify(telegramFacade).enviarMidia(eq(-200L), eq(GIF), contains("Fulano"));
        verify(repository).markSent(eq(100L), anyInt());

        verify(metricsService).success("aniversario_enviado");
        verify(metricsService, never()).error("aniversario_enviado");
    }

    @Test
    @DisplayName("enviarParabensPara: sem grupos, só privado, ainda registra success")
    void enviarParabensPara_semGrupos_soPrivado_registraSuccess() {
        setGif();
        Birthday b = new Birthday(1L, 100L, "Fulano", 5, 10, null);
        when(repository.findByDayAndMonth(5, 10)).thenReturn(List.of(b));
        when(groupAuthorizationService.getAllowedGroups()).thenReturn(Set.of());

        int enviados = service.enviarParabensPara(5, 10);

        assertThat(enviados).isEqualTo(1);
        verify(metricsService).success("aniversario_enviado");
    }

    @Test
    @DisplayName("enviarParabensPara: Forbidden no privado mas sucesso em grupo registra success")
    void enviarParabensPara_forbiddenNoPrivado_sucessoEmGrupo_registraSuccess() {
        setGif();
        Birthday b = new Birthday(1L, 100L, "Fulano", 5, 10, null);
        when(repository.findByDayAndMonth(5, 10)).thenReturn(List.of(b));
        when(groupAuthorizationService.getAllowedGroups()).thenReturn(Set.of(-100L));

        doThrow(
                        HttpClientErrorException.create(
                                HttpStatus.FORBIDDEN, "Forbidden", null, null, null))
                .when(telegramFacade)
                .enviarMidia(eq(100L), anyString(), anyString());
        doNothing().when(telegramFacade).enviarMidia(eq(-100L), anyString(), anyString());

        int enviados = service.enviarParabensPara(5, 10);

        assertThat(enviados).isEqualTo(1);
        verify(metricsService).success("aniversario_enviado");
        verify(metricsService, never()).error("aniversario_enviado");
    }

    @Test
    @DisplayName("enviarParabensPara: falha em um grupo continua nos outros e registra success")
    void enviarParabensPara_falhaEmUmGrupo_continua_registraSuccess() {
        setGif();
        Birthday b = new Birthday(1L, 100L, "Fulano", 5, 10, null);
        when(repository.findByDayAndMonth(5, 10)).thenReturn(List.of(b));
        when(groupAuthorizationService.getAllowedGroups()).thenReturn(Set.of(-100L, -200L));

        doNothing().when(telegramFacade).enviarMidia(eq(100L), anyString(), anyString());
        doNothing().when(telegramFacade).enviarMidia(eq(-200L), anyString(), anyString());
        doThrow(new RuntimeException("boom"))
                .when(telegramFacade)
                .enviarMidia(eq(-100L), anyString(), anyString());

        int enviados = service.enviarParabensPara(5, 10);

        assertThat(enviados).isEqualTo(1);
        verify(metricsService).success("aniversario_enviado");
    }

    // =========================
    // ENVIAR PARABÉNS — ERRO
    // =========================

    @Test
    @DisplayName("enviarParabensPara: falha total (privado e grupos) registra error")
    void enviarParabensPara_falhaTotal_registraError() {
        setGif();
        Birthday b = new Birthday(1L, 100L, "Fulano", 5, 10, null);
        when(repository.findByDayAndMonth(5, 10)).thenReturn(List.of(b));
        when(groupAuthorizationService.getAllowedGroups()).thenReturn(Set.of(-100L));

        doThrow(new RuntimeException("boom"))
                .when(telegramFacade)
                .enviarMidia(anyLong(), anyString(), anyString());

        assertThatCode(() -> service.enviarParabensPara(5, 10)).doesNotThrowAnyException();

        verify(metricsService).error("aniversario_enviado");
        verify(metricsService, never()).success("aniversario_enviado");
        verify(repository, never()).markSent(anyLong(), anyInt());
    }

    @Test
    @DisplayName("enviarParabensPara: Forbidden no privado + sem grupos registra error")
    void enviarParabensPara_forbiddenSemGrupos_registraError() {
        setGif();
        Birthday b = new Birthday(1L, 100L, "Fulano", 5, 10, null);
        when(repository.findByDayAndMonth(5, 10)).thenReturn(List.of(b));
        when(groupAuthorizationService.getAllowedGroups()).thenReturn(Set.of());

        doThrow(
                        HttpClientErrorException.create(
                                HttpStatus.FORBIDDEN, "Forbidden", null, null, null))
                .when(telegramFacade)
                .enviarMidia(eq(100L), anyString(), anyString());

        int enviados = service.enviarParabensPara(5, 10);

        assertThat(enviados).isZero();
        verify(metricsService).error("aniversario_enviado");
        verify(metricsService, never()).success("aniversario_enviado");
    }

    // =========================
    // NÃO ENVIA = NÃO REGISTRA
    // =========================

    @Test
    @DisplayName("enviarParabensPara: já enviado este ano não registra métrica")
    void enviarParabensPara_jaEnviadoNoAno_naoRegistraMetrica() {
        setGif();
        int year = LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE)).getYear();
        Birthday b = new Birthday(1L, 100L, "Fulano", 5, 10, year);
        when(repository.findByDayAndMonth(5, 10)).thenReturn(List.of(b));

        int enviados = service.enviarParabensPara(5, 10);

        assertThat(enviados).isZero();
        verifyNoInteractions(telegramFacade, metricsService);
    }

    @Test
    @DisplayName("enviarParabensPara: sem aniversariantes não registra métrica")
    void enviarParabensPara_semAniversariantes_naoRegistraMetrica() {
        when(repository.findByDayAndMonth(5, 10)).thenReturn(List.of());

        int enviados = service.enviarParabensPara(5, 10);

        assertThat(enviados).isZero();
        verifyNoInteractions(telegramFacade, metricsService);
    }

    // =========================
    // MÚLTIPLOS ANIVERSARIANTES
    // =========================

    @Test
    @DisplayName("enviarParabensPara: N aniversariantes registram N métricas")
    void enviarParabensPara_multiplosAniversariantes_registraUmaPorUsuario() {
        setGif();
        Birthday b1 = new Birthday(1L, 100L, "Fulano", 5, 10, null);
        Birthday b2 = new Birthday(2L, 200L, "Beltrano", 5, 10, null);
        when(repository.findByDayAndMonth(5, 10)).thenReturn(List.of(b1, b2));
        when(groupAuthorizationService.getAllowedGroups()).thenReturn(Set.of(-100L));

        int enviados = service.enviarParabensPara(5, 10);

        assertThat(enviados).isEqualTo(2);
        verify(metricsService, times(2)).success("aniversario_enviado");
        verify(metricsService, never()).error("aniversario_enviado");
    }

    @Test
    @DisplayName("enviarParabensPara: 1 sucesso + 1 falha registram ambos")
    void enviarParabensPara_umSucessoUmFalha_registraAmbos() {
        setGif();
        Birthday b1 = new Birthday(1L, 100L, "Fulano", 5, 10, null);
        Birthday b2 = new Birthday(2L, 200L, "Beltrano", 5, 10, null);
        when(repository.findByDayAndMonth(5, 10)).thenReturn(List.of(b1, b2));
        when(groupAuthorizationService.getAllowedGroups()).thenReturn(Set.of());

        // Fulano: sucesso; Beltrano: falha
        doNothing().when(telegramFacade).enviarMidia(eq(100L), anyString(), anyString());
        doThrow(new RuntimeException("boom"))
                .when(telegramFacade)
                .enviarMidia(eq(200L), anyString(), anyString());

        service.enviarParabensPara(5, 10);

        verify(metricsService, times(1)).success("aniversario_enviado");
        verify(metricsService, times(1)).error("aniversario_enviado");
    }

    // =========================
    // MENSAGEM E MENÇÃO
    // =========================

    @Test
    @DisplayName("buildMencaoHtml: usa tg://user?id e nome escapado")
    void buildMencaoHtml_usaIdEHtml() {
        Birthday b = new Birthday(1L, 999L, "Fulano", 5, 10, null);
        String mencao = service.buildMencaoHtml(b);
        assertThat(mencao).contains("tg://user?id=999").contains(">Fulano<").startsWith("<a");
    }

    @Test
    @DisplayName("buildMencaoHtml: nome com HTML é escapado")
    void buildMencaoHtml_escapaNome() {
        Birthday b = new Birthday(1L, 999L, "Fulano <script>", 5, 10, null);
        String mencao = service.buildMencaoHtml(b);
        assertThat(mencao).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("buildMencaoHtml: nome nulo usa 'amigo(a)'")
    void buildMencaoHtml_nomeNulo_usaFallback() {
        Birthday b = new Birthday(1L, 999L, null, 5, 10, null);
        String mencao = service.buildMencaoHtml(b);
        assertThat(mencao).contains("amigo(a)");
    }

    @Test
    @DisplayName("buildMensagemParabens: inclui nome, Marilyn e ano 1962")
    void buildMensagemParabens_contemNomeEMarilyn() {
        String msg = service.buildMensagemParabens("Fulano");
        assertThat(msg)
                .contains("Fulano")
                .contains("Marilyn Monroe")
                .contains("1962")
                .contains("HAPPY BIRTHDAY");
    }
}
