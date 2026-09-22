package net.ddns.adambravo79.tmill.telegram.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.pengrad.telegrambot.model.File;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.request.GetFile;
import com.pengrad.telegrambot.request.SendAnimation;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendPhoto;
import com.pengrad.telegrambot.request.SendVideo;
import com.pengrad.telegrambot.response.GetFileResponse;

import io.ksilisk.telegrambot.core.executor.TelegramBotExecutor;
import net.ddns.adambravo79.tmill.telegram.exception.TelegramFileException;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelegramFacadeTest {

    @Mock private TelegramBotExecutor executor;
    @Mock private TelegramSafeExecutor safeExecutor;
    @Mock private MetricsService metricsService;

    @InjectMocks private TelegramFacade facade;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(facade, "botToken", "token123");
        ReflectionTestUtils.setField(facade, "connectTimeout", 60);
        ReflectionTestUtils.setField(facade, "readTimeout", 120);
        ReflectionTestUtils.setField(facade, "writeTimeout", 120);
    }

    // =========================
    // HELPERS: forçar safeExecutor a executar a action
    // =========================

    private void mockSafeExecutorToRunAction() {
        doAnswer(
                        inv -> {
                            TelegramSafeExecutor.ThrowingRunnable action = inv.getArgument(2);
                            action.run();
                            return null;
                        })
                .when(safeExecutor)
                .run(
                        anyLong(),
                        any(TelegramSender.class),
                        any(TelegramSafeExecutor.ThrowingRunnable.class));
    }

    private void mockSafeExecutorWithFallback() {
        doAnswer(
                        inv -> {
                            Long chatId = inv.getArgument(0);
                            TelegramSender fallback = inv.getArgument(1);
                            TelegramSafeExecutor.ThrowingRunnable action = inv.getArgument(2);
                            try {
                                action.run();
                            } catch (Exception e) {
                                fallback.enviar(chatId, "⚠️ Erro ao processar. Tente novamente.");
                            }
                            return null;
                        })
                .when(safeExecutor)
                .run(
                        anyLong(),
                        any(TelegramSender.class),
                        any(TelegramSafeExecutor.ThrowingRunnable.class));
    }

    // =========================
    // ENVIAR MENSAGEM
    // =========================

    @Test
    @DisplayName("enviarMensagem: registra métrica de sucesso")
    void deveEnviarMensagem() throws Exception {
        mockSafeExecutorToRunAction();

        facade.enviarMensagem(123L, "texto simples");

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(executor).execute(captor.capture());
        SendMessage msg = captor.getValue();
        assertThat(msg.getParameters())
                .containsEntry("chat_id", 123L)
                .containsEntry("text", "texto simples")
                .doesNotContainKey("parse_mode");

        verify(metricsService).success("telegram_enviar_mensagem");
    }

    @Test
    @DisplayName("enviarMensagemHtml: registra métrica de sucesso")
    void deveEnviarMensagemHtml() throws Exception {
        mockSafeExecutorToRunAction();

        facade.enviarMensagemHtml(123L, "texto <b>HTML</b>");

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(executor).execute(captor.capture());
        assertThat(captor.getValue().getParameters()).containsEntry("parse_mode", ParseMode.HTML);

        verify(metricsService).success("telegram_enviar_mensagem_html");
    }

    @Test
    @DisplayName("enviarFotoHtml: registra métrica de sucesso")
    void deveEnviarFotoHtml() throws Exception {
        mockSafeExecutorToRunAction();

        facade.enviarFotoHtml(123L, "http://foto.jpg", "legenda");

        ArgumentCaptor<SendPhoto> captor = ArgumentCaptor.forClass(SendPhoto.class);
        verify(executor).execute(captor.capture());
        assertThat(captor.getValue().getParameters())
                .containsEntry("photo", "http://foto.jpg")
                .containsEntry("caption", "legenda");

        verify(metricsService).success("telegram_enviar_foto");
    }

    @Test
    @DisplayName("enviarComBotoesHtml: registra métrica de sucesso")
    void deveEnviarComBotoesHtml() throws Exception {
        mockSafeExecutorToRunAction();
        InlineKeyboardMarkup markup = mock(InlineKeyboardMarkup.class);

        facade.enviarComBotoesHtml(123L, "texto com botões", markup);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(executor).execute(captor.capture());
        assertThat(captor.getValue().getParameters())
                .containsEntry("reply_markup", markup)
                .containsEntry("parse_mode", ParseMode.HTML);

        verify(metricsService).success("telegram_enviar_mensagem_com_botoes_html");
    }

    // =========================
    // EDITAR MENSAGEM
    // =========================

    @Test
    @DisplayName("editarMensagem: NÃO registra métrica (não é instrumentado)")
    void editarMensagem_naoRegistraMetrica() throws Exception {
        mockSafeExecutorToRunAction();

        facade.editarMensagem(123L, 456, "texto simples");

        ArgumentCaptor<EditMessageText> captor = ArgumentCaptor.forClass(EditMessageText.class);
        verify(executor).execute(captor.capture());
        assertThat(captor.getValue().getParameters())
                .containsEntry("message_id", 456)
                .doesNotContainKey("parse_mode");

        verifyNoInteractions(metricsService);
    }

    @Test
    @DisplayName("editarMensagemHtml: registra métrica com chave própria (não compartilhada)")
    void editarMensagemHtml_registraMetrica() throws Exception {
        mockSafeExecutorToRunAction();

        facade.editarMensagemHtml(123L, 456, "novo texto HTML");

        ArgumentCaptor<EditMessageText> captor = ArgumentCaptor.forClass(EditMessageText.class);
        verify(executor).execute(captor.capture());
        assertThat(captor.getValue().getParameters())
                .containsEntry("message_id", 456)
                .containsEntry("parse_mode", "HTML");

        // 👇 chave corrigida — NÃO é mais telegram_enviar_mensagem_html
        verify(metricsService).success("telegram_editar_mensagem_html");
        verify(metricsService, never()).success("telegram_enviar_mensagem_html");
    }

    // =========================
    // ANSWER CALLBACK
    // =========================

    @Test
    @DisplayName("answerCallbackQuery: NÃO registra métrica")
    void answerCallbackQuery_naoRegistraMetrica() throws Exception {
        mockSafeExecutorToRunAction();

        facade.answerCallbackQuery("cb123", "processando", true);

        ArgumentCaptor<AnswerCallbackQuery> captor =
                ArgumentCaptor.forClass(AnswerCallbackQuery.class);
        verify(executor).execute(captor.capture());
        assertThat(captor.getValue().getParameters())
                .containsEntry("callback_query_id", "cb123")
                .containsEntry("show_alert", true);

        verifyNoInteractions(metricsService);
    }

    // =========================
    // MÍDIA
    // =========================

    @Test
    @DisplayName("enviarMidia: vídeo, NÃO registra métrica")
    void enviarMidia_video_naoRegistraMetrica() throws Exception {
        mockSafeExecutorToRunAction();

        facade.enviarMidia(123L, "video.mp4", "legenda");

        verify(executor).execute(any(SendVideo.class));
        verifyNoInteractions(metricsService);
    }

    @Test
    @DisplayName("enviarMidia: gif, NÃO registra métrica")
    void enviarMidia_gif_naoRegistraMetrica() throws Exception {
        mockSafeExecutorToRunAction();

        facade.enviarMidia(123L, "animacao.gif", "legenda");

        verify(executor).execute(any(SendAnimation.class));
        verifyNoInteractions(metricsService);
    }

    @Test
    @DisplayName("enviarMidia: imagem, NÃO registra métrica")
    void enviarMidia_imagem_naoRegistraMetrica() throws Exception {
        mockSafeExecutorToRunAction();

        facade.enviarMidia(123L, "foto.jpg", "legenda");

        verify(executor).execute(any(SendPhoto.class));
        verifyNoInteractions(metricsService);
    }

    @Test
    @DisplayName("enviarMidia: tipo desconhecido cai no fallback de foto")
    void enviarMidia_tipoDesconhecido_usaFallbackFoto() throws Exception {
        mockSafeExecutorToRunAction();

        facade.enviarMidia(123L, "arquivo.xyz", "texto");

        verify(executor).execute(any(SendPhoto.class));
        verifyNoInteractions(metricsService);
    }

    @Test
    @DisplayName("enviarMidia: exceção no envio → tenta enviar caption como texto")
    void enviarMidia_excecao_generica_enviaTexto() throws Exception {
        mockSafeExecutorToRunAction();
        doThrow(new RuntimeException("falha")).when(executor).execute(any(SendPhoto.class));

        facade.enviarMidia(123L, "foto.jpg", "legenda");

        // enviou a foto (que falhou) e depois o texto
        verify(executor, times(1)).execute(any(SendPhoto.class));
        verify(executor, times(1)).execute(any(SendMessage.class));

        // 🔧 FIX: o fallback chamou enviarMensagem, que registra métrica.
        // Portanto NÃO podemos usar verifyNoInteractions(metricsService).
        verify(metricsService).success("telegram_enviar_mensagem");
        // E não deve ter registrado a métrica de envio de foto
        verify(metricsService, never()).success("telegram_enviar_foto");
    }

    // =========================
    // GET FILE
    // =========================

    @Test
    @DisplayName("getFile: sucesso retorna File")
    void getFile_sucesso() {
        GetFileResponse response = mock(GetFileResponse.class);
        when(response.isOk()).thenReturn(true);
        File file = mock(File.class);
        when(response.file()).thenReturn(file);
        when(executor.execute(any(GetFile.class))).thenReturn(response);

        File result = facade.getFile("fileId");

        assertThat(result).isSameAs(file);
        verifyNoInteractions(metricsService);
    }

    @Test
    @DisplayName("getFile: erro lança TelegramFileException")
    void getFile_erro() {
        GetFileResponse response = mock(GetFileResponse.class);
        when(response.isOk()).thenReturn(false);
        when(response.description()).thenReturn("Erro");
        when(executor.execute(any(GetFile.class))).thenReturn(response);

        assertThatExceptionOfType(TelegramFileException.class)
                .isThrownBy(() -> facade.getFile("fileId"))
                .withMessageContaining("Falha ao obter arquivo");
    }

    // =========================
    // FALLBACK DO SAFE EXECUTOR
    // =========================

    @Test
    @DisplayName("safeExecutor com fallback: mensagem original falha e fallback é chamado")
    void safeExecutorFallback_deveSerChamadoEmErro() throws Exception {
        mockSafeExecutorWithFallback();
        doThrow(new RuntimeException("erro")).when(executor).execute(any(SendMessage.class));

        facade.enviarMensagem(123L, "texto");

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(executor, times(2)).execute(captor.capture());

        assertThat(captor.getAllValues().get(0).getParameters()).containsEntry("text", "texto");
        assertThat(captor.getAllValues().get(1).getParameters())
                .containsEntry("text", "⚠️ Erro ao processar. Tente novamente.");

        verifyNoInteractions(metricsService);
    }

    // =========================
    // INIT
    // =========================

    @Test
    @DisplayName("init: não lança exceção")
    void init_naoLancaExcecao() {
        assertThatCode(() -> facade.init()).doesNotThrowAnyException();
    }

    // =========================
    // MASK TOKEN
    // =========================

    @Test
    @DisplayName("maskToken: token curto retorna ***")
    void maskToken_tokenCurto_retornaAsteriscos() throws Exception {
        var method = TelegramFacade.class.getDeclaredMethod("maskToken", String.class);
        method.setAccessible(true);
        String result = (String) method.invoke(facade, "12345");
        assertThat(result).isEqualTo("***");
    }

    @Test
    @DisplayName("maskToken: token longo é mascarado")
    void maskToken_tokenLongo_retornaMascarado() throws Exception {
        var method = TelegramFacade.class.getDeclaredMethod("maskToken", String.class);
        method.setAccessible(true);
        String result = (String) method.invoke(facade, "1234567890:ABCdefGHIjklMNOpqrsTUVwxyz");
        assertThat(result).isEqualTo("1234...wxyz");
    }

    // =========================
    // DOWNLOAD FILE
    // =========================

    @Test
    @DisplayName("downloadFile: falha de rede lança TelegramFileException")
    void downloadFile_deveLancarExcecao() {
        File file = mock(File.class);
        when(file.filePath()).thenReturn("path/to/file");

        // Usa spy para executar o código real
        TelegramFacade spyFacade = spy(facade);
        ReflectionTestUtils.setField(spyFacade, "botToken", "token123");

        assertThatThrownBy(() -> spyFacade.downloadFile(file))
                .isInstanceOf(TelegramFileException.class)
                .hasCauseInstanceOf(IOException.class);
    }
}
