package net.ddns.adambravo79.tmill.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import net.ddns.adambravo79.tmill.client.AzureTtsClient;
import net.ddns.adambravo79.tmill.model.AutoResponseOverride;
import net.ddns.adambravo79.tmill.repository.ReleaseNotifiedRepository;
import net.ddns.adambravo79.tmill.service.*;
import net.ddns.adambravo79.tmill.service.cache.FileTranscriptionCacheService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;

@WebMvcTest(AdminWebController.class)
@TestPropertySource(
        properties = {
            "worldcup.enabled=false",
            "telegram.owner.id=1400513378",
            "podcast.publish.chat-id=-1003703557250",
            "spring.aop.proxy-target-class=true",
            "spring.threads.virtual.enabled=false",
            // Propriedades adicionais para o endpoint /properties
            "spring.application.name=tmill-test",
            "server.port=8080",
            "telegram.bot.username=test_bot",
            "telegram.bot.polling.enabled=true",
            "telegram.bot.polling.timeout=30",
            "telegram.message.limit=4000",
            "telegram.bot.token=dummyToken123",
            "groq.api.key=dummyKey",
            "tmdb.token=dummyTmdb",
            "groq.model.transcription=whisper",
            "groq.model.refinement=llama",
            "groq.model.digest=llama4",
            "cache.transcription.enabled=true",
            "cache.transcription.ttl-seconds=3600",
            "digest.enabled=true",
            "digest.chat-ids=123,456",
            "worldcup.data.file=worldcup.json",
            "worldcup.update.enabled=false",
            "auto.response.enabled=true",
            "auto.response.file=auto-responses.json",
            "easter-egg.file=easter-eggs.json",
            "weekly.reminder.media-file=reminder.mp4",
            "t1000.features.transcription-enabled=true",
            "t1000.audio.max-size-mb=20",
            "bot.allowed-chats=-100123"
        })
class AdminWebControllerTest {

    @Autowired private MockMvc mockMvc;

    // Mocks para serviços (não mockar ObjectMapper nem ResourceLoader)
    @MockitoBean private EasterEggService easterEggService;
    @MockitoBean private DailyDigestService dailyDigestService;
    @MockitoBean private WeeklyReminderService weeklyReminderService;
    @MockitoBean private AutoResponseService autoResponseService;
    @MockitoBean private WorldCupSchedulerService worldCupSchedulerService;
    @MockitoBean private StaticWorldCupService staticWorldCupService;
    @MockitoBean private TelegramFacade telegramFacade;
    @MockitoBean private DailyReleasesService dailyReleasesService;
    @MockitoBean private ReleaseNotifiedRepository releaseNotifiedRepository;
    @MockitoBean private AzureTtsClient azureTtsClient;
    @MockitoBean private PodcastPublisherService podcastPublisherService;
    @MockitoBean private FileTranscriptionCacheService cacheService;
    @MockitoBean private TempDirService tempDirService;

    // ===================== PÁGINA PRINCIPAL =====================

