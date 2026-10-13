package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Month;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.SimpleTriggerContext;
import org.springframework.test.util.ReflectionTestUtils;

import net.ddns.adambravo79.tmill.client.AzureTtsClient;
import net.ddns.adambravo79.tmill.service.config.AudioConfigService;
import net.ddns.adambravo79.tmill.service.prompt.PromptRegistryService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;

@ExtendWith(MockitoExtension.class)
class PodcastPublisherServiceTest {

    @Mock private PodcastScriptService scriptService;
    @Mock private AzureTtsClient ttsClient;
    @Mock private TelegramFacade telegramFacade;
    @Mock private TempDirService tempDirService;
    @Mock private MetricsService metricsService;
    @Mock private PromptRegistryService promptRegistryService;
    @Mock private AudioConfigService audioConfig;

    @InjectMocks private PodcastPublisherService service;

    @TempDir Path tempDir;

    private static final long CHAT_ID = -100123L;
    private static final LocalDate START = LocalDate.of(2026, Month.JULY, 6);
    private static final LocalDate END = LocalDate.of(2026, Month.JULY, 12);

    /** 5 MB — o mesmo default do PodcastAudioConfig. */
    private static final long FIVE_MB = 5L * 1024 * 1024;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(service, "publishChatId", CHAT_ID);
        ReflectionTestUtils.setField(service, "maxRetryAttempts", 3);
        ReflectionTestUtils.setField(service, "retryDelayMs", 10L);

        // 🔧 Onda 3.4: AudioConfigService precisa devolver PodcastAudioConfig e TtsConfig
        lenient()
                .when(audioConfig.getPodcast())
                .thenReturn(
                        new AudioConfigService.PodcastAudioConfig(
                                /* maxAudioSizeBytes      */ FIVE_MB,
                                /* compressTimeoutSeconds */ 5));

        lenient()
                .when(audioConfig.getTts())
                .thenReturn(
                        new AudioConfigService.TtsConfig(
                                /* maxAudioSizeBytes      */ FIVE_MB,
                                /* compressBitrate        */ "64k",
                                /* compressChannels       */ 1,
                                /* compressSampleRate     */ 22050,
                                /* compressTimeoutSeconds */ 5));

