/* (c) 2026 | 22/07/2026 */
package net.ddns.adambravo79.tmill.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

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
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;

import lombok.SneakyThrows;
import net.ddns.adambravo79.tmill.dto.AudioProcessedEvent;
import net.ddns.adambravo79.tmill.dto.AudioRequest;
import net.ddns.adambravo79.tmill.exception.AudioProcessingException;
import net.ddns.adambravo79.tmill.model.TranscriptionCacheEntry;
import net.ddns.adambravo79.tmill.service.AudioPipelineService;
import net.ddns.adambravo79.tmill.service.TelegramFileService;
import net.ddns.adambravo79.tmill.service.cache.FileTranscriptionCacheService;
import net.ddns.adambravo79.tmill.service.config.AudioConfigService;
import net.ddns.adambravo79.tmill.service.feature.FeatureFlagAdminService;
import net.ddns.adambravo79.tmill.service.kafka.AudioEventPublisher;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;
import net.ddns.adambravo79.tmill.telegram.util.TelegramUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AudioHandlerTest {

    @Mock private TelegramFileService fileService;
    @Mock private AudioPipelineService audioService;
    @Mock private FileTranscriptionCacheService cacheService;
    @Mock private TelegramFacade telegramFacade;
    @Mock private TelegramUtils utils;
    @Mock private AudioEventPublisher audioEventPublisher;
    @Mock private FeatureFlagAdminService featureFlags;
    // 🔧 Onda 3.4: novos mocks obrigatórios para o construtor do AudioHandler
    @Mock private AudioConfigService audioConfig;
    @Mock private MetricsService metricsService;

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
        // 🔧 Onda 3.4: maxSizeMb agora vem do AudioConfigService
        lenient()
                .when(audioConfig.getTelegram())
                .thenReturn(new AudioConfigService.TelegramAudioConfig(20, 5));

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
        lenient().when(featureFlags.isEnabled("transcription.enabled")).thenReturn(true);
    }

    // =========================
    // 🧪 TESTES DE DESATIVAÇÃO E TAMANHO
    // =========================

    @Test
    void deveIgnorarAudioSeTranscricaoDesativada() {
        when(featureFlags.isEnabled("transcription.enabled")).thenReturn(false);
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

    @Test
    @DisplayName("AudioConfigService customizado: limite de 5 MB rejeita áudio de 10 MB")
    void deveRespeitarLimiteCustomizadoDoAudioConfig() {
        when(audioConfig.getTelegram())
                .thenReturn(new AudioConfigService.TelegramAudioConfig(5, 2));

        when(chat.id()).thenReturn(CHAT_ID);
        when(message.audio()).thenReturn(audio);
        when(audio.fileId()).thenReturn(FILE_ID);
        when(audio.fileSize()).thenReturn(10L * 1024 * 1024); // 10 MB > 5 MB

        audioHandler.handleAudioUpdate(update);

        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("excede 5 MB"));
        verifyNoInteractions(fileService, audioService);
    }

    // =========================
    // 🧪 ÁUDIO EM PRIVADO
    // =========================

    @Test
    void deveProcessarAudioPrivadoPublicandoNoKafka() {
        when(chat.id()).thenReturn(CHAT_ID);
        when(chat.type()).thenReturn(com.pengrad.telegrambot.model.Chat.Type.Private);
        when(message.audio()).thenReturn(audio);
        when(audio.fileId()).thenReturn(FILE_ID);
        when(audio.fileSize()).thenReturn(1024L);
        when(audio.duration()).thenReturn(DURATION);

        audioHandler.handleAudioUpdate(update);

        verify(audioEventPublisher)
                .publish(
                        argThat(
                                event ->
                                        event.fileId().equals(FILE_ID)
                                                && event.chatId() == CHAT_ID
                                                && event.userId() == USER_ID
                                                && event.tipoFluxo().equals("AMBOS")));

        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("fila de processamento"));
        verify(audioService, never())
                .processarFluxoAudio(any(), anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void deveEnviarBotoesAoReceberAudioProcessedEvent() {
        AudioProcessedEvent processed =
                new AudioProcessedEvent(
                        FILE_ID, GROUP_CHAT_ID, USER_ID, "Testador Silva", true, null, 400);

        audioHandler.onAudioProcessed(processed);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramFacade).enviarComBotoesHtml(eq(GROUP_CHAT_ID), captor.capture(), any());
        assertThat(captor.getValue()).contains("Testador Silva");
    }

    @Test
    void deveCapturarHttpClientErrorExceptionNoSafeSendButtons() {
        AudioProcessedEvent processed =
                new AudioProcessedEvent(
                        FILE_ID, GROUP_CHAT_ID, USER_ID, "Testador Silva", true, null, 400);

        doThrow(
                        HttpClientErrorException.create(
                                HttpStatus.BAD_REQUEST, "Bad Request", null, null, null))
                .when(telegramFacade)
                .enviarComBotoesHtml(eq(GROUP_CHAT_ID), anyString(), any());

        assertThatCode(() -> audioHandler.onAudioProcessed(processed)).doesNotThrowAnyException();

        verify(telegramFacade).enviarComBotoesHtml(eq(GROUP_CHAT_ID), anyString(), any());
    }

    @Test
    void deveCapturarResourceAccessExceptionNoSafeSendButtons() {
        AudioProcessedEvent processed =
                new AudioProcessedEvent(
                        FILE_ID, GROUP_CHAT_ID, USER_ID, "Testador Silva", true, null, 0);

        doThrow(new ResourceAccessException("Timeout"))
                .when(telegramFacade)
                .enviarComBotoesHtml(eq(GROUP_CHAT_ID), anyString(), any());

        assertThatCode(() -> audioHandler.onAudioProcessed(processed)).doesNotThrowAnyException();

        verify(telegramFacade).enviarComBotoesHtml(eq(GROUP_CHAT_ID), anyString(), any());
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

        verify(telegramFacade)
                .enviarMensagem(
                        eq(GROUP_CHAT_ID),
                        contains(
                                "⚠️ Usuario precisa iniciar conversa com o bot no privado para"
                                        + " receber transcricoes."));
    }

    // ========================= TESTES ADICIONAIS PARA COBERTURA =========================

    @Test
    void deveIgnorarUpdateSemAudioNemVoice() {
        when(chat.id()).thenReturn(CHAT_ID);
        when(message.audio()).thenReturn(null);
        when(message.voice()).thenReturn(null);

        audioHandler.handleAudioUpdate(update);

        verifyNoInteractions(fileService, audioService, telegramFacade);
    }

    @Test
    void deveIgnorarCallbackMalformado() {
        CallbackQuery callback = mock(CallbackQuery.class);
        when(callback.data()).thenReturn("trans_bruto"); // sem token

        audioHandler.handleTranscriptionCallback(callback, "trans_bruto");

        verifyNoInteractions(cacheService, fileService, audioService, telegramFacade);
    }

    @Test
    void deveIgnorarCallbackComMensagemInacessivel() {
        CallbackQuery callback = mock(CallbackQuery.class);
        when(callback.from()).thenReturn(user);
        when(callback.id()).thenReturn("cb123");
        when(callback.data()).thenReturn("trans_refinado|token123");
        when(callback.maybeInaccessibleMessage()).thenReturn(null);

        audioHandler.handleTranscriptionCallback(callback, "trans_refinado|token123");

        verify(telegramFacade)
                .answerCallbackQuery("cb123", "Mensagem original não disponível", true);
        verifyNoInteractions(cacheService, fileService, audioService);
    }

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

        audioHandler.handleTranscriptionCallback(callback, "trans_refinado|token456");
        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            verify(telegramFacade)
                                    .enviarMensagem(eq(USER_ID), contains("Erro no processamento"));
                        });
    }

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

    @Test
    void deveCapturarHttpClientErrorExceptionNoSafeSendMessage() {
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

        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), anyString());
    }

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

        doThrow(
                        HttpClientErrorException.create(
                                HttpStatus.BAD_REQUEST, "Bad Request", null, null, null))
                .when(telegramFacade)
                .enviarMensagem(eq(USER_ID), anyString());

        audioHandler.handleTranscriptionCallback(callback, "trans_refinado|token456");
        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            verify(telegramFacade, atLeastOnce())
                                    .enviarMensagem(eq(USER_ID), contains("Erro de comunicação"));
                        });
    }

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

    @Test
    void deveTratarErroTranscricaoComForbiddenTrue() throws Exception {
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

    @Test
    void deveGerarTokenCorretamente() throws Exception {
        java.lang.reflect.Method method =
                AudioHandler.class.getDeclaredMethod("gerarToken", String.class);
        method.setAccessible(true);

        String token = (String) method.invoke(audioHandler, FILE_ID);
        assertThat(token).isNotNull();
        assertThat(token.length()).isLessThanOrEqualTo(20);
    }

    @Test
    void deveLimparTokensExpirados() throws Exception {
        java.lang.reflect.Method method =
                AudioHandler.class.getDeclaredMethod("cleanExpiredTokens");
        method.setAccessible(true);

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

        assertThat(pendingMap).containsKey("token_novo").doesNotContainKey("token_antigo");
    }

    @Test
    void deveDividirMensagemLongaNaSafeSendTranscription() {
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
                new TranscriptionCacheEntry(
                        "bruto",
                        "texto muito longo " + "a".repeat(5000),
                        System.currentTimeMillis());
        when(cacheService.get(FILE_ID)).thenReturn(entry);

        when(utils.splitMessage(anyString(), anyInt())).thenReturn(List.of("Parte 1", "Parte 2"));

        audioHandler.handleTranscriptionCallback(callback, "trans_refinado|token456");

        verify(utils).splitMessage(contains("texto muito longo"), eq(4000));
        verify(telegramFacade, times(2)).enviarMensagem(eq(USER_ID), anyString());
    }

    @Test
    void deveEnviarMensagemSilasCastParaDuracaoLonga() {
        AudioProcessedEvent processed =
                new AudioProcessedEvent(
                        FILE_ID, GROUP_CHAT_ID, USER_ID, "Testador Silva", true, null, 400);

        audioHandler.onAudioProcessed(processed);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramFacade).enviarComBotoesHtml(eq(GROUP_CHAT_ID), captor.capture(), any());

        String mensagem = captor.getValue();
        assertThat(mensagem).contains("Testador Silva");
    }

    @Test
    void transcreverAudio_semResultado_deveLancarIllegalStateException() {
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

        File mockFile = new File("audio.oga");
        when(fileService.baixarArquivo(FILE_ID)).thenReturn(mockFile);

        doAnswer(inv -> null)
                .when(audioService)
                .processarFluxoAudio(any(File.class), anyLong(), anyLong(), anyString(), any());

        audioHandler.handleTranscriptionCallback(callback, "trans_refinado|token456");
        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            verify(telegramFacade)
                                    .enviarMensagem(eq(USER_ID), contains("Erro no processamento"));
                            assertThat(pendingMap).doesNotContainKey("token456");
                        });
    }

    @Test
    void deveCapturarHttpClientErrorExceptionNoSafeAnswerCallback() {
        CallbackQuery callback = mock(CallbackQuery.class);
        when(callback.id()).thenReturn("cb123");
        when(callback.from()).thenReturn(user);
        when(callback.data()).thenReturn("trans_refinado|token999");

        Message callbackMessage = mock(Message.class);
        when(callback.maybeInaccessibleMessage()).thenReturn(callbackMessage);
        when(callbackMessage.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(GROUP_CHAT_ID);

        doThrow(
                        HttpClientErrorException.create(
                                HttpStatus.BAD_REQUEST, "Bad Request", null, null, null))
                .when(telegramFacade)
                .answerCallbackQuery(eq("cb123"), anyString(), anyBoolean());

        assertThatCode(
                        () ->
                                audioHandler.handleTranscriptionCallback(
                                        callback, "trans_refinado|token999"))
                .doesNotThrowAnyException();

        verify(telegramFacade)
                .answerCallbackQuery("cb123", "Pedido expirado. Envie o audio novamente.", true);
    }

    @Test
    void deveCapturarResourceAccessExceptionNoSafeAnswerCallback() {
        CallbackQuery callback = mock(CallbackQuery.class);
        when(callback.id()).thenReturn("cb123");
        when(callback.from()).thenReturn(user);
        when(callback.data()).thenReturn("trans_refinado|token999");

        Message callbackMessage = mock(Message.class);
        when(callback.maybeInaccessibleMessage()).thenReturn(callbackMessage);
        when(callbackMessage.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(GROUP_CHAT_ID);

        doThrow(new ResourceAccessException("Timeout"))
                .when(telegramFacade)
                .answerCallbackQuery(eq("cb123"), anyString(), anyBoolean());

        assertThatCode(
                        () ->
                                audioHandler.handleTranscriptionCallback(
                                        callback, "trans_refinado|token999"))
                .doesNotThrowAnyException();

        verify(telegramFacade)
                .answerCallbackQuery("cb123", "Pedido expirado. Envie o audio novamente.", true);
    }

    @Test
    void deveProcessarAudioGrupoPublicandoNoKafka() {
        when(chat.id()).thenReturn(GROUP_CHAT_ID);
        when(chat.type()).thenReturn(com.pengrad.telegrambot.model.Chat.Type.supergroup);
        when(message.audio()).thenReturn(audio);
        when(audio.fileId()).thenReturn(FILE_ID);
        when(audio.fileSize()).thenReturn(1024L);
        when(audio.duration()).thenReturn(DURATION);

        audioHandler.handleAudioUpdate(update);

        verify(audioEventPublisher)
                .publish(
                        argThat(
                                event ->
                                        event.fileId().equals(FILE_ID)
                                                && event.chatId() == GROUP_CHAT_ID
                                                && event.groupId() == GROUP_CHAT_ID
                                                && event.tipoFluxo()
                                                        .equals("PRE_PROCESSAMENTO_GRUPO")));

        verify(telegramFacade).enviarMensagem(eq(GROUP_CHAT_ID), contains("Skynet"));
        verify(audioService, never()).processarEArmazenar(any(), anyLong(), anyLong(), anyString());
    }

    @Test
    void naoDeveChamarProcessarEArmazenarNoHandler() {
        when(chat.id()).thenReturn(GROUP_CHAT_ID);
        when(chat.type()).thenReturn(com.pengrad.telegrambot.model.Chat.Type.supergroup);
        when(message.audio()).thenReturn(audio);
        when(audio.fileId()).thenReturn(FILE_ID);
        when(audio.fileSize()).thenReturn(1024L);

        audioHandler.handleAudioUpdate(update);

        verify(audioService, never()).processarEArmazenar(any(), anyLong(), anyLong(), anyString());
        verify(audioEventPublisher).publish(any());
    }

    @Test
    @DisplayName("onAudioProcessed com sucesso envia botões")
    void onAudioProcessed_success_sendsButtons() {
        AudioProcessedEvent event =
                new AudioProcessedEvent(FILE_ID, GROUP_CHAT_ID, USER_ID, "User", true, null, 100);

        audioHandler.onAudioProcessed(event);

        verify(telegramFacade).enviarComBotoesHtml(eq(GROUP_CHAT_ID), anyString(), any());
    }

    @Test
    @DisplayName("onAudioProcessed com falha envia mensagem de erro")
    void onAudioProcessed_failure_sendsErrorMessage() {
        AudioProcessedEvent event =
                new AudioProcessedEvent(
                        FILE_ID, GROUP_CHAT_ID, USER_ID, "User", false, "FFmpeg falhou", 0);

        audioHandler.onAudioProcessed(event);

        verify(telegramFacade)
                .enviarMensagem(eq(GROUP_CHAT_ID), argThat(msg -> msg.contains("FFmpeg falhou")));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("handleTranscriptionCallback com cache miss e entrega OK")
    void callback_cacheMiss_deliversTranscription() {
        CallbackQuery callback = mock(CallbackQuery.class);
        Message callbackMessage = mock(Message.class);
        Chat chat = mock(Chat.class);
        User callbackUser = mock(User.class);

        when(callback.from()).thenReturn(callbackUser);
        when(callbackUser.id()).thenReturn(USER_ID);
        when(callback.id()).thenReturn("cb-123");
        when(callback.maybeInaccessibleMessage()).thenReturn(callbackMessage);
        when(callbackMessage.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(GROUP_CHAT_ID);

        String token = "token-abc";
        AudioRequest request =
                new AudioRequest(
                        FILE_ID, GROUP_CHAT_ID, System.currentTimeMillis(), USER_ID, "User");
        ReflectionTestUtils.setField(audioHandler, "pendingRequests", new ConcurrentHashMap<>());
        var pending =
                (ConcurrentHashMap<String, AudioRequest>)
                        ReflectionTestUtils.getField(audioHandler, "pendingRequests");
        pending.put(token, request);

        var entry =
                new net.ddns.adambravo79.tmill.model.TranscriptionCacheEntry(
                        "bruto", "refinado", System.currentTimeMillis());
        when(cacheService.get(FILE_ID)).thenReturn(entry);

        audioHandler.handleTranscriptionCallback(callback, "trans_refinado|" + token);

        verify(telegramFacade)
                .enviarMensagem(eq(USER_ID), argThat(msg -> msg.contains("refinado")));
    }

    @Test
    @DisplayName("handleTranscriptionCallback com token inválido responde alerta")
    void callback_invalidToken() {
        CallbackQuery callback = mock(CallbackQuery.class);
        Message callbackMessage = mock(Message.class);
        Chat chat = mock(Chat.class);
        User callbackUser = mock(User.class);

        when(callback.from()).thenReturn(callbackUser);
        when(callbackUser.id()).thenReturn(USER_ID);
        when(callback.id()).thenReturn("cb-123");
        when(callback.maybeInaccessibleMessage()).thenReturn(callbackMessage);
        when(callbackMessage.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(GROUP_CHAT_ID);

        ReflectionTestUtils.setField(audioHandler, "pendingRequests", new ConcurrentHashMap<>());

        audioHandler.handleTranscriptionCallback(callback, "trans_refinado|inexistente");

        verify(telegramFacade).answerCallbackQuery(eq("cb-123"), anyString(), eq(true));
    }
}
