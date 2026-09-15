package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

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

@ExtendWith(MockitoExtension.class)
class BirthdayServiceTest {

    @Mock private BirthdayRepository repository;
    @Mock private TelegramFacade telegramFacade;
    @Mock private GroupAuthorizationService groupAuthorizationService;

    @InjectMocks private BirthdayService service;

    private static final String GIF = "https://example.com/gif.gif";

    private void setGif() {
        ReflectionTestUtils.setField(service, "gifUrl", GIF);
    }

    // =========================
    // REGISTRAR
    // =========================

    @Test
    void registrar_dataValida_chamaUpsert() {
        String resp = service.registrar(1L, "Fulano", "05/10");
        verify(repository).upsert(1L, "Fulano", 5, 10);
        assertThat(resp).contains("05/10").contains("Aniversário registrado");
    }

    @Test
    void registrar_dataComHifen_aceita() {
        service.registrar(1L, "Fulano", "05-10");
        verify(repository).upsert(1L, "Fulano", 5, 10);
    }

    @Test
    void registrar_dataSemZeroAEsquerda_aceita() {
        service.registrar(1L, "Fulano", "5/10");
        verify(repository).upsert(1L, "Fulano", 5, 10);
    }

    @Test
    void registrar_dataComTextoExtra_extraiData() {
        service.registrar(1L, "Fulano", "meu aniversario é 05/10 valeu");
        verify(repository).upsert(1L, "Fulano", 5, 10);
    }

    @Test
    void registrar_textoVazio_retornaFormatoInvalido() {
        String resp = service.registrar(1L, "Fulano", "");
        assertThat(resp).isEqualTo(BotMessages.ANIVERSARIO_FORMATO_INVALIDO);
        verifyNoInteractions(repository);
    }

    @Test
    void registrar_textoNulo_retornaFormatoInvalido() {
        String resp = service.registrar(1L, "Fulano", null);
        assertThat(resp).isEqualTo(BotMessages.ANIVERSARIO_FORMATO_INVALIDO);
        verifyNoInteractions(repository);
    }

    @Test
    void registrar_textoSemData_retornaFormatoInvalido() {
        String resp = service.registrar(1L, "Fulano", "qualquer coisa");
        assertThat(resp).isEqualTo(BotMessages.ANIVERSARIO_FORMATO_INVALIDO);
        verifyNoInteractions(repository);
    }

    @Test
    void registrar_dataInvalida_retornaErro() {
        String resp = service.registrar(1L, "Fulano", "31/02");
        assertThat(resp).isEqualTo(BotMessages.ANIVERSARIO_DATA_INVALIDA);
        verifyNoInteractions(repository);
    }

    @Test
    void registrar_mes13_retornaErro() {
        String resp = service.registrar(1L, "Fulano", "05/13");
        assertThat(resp).isEqualTo(BotMessages.ANIVERSARIO_DATA_INVALIDA);
        verifyNoInteractions(repository);
    }

    @Test
    void registrar_dia32_retornaErro() {
        String resp = service.registrar(1L, "Fulano", "32/10");
        assertThat(resp).isEqualTo(BotMessages.ANIVERSARIO_DATA_INVALIDA);
    }

    @Test
    void registrar_29Fevereiro_aceita() {
        service.registrar(1L, "Fulano", "29/02");
        verify(repository).upsert(1L, "Fulano", 29, 2);
    }

    // =========================
    // isDataValida
    // =========================

    @Test
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
    // ENVIAR PARABÉNS — PRIVADO + GRUPOS
    // =========================

    @Test
    void enviarParabensPara_enviaPrivadoEDepoisGrupos() {
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
    }

    @Test
    void enviarParabensPara_semGrupos_soEnviaPrivado() {
        setGif();
        Birthday b = new Birthday(1L, 100L, "Fulano", 5, 10, null);
        when(repository.findByDayAndMonth(5, 10)).thenReturn(List.of(b));
        when(groupAuthorizationService.getAllowedGroups()).thenReturn(Set.of());

        int enviados = service.enviarParabensPara(5, 10);

        assertThat(enviados).isEqualTo(1);
        verify(telegramFacade).enviarMidia(eq(100L), eq(GIF), anyString());
        verify(telegramFacade, times(1)).enviarMidia(anyLong(), anyString(), anyString());
    }

    @Test
    void enviarParabensPara_forbiddenNoPrivado_aindaEnviaNoGrupo() {
        setGif();
        Birthday b = new Birthday(1L, 100L, "Fulano", 5, 10, null);
        when(repository.findByDayAndMonth(5, 10)).thenReturn(List.of(b));
        when(groupAuthorizationService.getAllowedGroups()).thenReturn(Set.of(-100L));

        doThrow(
                        HttpClientErrorException.create(
                                HttpStatus.FORBIDDEN, "Forbidden", null, null, null))
                .when(telegramFacade)
                .enviarMidia(eq(100L), anyString(), anyString());

        int enviados = service.enviarParabensPara(5, 10);

        assertThat(enviados).isEqualTo(1);
        verify(telegramFacade).enviarMidia(eq(100L), anyString(), anyString());
        verify(telegramFacade).enviarMidia(eq(-100L), anyString(), anyString());
        verify(repository).markSent(eq(100L), anyInt());
    }

