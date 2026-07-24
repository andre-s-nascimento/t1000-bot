/* (c) 2026 | 22/07/2026 */
package net.ddns.adambravo79.tmill.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;

import net.ddns.adambravo79.tmill.dto.AudioRequest;
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
        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), eq("Texto refinado"));
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
        when(audioService.processarEArmazenar(
                        eq(mockFile), eq(GROUP_CHAT_ID), eq(USER_ID), eq("Testador Silva")))
                .thenReturn(CompletableFuture.completedFuture(processed));

        audioHandler.handleAudioUpdate(update);

        verify(fileService).baixarArquivo(FILE_ID);
        verify(audioService)
                .processarEArmazenar(
                        eq(mockFile), eq(GROUP_CHAT_ID), eq(USER_ID), eq("Testador Silva"));
        verify(cacheService).put(eq(FILE_ID), eq("bruto"), eq("refinado"));
        verify(transcriptStore)
                .saveTranscriptWithRaw(
                        eq(GROUP_CHAT_ID),
                        eq(USER_ID),
                        eq("Testador Silva"),
                        eq("bruto"),
                        eq("refinado"));
        verify(telegramFacade)
                .enviarComBotoesHtml(
                        eq(GROUP_CHAT_ID), anyString(), any(InlineKeyboardMarkup.class));
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
                .answerCallbackQuery(
                        eq("cb123"), eq("Processando áudio... enviarei no privado."), eq(false));
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
                .answerCallbackQuery(
                        eq("cb123"), eq("Pedido expirado. Envie o audio novamente."), eq(true));
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
}
