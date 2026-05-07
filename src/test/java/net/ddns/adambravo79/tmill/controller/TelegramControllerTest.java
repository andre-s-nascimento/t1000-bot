/* (c) 2026 | 07/05/2026 */
package net.ddns.adambravo79.tmill.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import net.ddns.adambravo79.tmill.cache.TranscricaoCache;
import net.ddns.adambravo79.tmill.client.BloggerClient;
import net.ddns.adambravo79.tmill.dto.AudioRequest;
import net.ddns.adambravo79.tmill.model.*;
import net.ddns.adambravo79.tmill.service.*;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.core.TelegramSafeExecutor;

class TelegramControllerTest {

    private MovieService movieService;
    private AudioPipelineService audioService;
    private BloggerClient bloggerClient;
    private TranscricaoCache cache;
    private TelegramFileService fileService;
    private TelegramFacade telegramFacade;
    private TelegramSafeExecutor safeExecutor; // instância real
    private IdeasLogger ideasLogger;
    private TelegramController controller;

    @BeforeEach
    void setup() {
        movieService = mock(MovieService.class);
        audioService = mock(AudioPipelineService.class);
        bloggerClient = mock(BloggerClient.class);
        cache = mock(TranscricaoCache.class);
        fileService = mock(TelegramFileService.class);
        telegramFacade = mock(TelegramFacade.class);
        safeExecutor = new TelegramSafeExecutor(); // real
        ideasLogger = mock(IdeasLogger.class); // 🆕
        UserInteractionLogger userLogger = mock(UserInteractionLogger.class); // 🆕

        controller =
                new TelegramController(
                        movieService,
                        audioService,
                        bloggerClient,
                        cache,
                        fileService,
                        telegramFacade,
                        safeExecutor,
                        userLogger,
                        ideasLogger); // 🆕

        // Configurar campos injetáveis
        ReflectionTestUtils.setField(controller, "transcriptionEnabled", true);
        ReflectionTestUtils.setField(controller, "ownerId", 12345L);
        ReflectionTestUtils.setField(controller, "maxAudioSizeMb", 20);
        ReflectionTestUtils.setField(controller, "telegramMessageLimit", 4000);
    }

    // =========================
    // 🎬 FILMES
    // =========================

    @Test
    void deveProcessarBuscaComUmResultado() {
        Long id = 1L;
        var search =
                new MovieSearchResponse(
                        1,
                        1,
                        1,
                        List.of(
                                new MovieRecord(
                                        id, "Duna", "Dune", "2021", "desc", 1.0, 1.0, "",
                                        List.of())));

        when(movieService.buscarFilme("duna")).thenReturn(search);
        when(movieService.buscarPorId(id)).thenReturn(new MovieOrchestrationResponse("ok", "url"));

        controller.consume(buildTextUpdate(1L, "t1000 buscar duna"));

        verify(movieService).buscarFilme("duna");
        verify(movieService).buscarPorId(id);
    }

    @Test
    void deveChamarDesambiguacaoQuandoMultiplosResultados() {
        var search =
                new MovieSearchResponse(
                        1,
                        1,
                        1,
                        List.of(
                                new MovieRecord(1L, "A", "a", "2020", "", 1.0, 1.0, "", List.of()),
                                new MovieRecord(
                                        2L, "B", "b", "2021", "", 1.0, 1.0, "", List.of())));

        when(movieService.buscarFilme("teste")).thenReturn(search);

        controller.consume(buildTextUpdate(1L, "t1000 buscar teste"));

        verify(movieService).buscarFilme("teste");
        verify(movieService, never()).buscarPorId(anyLong());
    }

    @Test
    void deveInformarQuandoFilmeNaoEncontrado() {
        when(movieService.buscarFilme("inexistente"))
                .thenReturn(new MovieSearchResponse(1, 0, 0, List.of()));

        controller.consume(buildTextUpdate(1L, "t1000 buscar inexistente"));

        verify(telegramFacade).enviarMensagem(1L, "❌ Filme não encontrado.");
    }

    @Test
    void deveRejeitarBuscaComMenosDe3Caracteres() {
        controller.consume(buildTextUpdate(1L, "t1000 buscar ab"));
        verify(telegramFacade).enviarMensagem(1L, "🔍 O termo deve ter pelo menos 3 caracteres.");
        verifyNoInteractions(movieService);
    }