        lenient()
                .when(tempDirService.createTempFile(anyString(), anyString()))
                .thenAnswer(
                        inv ->
                                Files.createTempFile(
                                        tempDir, inv.getArgument(0), inv.getArgument(1)));
    }

    // ============================================================
    // SEM ROTEIRO — 'podcast_sem_roteiro'
    // ============================================================

    @Test
    @DisplayName("Script vazio: registra 'podcast_sem_roteiro', não chama TTS")
    void scriptVazio_registraMetrica() {
        when(scriptService.generateScript(START, END)).thenReturn("");

        service.generateAndSendPodcast(START, END, CHAT_ID);

        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("Nenhuma transcrição"));
        verify(metricsService).error("podcast_sem_roteiro");
        verifyNoInteractions(ttsClient);
    }

    @Test
    @DisplayName("Script null: registra 'podcast_sem_roteiro'")
    void scriptNulo_registraMetrica() {
        when(scriptService.generateScript(START, END)).thenReturn(null);

        service.generateAndSendPodcast(START, END, CHAT_ID);

        verify(metricsService).error("podcast_sem_roteiro");
        verifyNoInteractions(ttsClient);
    }

    // ============================================================
    // TTS VAZIO — 'podcast_tts_vazio'
    // ============================================================

    @Test
    @DisplayName("TTS vazio: registra 'podcast_tts_vazio'")
    void ttsVazio_registraMetrica() {
        when(scriptService.generateScript(START, END)).thenReturn("roteiro ok");
        when(ttsClient.synthesizeFullText(anyString())).thenReturn(new byte[0]);

        service.generateAndSendPodcast(START, END, CHAT_ID);

        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), contains("Erro ao gerar áudio"));
        verify(metricsService).error("podcast_tts_vazio");
        verify(metricsService, never()).success(anyString());
    }

    @Test
    @DisplayName("TTS null: registra 'podcast_tts_vazio'")
    void ttsNulo_registraMetrica() {
        when(scriptService.generateScript(START, END)).thenReturn("roteiro ok");
        when(ttsClient.synthesizeFullText(anyString())).thenReturn(null);

        service.generateAndSendPodcast(START, END, CHAT_ID);

        verify(metricsService).error("podcast_tts_vazio");
    }

    // ============================================================
    // SUCESSO — 'podcast_publicado' + 'podcast_compressao_pulada'
    // ============================================================

    @Test
    @DisplayName("Envio com sucesso: 'podcast_publicado' + 'podcast_compressao_pulada'")
    void sucesso_registraMetricas() {
        when(scriptService.generateScript(START, END)).thenReturn("roteiro do podcast");
        when(ttsClient.synthesizeFullText(anyString())).thenReturn("audio-pequeno".getBytes());
        // 🔧 Onda 3.4: enviarMidia agora retorna boolean
        when(telegramFacade.enviarMidia(eq(CHAT_ID), anyString(), anyString())).thenReturn(true);

        service.generateAndSendPodcast(START, END, CHAT_ID);

        verify(telegramFacade, times(1))
                .enviarMidia(eq(CHAT_ID), anyString(), contains("Silas Cast"));
        verify(metricsService).success("podcast_compressao_pulada");
        verify(metricsService).success("podcast_publicado");
        verify(metricsService, never()).error(anyString());
    }

    // ============================================================
    // FALHA APÓS RETRIES
    // ============================================================

    @Test
    @DisplayName("Falha após 3 tentativas: 'podcast_falha_envio' + 'podcast_fallback_texto'")
    void falhaAposRetries_registraMetricas() {
        when(scriptService.generateScript(START, END)).thenReturn("roteiro de fallback");
        when(ttsClient.synthesizeFullText(anyString())).thenReturn("audio-pequeno".getBytes());
        // 🔧 Onda 3.4: agora a falha é representada por retorno false (não exceção)
        when(telegramFacade.enviarMidia(eq(CHAT_ID), anyString(), anyString())).thenReturn(false);

        service.generateAndSendPodcast(START, END, CHAT_ID);

        // sendWithRetry tenta 3x por causa do retorno false
        verify(telegramFacade, times(3)).enviarMidia(eq(CHAT_ID), anyString(), anyString());
        verify(metricsService).error("podcast_falha_envio");
        verify(metricsService).success("podcast_fallback_texto");
        verify(metricsService, never()).success("podcast_publicado");
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("Áudio indisponível"));
    }

    @Test
    @DisplayName("Falha 1x (retorno false) mas sucesso na 2ª: 'podcast_publicado'")
    void falhaDepoisSucesso() {
        when(scriptService.generateScript(START, END)).thenReturn("roteiro");
        when(ttsClient.synthesizeFullText(anyString())).thenReturn("audio-pequeno".getBytes());

        // 🔧 Onda 3.4: primeiro retorno é false, segundo é true
        when(telegramFacade.enviarMidia(eq(CHAT_ID), anyString(), anyString()))
                .thenReturn(false)
                .thenReturn(true);

        service.generateAndSendPodcast(START, END, CHAT_ID);

        verify(telegramFacade, times(2)).enviarMidia(eq(CHAT_ID), anyString(), anyString());
        verify(metricsService).success("podcast_publicado");
        verify(metricsService, never()).error("podcast_falha_envio");
    }

    @Test
    @DisplayName("Falha 1x por exceção, sucesso na 2ª: 'podcast_publicado'")
    void falhaPorExcecaoDepoisSucesso() {
        when(scriptService.generateScript(START, END)).thenReturn("roteiro");
        when(ttsClient.synthesizeFullText(anyString())).thenReturn("audio-pequeno".getBytes());

        // sendWithRetry também captura exceções do enviarMidia
        when(telegramFacade.enviarMidia(eq(CHAT_ID), anyString(), anyString()))
                .thenThrow(new RuntimeException("falha 1"))
                .thenReturn(true);

        service.generateAndSendPodcast(START, END, CHAT_ID);

        verify(telegramFacade, times(2)).enviarMidia(eq(CHAT_ID), anyString(), anyString());
        verify(metricsService).success("podcast_publicado");
        verify(metricsService, never()).error("podcast_falha_envio");
    }

    // ============================================================
    // COMPRESSÃO — só chamada se > FIVE_MB
    // ============================================================

    @Test
    @DisplayName("Áudio >5MB: tenta compressão; sem ffmpeg, registra 'podcast_compressao_falha'")
    void audioGrande_tentaCompressao() {
        byte[] audioGrande = new byte[6 * 1024 * 1024]; // 6 MB
        when(scriptService.generateScript(START, END)).thenReturn("roteiro");
        when(ttsClient.synthesizeFullText(anyString())).thenReturn(audioGrande);
        when(telegramFacade.enviarMidia(eq(CHAT_ID), anyString(), anyString())).thenReturn(true);

        service.generateAndSendPodcast(START, END, CHAT_ID);

        // Em ambiente de teste sem ffmpeg, o compressAudio retorna o original
        // e registra "podcast_compressao_falha".
        // Se ffmpeg estiver presente, registra "podcast_compressao_ok".
        verify(metricsService, times(1))
                .success(
                        argThat(
                                (String s) ->
                                        s.equals("podcast_compressao_ok")
                                                || s.equals("podcast_publicado")));
        verify(metricsService, times(1))
                .error(argThat((String s) -> s.equals("podcast_compressao_falha")));
        verify(metricsService).success("podcast_publicado");
    }

    // ============================================================
    // ERRO NA SÍNTESE — fallback de texto
    // ============================================================

    @Test
    @DisplayName("Exceção na síntese: fallback de texto, sem 'podcast_publicado'")
    void excecaoNaSintese_fallbackTexto() {
        when(scriptService.generateScript(START, END)).thenReturn("roteiro");
        when(ttsClient.synthesizeFullText(anyString()))
                .thenThrow(new RuntimeException("Azure down"));

        assertThatCode(() -> service.generateAndSendPodcast(START, END, CHAT_ID))
                .doesNotThrowAnyException();

        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), contains("Áudio indisponível"));
        verify(metricsService).success("podcast_fallback_texto");
        verify(metricsService, never()).success("podcast_publicado");
    }

    // ============================================================
    // FALLBACK DE TEXTO — MÚLTIPLAS PARTES
    // ============================================================

    @Test
    @DisplayName("Fallback com >4000 chars: envia em múltiplas partes")
    void fallbackTextoMultiplasPartes() {
        String roteiroLongo = "a".repeat(5000);
        when(scriptService.generateScript(START, END)).thenReturn(roteiroLongo);
        when(ttsClient.synthesizeFullText(anyString()))
                .thenThrow(new RuntimeException("Azure down"));

        assertThatCode(() -> service.generateAndSendPodcast(START, END, CHAT_ID))
                .doesNotThrowAnyException();

        verify(telegramFacade, times(2)).enviarMensagemHtml(eq(CHAT_ID), contains("Parte"));
        verify(metricsService).success("podcast_fallback_texto");
    }

    // ============================================================
    // FALLBACK DE TEXTO FALHANDO
    // ============================================================

    @Test
    @DisplayName("Fallback de texto também falha: não registra 'podcast_fallback_texto'")
    void fallbackTextoFalha() {
        when(scriptService.generateScript(START, END)).thenReturn("roteiro");
        when(ttsClient.synthesizeFullText(anyString()))
                .thenThrow(new RuntimeException("Azure down"));
        doThrow(new RuntimeException("Telegram down"))
                .when(telegramFacade)
                .enviarMensagemHtml(eq(CHAT_ID), anyString());

        assertThatCode(() -> service.generateAndSendPodcast(START, END, CHAT_ID))
                .doesNotThrowAnyException();

        verify(metricsService, never()).success("podcast_fallback_texto");
    }

    // ============================================================
    // MÉTRICAS — múltiplas execuções
    // ============================================================

    @Test
    @DisplayName("3 execuções com sucesso: 3 'podcast_publicado' + 3 'podcast_compressao_pulada'")
    void tresSucessos() {
        when(scriptService.generateScript(START, END)).thenReturn("roteiro");
        when(ttsClient.synthesizeFullText(anyString())).thenReturn("audio-pequeno".getBytes());
        when(telegramFacade.enviarMidia(eq(CHAT_ID), anyString(), anyString())).thenReturn(true);

        service.generateAndSendPodcast(START, END, CHAT_ID);
        service.generateAndSendPodcast(START, END, CHAT_ID);
        service.generateAndSendPodcast(START, END, CHAT_ID);

        verify(metricsService, times(3)).success("podcast_publicado");
        verify(metricsService, times(3)).success("podcast_compressao_pulada");
        verify(metricsService, never()).error(anyString());
    }

    @Test
    @DisplayName("2 sucessos + 1 sem roteiro: métricas corretas")
    void doisSucessosUmSemRoteiro() {
        when(scriptService.generateScript(START, END))
                .thenReturn("roteiro")
                .thenReturn("roteiro")
                .thenReturn("");
        when(ttsClient.synthesizeFullText(anyString())).thenReturn("audio-pequeno".getBytes());
        when(telegramFacade.enviarMidia(eq(CHAT_ID), anyString(), anyString())).thenReturn(true);

        service.generateAndSendPodcast(START, END, CHAT_ID);
        service.generateAndSendPodcast(START, END, CHAT_ID);
        service.generateAndSendPodcast(START, END, CHAT_ID);

        verify(metricsService, times(2)).success("podcast_publicado");
        verify(metricsService, times(1)).error("podcast_sem_roteiro");
    }

    // ============================================================
    // NENHUMA MÉTRICA DE ERRO EM CAMINHO LIMPO
    // ============================================================

    @Test
    @DisplayName("Envio ok: nenhuma métrica de erro")
    void envioOkSemErros() {
        when(scriptService.generateScript(START, END)).thenReturn("roteiro");
        when(ttsClient.synthesizeFullText(anyString())).thenReturn("audio".getBytes());
        when(telegramFacade.enviarMidia(eq(CHAT_ID), anyString(), anyString())).thenReturn(true);

        service.generateAndSendPodcast(START, END, CHAT_ID);

        verify(metricsService, never()).error(anyString());
        verify(metricsService).success("podcast_publicado");
    }

    // ============================================================
    // CONFIGURE TASKS
    // ============================================================

    @Test
    @DisplayName("configureTasks: deve registrar as tasks e invocar getCron cobrindo a lambda")
    void configureTasks_deveRegistrarAsTasksECobrirLambda() {
        lenient().when(promptRegistryService.getPodcastCron()).thenReturn("0 0 12 * * *");

        ScheduledTaskRegistrar taskRegistrar = mock(ScheduledTaskRegistrar.class);

        service.configureTasks(taskRegistrar);

        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(taskRegistrar, atLeastOnce())
                .addTriggerTask(any(Runnable.class), triggerCaptor.capture());

        SimpleTriggerContext ctx = new SimpleTriggerContext();
        for (Trigger trigger : triggerCaptor.getAllValues()) {
            trigger.nextExecution(ctx);
        }
    }
}
