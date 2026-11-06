/* (c) 2026 | 05/11/2026 */
package net.ddns.adambravo79.tmill.telegram.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;

import net.ddns.adambravo79.tmill.model.TorrentLaunch;
import net.ddns.adambravo79.tmill.service.TorrentLaunchService;
import net.ddns.adambravo79.tmill.telegram.core.GroupAuthorizationService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;
import net.ddns.adambravo79.tmill.telegram.util.TelegramUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TorrentHandlerTest {

    @Mock private TelegramFacade telegramFacade;
    @Mock private GroupAuthorizationService authService;
    @Mock private TelegramUtils utils;
    @Mock private TorrentLaunchService torrentService;
    @Mock private MetricsService metricsService;

    private TorrentHandler handler;

    private static final long CHAT_ID = 12345L;

    @BeforeEach
    void setUp() {
        handler =
                new TorrentHandler(
                        telegramFacade, authService, utils, torrentService, metricsService);
        lenient().when(authService.isAuthorized(any(Update.class))).thenReturn(true);
        lenient().when(utils.escapeHtml(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(torrentService.getAno()).thenReturn(2026);
        lenient().when(torrentService.getMesReferencia()).thenReturn("novembro");
        lenient().when(torrentService.getMesesDisponiveis()).thenReturn(List.of("novembro"));
    }

    private Update mockUpdate(String texto) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);
        when(update.message()).thenReturn(message);
        when(message.text()).thenReturn(texto);
        when(message.chat()).thenReturn(chat);
        when(message.from()).thenReturn(user);
        when(chat.id()).thenReturn(CHAT_ID);
        when(user.id()).thenReturn(1L);
        when(user.firstName()).thenReturn("Test");
        return update;
    }

    private TorrentLaunch filme(String titulo, String data, String diretor, List<String> atores) {
        return new TorrentLaunch(
                titulo,
                data,
                "filme",
                diretor,
                atores,
                "https://letterboxd.com/film/" + titulo,
                null);
    }

    // =========================================================================
    // SEM ARGUMENTO
    // =========================================================================

    @Test
    @DisplayName("Sem argumento: envia mensagem de ajuda")
    void semArgumento() {
        Update update = mockUpdate("t1000 torrent");

        handler.processarComando(update);

        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("Módulo Torrent"));
    }

    // =========================================================================
    // POR MÊS
    // =========================================================================

    @Test
    @DisplayName("Mês válido: envia lista formatada")
    void porMes() {
        List<TorrentLaunch> lancamentos =
                List.of(
                        filme("Resident Evil", "02/11", "Zach Cragger", List.of()),
                        filme(
                                "Primetime",
                                "03/11",
                                "Lance Oppenheim",
                                List.of("Robert Pattinson")));
        when(torrentService.listarPorMes("novembro")).thenReturn(lancamentos);
        Update update = mockUpdate("t1000 torrent novembro");

        handler.processarComando(update);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());
        String msg = captor.getValue();
        assertThat(msg)
                .contains("Lançamentos do torrent de Novembro")
                .contains("02/11 - Resident Evil")
                .contains("03/11 - Primetime");
    }

    @Test
    @DisplayName("Mês sem lançamentos: envia mensagem")
    void mesSemLancamentos() {
        when(torrentService.listarPorMes("dezembro")).thenReturn(List.of());
        Update update = mockUpdate("t1000 torrent dezembro");

        handler.processarComando(update);

        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("dezembro"));
    }

    // =========================================================================
    // POR DATA
    // =========================================================================

    @Test
    @DisplayName("Data válida: envia lançamentos do dia")
    void porData() {
        List<TorrentLaunch> lancamentos =
                List.of(filme("Resident Evil", "02/11", "Zach", List.of()));
        when(torrentService.buscarPorData("02/11")).thenReturn(lancamentos);
        Update update = mockUpdate("t1000 torrent 02/11");

        handler.processarComando(update);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue())
                .contains("Lançamentos do dia 02/11")
                .contains("Resident Evil");
    }

    @Test
    @DisplayName("Data sem lançamentos: envia mensagem")
    void dataSemLancamentos() {
        when(torrentService.buscarPorData("31/12")).thenReturn(List.of());
        Update update = mockUpdate("t1000 torrent 31/12");

        handler.processarComando(update);

        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("31/12"));
    }

    // =========================================================================
    // POR TÍTULO
    // =========================================================================

    @Test
    @DisplayName("Título exato: envia ficha completa")
    void tituloExato() {
        TorrentLaunch l = filme("Resident Evil", "02/11", "Zach Cragger", List.of());
        when(torrentService.buscarPrimeiroPorTitulo("Resident Evil"))
                .thenReturn(java.util.Optional.of(l));
        Update update = mockUpdate("t1000 torrent Resident Evil");

        handler.processarComando(update);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue())
                .contains("Resident Evil")
                .contains("02/11")
                .contains("Zach Cragger");
    }

    @Test
    @DisplayName("Título parcial com 1 resultado: envia ficha")
    void tituloParcialUmResultado() {
        TorrentLaunch l = filme("Resident Evil", "02/11", "Zach Cragger", List.of());
        when(torrentService.buscarPrimeiroPorTitulo("Resident"))
                .thenReturn(java.util.Optional.empty());
        when(torrentService.buscarPorTitulo("Resident")).thenReturn(List.of(l));
        Update update = mockUpdate("t1000 torrent Resident");

        handler.processarComando(update);

        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("Resident Evil"));
    }

    @Test
    @DisplayName("Título parcial com múltiplos resultados: lista resumida")
    void tituloParcialMultiplos() {
        List<TorrentLaunch> lista =
                List.of(
                        filme("Resident Evil", "02/11", "Zach", List.of()),
                        filme("Resident Evil 2", "05/11", "Zach", List.of()));
        when(torrentService.buscarPrimeiroPorTitulo("Resident"))
                .thenReturn(java.util.Optional.empty());
        when(torrentService.buscarPorTitulo("Resident")).thenReturn(lista);
        Update update = mockUpdate("t1000 torrent Resident");

        handler.processarComando(update);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue())
                .contains("Encontrei 2 lançamento")
                .contains("Resident Evil")
                .contains("Resident Evil 2");
    }

    @Test
    @DisplayName("Título inexistente: envia mensagem")
    void tituloInexistente() {
        when(torrentService.buscarPrimeiroPorTitulo("xyz")).thenReturn(java.util.Optional.empty());
        when(torrentService.buscarPorTitulo("xyz")).thenReturn(List.of());
        Update update = mockUpdate("t1000 torrent xyz");

        handler.processarComando(update);

        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("xyz"));
    }

    // =========================================================================
    // MÉTRICAS
    // =========================================================================

    @Test
    @DisplayName("Sempre emite success('comando_torrent')")
    void metricaSuccess() {
        Update update = mockUpdate("t1000 torrent");
        handler.processarComando(update);
        verify(metricsService).success("comando_torrent");
        verify(metricsService, never()).error(anyString());
    }

    // =========================================================================
    // AUTORIZAÇÃO
    // =========================================================================

    @Test
    @DisplayName("Não autorizado: não processa nada")
    void naoAutorizado() {
        when(authService.isAuthorized(any(Update.class))).thenReturn(false);
        Update update = mockUpdate("t1000 torrent novembro");

        handler.processarComando(update);

        verifyNoInteractions(torrentService);
        verify(telegramFacade, never()).enviarMensagem(anyLong(), anyString());
        verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
    }

    // =========================================================================
    // COBERTURA ADICIONAL (BORDAS)
    // =========================================================================

    @Test
    @DisplayName("Múltiplos resultados parciais: lista resumida com contagem")
    void tituloMultiplosResultados() {
        List<TorrentLaunch> lista =
                List.of(
                        filme("Resident Evil 1", "02/11", "Zach", List.of()),
                        filme("Resident Evil 2", "03/11", "Zach", List.of()));
        when(torrentService.buscarPrimeiroPorTitulo("Resident"))
                .thenReturn(java.util.Optional.empty());
        when(torrentService.buscarPorTitulo("Resident")).thenReturn(lista);
        Update update = mockUpdate("t1000 torrent Resident");

        handler.processarComando(update);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue()).contains("Encontrei 2 lançamento");
    }

    @Test
    @DisplayName("Ficha com campos nulos (diretor null, atores vazios)")
    void fichaComCamposNulos() {
        TorrentLaunch l =
                new TorrentLaunch("Filme Teste", "01/11", "filme", null, List.of(), "url", null);
        when(torrentService.buscarPrimeiroPorTitulo("Filme Teste"))
                .thenReturn(java.util.Optional.of(l));
        Update update = mockUpdate("t1000 torrent Filme Teste");

        handler.processarComando(update);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue())
                .contains("Filme Teste")
                .doesNotContain("🎬 Diretor:")
                .doesNotContain("👥 Atores:");
    }

    @Test
    @DisplayName("Ficha de série com temporada")
    void fichaSerieComTemporada() {
        TorrentLaunch l =
                new TorrentLaunch("Série Teste", "01/11", "serie", null, List.of("Ator"), "url", 2);
        when(torrentService.buscarPrimeiroPorTitulo("Série Teste"))
                .thenReturn(java.util.Optional.of(l));
        Update update = mockUpdate("t1000 torrent Série Teste");

        handler.processarComando(update);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue()).contains("Tipo: Série (Temporada 2)");
    }

    @Test
    @DisplayName("Ficha sem ano (ano = 0)")
    void fichaSemAno() {
        TorrentLaunch l = filme("Filme", "01/11", "Dir", List.of());
        when(torrentService.getAno()).thenReturn(0);
        when(torrentService.buscarPrimeiroPorTitulo("Filme")).thenReturn(java.util.Optional.of(l));
        Update update = mockUpdate("t1000 torrent Filme");

        handler.processarComando(update);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue()).contains("Lançamento: Filme\n").doesNotContain("(2026)");
    }
}