    @Test
    void deveRejeitarBuscaComMaisDe100Caracteres() {
        String termoLongo = "a".repeat(101);
        controller.consume(buildTextUpdate(1L, "t1000 buscar " + termoLongo));
        verify(telegramFacade)
                .enviarMensagem(1L, "🔍 O termo é muito longo (máx. 100 caracteres).");
        verifyNoInteractions(movieService);
    }

    // =========================
    // 🎙️ AUDIO
    // =========================

    @Test
    void deveProcessarAudioCompleto() {
        ReflectionTestUtils.setField(controller, "transcriptionEnabled", true);

        when(fileService.baixarArquivo(any())).thenReturn(new File("audio.oga"));
        doAnswer(
                        inv -> {
                            BiConsumer<String, Boolean> cb = inv.getArgument(2);
                            cb.accept("bruto", false);
                            cb.accept("refinado", true);
                            return null;
                        })
                .when(audioService)
                .processarFluxoAudio(any(), anyLong(), any());

        controller.consume(buildVoiceUpdate(1L, "file-id", 1L));

        verify(audioService).processarFluxoAudio(any(), anyLong(), any());
    }

    @Test
    void naoDeveProcessarSeDownloadFalhar() {
        ReflectionTestUtils.setField(controller, "transcriptionEnabled", true);

        when(fileService.baixarArquivo(any())).thenThrow(new RuntimeException("falha"));

        controller.consume(buildVoiceUpdate(1L, "file-id", 1L));

        verifyNoInteractions(audioService);
    }

    @Test
    void deveDividirMensagemGrandeEEnviarPartes() {
        ReflectionTestUtils.setField(controller, "transcriptionEnabled", true);

        when(fileService.baixarArquivo(any())).thenReturn(new File("audio.oga"));
        doAnswer(
                        inv -> {
                            BiConsumer<String, Boolean> cb = inv.getArgument(2);
                            cb.accept("a ".repeat(5000), true);
                            return null;
                        })
                .when(audioService)
                .processarFluxoAudio(any(), anyLong(), any());

        controller.consume(buildVoiceUpdate(1L, "file-id", 1L));

        // 🔥 Agora verifica enviarMensagemSemMarkdown
        verify(telegramFacade, atLeast(2)).enviarMensagemSemMarkdown(eq(1L), any());
    }

    @Test
    void deveAvisarQuandoTranscricaoDesativada() {
        ReflectionTestUtils.setField(controller, "transcriptionEnabled", false);

        controller.consume(buildVoiceUpdate(1L, "file-id", 1L));

        verify(telegramFacade).enviarMensagem(1L, "🔇 Transcrição desativada.");
    }

    @Test
    void deveRejeitarAudioMaiorQue20MB() {
        ReflectionTestUtils.setField(controller, "transcriptionEnabled", true);

        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Voice voice = mock(Voice.class);
        User user = mock(User.class);

        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(1L);
        when(message.hasVoice()).thenReturn(true);
        when(message.getVoice()).thenReturn(voice);
        when(voice.getFileId()).thenReturn("file-id");
        when(voice.getFileSize()).thenReturn(21L * 1024 * 1024); // 21 MB
        when(message.getFrom()).thenReturn(user);
        when(user.getId()).thenReturn(1L);

        controller.consume(update);

        verify(telegramFacade)
                .enviarMensagem(1L, "📂 O arquivo de áudio excede 20 MB. Envie um arquivo menor.");
        verifyNoInteractions(fileService);
    }

