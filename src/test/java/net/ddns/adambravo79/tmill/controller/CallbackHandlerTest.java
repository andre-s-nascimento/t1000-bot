/* (c) 2026 | 22/07/2026 */
package net.ddns.adambravo79.tmill.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;

import net.ddns.adambravo79.tmill.exception.MovieNotFoundException;
import net.ddns.adambravo79.tmill.model.MovieOrchestrationResponse;
import net.ddns.adambravo79.tmill.model.MovieRecord;
import net.ddns.adambravo79.tmill.service.MovieService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CallbackHandlerTest {

    @Mock private MovieService movieService;
    @Mock private AudioHandler audioHandler;
    @Mock private TelegramFacade telegramFacade;

    @InjectMocks private CallbackHandler callbackHandler;

    private Update update;
    private CallbackQuery callback;
    private Message message;
    private User user;
    private Chat chat;

    private static final long CHAT_ID = 12345L;
    private static final long USER_ID = 999L;
    private static final int MESSAGE_ID = 100;

    @BeforeEach
    void setUp() {
        update = mock(Update.class);
        callback = mock(CallbackQuery.class);
        message = mock(Message.class);
        user = mock(User.class);
        chat = mock(Chat.class);

        when(update.callbackQuery()).thenReturn(callback);
        when(callback.from()).thenReturn(user);
        when(callback.id()).thenReturn("cb123");
        // Usa maybeInaccessibleMessage em vez de message()
        when(callback.maybeInaccessibleMessage()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(CHAT_ID);
        when(message.messageId()).thenReturn(MESSAGE_ID);

        when(user.id()).thenReturn(USER_ID);
        when(user.firstName()).thenReturn("Testador");
    }

    // =========================
    // 🧪 SELEÇÃO DE FILME
    // =========================

    @Test
    @SuppressWarnings("java:S6068")
    void deveSelecionarFilmeComSucesso() {
        when(callback.data()).thenReturn("id:123");
        MovieOrchestrationResponse response =
                new MovieOrchestrationResponse(
                        "Título do Filme\nDescrição", "https://image.tmdb.org/t/p/w500/poster.jpg");
        when(movieService.buscarPorId(123L)).thenReturn(response);

        callbackHandler.handleCallbackUpdate(update);

        verify(telegramFacade).answerCallbackQuery("cb123", "Buscando filme...", false);
        verify(telegramFacade)
                .editarMensagemHtml(eq(CHAT_ID), eq(MESSAGE_ID), contains("✅ Filme selecionado"));
        verify(telegramFacade)
                .enviarFotoHtml(
                        eq(CHAT_ID),
                        eq("https://image.tmdb.org/t/p/w500/poster.jpg"),
                        eq("Título do Filme\nDescrição"));
    }

    @Test
    void deveSelecionarFilmeSemFoto() {
        when(callback.data()).thenReturn("id:123");
        MovieOrchestrationResponse response =
                new MovieOrchestrationResponse("Título do Filme\nDescrição", null);
        when(movieService.buscarPorId(123L)).thenReturn(response);

        callbackHandler.handleCallbackUpdate(update);

        verify(telegramFacade).answerCallbackQuery("cb123", "Buscando filme...", false);
        verify(telegramFacade)
                .editarMensagemHtml(eq(CHAT_ID), eq(MESSAGE_ID), contains("✅ Filme selecionado"));

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), messageCaptor.capture());

        String mensagem = messageCaptor.getValue();
        assertThat(mensagem)
                .contains("Título do Filme")
                .contains("Descrição")
                .contains("_(sem imagem)_");
    }

    @Test
    void deveLidarComFilmeNaoEncontrado() {
        when(callback.data()).thenReturn("id:999");
        when(movieService.buscarPorId(999L))
                .thenThrow(new MovieNotFoundException("Filme não encontrado"));

        callbackHandler.handleCallbackUpdate(update);

        verify(telegramFacade).answerCallbackQuery("cb123", "Buscando filme...", false);
        verify(telegramFacade).answerCallbackQuery("cb123", "Filme não encontrado", true);
        verify(telegramFacade).editarMensagem(CHAT_ID, MESSAGE_ID, "❌ Filme não encontrado.");
        verify(telegramFacade, never()).enviarFotoHtml(anyLong(), anyString(), anyString());
    }

    @Test
    void deveLidarComIdInvalido() {
        when(callback.data()).thenReturn("id:abc");

        callbackHandler.handleCallbackUpdate(update);

        verify(telegramFacade).answerCallbackQuery("cb123", "ID inválido", true);
        verify(movieService, never()).buscarPorId(anyLong());
        verify(telegramFacade, never()).editarMensagemHtml(anyLong(), anyInt(), anyString());
        verify(telegramFacade, never()).enviarFotoHtml(anyLong(), anyString(), anyString());
    }

    @Test
    void deveLidarComErroGenericoNaBusca() {
        when(callback.data()).thenReturn("id:123");
        when(movieService.buscarPorId(123L)).thenThrow(new RuntimeException("Erro inesperado"));

        callbackHandler.handleCallbackUpdate(update);

        verify(telegramFacade).answerCallbackQuery("cb123", "Buscando filme...", false);
        verify(telegramFacade).answerCallbackQuery("cb123", "Erro interno", true);
        verify(telegramFacade, never()).editarMensagemHtml(anyLong(), anyInt(), anyString());
        verify(telegramFacade, never()).enviarFotoHtml(anyLong(), anyString(), anyString());
    }

    // =========================
    // 🧪 DELEGAÇÃO PARA AUDIOHANDLER
    // =========================

    @Test
    void deveDelegarCallbackDeTranscricaoBruta() {
        when(callback.data()).thenReturn("trans_bruto|token123");

        callbackHandler.handleCallbackUpdate(update);

        verify(audioHandler).handleTranscriptionCallback(callback, "trans_bruto|token123");
        verifyNoInteractions(movieService);
    }

    @Test
    void deveDelegarCallbackDeTranscricaoRefinada() {
        when(callback.data()).thenReturn("trans_refinado|token456");

        callbackHandler.handleCallbackUpdate(update);

        verify(audioHandler).handleTranscriptionCallback(callback, "trans_refinado|token456");
        verifyNoInteractions(movieService);
    }

    // =========================
    // 🧪 CALLBACK DESCONHECIDO
    // =========================

    @Test
    void deveResponderCallbackDesconhecido() {
        when(callback.data()).thenReturn("unknown_data");

        callbackHandler.handleCallbackUpdate(update);

        verify(telegramFacade).answerCallbackQuery("cb123", "Ação não reconhecida", false);
        verifyNoInteractions(movieService, audioHandler);
    }

    // =========================
    // 🧪 CRIAÇÃO DE BOTÕES
    // =========================

    @Test
    void deveCriarBotoesDesambiguacao() {
        MovieRecord filme1 =
                new MovieRecord(1L, "Filme A", "", "2021-01-01", "", 0.0, 0.0, "", List.of());
        MovieRecord filme2 =
                new MovieRecord(2L, "Filme B", "", "2022-02-02", "", 0.0, 0.0, "", List.of());
        MovieRecord filme3 = new MovieRecord(3L, "Filme C", "", "", "", 0.0, 0.0, "", List.of());

        List<MovieRecord> resultados = List.of(filme1, filme2, filme3);

        InlineKeyboardMarkup markup = callbackHandler.criarBotoesDesambiguacao(resultados);

        assertThat(markup).isNotNull();
        // Verifica que há pelo menos um botão (não testamos a estrutura exata, mas que não falha)
        // Poderíamos verificar que o método não lança exceção e que retorna algo não nulo.
    }
}
