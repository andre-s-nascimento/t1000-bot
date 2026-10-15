package net.ddns.adambravo79.tmill.telegram.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import com.pengrad.telegrambot.request.SendAudio;
import com.pengrad.telegrambot.request.SendDocument;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendPhoto;
import com.pengrad.telegrambot.request.SendVideo;
import com.pengrad.telegrambot.response.GetFileResponse;

import io.ksilisk.telegrambot.core.executor.TelegramBotExecutor;
import net.ddns.adambravo79.tmill.service.config.AudioConfigService;
import net.ddns.adambravo79.tmill.telegram.exception.TelegramFileException;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelegramFacadeTest {

    @Mock private TelegramBotExecutor executor;
    @Mock private TelegramSafeExecutor safeExecutor;
    @Mock private MetricsService metricsService;
    // 🔧 Onda 3.4: novo mock obrigatório para o construtor do TelegramFacade
    @Mock private AudioConfigService audioConfig;

    @InjectMocks private TelegramFacade facade;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(facade, "botToken", "token123");
        ReflectionTestUtils.setField(facade, "connectTimeout", 60);
        ReflectionTestUtils.setField(facade, "readTimeout", 120);
        ReflectionTestUtils.setField(facade, "writeTimeout", 120);

        // 🔧 Onda 3.4: default — áudio > 5 MB vira Document
        lenient()
                .when(audioConfig.getTelegram())
                .thenReturn(new AudioConfigService.TelegramAudioConfig(20, 5));
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
    void deveEnviarMensagem() {
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
    void deveEnviarMensagemHtml() {
        mockSafeExecutorToRunAction();

        facade.enviarMensagemHtml(123L, "texto <b>HTML</b>");

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(executor).execute(captor.capture());
        assertThat(captor.getValue().getParameters()).containsEntry("parse_mode", ParseMode.HTML);

        verify(metricsService).success("telegram_enviar_mensagem_html");
    }

    @Test
    @DisplayName("enviarFotoHtml: registra métrica de sucesso")
    void deveEnviarFotoHtml() {
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
    void deveEnviarComBotoesHtml() {
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
    void editarMensagem_naoRegistraMetrica() {
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
    void editarMensagemHtml_registraMetrica() {
        mockSafeExecutorToRunAction();

        facade.editarMensagemHtml(123L, 456, "novo texto HTML");

        ArgumentCaptor<EditMessageText> captor = ArgumentCaptor.forClass(EditMessageText.class);
        verify(executor).execute(captor.capture());
        assertThat(captor.getValue().getParameters())
                .containsEntry("message_id", 456)
                .containsEntry("parse_mode", "HTML");

        verify(metricsService).success("telegram_editar_mensagem_html");
        verify(metricsService, never()).success("telegram_enviar_mensagem_html");
    }

    // =========================
    // ANSWER CALLBACK
    // =========================

    @Test
    @DisplayName("answerCallbackQuery: NÃO registra métrica")
    void answerCallbackQuery_naoRegistraMetrica() {
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
    // MÍDIA — enviarMidia agora retorna boolean
    // =========================

    @Test
    @DisplayName("enviarMidia: vídeo, NÃO registra métrica e retorna true")
    void enviarMidia_video_naoRegistraMetrica() {
        mockSafeExecutorToRunAction();

        boolean ok = facade.enviarMidia(123L, "video.mp4", "legenda");

        verify(executor).execute(any(SendVideo.class));
        verifyNoInteractions(metricsService);
        assertThat(ok).isTrue();
    }

    @Test
    @DisplayName("enviarMidia: gif, NÃO registra métrica e retorna true")
    void enviarMidia_gif_naoRegistraMetrica() {
        mockSafeExecutorToRunAction();

        boolean ok = facade.enviarMidia(123L, "animacao.gif", "legenda");

        verify(executor).execute(any(SendAnimation.class));
        verifyNoInteractions(metricsService);
        assertThat(ok).isTrue();
    }

    @Test
    @DisplayName("enviarMidia: imagem, NÃO registra métrica e retorna true")
    void enviarMidia_imagem_naoRegistraMetrica() {
        mockSafeExecutorToRunAction();

        boolean ok = facade.enviarMidia(123L, "foto.jpg", "legenda");

        verify(executor).execute(any(SendPhoto.class));
        verifyNoInteractions(metricsService);
        assertThat(ok).isTrue();
    }

    @Test
    @DisplayName("enviarMidia: tipo desconhecido cai no fallback de foto e retorna true")
    void enviarMidia_tipoDesconhecido_usaFallbackFoto() {
        mockSafeExecutorToRunAction();

        boolean ok = facade.enviarMidia(123L, "arquivo.xyz", "texto");

        verify(executor).execute(any(SendPhoto.class));
        verifyNoInteractions(metricsService);
        assertThat(ok).isTrue();
    }

    @Test
    @DisplayName("enviarMidia: exceção no envio → tenta enviar caption como texto → retorna true")
    void enviarMidia_excecao_generica_enviaTexto() {
        mockSafeExecutorToRunAction();
        doThrow(new RuntimeException("falha")).when(executor).execute(any(SendPhoto.class));

        boolean ok = facade.enviarMidia(123L, "foto.jpg", "legenda");

        // enviou a foto (que falhou) e depois o texto
        verify(executor, times(1)).execute(any(SendPhoto.class));
        verify(executor, times(1)).execute(any(SendMessage.class));

        // 🔧 Onda 3.4: fallback funcionou → retorno é true
        assertThat(ok).isTrue();

        // Fallback chamou enviarMensagem → métrica de mensagem registrada
        verify(metricsService).success("telegram_enviar_mensagem");
        verify(metricsService, never()).success("telegram_enviar_foto");
    }

    @Test
    @DisplayName("enviarMidia: mídia E fallback falham → retorna false")
    void enviarMidia_midiaEFallbackFalham_retornaFalse() {
        mockSafeExecutorToRunAction();
        // Qualquer chamada ao executor lança exceção → nem foto nem texto funcionam
        doThrow(new RuntimeException("falha total")).when(executor).execute(any());

        boolean ok = facade.enviarMidia(123L, "foto.jpg", "legenda");

        // 🔧 Onda 3.4: nem mídia nem fallback funcionaram → retorno é false
        assertThat(ok).isFalse();
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
    void safeExecutorFallback_deveSerChamadoEmErro() {
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
    @DisplayName("downloadFile: falha na resposta HTTP lança TelegramFileException")
    void downloadFile_deveLancarExcecao() {
        File file = mock(File.class);
        when(file.filePath()).thenReturn("path/to/file");

        TelegramFacade spyFacade = spy(facade);
        ReflectionTestUtils.setField(spyFacade, "botToken", "token123");
        ReflectionTestUtils.setField(spyFacade, "connectTimeout", 60);
        ReflectionTestUtils.setField(spyFacade, "readTimeout", 120);
        ReflectionTestUtils.setField(spyFacade, "writeTimeout", 120);
        spyFacade.init();

        assertThatThrownBy(() -> spyFacade.downloadFile(file))
                .isInstanceOf(TelegramFileException.class)
                .hasMessageContaining("Erro ao baixar arquivo");
    }

    // =========================================================================
    // COBERTURA ADICIONAL
    // =========================================================================

    @Test
    @DisplayName("enviarMidia com .mp4 (URL) usa SendVideo")
    void enviarMidia_mp4_usesSendVideo() {
        mockSafeExecutorToRunAction();

        facade.enviarMidia(123L, "http://exemplo.com/video.mp4", "legenda");

        verify(executor).execute(any(SendVideo.class));
    }

    @Test
    @DisplayName("enviarMidia com .gif (URL) usa SendAnimation")
    void enviarMidia_gif_usesSendAnimation() {
        mockSafeExecutorToRunAction();

        facade.enviarMidia(123L, "http://exemplo.com/anim.gif", "legenda");

        verify(executor).execute(any(SendAnimation.class));
    }

    @Test
    @DisplayName("enviarMidia com .jpg (URL) usa SendPhoto")
    void enviarMidia_jpg_usesSendPhoto() {
        mockSafeExecutorToRunAction();

        facade.enviarMidia(123L, "http://exemplo.com/foto.jpg", "legenda");

        verify(executor).execute(any(SendPhoto.class));
    }

    @Test
    @DisplayName("enviarMidia com extensão desconhecida (URL) usa SendPhoto como fallback")
    void enviarMidia_unknown_fallsBackToPhoto() {
        mockSafeExecutorToRunAction();

        facade.enviarMidia(123L, "http://exemplo.com/arquivo.xyz", "legenda");

        verify(executor).execute(any(SendPhoto.class));
    }

    @Test
    @DisplayName("enviarMidia com arquivo local .mp3 pequeno usa SendAudio")
    void enviarMidia_localMp3_usesSendAudio() throws Exception {
        mockSafeExecutorToRunAction();

        java.nio.file.Path temp = java.nio.file.Files.createTempFile("test-", ".mp3");
        java.nio.file.Files.writeString(temp, "audio");

        try {
            facade.enviarMidia(123L, temp.toString(), "legenda");
            verify(executor).execute(any(SendAudio.class));
            verify(executor, never()).execute(any(SendDocument.class));
        } finally {
            java.nio.file.Files.deleteIfExists(temp);
        }
    }

    @Test
    @DisplayName("enviarMidia com arquivo local desconhecido usa SendDocument")
    void enviarMidia_localUnknown_usesSendDocument() throws Exception {
        mockSafeExecutorToRunAction();

        java.nio.file.Path temp = java.nio.file.Files.createTempFile("test-", ".xyz");
        java.nio.file.Files.writeString(temp, "data");

        try {
            facade.enviarMidia(123L, temp.toString(), "legenda");
            verify(executor).execute(any(SendDocument.class));
        } finally {
            java.nio.file.Files.deleteIfExists(temp);
        }
    }

    // =========================================================================
    // ONDA 3.4: useDocumentWhenOverMb
    // =========================================================================

    @Test
    @DisplayName("enviarMidia com .mp3 grande (> useDocumentWhenOverMb) usa SendDocument")
    void enviarMidia_mp3Grande_usaSendDocument() throws Exception {
        mockSafeExecutorToRunAction();

        // Configura limite muito baixo: arquivos > 1 KB viram documento
        when(audioConfig.getTelegram())
                .thenReturn(new AudioConfigService.TelegramAudioConfig(20, 0));
        // 0 MB → qualquer arquivo > 0 bytes vira Document

        java.nio.file.Path temp = java.nio.file.Files.createTempFile("test-big-", ".mp3");
        // Escreve 2 KB para garantir que seja > 0
        java.nio.file.Files.write(temp, new byte[2048]);

        try {
            boolean ok = facade.enviarMidia(123L, temp.toString(), "legenda");

            verify(executor).execute(any(SendDocument.class));
            verify(executor, never()).execute(any(SendAudio.class));
            assertThat(ok).isTrue();
        } finally {
            java.nio.file.Files.deleteIfExists(temp);
        }
    }
}