    @Test
    void deveEnviarBotoesAoReceberAudioEmGrupo() {
        ReflectionTestUtils.setField(controller, "transcriptionEnabled", true);

        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Voice voice = mock(Voice.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(-100L);
        when(message.hasVoice()).thenReturn(true);
        when(message.getVoice()).thenReturn(voice);
        when(voice.getFileId()).thenReturn("file-id");
        when(voice.getFileSize()).thenReturn(1024L);
        when(voice.getDuration()).thenReturn(120);
        when(message.getChat()).thenReturn(chat);
        when(chat.isGroupChat()).thenReturn(true);
        when(message.getFrom()).thenReturn(user);
        when(user.getId()).thenReturn(123L);
        when(user.getFirstName()).thenReturn("André");
        when(user.getLastName()).thenReturn("Nascimento");

        controller.consume(update);

        // 🔥 Agora verifica enviarComBotoesHtml
        verify(telegramFacade)
                .enviarComBotoesHtml(eq(-100L), anyString(), any(InlineKeyboardMarkup.class));
        verifyNoInteractions(fileService, audioService);
    }

    // =========================
    // 🔘 CALLBACKS
    // =========================

    @Test
    void deveProcessarCallbackPublicar() {
        when(cache.recuperar(1L)).thenReturn("texto");
        when(bloggerClient.criarRascunho(any(), any())).thenReturn("url");

        controller.consume(buildCallbackUpdate(1L, "blogger:publicar"));

        verify(bloggerClient).criarRascunho("Post automático", "texto");
        verify(telegramFacade).enviarMensagem(1L, "✅ Publicado: url");
    }

    @Test
    void deveProcessarCallbackPublicarSemTexto() {
        when(cache.recuperar(1L)).thenReturn(null);

        controller.consume(buildCallbackUpdate(1L, "blogger:publicar"));

        verify(telegramFacade).enviarMensagem(1L, "⚠️ Nenhuma transcrição disponível.");
    }

    @Test
    void deveProcessarCallbackCancelar() {
        controller.consume(buildCallbackUpdate(1L, "blogger:cancelar"));

        verify(cache).remover(1L);
        verify(telegramFacade).enviarMensagem(1L, "❌ Publicação cancelada.");
    }

    @Test
    void deveEnviarFotoQuandoUrlValida() {
        when(movieService.buscarPorId(123L))
                .thenReturn(
                        new MovieOrchestrationResponse(
                                "texto", "https://image.tmdb.org/t/p/w500/poster.jpg"));

        controller.consume(buildCallbackUpdate(1L, "id:123"));

        verify(movieService).buscarPorId(123L);
        verify(telegramFacade)
                .enviarFotoHtml(
                        eq(1L), eq("https://image.tmdb.org/t/p/w500/poster.jpg"), anyString());
        verify(telegramFacade, never()).enviarMensagem(anyLong(), anyString());
    }

    @Test
    void deveEnviarMensagemSemImagemQuandoUrlInvalida() {
        when(movieService.buscarPorId(123L))
                .thenReturn(new MovieOrchestrationResponse("texto", ""));

        controller.consume(buildCallbackUpdate(1L, "id:123"));

        verify(movieService).buscarPorId(123L);
        verify(telegramFacade).enviarMensagemHtml(eq(1L), contains("texto"));
        verify(telegramFacade, never()).enviarFoto(anyLong(), anyString(), anyString());
        verify(telegramFacade, never()).enviarMensagem(anyLong(), anyString());
    }

    @Test
    void deveProcessarCallbackTranscricaoBrutaEmGrupo() throws Exception {
        ReflectionTestUtils.setField(controller, "transcriptionEnabled", true);

        Map<String, AudioRequest> pendingMap =
                (Map<String, AudioRequest>)
                        ReflectionTestUtils.getField(controller, "pendingGroupAudio");
        String fakeToken = "abc123";
        // Cria o request com senderId e senderName fictícios
        pendingMap.put(
                fakeToken,
                new AudioRequest(
                        "file123", -100L, System.currentTimeMillis(), 999L, "Fulano Teste"));

        CallbackQuery cb = mock(CallbackQuery.class);
        Message msg = mock(Message.class);
        User user = mock(User.class);
        when(cb.getData()).thenReturn("trans_bruto|" + fakeToken);
        when(cb.getMessage()).thenReturn(msg);
        when(msg.getChatId()).thenReturn(-100L);
        when(msg.getMessageId()).thenReturn(42);
        when(cb.getFrom()).thenReturn(user);
        when(user.getId()).thenReturn(999L);
        when(cb.getId()).thenReturn("cb123");

        File fakeFile = new File("audio.oga");
        when(fileService.baixarArquivo("file123")).thenReturn(fakeFile);

        doAnswer(
                        inv -> {
                            BiConsumer<String, Boolean> cbk = inv.getArgument(2);
                            cbk.accept("texto bruto", false);
                            return null;
                        })
                .when(audioService)
                .processarFluxoAudio(any(), eq(999L), any());

        controller.consume(buildCallbackUpdate(cb));

        verify(telegramFacade, timeout(2000)).enviarMensagemSemMarkdown(eq(999L), anyString());
        verify(telegramFacade)
                .answerCallbackQuery(
                        anyString(), eq("Processando áudio... enviarei no privado."), eq(false));
        assertThat(pendingMap).containsKey(fakeToken);
    }

    // =========================
    // 🧩 UTIL
    // =========================

    @Test
    void deveDividirMensagemGrande() {
        String texto = "a ".repeat(5000);
        List<String> partes =
                (List<String>)
                        ReflectionTestUtils.invokeMethod(
                                controller, "dividirMensagem", texto, 4000);
        assertThat(partes).isNotNull().hasSizeGreaterThan(1);
    }

    @Test
    void deveIgnorarMensagemSemConteudo() {
        var update = mock(Update.class);
        var message = mock(Message.class);
        var user = mock(User.class);

        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.getFrom()).thenReturn(user);
        when(user.getId()).thenReturn(12345L);
        when(message.hasText()).thenReturn(false);
        when(message.hasVoice()).thenReturn(false);

        controller.consume(update);

        verifyNoInteractions(movieService, audioService);
    }