    @Test
    void adminPage_shouldReturnAdminView() throws Exception {
        mockMvc.perform(get("/admin-web"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin"))
                .andExpect(
                        model().attributeExists(
                                        "worldcupEnabled", "ownerId", "publishChatId", "now"));
    }

    // ===================== MENSAGENS =====================

    @Test
    void falaT1000_withValidMessageAndChatId_shouldReturnSuccess() throws Exception {
        String message = "Hello, world!";
        long chatId = 123456L;

        mockMvc.perform(
                        post("/admin-web/fala-t1000")
                                .param("message", message)
                                .param("chatId", String.valueOf(chatId))
                                .param("parseMode", "HTML"))
                .andExpect(status().isOk())
                .andExpect(content().string("✅ Mensagem enviada para o chat " + chatId));

        verify(telegramFacade).enviarMensagemHtml(chatId, message);
    }

    @Test
    void falaT1000_withoutChatId_shouldUseOwnerId() throws Exception {
        String message = "Using owner ID";
        long expectedChatId = 1400513378L;

        mockMvc.perform(
                        post("/admin-web/fala-t1000")
                                .param("message", message)
                                .param("parseMode", "HTML"))
                .andExpect(status().isOk())
                .andExpect(content().string("✅ Mensagem enviada para o chat " + expectedChatId));

        verify(telegramFacade).enviarMensagemHtml(expectedChatId, message);
    }

    @Test
    void falaT1000_withParseModeText_shouldSendPlainText() throws Exception {
        String message = "Plain text";
        long chatId = 999L;

        mockMvc.perform(
                        post("/admin-web/fala-t1000")
                                .param("message", message)
                                .param("chatId", String.valueOf(chatId))
                                .param("parseMode", "TEXT"))
                .andExpect(status().isOk());

        verify(telegramFacade).enviarMensagem(chatId, message);
        verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
    }

    @Test
    void falaT1000_whenTelegramFails_shouldReturnError() throws Exception {
        String message = "Fails";
        long chatId = 1L;
        doThrow(new RuntimeException("Telegram error"))
                .when(telegramFacade)
                .enviarMensagemHtml(chatId, message);

        mockMvc.perform(
                        post("/admin-web/fala-t1000")
                                .param("message", message)
                                .param("chatId", String.valueOf(chatId)))
                .andExpect(status().is5xxServerError())
                .andExpect(content().string(containsString("Erro")));
    }

    // ===================== TTS =====================

    @Test
    void falaT1000Tts_withValidText_shouldReturnSuccess() throws Exception {
        String text = "Test TTS";
        long chatId = 123L;
        byte[] audioData = new byte[] {1, 2, 3};
        Path tempFile = Paths.get("/tmp/tts_audio.mp3");

        when(azureTtsClient.synthesizeFullText(text)).thenReturn(audioData);
        when(tempDirService.createTempFile(anyString(), anyString())).thenReturn(tempFile);

        mockMvc.perform(
                        post("/admin-web/fala-t1000-tts")
                                .param("message", text)
                                .param("chatId", String.valueOf(chatId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Áudio enviado para o chat " + chatId)));

        verify(azureTtsClient).synthesizeFullText(text);
        verify(telegramFacade).enviarMidia(eq(chatId), anyString(), eq("🔊 Áudio sintetizado"));
    }

    @Test
    void falaT1000Tts_whenSynthesisFails_shouldReturnError() throws Exception {
        String text = "Failing TTS";
        when(azureTtsClient.synthesizeFullText(text)).thenReturn(null);

        mockMvc.perform(post("/admin-web/fala-t1000-tts").param("message", text))
                .andExpect(status().is5xxServerError())
                .andExpect(content().string(containsString("Falha na síntese")));
    }

    // ===================== AUTO-RESPONSE =====================

    @Test
    void testAutoResponse_withValidRule_shouldReturnResponse() throws Exception {
        long userId = 100L;
        String message = "test";
        long chatId = 200L;
        // Use uma URL válida para forçar o envio de mídia
        String animationUrl = "https://example.com/animacao.gif";
        AutoResponseOverride override = new AutoResponseOverride("Resposta", animationUrl);

        when(autoResponseService.getResponseRule(eq(userId), eq(message), isNull()))
                .thenReturn(Optional.of(override));

        mockMvc.perform(
                        post("/admin-web/test-auto-response")
                                .param("userId", String.valueOf(userId))
                                .param("message", message)
                                .param("chatId", String.valueOf(chatId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Resposta automática enviada")));

        verify(telegramFacade).enviarMidia(eq(chatId), eq(animationUrl), anyString());
    }

    @Test
    void testAutoResponse_whenNoRule_shouldReturnNotFoundMessage() throws Exception {
        String message = "unknown";
        when(autoResponseService.getResponseRule(any(), anyString(), any()))
                .thenReturn(Optional.empty());

        mockMvc.perform(
                        post("/admin-web/test-auto-response")
                                .param("userId", "0")
                                .param("message", message))
                .andExpect(status().isOk())
                .andExpect(
                        content().string(containsString("Nenhuma resposta automática encontrada")));
    }

    // ===================== COPA =====================

    @Test
    void testWorldCup_whenDisabled_shouldRedirectWithError() throws Exception {
        mockMvc.perform(post("/admin-web/test-worldcup"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin-web"));

        verify(worldCupSchedulerService, never()).sendManualTest();
    }

    @Test
    void reloadWorldCup_shouldRedirect() throws Exception {
        mockMvc.perform(post("/admin-web/reload-worldcup"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin-web"));
        verify(staticWorldCupService).reload();
    }

    @Test
    void testWorldCupResults_withValidDate_shouldRedirect() throws Exception {
        mockMvc.perform(
                        post("/admin-web/test-worldcup-results")
                                .param("dateParam", "ontem")
                                .param("chatId", "12345"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin-web"));

        verify(worldCupSchedulerService, never()).sendResultsToChat(anyLong(), any());
    }

    // ===================== DIGEST =====================

    @Test
    void testMorningDigest_shouldTrigger() throws Exception {
        mockMvc.perform(post("/admin-web/test-morning-digest"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin-web"));
        verify(dailyDigestService).generateMorningDigest();
    }

    @Test
    void testEveningDigest_shouldTrigger() throws Exception {
        mockMvc.perform(post("/admin-web/test-evening-digest"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin-web"));
        verify(dailyDigestService).generateEveningDigest();
    }

    @Test
    void customDigest_withValidDates_shouldTrigger() throws Exception {
        String start = "2026-08-01";
        String end = "2026-08-02";

        mockMvc.perform(
                        post("/admin-web/custom-digest")
                                .param("start", start)
                                .param("end", end)
                                .param("chatId", "123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin-web"));

        verify(dailyDigestService).generateDigestCustom(any(), any(), eq(123L));
    }

    // ===================== LEMBRETES =====================

    @Test
    void testWeeklyReminder_shouldTrigger() throws Exception {
        mockMvc.perform(post("/admin-web/test-weekly-reminder"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin-web"));
        verify(weeklyReminderService).sendWednesdayReminder();
    }

    @Test
    void testWeeklyReminderShowcase_withChatId_shouldSendToSpecific() throws Exception {
        long chatId = 456L;
        mockMvc.perform(
                        post("/admin-web/test-weekly-reminder-showcase")
                                .param("chatId", String.valueOf(chatId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin-web"));
        verify(weeklyReminderService).sendReminderToChat(chatId);
    }

    // ===================== ADMIN =====================

    @Test
    void clearReleases_shouldRedirect() throws Exception {
        mockMvc.perform(post("/admin-web/clear-releases"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin-web"));
        verify(releaseNotifiedRepository).clearAll();
    }

    @Test
    void clearAllData_shouldRedirect() throws Exception {
        when(releaseNotifiedRepository.deleteAll()).thenReturn(42);
        mockMvc.perform(post("/admin-web/clear-all-data"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin-web"));
        verify(releaseNotifiedRepository).deleteAll();
    }

    @Test
    void reloadAutoResponses_shouldRedirect() throws Exception {
        mockMvc.perform(post("/admin-web/reload-auto-responses"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin-web"));
        verify(autoResponseService).reload();
    }

    @Test
    void reloadEasterEggs_shouldRedirect() throws Exception {
        mockMvc.perform(post("/admin-web/reload-easter-eggs"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin-web"));
        verify(easterEggService).reload();
    }

    // ===================== PODCAST =====================

    @Test
    void testPodcast_withValidDates_shouldRedirect() throws Exception {
        String start = "2026-08-01";
        String end = "2026-08-02";

        mockMvc.perform(
                        post("/admin-web/test-podcast")
                                .param("start", start)
                                .param("end", end)
                                .param("chatId", "123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin-web"));
    }

    // ===================== MONITORAMENTO (JSON) =====================

    @Test
    void cacheStats_shouldReturnStats() throws Exception {
        when(cacheService.getStats()).thenReturn(Map.of("size", 10L));

        mockMvc.perform(get("/admin-web/cache-stats"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void properties_shouldReturnMaskedProperties() throws Exception {
        mockMvc.perform(get("/admin-web/properties"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void configFiles_shouldReturnConfigContent() throws Exception {
        mockMvc.perform(get("/admin-web/config-files"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void debugAutoResponse_shouldReturnDebugInfo() throws Exception {
        when(autoResponseService.getResponseRule(any(), anyString(), any()))
                .thenReturn(Optional.empty());

        mockMvc.perform(
                        get("/admin-web/debug-auto-response")
                                .param("message", "debug")
                                .param("userId", "123")
                                .param("time", "14:30"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.found").value(false));
    }

    @Test
    void autoResponseRules_shouldReturnSummary() throws Exception {
        when(autoResponseService.getRulesCount()).thenReturn(5);
        when(autoResponseService.getRulesSummary()).thenReturn(Map.of("rule1", "active"));

        mockMvc.perform(get("/admin-web/auto-response-rules"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalRules").value(5));
    }
}
