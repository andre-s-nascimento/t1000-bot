/* (c) 2026 | 22/07/2026 */
package net.ddns.adambravo79.tmill.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;

import lombok.SneakyThrows;
import net.ddns.adambravo79.tmill.dto.AudioRequest;
import net.ddns.adambravo79.tmill.exception.AudioProcessingException;
import net.ddns.adambravo79.tmill.model.TranscriptionCacheEntry;
import net.ddns.adambravo79.tmill.service.AudioPipelineService;
import net.ddns.adambravo79.tmill.service.TelegramFileService;
import net.ddns.adambravo79.tmill.service.TranscriptStoreService;
import net.ddns.adambravo79.tmill.service.cache.FileTranscriptionCacheService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.TelegramUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AudioHandlerTest {

    @Mock private TelegramFileService fileService;
    @Mock private AudioPipelineService audioService;
    @Mock private FileTranscriptionCacheService cacheService;
    @Mock private TranscriptStoreService transcriptStore;
    @Mock private TelegramFacade telegramFacade;
    @Mock private TelegramUtils utils;

    @InjectMocks private AudioHandler audioHandler;

    private Update update;
    private Message message;
    private User user;
    private Chat chat;
    private com.pengrad.telegrambot.model.Audio audio;

    private static final long CHAT_ID = 12345L;
    private static final long USER_ID = 999L;
    private static final long GROUP_CHAT_ID = -100L;
    private static final String FILE_ID = "file-id";
    private static final int DURATION = 120;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(audioHandler, "transcriptionEnabled", true);
        ReflectionTestUtils.setField(audioHandler, "maxSizeMb", 20);
        ReflectionTestUtils.setField(audioHandler, "telegramMessageLimit", 4000);

        update = mock(Update.class);
        message = mock(Message.class);
        user = mock(User.class);
        chat = mock(Chat.class);
        audio = mock(com.pengrad.telegrambot.model.Audio.class);

        when(update.message()).thenReturn(message);
        when(message.from()).thenReturn(user);
        when(message.chat()).thenReturn(chat);
        when(user.id()).thenReturn(USER_ID);
        when(user.firstName()).thenReturn("Testador");
        when(user.lastName()).thenReturn("Silva");

        when(utils.buildFullName(any(User.class))).thenReturn("Testador Silva");
        when(utils.escapeHtml(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    // =========================
    // 🧪 TESTES DE DESATIVAÇÃO E TAMANHO
    // =========================

    @Test
    void deveIgnorarAudioSeTranscricaoDesativada() {
        ReflectionTestUtils.setField(audioHandler, "transcriptionEnabled", false);
        when(chat.id()).thenReturn(CHAT_ID);
        when(message.audio()).thenReturn(audio);
        when(audio.fileId()).thenReturn(FILE_ID);
        when(audio.fileSize()).thenReturn(1024L);

        audioHandler.handleAudioUpdate(update);

        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("Transcricao desativada"));
        verifyNoInteractions(fileService, audioService);
    }

    @Test
    void deveRejeitarAudioGrande() {
        when(chat.id()).thenReturn(CHAT_ID);
        when(message.audio()).thenReturn(audio);
        when(audio.fileId()).thenReturn(FILE_ID);
        when(audio.fileSize()).thenReturn(25L * 1024 * 1024); // 25 MB

        audioHandler.handleAudioUpdate(update);

        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("excede 20 MB"));
        verifyNoInteractions(fileService, audioService);
    }

    // =========================
    // 🧪 ÁUDIO EM PRIVADO
    // =========================

    @Test
    void deveProcessarAudioPrivadoComSucesso() {
        when(chat.id()).thenReturn(CHAT_ID);
        when(chat.type()).thenReturn(com.pengrad.telegrambot.model.Chat.Type.Private);
        when(message.audio()).thenReturn(audio);
        when(audio.fileId()).thenReturn(FILE_ID);
        when(audio.fileSize()).thenReturn(1024L);
        when(audio.duration()).thenReturn(DURATION);

        File mockFile = new File("audio.oga");
        when(fileService.baixarArquivo(FILE_ID)).thenReturn(mockFile);

        doAnswer(
                        inv -> {
                            BiConsumer<String, Boolean> callback = inv.getArgument(4);
                            callback.accept("Texto refinado", true);
                            return null;
                        })
                .when(audioService)
                .processarFluxoAudio(any(File.class), anyLong(), anyLong(), anyString(), any());

        audioHandler.handleAudioUpdate(update);

        verify(fileService).baixarArquivo(FILE_ID);
        verify(audioService)
                .processarFluxoAudio(
                        eq(mockFile), eq(CHAT_ID), eq(USER_ID), eq("Testador Silva"), any());
        verify(telegramFacade).enviarMensagem(CHAT_ID, "Texto refinado");
        verify(telegramFacade, never()).enviarComBotoesHtml(anyLong(), anyString(), any());
    }

    @Test
    void deveDividirMensagemLongaNoPrivado() {
        when(chat.id()).thenReturn(CHAT_ID);
        when(chat.type()).thenReturn(com.pengrad.telegrambot.model.Chat.Type.Private);
        when(message.audio()).thenReturn(audio);
        when(audio.fileId()).thenReturn(FILE_ID);
        when(audio.fileSize()).thenReturn(1024L);

        File mockFile = new File("audio.oga");
        when(fileService.baixarArquivo(FILE_ID)).thenReturn(mockFile);

        String textoLongo = "a".repeat(5000);
        doAnswer(
                        inv -> {
                            BiConsumer<String, Boolean> callback = inv.getArgument(4);
                            callback.accept(textoLongo, true);
                            return null;
                        })
                .when(audioService)
                .processarFluxoAudio(any(File.class), anyLong(), anyLong(), anyString(), any());

        when(utils.splitMessage(anyString(), anyInt())).thenReturn(List.of("Parte 1", "Parte 2"));

        audioHandler.handleAudioUpdate(update);

        verify(utils).splitMessage(textoLongo, 4000);
        verify(telegramFacade, times(2)).enviarMensagem(eq(CHAT_ID), anyString());
    }

    // =========================
    // 🧪 ÁUDIO EM GRUPO
    // =========================

    @Test
    @SneakyThrows
    void deveProcessarAudioGrupoComSucesso() {
        when(chat.id()).thenReturn(GROUP_CHAT_ID);
        when(chat.type()).thenReturn(com.pengrad.telegrambot.model.Chat.Type.supergroup);
        when(message.audio()).thenReturn(audio);
        when(audio.fileId()).thenReturn(FILE_ID);
        when(audio.fileSize()).thenReturn(1024L);
        when(audio.duration()).thenReturn(DURATION);

        File mockFile = new File("audio.oga");
        when(fileService.baixarArquivo(FILE_ID)).thenReturn(mockFile);

        AudioPipelineService.ProcessedAudio processed =
                new AudioPipelineService.ProcessedAudio("bruto", "refinado");
        when(audioService.processarEArmazenar(mockFile, GROUP_CHAT_ID, USER_ID, "Testador Silva"))
                .thenReturn(CompletableFuture.completedFuture(processed));

        audioHandler.handleAudioUpdate(update);
        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            verify(fileService).baixarArquivo(FILE_ID);
                            verify(audioService)
                                    .processarEArmazenar(
                                            mockFile, GROUP_CHAT_ID, USER_ID, "Testador Silva");
                            verify(cacheService).put(FILE_ID, "bruto", "refinado");
                            verify(transcriptStore)
                                    .saveTranscriptWithRaw(
                                            GROUP_CHAT_ID,
                                            USER_ID,
                                            "Testador Silva",
                                            "bruto",
                                            "refinado");
                            verify(telegramFacade)
                                    .enviarComBotoesHtml(
                                            eq(GROUP_CHAT_ID),
                                            anyString(),
                                            any(InlineKeyboardMarkup.class));
                        });
    }

    // =========================
    // 🧪 CALLBACK DE TRANSCRIÇÃO – CACHE HIT
    // =========================

    @Test
    void deveEntregarTranscricaoDoCache() {
        CallbackQuery callback = mock(CallbackQuery.class);
        when(callback.from()).thenReturn(user);
        when(callback.id()).thenReturn("cb123");
        when(callback.data()).thenReturn("trans_refinado|token123");

        Message callbackMessage = mock(Message.class);
        when(callback.maybeInaccessibleMessage()).thenReturn(callbackMessage);
        when(callbackMessage.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(GROUP_CHAT_ID);

        AudioRequest request =
                new AudioRequest(
                        FILE_ID,
                        GROUP_CHAT_ID,
                        System.currentTimeMillis(),
                        USER_ID,
                        "Testador Silva");
        java.util.Map<String, AudioRequest> pendingMap =
                new java.util.concurrent.ConcurrentHashMap<>();
        pendingMap.put("token123", request);
        ReflectionTestUtils.setField(audioHandler, "pendingRequests", pendingMap);

        TranscriptionCacheEntry entry =
                new TranscriptionCacheEntry("bruto", "refinado", System.currentTimeMillis());
        when(cacheService.get(FILE_ID)).thenReturn(entry);

        audioHandler.handleTranscriptionCallback(callback, "trans_refinado|token123");

        verify(telegramFacade)
                .enviarMensagem(eq(USER_ID), contains("✨ Transcrição Refinada:\nrefinado"));
    }

    // =========================
    // 🧪 CALLBACK DE TRANSCRIÇÃO – CACHE MISS
    // =========================

    @Test
    void deveProcessarAudioNovamenteNoCacheMiss() {
        CallbackQuery callback = mock(CallbackQuery.class);
        when(callback.from()).thenReturn(user);
        when(callback.id()).thenReturn("cb123");
        when(callback.data()).thenReturn("trans_bruto|token456");

        Message callbackMessage = mock(Message.class);
        when(callback.maybeInaccessibleMessage()).thenReturn(callbackMessage);
        when(callbackMessage.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(GROUP_CHAT_ID);

        AudioRequest request =
                new AudioRequest(
                        FILE_ID,
                        GROUP_CHAT_ID,
                        System.currentTimeMillis(),
                        USER_ID,
                        "Testador Silva");
        java.util.Map<String, AudioRequest> pendingMap =
                new java.util.concurrent.ConcurrentHashMap<>();
        pendingMap.put("token456", request);
        ReflectionTestUtils.setField(audioHandler, "pendingRequests", pendingMap);

        when(cacheService.get(FILE_ID)).thenReturn(null);

        File mockFile = new File("audio.oga");
        when(fileService.baixarArquivo(FILE_ID)).thenReturn(mockFile);

        doAnswer(
                        inv -> {
                            BiConsumer<String, Boolean> cb = inv.getArgument(4);
                            cb.accept("texto bruto", false);
                            return null;
                        })
                .when(audioService)
                .processarFluxoAudio(any(File.class), anyLong(), anyLong(), anyString(), any());

        audioHandler.handleTranscriptionCallback(callback, "trans_bruto|token456");

        verify(telegramFacade)
                .answerCallbackQuery("cb123", "Processando áudio... enviarei no privado.", false);
    }

    // =========================
    // 🧪 TOKEN INVÁLIDO
    // =========================

    @Test
    void deveAvisarTokenInvalido() {
        CallbackQuery callback = mock(CallbackQuery.class);
        when(callback.from()).thenReturn(user);
        when(callback.id()).thenReturn("cb123");
        when(callback.data()).thenReturn("trans_refinado|token999");

        Message callbackMessage = mock(Message.class);
        when(callback.maybeInaccessibleMessage()).thenReturn(callbackMessage);
        when(callbackMessage.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(GROUP_CHAT_ID);

        audioHandler.handleTranscriptionCallback(callback, "trans_refinado|token999");

        verify(telegramFacade)
                .answerCallbackQuery("cb123", "Pedido expirado. Envie o audio novamente.", true);
        verifyNoInteractions(cacheService, fileService, audioService);
    }

    // =========================
    // 🧪 ERRO 403 (USUÁRIO NÃO INICIOU O BOT)
    // =========================

    @Test
    void deveAvisarNoGrupoQuandoUsuarioNaoIniciouBot() {
        CallbackQuery callback = mock(CallbackQuery.class);
        when(callback.from()).thenReturn(user);
        when(callback.id()).thenReturn("cb123");
        when(callback.data()).thenReturn("trans_refinado|token789");

        Message callbackMessage = mock(Message.class);
        when(callback.maybeInaccessibleMessage()).thenReturn(callbackMessage);
        when(callbackMessage.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(GROUP_CHAT_ID);

        AudioRequest request =
                new AudioRequest(
                        FILE_ID,
                        GROUP_CHAT_ID,
                        System.currentTimeMillis(),
                        USER_ID,
                        "Testador Silva");
        java.util.Map<String, AudioRequest> pendingMap =
                new java.util.concurrent.ConcurrentHashMap<>();
        pendingMap.put("token789", request);
        ReflectionTestUtils.setField(audioHandler, "pendingRequests", pendingMap);

        TranscriptionCacheEntry entry =
                new TranscriptionCacheEntry("bruto", "refinado", System.currentTimeMillis());
        when(cacheService.get(FILE_ID)).thenReturn(entry);

        // Lança a exceção correta (subclasse Forbidden)
        doThrow(
                        HttpClientErrorException.create(
                                HttpStatus.FORBIDDEN,
                                "can't initiate conversation",
                                null,
                                null,
                                null))
                .when(telegramFacade)
                .enviarMensagem(eq(USER_ID), anyString());

        audioHandler.handleTranscriptionCallback(callback, "trans_refinado|token789");

        // Verifica que a mensagem de aviso foi enviada para o grupo
        verify(telegramFacade)
                .enviarMensagem(
                        eq(GROUP_CHAT_ID),
                        contains(
                                "⚠️ Usuario precisa iniciar conversa com o bot no privado para"
                                        + " receber transcricoes."));
    }

    // ========================= TESTES ADICIONAIS PARA COBERTURA =========================

    // 1. Update sem áudio e sem voice
    @Test
    void deveIgnorarUpdateSemAudioNemVoice() {
        when(chat.id()).thenReturn(CHAT_ID);
        when(message.audio()).thenReturn(null);
        when(message.voice()).thenReturn(null);

        audioHandler.handleAudioUpdate(update);

        verifyNoInteractions(fileService, audioService, telegramFacade);
    }

    // 2. ProcessGroupAudio com resultado nulo
    @Test
    @SneakyThrows
    void deveNotificarErroQuandoResultadoProcessamentoNulo() {
        when(chat.id()).thenReturn(GROUP_CHAT_ID);
        when(chat.type()).thenReturn(com.pengrad.telegrambot.model.Chat.Type.supergroup);
        when(message.audio()).thenReturn(audio);
        when(audio.fileId()).thenReturn(FILE_ID);
        when(audio.fileSize()).thenReturn(1024L);
        when(audio.duration()).thenReturn(DURATION);

        File mockFile = new File("audio.oga");
        when(fileService.baixarArquivo(FILE_ID)).thenReturn(mockFile);
        when(audioService.processarEArmazenar(any(), anyLong(), anyLong(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        audioHandler.handleAudioUpdate(update);
        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            verify(telegramFacade)
                                    .enviarMensagem(
                                            eq(GROUP_CHAT_ID),
                                            contains("Erro ao processar o audio"));
                        });
    }

    // 3. Falha no pipeline com AudioProcessingException
    @Test
    @SneakyThrows
    void deveTratarFalhaNoPipelineComAudioProcessingException() {
        when(chat.id()).thenReturn(GROUP_CHAT_ID);
        when(chat.type()).thenReturn(com.pengrad.telegrambot.model.Chat.Type.supergroup);
        when(message.audio()).thenReturn(audio);
        when(audio.fileId()).thenReturn(FILE_ID);
        when(audio.fileSize()).thenReturn(1024L);
        when(audio.duration()).thenReturn(DURATION);

        File mockFile = new File("audio.oga");
        when(fileService.baixarArquivo(FILE_ID)).thenReturn(mockFile);

        CompletableFuture<AudioPipelineService.ProcessedAudio> failedFuture =
                new CompletableFuture<>();
        failedFuture.completeExceptionally(new AudioProcessingException("Falha no pipeline"));
        when(audioService.processarEArmazenar(mockFile, GROUP_CHAT_ID, USER_ID, "Testador Silva"))
                .thenReturn(failedFuture);

        audioHandler.handleAudioUpdate(update);
        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            verify(telegramFacade)
                                    .enviarMensagem(
                                            eq(GROUP_CHAT_ID),
                                            contains("Erro ao processar o audio"));
                        }); // Aguarda a execução assíncrona

        // Mensagem real da constante ERRO_PROCESSAR_AUDIO

    }

    // 4. Falha no pipeline com RuntimeException
    @Test
    @SneakyThrows
    void deveTratarFalhaNoPipelineComRuntimeException() {
        when(chat.id()).thenReturn(GROUP_CHAT_ID);
        when(chat.type()).thenReturn(com.pengrad.telegrambot.model.Chat.Type.supergroup);
        when(message.audio()).thenReturn(audio);
        when(audio.fileId()).thenReturn(FILE_ID);
        when(audio.fileSize()).thenReturn(1024L);
        when(audio.duration()).thenReturn(DURATION);

        File mockFile = new File("audio.oga");
        when(fileService.baixarArquivo(FILE_ID)).thenReturn(mockFile);

        CompletableFuture<AudioPipelineService.ProcessedAudio> failedFuture =
                new CompletableFuture<>();
        failedFuture.completeExceptionally(new AudioProcessingException("Falha no pipeline"));
        when(audioService.processarEArmazenar(any(), anyLong(), anyLong(), anyString()))
                .thenReturn(failedFuture);

        audioHandler.handleAudioUpdate(update);

        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            verify(telegramFacade)
                                    .enviarMensagem(
                                            eq(GROUP_CHAT_ID),
                                            contains("Erro ao processar o audio"));
                        });
    }

    // 5. Callback malformado (menos de 2 partes)
    @Test
    void deveIgnorarCallbackMalformado() {
        CallbackQuery callback = mock(CallbackQuery.class);
        when(callback.data()).thenReturn("trans_bruto"); // sem token

        audioHandler.handleTranscriptionCallback(callback, "trans_bruto");

        verifyNoInteractions(cacheService, fileService, audioService, telegramFacade);
    }

    // 6. Callback com mensagem inacessível
    @Test
    void deveIgnorarCallbackComMensagemInacessivel() {
        CallbackQuery callback = mock(CallbackQuery.class);
        when(callback.from()).thenReturn(user);
        when(callback.id()).thenReturn("cb123");
        when(callback.data()).thenReturn("trans_refinado|token123");
        when(callback.maybeInaccessibleMessage()).thenReturn(null);

        audioHandler.handleTranscriptionCallback(callback, "trans_refinado|token123");

        // 👇 Removido o eq(true) e passado true diretamente
        verify(telegramFacade)
                .answerCallbackQuery("cb123", "Mensagem original não disponível", true);
        verifyNoInteractions(cacheService, fileService, audioService);
    }

    // 7. processarAudioCallback com AudioProcessingException
    @Test
    void deveTratarErroAudioProcessingExceptionNoProcessamentoCallback() {
        CallbackQuery callback = mock(CallbackQuery.class);
        when(callback.from()).thenReturn(user);
        when(callback.id()).thenReturn("cb123");
        when(callback.data()).thenReturn("trans_refinado|token456");

        Message callbackMessage = mock(Message.class);
        when(callback.maybeInaccessibleMessage()).thenReturn(callbackMessage);
        when(callbackMessage.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(GROUP_CHAT_ID);

        AudioRequest request =
                new AudioRequest(
                        FILE_ID,
                        GROUP_CHAT_ID,
                        System.currentTimeMillis(),
                        USER_ID,
                        "Testador Silva");
        java.util.Map<String, AudioRequest> pendingMap =
                new java.util.concurrent.ConcurrentHashMap<>();
        pendingMap.put("token456", request);
        ReflectionTestUtils.setField(audioHandler, "pendingRequests", pendingMap);

        when(cacheService.get(FILE_ID)).thenReturn(null);
        when(fileService.baixarArquivo(FILE_ID)).thenReturn(new File("audio.oga"));

        doThrow(new AudioProcessingException("Falha no áudio"))
                .when(audioService)
                .processarFluxoAudio(any(File.class), anyLong(), anyLong(), anyString(), any());

        // Executa e espera a thread assíncrona (usando sleep para garantir execução)
        audioHandler.handleTranscriptionCallback(callback, "trans_refinado|token456");
        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            verify(telegramFacade)
                                    .enviarMensagem(eq(USER_ID), contains("Erro no processamento"));
                        });
    }

    // 8. processarAudioCallback com HttpClientErrorException (não-403)
    @Test
    void deveTratarErroHttpClientExceptionNoProcessamentoCallback() {
        CallbackQuery callback = mock(CallbackQuery.class);
        when(callback.from()).thenReturn(user);
        when(callback.id()).thenReturn("cb123");
        when(callback.data()).thenReturn("trans_refinado|token456");

        Message callbackMessage = mock(Message.class);
        when(callback.maybeInaccessibleMessage()).thenReturn(callbackMessage);
        when(callbackMessage.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(GROUP_CHAT_ID);

        AudioRequest request =
                new AudioRequest(
                        FILE_ID,
                        GROUP_CHAT_ID,
                        System.currentTimeMillis(),
                        USER_ID,
                        "Testador Silva");
        java.util.Map<String, AudioRequest> pendingMap =
                new java.util.concurrent.ConcurrentHashMap<>();
        pendingMap.put("token456", request);
        ReflectionTestUtils.setField(audioHandler, "pendingRequests", pendingMap);

        when(cacheService.get(FILE_ID)).thenReturn(null);
        when(fileService.baixarArquivo(FILE_ID)).thenReturn(new File("audio.oga"));

        doThrow(
                        HttpClientErrorException.create(
                                HttpStatus.BAD_REQUEST, "Bad Request", null, null, null))
                .when(audioService)
                .processarFluxoAudio(any(File.class), anyLong(), anyLong(), anyString(), any());

        audioHandler.handleTranscriptionCallback(callback, "trans_refinado|token456");
        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            verify(telegramFacade)
                                    .enviarMensagem(eq(USER_ID), contains("Erro no processamento"));
                        });
    }

    // 9. processarAudioCallback com ResourceAccessException
    @Test
    void deveTratarErroResourceAccessExceptionNoProcessamentoCallback() {
        CallbackQuery callback = mock(CallbackQuery.class);
        when(callback.from()).thenReturn(user);
        when(callback.id()).thenReturn("cb123");
        when(callback.data()).thenReturn("trans_refinado|token456");

        Message callbackMessage = mock(Message.class);
        when(callback.maybeInaccessibleMessage()).thenReturn(callbackMessage);
        when(callbackMessage.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(GROUP_CHAT_ID);

        AudioRequest request =
                new AudioRequest(
                        FILE_ID,
                        GROUP_CHAT_ID,
                        System.currentTimeMillis(),
                        USER_ID,
                        "Testador Silva");
        java.util.Map<String, AudioRequest> pendingMap =
                new java.util.concurrent.ConcurrentHashMap<>();
        pendingMap.put("token456", request);
        ReflectionTestUtils.setField(audioHandler, "pendingRequests", pendingMap);

        when(cacheService.get(FILE_ID)).thenReturn(null);
        when(fileService.baixarArquivo(FILE_ID)).thenReturn(new File("audio.oga"));

        doThrow(new ResourceAccessException("Falha de rede"))
                .when(audioService)
                .processarFluxoAudio(any(File.class), anyLong(), anyLong(), anyString(), any());

        audioHandler.handleTranscriptionCallback(callback, "trans_refinado|token456");
        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            verify(telegramFacade)
                                    .enviarMensagem(
                                            eq(USER_ID), contains("Falha de conectividade"));
                        });
    }

    // 10. processarAudioCallback com RuntimeException
    @Test
    void deveTratarErroRuntimeExceptionNoProcessamentoCallback() {
        CallbackQuery callback = mock(CallbackQuery.class);
        when(callback.from()).thenReturn(user);
        when(callback.id()).thenReturn("cb123");
        when(callback.data()).thenReturn("trans_refinado|token456");

        Message callbackMessage = mock(Message.class);
        when(callback.maybeInaccessibleMessage()).thenReturn(callbackMessage);
        when(callbackMessage.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(GROUP_CHAT_ID);

        AudioRequest request =
                new AudioRequest(
                        FILE_ID,
                        GROUP_CHAT_ID,
                        System.currentTimeMillis(),
                        USER_ID,
                        "Testador Silva");
        java.util.Map<String, AudioRequest> pendingMap =
                new java.util.concurrent.ConcurrentHashMap<>();
        pendingMap.put("token456", request);
        ReflectionTestUtils.setField(audioHandler, "pendingRequests", pendingMap);

        when(cacheService.get(FILE_ID)).thenReturn(null);
        when(fileService.baixarArquivo(FILE_ID)).thenReturn(new File("audio.oga"));

        doThrow(new RuntimeException("Erro inesperado"))
                .when(audioService)
                .processarFluxoAudio(any(File.class), anyLong(), anyLong(), anyString(), any());

        audioHandler.handleTranscriptionCallback(callback, "trans_refinado|token456");
        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            verify(telegramFacade)
                                    .enviarMensagem(eq(USER_ID), contains("Erro no processamento"));
                        });
    }

    // 11. safeSendMessage com HttpClientErrorException genérico
    @Test
    void deveCapturarHttpClientErrorExceptionNoSafeSendMessage() {
        // Provoca exceção no envio da mensagem
        when(chat.id()).thenReturn(CHAT_ID);
        when(message.audio()).thenReturn(audio);
        when(audio.fileId()).thenReturn(FILE_ID);
        when(audio.fileSize()).thenReturn(1024L);
        when(audio.duration()).thenReturn(DURATION);
        when(chat.type()).thenReturn(com.pengrad.telegrambot.model.Chat.Type.Private);

        File mockFile = new File("audio.oga");
        when(fileService.baixarArquivo(FILE_ID)).thenReturn(mockFile);

        doAnswer(
                        inv -> {
                            BiConsumer<String, Boolean> callback = inv.getArgument(4);
                            callback.accept("Texto", true);
                            return null;
                        })
                .when(audioService)
                .processarFluxoAudio(any(File.class), anyLong(), anyLong(), anyString(), any());

        doThrow(
                        HttpClientErrorException.create(
                                HttpStatus.BAD_GATEWAY, "Bad Gateway", null, null, null))
                .when(telegramFacade)
                .enviarMensagem(eq(CHAT_ID), anyString());

        audioHandler.handleAudioUpdate(update);

        // Não deve propagar exceção, apenas logar
        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), anyString());
    }

    // 12. safeSendMessage com ResourceAccessException
    @Test
    void deveCapturarResourceAccessExceptionNoSafeSendMessage() {
        when(chat.id()).thenReturn(CHAT_ID);
        when(message.audio()).thenReturn(audio);
        when(audio.fileId()).thenReturn(FILE_ID);
        when(audio.fileSize()).thenReturn(1024L);
        when(audio.duration()).thenReturn(DURATION);
        when(chat.type()).thenReturn(com.pengrad.telegrambot.model.Chat.Type.Private);

        File mockFile = new File("audio.oga");
        when(fileService.baixarArquivo(FILE_ID)).thenReturn(mockFile);

        doAnswer(
                        inv -> {
                            BiConsumer<String, Boolean> callback = inv.getArgument(4);
                            callback.accept("Texto", true);
                            return null;
                        })
                .when(audioService)
                .processarFluxoAudio(any(File.class), anyLong(), anyLong(), anyString(), any());

        doThrow(new ResourceAccessException("Falha de rede"))
                .when(telegramFacade)
                .enviarMensagem(eq(CHAT_ID), anyString());

        audioHandler.handleAudioUpdate(update);

        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), anyString());
    }

    // 13. safeSendTranscription com HttpClientErrorException (não-403)
    @Test
    @SneakyThrows
    void deveCapturarHttpClientErrorExceptionNoSafeSendTranscription() {
        CallbackQuery callback = mock(CallbackQuery.class);
        when(callback.from()).thenReturn(user);
        when(callback.id()).thenReturn("cb123");
        when(callback.data()).thenReturn("trans_refinado|token456");

        Message callbackMessage = mock(Message.class);
        when(callback.maybeInaccessibleMessage()).thenReturn(callbackMessage);
        when(callbackMessage.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(GROUP_CHAT_ID);

        AudioRequest request =
                new AudioRequest(
                        FILE_ID,
                        GROUP_CHAT_ID,
                        System.currentTimeMillis(),
                        USER_ID,
                        "Testador Silva");
        java.util.Map<String, AudioRequest> pendingMap =
                new java.util.concurrent.ConcurrentHashMap<>();
        pendingMap.put("token456", request);
        ReflectionTestUtils.setField(audioHandler, "pendingRequests", pendingMap);

        TranscriptionCacheEntry entry =
                new TranscriptionCacheEntry("bruto", "refinado", System.currentTimeMillis());
        when(cacheService.get(FILE_ID)).thenReturn(entry);

        // A primeira tentativa falha, e o fallback também pode enviar uma mensagem
        doThrow(
                        HttpClientErrorException.create(
                                HttpStatus.BAD_REQUEST, "Bad Request", null, null, null))
                .when(telegramFacade)
                .enviarMensagem(eq(USER_ID), anyString());

        audioHandler.handleTranscriptionCallback(callback, "trans_refinado|token456");
        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            // Verifica que a mensagem de erro foi enviada
                            verify(telegramFacade, atLeastOnce())
                                    .enviarMensagem(eq(USER_ID), contains("Erro de comunicação"));
                        });
    }

    // 14. safeSendTranscription com ResourceAccessException
    @Test
    void deveCapturarResourceAccessExceptionNoSafeSendTranscription() {
        CallbackQuery callback = mock(CallbackQuery.class);
        when(callback.from()).thenReturn(user);
        when(callback.id()).thenReturn("cb123");
        when(callback.data()).thenReturn("trans_refinado|token456");

        Message callbackMessage = mock(Message.class);
        when(callback.maybeInaccessibleMessage()).thenReturn(callbackMessage);
        when(callbackMessage.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(GROUP_CHAT_ID);

        AudioRequest request =
                new AudioRequest(
                        FILE_ID,
                        GROUP_CHAT_ID,
                        System.currentTimeMillis(),
                        USER_ID,
                        "Testador Silva");
        java.util.Map<String, AudioRequest> pendingMap =
                new java.util.concurrent.ConcurrentHashMap<>();
        pendingMap.put("token456", request);
        ReflectionTestUtils.setField(audioHandler, "pendingRequests", pendingMap);

        TranscriptionCacheEntry entry =
                new TranscriptionCacheEntry("bruto", "refinado", System.currentTimeMillis());
        when(cacheService.get(FILE_ID)).thenReturn(entry);

        doThrow(new ResourceAccessException("Falha de rede"))
                .when(telegramFacade)
                .enviarMensagem(eq(USER_ID), anyString());

        audioHandler.handleTranscriptionCallback(callback, "trans_refinado|token456");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramFacade, times(2)).enviarMensagem(eq(USER_ID), captor.capture());
        List<String> messages = captor.getAllValues();
        assertThat(messages)
                .anyMatch(msg -> msg.contains("✨ Transcrição Refinada"))
                .anyMatch(msg -> msg.contains("Falha de conectividade"));
    }

    // 15. safeSendButtons com HttpClientErrorException
    @Test
    @SneakyThrows
    void deveCapturarHttpClientErrorExceptionNoSafeSendButtons() {
        when(chat.id()).thenReturn(GROUP_CHAT_ID);
        when(chat.type()).thenReturn(com.pengrad.telegrambot.model.Chat.Type.supergroup);
        when(message.audio()).thenReturn(audio);
        when(audio.fileId()).thenReturn(FILE_ID);
        when(audio.fileSize()).thenReturn(1024L);
        when(audio.duration()).thenReturn(DURATION);

        File mockFile = new File("audio.oga");
        when(fileService.baixarArquivo(FILE_ID)).thenReturn(mockFile);

        AudioPipelineService.ProcessedAudio processed =
                new AudioPipelineService.ProcessedAudio("bruto", "refinado");
        when(audioService.processarEArmazenar(mockFile, GROUP_CHAT_ID, USER_ID, "Testador Silva"))
                .thenReturn(CompletableFuture.completedFuture(processed));

        // Agora simula a exceção no envio dos botões
        doThrow(
                        HttpClientErrorException.create(
                                HttpStatus.BAD_REQUEST, "Bad Request", null, null, null))
                .when(telegramFacade)
                .enviarComBotoesHtml(eq(GROUP_CHAT_ID), anyString(), any());

        audioHandler.handleAudioUpdate(update);
        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            verify(telegramFacade)
                                    .enviarComBotoesHtml(eq(GROUP_CHAT_ID), anyString(), any());
                        });
    }

    // 17. tratarErroTranscricao com isForbidden = true
    @Test
    void deveTratarErroTranscricaoComForbiddenTrue() throws Exception {
        // Usa reflexão para chamar o método privado
        java.lang.reflect.Method method =
                AudioHandler.class.getDeclaredMethod(
                        "tratarErroTranscricao", Exception.class, long.class, long.class);
        method.setAccessible(true);

        Exception e =
                HttpClientErrorException.create(
                        HttpStatus.FORBIDDEN, "can't initiate conversation", null, null, null);
        method.invoke(audioHandler, e, USER_ID, GROUP_CHAT_ID);

        verify(telegramFacade)
                .enviarMensagem(eq(GROUP_CHAT_ID), contains("Usuario precisa iniciar conversa"));
        verify(telegramFacade, never()).enviarMensagem(eq(USER_ID), anyString());
    }

    // 18. tratarErroTranscricao com isForbidden = false
    @Test
    void deveTratarErroTranscricaoComForbiddenFalse() throws Exception {
        java.lang.reflect.Method method =
                AudioHandler.class.getDeclaredMethod(
                        "tratarErroTranscricao", Exception.class, long.class, long.class);
        method.setAccessible(true);

        Exception e = new RuntimeException("Erro genérico");
        method.invoke(audioHandler, e, USER_ID, GROUP_CHAT_ID);

        verify(telegramFacade).enviarMensagem(eq(USER_ID), contains("Erro no processamento"));
        verify(telegramFacade, never()).enviarMensagem(eq(GROUP_CHAT_ID), anyString());
    }

    // 19. gerarToken
    @Test
    void deveGerarTokenCorretamente() throws Exception {
        java.lang.reflect.Method method =
                AudioHandler.class.getDeclaredMethod("gerarToken", String.class);
        method.setAccessible(true);

        String token = (String) method.invoke(audioHandler, FILE_ID);
        assertThat(token).isNotNull();
        assertThat(token.length()).isLessThanOrEqualTo(20);
    }

    // 20. cleanExpiredTokens
    @Test
    void deveLimparTokensExpirados() throws Exception {
        java.lang.reflect.Method method =
                AudioHandler.class.getDeclaredMethod("cleanExpiredTokens");
        method.setAccessible(true);

        // Cria um token expirado
        java.util.Map<String, AudioRequest> pendingMap =
                new java.util.concurrent.ConcurrentHashMap<>();
        AudioRequest request =
                new AudioRequest(
                        FILE_ID,
                        GROUP_CHAT_ID,
                        System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000 - 1000,
                        USER_ID,
                        "Testador Silva");
        pendingMap.put("token_antigo", request);
        pendingMap.put(
                "token_novo",
                new AudioRequest(
                        FILE_ID, GROUP_CHAT_ID, System.currentTimeMillis(), USER_ID, "Testador"));
        ReflectionTestUtils.setField(audioHandler, "pendingRequests", pendingMap);

        method.invoke(audioHandler);

        // Deve remover apenas o token antigo
        assertThat(pendingMap).containsKey("token_novo").doesNotContainKey("token_antigo");
    }
}