    @Test
    void deveConsumirListaDeUpdates() {
        var update = mock(Update.class);
        when(update.hasMessage()).thenReturn(false);

        controller.consume(List.of(update));

        verifyNoInteractions(movieService, audioService);
    }

    @Test
    void deveIgnorarListaVaziaOuNula() {
        controller.consume(List.of());
        controller.consume((List<Update>) null);

        verifyNoInteractions(
                movieService, audioService, bloggerClient, cache, fileService, telegramFacade);
    }

    @Test
    void deveFecharMarkdownQuandoAsteriscosImpares() {
        String fechado =
                (String)
                        ReflectionTestUtils.invokeMethod(
                                controller, "fecharMarkdown", "*texto* com *asterisco");
        assertThat(fechado).endsWith("*");
    }

    @Test
    void deveEnviarRespostaComBotoesBlogger() {
        ReflectionTestUtils.invokeMethod(controller, "enviarRespostaComBotoesBlogger", 1L, "texto");
        verify(cache).salvar(1L, "texto");
        verify(telegramFacade).enviarComBotoes(eq(1L), eq("texto"), any());
    }

    // =========================
    // 🏗️ BUILDERS
    // =========================

    private Update buildTextUpdate(long chatId, String text) {
        var update = mock(Update.class);
        var message = mock(Message.class);
        var user = mock(User.class);

        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(chatId);
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn(text);
        when(message.getFrom()).thenReturn(user);
        when(user.getId()).thenReturn(12345L);

        return update;
    }

    private Update buildVoiceUpdate(long chatId, String fileId, long userId) {
        var update = mock(Update.class);
        var message = mock(Message.class);
        var voice = mock(Voice.class);
        var user = mock(User.class);
        var chat = mock(Chat.class);

        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(chatId);
        when(message.hasVoice()).thenReturn(true);
        when(message.getVoice()).thenReturn(voice);
        when(voice.getFileId()).thenReturn(fileId);
        when(voice.getFileSize()).thenReturn(1024L);
        when(message.getFrom()).thenReturn(user);
        when(user.getId()).thenReturn(userId);
        when(message.getChat()).thenReturn(chat);
        when(chat.isGroupChat()).thenReturn(false);
        when(chat.isSuperGroupChat()).thenReturn(false);

        return update;
    }

    private Update buildCallbackUpdate(long chatId, String data) {
        var update = mock(Update.class);
        var cb = mock(CallbackQuery.class);
        var message = mock(Message.class);
        var user = mock(User.class);

        when(update.hasCallbackQuery()).thenReturn(true);
        when(update.getCallbackQuery()).thenReturn(cb);
        when(cb.getData()).thenReturn(data);
        when(cb.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(chatId);
        when(cb.getId()).thenReturn("fake-cb-id");
        when(cb.getFrom()).thenReturn(user); // 🔥 ESSENCIAL
        when(user.getId()).thenReturn(987654L);
        when(user.getFirstName()).thenReturn("Testador");
        when(user.getLastName()).thenReturn("Silva");

        return update;
    }

    private Update buildCallbackUpdate(CallbackQuery cb) {
        var update = mock(Update.class);
        when(update.hasCallbackQuery()).thenReturn(true);
        when(update.getCallbackQuery()).thenReturn(cb);
        return update;
    }
}