    @Test
    void enviarParabensPara_falhaEmUmGrupo_continuaNosOutros() {
        setGif();
        Birthday b = new Birthday(1L, 100L, "Fulano", 5, 10, null);
        when(repository.findByDayAndMonth(5, 10)).thenReturn(List.of(b));
        when(groupAuthorizationService.getAllowedGroups()).thenReturn(Set.of(-100L, -200L));

        doThrow(new RuntimeException("boom"))
                .when(telegramFacade)
                .enviarMidia(eq(-100L), anyString(), anyString());

        int enviados = service.enviarParabensPara(5, 10);

        assertThat(enviados).isEqualTo(1);
        verify(telegramFacade).enviarMidia(eq(-200L), anyString(), anyString());
        verify(repository).markSent(eq(100L), anyInt());
    }

    @Test
    void enviarParabensPara_jaEnviadoNoAno_naoReenvia() {
        setGif();
        int year = LocalDate.now(ZoneId.of(BotMessages.BRAZIL_ZONE)).getYear();
        Birthday b = new Birthday(1L, 100L, "Fulano", 5, 10, year);
        when(repository.findByDayAndMonth(5, 10)).thenReturn(List.of(b));

        int enviados = service.enviarParabensPara(5, 10);

        assertThat(enviados).isZero();
        verify(telegramFacade, never()).enviarMidia(anyLong(), anyString(), anyString());
        verify(repository, never()).markSent(anyLong(), anyInt());
    }

    @Test
    void enviarParabensPara_semAniversariantes_naoEnvia() {
        when(repository.findByDayAndMonth(5, 10)).thenReturn(List.of());
        int enviados = service.enviarParabensPara(5, 10);
        assertThat(enviados).isZero();
        verifyNoInteractions(telegramFacade, groupAuthorizationService);
    }

    @Test
    void enviarParabensPara_multiplosAniversariantes_enviaParaTodos() {
        setGif();
        Birthday b1 = new Birthday(1L, 100L, "Fulano", 5, 10, null);
        Birthday b2 = new Birthday(2L, 200L, "Beltrano", 5, 10, null);
        when(repository.findByDayAndMonth(5, 10)).thenReturn(List.of(b1, b2));
        when(groupAuthorizationService.getAllowedGroups()).thenReturn(Set.of(-100L));

        int enviados = service.enviarParabensPara(5, 10);

        assertThat(enviados).isEqualTo(2);
        // Privado de cada um
        verify(telegramFacade).enviarMidia(eq(100L), anyString(), anyString());
        verify(telegramFacade).enviarMidia(eq(200L), anyString(), anyString());
        // Grupo recebe 2x (uma por aniversariante)
        verify(telegramFacade, times(2)).enviarMidia(eq(-100L), anyString(), anyString());
    }

    @Test
    void enviarParabensPara_falhaTotal_naoLanca() {
        setGif();
        Birthday b = new Birthday(1L, 100L, "Fulano", 5, 10, null);
        when(repository.findByDayAndMonth(5, 10)).thenReturn(List.of(b));
        when(groupAuthorizationService.getAllowedGroups()).thenReturn(Set.of());
        doThrow(new RuntimeException("boom"))
                .when(telegramFacade)
                .enviarMidia(anyLong(), anyString(), anyString());

        assertThatCode(() -> service.enviarParabensPara(5, 10)).doesNotThrowAnyException();
    }

    // =========================
    // MENÇÃO
    // =========================

    @Test
    void buildMencaoHtml_usaIdEHtml() {
        Birthday b = new Birthday(1L, 999L, "Fulano", 5, 10, null);
        String mencao = service.buildMencaoHtml(b);
        assertThat(mencao).contains("tg://user?id=999").contains(">Fulano<").startsWith("<a");
    }

    @Test
    void buildMencaoHtml_escapaNome() {
        Birthday b = new Birthday(1L, 999L, "Fulano <script>", 5, 10, null);
        String mencao = service.buildMencaoHtml(b);
        assertThat(mencao).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    void buildMencaoHtml_nomeNulo_usaFallback() {
        Birthday b = new Birthday(1L, 999L, null, 5, 10, null);
        String mencao = service.buildMencaoHtml(b);
        assertThat(mencao).contains("amigo(a)");
    }

    // =========================
    // MENSAGEM
    // =========================

    @Test
    void buildMensagemParabens_contemNomeEMarilyn() {
        String msg = service.buildMensagemParabens("Fulano");
        assertThat(msg)
                .contains("Fulano")
                .contains("Marilyn Monroe")
                .contains("1962")
                .contains("HAPPY BIRTHDAY");
    }
}
