package net.ddns.adambravo79.tmill.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import lombok.SneakyThrows;
import net.ddns.adambravo79.tmill.client.AzureTtsClient;
import net.ddns.adambravo79.tmill.dto.MigrationResult;
import net.ddns.adambravo79.tmill.model.AutoResponseOverride;
import net.ddns.adambravo79.tmill.model.Birthday;
import net.ddns.adambravo79.tmill.repository.BirthdayRepository;
import net.ddns.adambravo79.tmill.repository.ReleaseNotifiedRepository;
import net.ddns.adambravo79.tmill.service.AutoResponseService;
import net.ddns.adambravo79.tmill.service.BirthdayService;
import net.ddns.adambravo79.tmill.service.DailyDigestService;
import net.ddns.adambravo79.tmill.service.DailyReleasesService;
import net.ddns.adambravo79.tmill.service.EasterEggService;
import net.ddns.adambravo79.tmill.service.MigrationService;
import net.ddns.adambravo79.tmill.service.PodcastPublisherService;
import net.ddns.adambravo79.tmill.service.StaticWorldCupService;
import net.ddns.adambravo79.tmill.service.TempDirService;
import net.ddns.adambravo79.tmill.service.WeeklyReminderService;
import net.ddns.adambravo79.tmill.service.WorldCupSchedulerService;
import net.ddns.adambravo79.tmill.service.cache.FileTranscriptionCacheService;
import net.ddns.adambravo79.tmill.service.feature.FeatureFlagAdminService;
import net.ddns.adambravo79.tmill.service.prompt.PromptRegistryService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AdminWebControllerTest {

    // =========================================================================
    // MOCKS — todas as dependências do controller
    // =========================================================================

    @Mock private EasterEggService easterEggService;
    @Mock private DailyDigestService dailyDigestService;
    @Mock private WeeklyReminderService weeklyReminderService;
    @Mock private AutoResponseService autoResponseService;
    @Mock private WorldCupSchedulerService worldCupSchedulerService;
    @Mock private StaticWorldCupService staticWorldCupService;
    @Mock private TelegramFacade telegramFacade;
    @Mock private DailyReleasesService dailyReleasesService;
    @Mock private ReleaseNotifiedRepository releaseNotifiedRepository;
    @Mock private AzureTtsClient azureTtsClient;
    @Mock private PodcastPublisherService podcastPublisherService;
    @Mock private FileTranscriptionCacheService cacheService;
    @Mock private Environment environment;
    @Mock private ResourceLoader resourceLoader;
    @Mock private ObjectMapper objectMapper;
    @Mock private TempDirService tempDirService;
    @Mock private BirthdayService birthdayService;
    @Mock private BirthdayRepository birthdayRepository;
    @Mock private MigrationService migrationService;
    @Mock private FeatureFlagAdminService featureFlagAdminService;
    @Mock private PromptRegistryService promptRegistryService;

    private AdminWebController controller;
    private MockMvc mockMvc;

    // =========================================================================
    // SETUP
    // =========================================================================

    @BeforeEach
    void setUp() {
        controller =
                new AdminWebController(
                        easterEggService,
                        dailyDigestService,
                        weeklyReminderService,
                        autoResponseService,
                        worldCupSchedulerService,
                        staticWorldCupService,
                        telegramFacade,
                        dailyReleasesService,
                        releaseNotifiedRepository,
                        azureTtsClient,
                        podcastPublisherService,
                        cacheService,
                        environment,
                        resourceLoader,
                        objectMapper,
                        tempDirService,
                        birthdayService,
                        birthdayRepository,
                        migrationService,
                        featureFlagAdminService,
                        promptRegistryService);

        // @Value fields — não injetados por ReflectionTestUtils
        ReflectionTestUtils.setField(controller, "worldcupEnabled", true);
        ReflectionTestUtils.setField(controller, "ownerId", 999L);
        ReflectionTestUtils.setField(controller, "publishChatId", 555L);
        ReflectionTestUtils.setField(controller, "botAllowedChats", "111,222");
        ReflectionTestUtils.setField(controller, "digestChatIdsStr", "333,444");
        ReflectionTestUtils.setField(controller, "migrationSqlitePath", "./data/test.db");

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private RedirectAttributes newRedirectAttributes() {
        return new RedirectAttributesModelMap();
    }

    // =========================================================================
    // PÁGINA PRINCIPAL
    // =========================================================================

    @Test
    @DisplayName("adminPage: adiciona atributos ao model")
    void adminPage_devePopularModel() {
        Model model = new ExtendedModelMap();

        String view = controller.adminPage(model);

        assertThat(view).isEqualTo("admin");
        assertThat(model.getAttribute("worldcupEnabled")).isEqualTo(true);
        assertThat(model.getAttribute("ownerId")).isEqualTo(999L);
        assertThat(model.getAttribute("publishChatId")).isEqualTo(555L);
        assertThat(model.getAttribute("now")).isNotNull();
        assertThat(model.getAttribute("availableChatIds")).isNotNull();
    }

    @Test
    @DisplayName("adminPage: unifica botAllowedChats + digestChatIds")
    @SuppressWarnings("unchecked")
    void adminPage_unificaChatIds() {
        Model model = new ExtendedModelMap();
        controller.adminPage(model);

        var chats = (java.util.Set<String>) model.getAttribute("availableChatIds");
        assertThat(chats).containsExactlyInAnyOrder("111", "222", "333", "444");
    }

    @Test
    @DisplayName("adminPage: lida com botAllowedChats e digestChatIds vazios")
    @SuppressWarnings("unchecked")
    void adminPage_chatsVazios() {
        ReflectionTestUtils.setField(controller, "botAllowedChats", "");
        ReflectionTestUtils.setField(controller, "digestChatIdsStr", "  ");

        Model model = new ExtendedModelMap();
        controller.adminPage(model);

        var chats = (java.util.Set<String>) model.getAttribute("availableChatIds");
        assertThat(chats).isEmpty();
    }

    // =========================================================================
    // FALA T1000 (TEXTO)
    // =========================================================================

    @Test
    @DisplayName("falaT1000: mensagem válida retorna OK")
    void falaT1000_mensagemValida() throws Exception {
        doNothing().when(telegramFacade).enviarMensagemHtml(eq(123L), anyString());

        mockMvc.perform(
                        post("/admin-web/fala-t1000")
                                .param("message", "Olá")
                                .param("chatId", "123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mensagem enviada para o chat 123")));

        verify(telegramFacade).enviarMensagemHtml(eq(123L), eq("Olá"));
    }

    @Test
    @DisplayName("falaT1000: mensagem vazia retorna 400")
    void falaT1000_mensagemVazia() throws Exception {
        mockMvc.perform(post("/admin-web/fala-t1000").param("message", "").param("chatId", "123"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("obrigatório")));
    }

    @Test
    @DisplayName("falaT1000: mensagem ausente retorna 400")
    void falaT1000_mensagemAusente() throws Exception {
        mockMvc.perform(post("/admin-web/fala-t1000").param("chatId", "123"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("falaT1000: parseMode não-HTML usa enviarMensagem")
    void falaT1000_parseModeNaoHtml() throws Exception {
        doNothing().when(telegramFacade).enviarMensagem(eq(123L), anyString());

        mockMvc.perform(
                        post("/admin-web/fala-t1000")
                                .param("message", "Olá")
                                .param("chatId", "123")
                                .param("parseMode", "MarkdownV2"))
                .andExpect(status().isOk());

        verify(telegramFacade).enviarMensagem(eq(123L), eq("Olá"));
        verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
    }

    @Test
    @DisplayName("falaT1000: falha no envio retorna 500 com MSG_ERRO_INTERNO")
    void falaT1000_falhaEnvio() throws Exception {
        doThrow(new RuntimeException("Falha"))
                .when(telegramFacade)
                .enviarMensagemHtml(anyLong(), anyString());

        mockMvc.perform(
                        post("/admin-web/fala-t1000")
                                .param("message", "Olá")
                                .param("chatId", "123"))
                .andExpect(status().is5xxServerError())
                .andExpect(content().string(containsString("Erro interno")));
    }

    @Test
    @DisplayName("falaT1000: sem chatId usa ownerId")
    void falaT1000_semChatIdUsaOwner() throws Exception {
        doNothing().when(telegramFacade).enviarMensagemHtml(anyLong(), anyString());

        mockMvc.perform(post("/admin-web/fala-t1000").param("message", "Olá"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("chat 999")));

        verify(telegramFacade).enviarMensagemHtml(eq(999L), anyString());
    }

    // =========================================================================
    // FALA T1000 TTS
    // =========================================================================

    @Test
    @DisplayName("falaT1000Tts: texto válido retorna OK")
    void falaT1000Tts_textoValido() throws Exception {
        byte[] audio = new byte[] {1, 2, 3};
        Path tempPath = Paths.get("/tmp/tts_audio.mp3");
        Path finalPath = Paths.get("/tmp/Cronicas-do-T1000-Audio-1.mp3");

        when(azureTtsClient.synthesizeFullText("Test")).thenReturn(audio);
        when(tempDirService.createTempFile(anyString(), anyString())).thenReturn(tempPath);

        try (var mockedFiles = mockStatic(Files.class)) {
            mockedFiles
                    .when(() -> Files.write(any(Path.class), any(byte[].class)))
                    .thenReturn(tempPath);
            mockedFiles
                    .when(() -> Files.move(any(Path.class), any(Path.class)))
                    .thenReturn(finalPath);
            mockedFiles.when(() -> Files.deleteIfExists(any(Path.class))).thenReturn(true);
            mockedFiles.when(() -> Files.exists(any(Path.class))).thenReturn(false);

            mockMvc.perform(
                            post("/admin-web/fala-t1000-tts")
                                    .param("message", "Test")
                                    .param("chatId", "123"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Áudio enviado com sucesso")));

            verify(telegramFacade).enviarMidia(eq(123L), anyString(), contains("Skynet"));
        }
    }

    @Test
    @DisplayName("falaT1000Tts: mensagem vazia retorna 400")
    void falaT1000Tts_mensagemVazia() throws Exception {
        mockMvc.perform(
                        post("/admin-web/fala-t1000-tts")
                                .param("message", "")
                                .param("chatId", "123"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("obrigatório")));
    }

    @Test
    @DisplayName("falaT1000Tts: TTS retorna áudio vazio → 500")
    void falaT1000Tts_audioVazio() throws Exception {
        when(azureTtsClient.synthesizeFullText("Test")).thenReturn(new byte[0]);

        mockMvc.perform(
                        post("/admin-web/fala-t1000-tts")
                                .param("message", "Test")
                                .param("chatId", "123"))
                .andExpect(status().is5xxServerError())
                .andExpect(content().string(containsString("Falha na síntese")));
    }

    @Test
    @DisplayName("falaT1000Tts: TTS lança exceção → 500 MSG_ERRO_INTERNO")
    void falaT1000Tts_ttsLancaExcecao() throws Exception {
        when(azureTtsClient.synthesizeFullText("Test")).thenThrow(new RuntimeException("TTS down"));

        mockMvc.perform(
                        post("/admin-web/fala-t1000-tts")
                                .param("message", "Test")
                                .param("chatId", "123"))
                .andExpect(status().is5xxServerError())
                .andExpect(content().string(containsString("Erro interno")));
    }

    @Test
    @DisplayName("falaT1000Tts: sem chatId usa ownerId")
    void falaT1000Tts_semChatIdUsaOwner() throws Exception {
        byte[] audio = new byte[] {1, 2, 3};
        Path tempPath = Paths.get("/tmp/tts_audio.mp3");
        when(azureTtsClient.synthesizeFullText("Test")).thenReturn(audio);
        when(tempDirService.createTempFile(anyString(), anyString())).thenReturn(tempPath);

        try (var mockedFiles = mockStatic(Files.class)) {
            mockedFiles
                    .when(() -> Files.write(any(Path.class), any(byte[].class)))
                    .thenReturn(tempPath);
            mockedFiles
                    .when(() -> Files.move(any(Path.class), any(Path.class)))
                    .thenReturn(tempPath);
            mockedFiles.when(() -> Files.deleteIfExists(any(Path.class))).thenReturn(true);
            mockedFiles.when(() -> Files.exists(any(Path.class))).thenReturn(false);

            mockMvc.perform(post("/admin-web/fala-t1000-tts").param("message", "Test"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("chat 999")));
        }
    }

    // =========================================================================
    // TEST AZURE TTS
    // =========================================================================

    @Test
    @DisplayName("testAzureTts: publishChatId=0 → flash error")
    void testAzureTts_publishChatIdZero() {
        ReflectionTestUtils.setField(controller, "publishChatId", 0L);
        RedirectAttributes attrs = newRedirectAttributes();

        String view = controller.testAzureTts(attrs);

        assertThat(view).isEqualTo("redirect:/admin-web");
        assertThat(attrs.getFlashAttributes().get("error"))
                .isEqualTo("publishChatId não configurado.");
    }

    @Test
    @DisplayName("testAzureTts: áudio vazio → flash error")
    void testAzureTts_audioVazio() {
        when(azureTtsClient.synthesizeFullText(anyString())).thenReturn(new byte[0]);
        RedirectAttributes attrs = newRedirectAttributes();

        String view = controller.testAzureTts(attrs);

        assertThat(view).isEqualTo("redirect:/admin-web");
        assertThat(attrs.getFlashAttributes().get("error").toString()).contains("Falha na síntese");
    }

    @Test
    @DisplayName("testAzureTts: sucesso → flash success")
    @SneakyThrows
    void testAzureTts_sucesso() {
        byte[] audio = new byte[] {1, 2, 3};
        Path tempPath = Paths.get("/tmp/test_azure.mp3");
        when(azureTtsClient.synthesizeFullText(anyString())).thenReturn(audio);
        when(tempDirService.createTempFile(anyString(), anyString())).thenReturn(tempPath);

        try (var mockedFiles = mockStatic(Files.class)) {
            mockedFiles
                    .when(() -> Files.write(any(Path.class), any(byte[].class)))
                    .thenReturn(tempPath);
            mockedFiles.when(() -> Files.deleteIfExists(any(Path.class))).thenReturn(true);
            mockedFiles.when(() -> Files.exists(any(Path.class))).thenReturn(false);

            RedirectAttributes attrs = newRedirectAttributes();
            String view = controller.testAzureTts(attrs);

            assertThat(view).isEqualTo("redirect:/admin-web");
            assertThat(attrs.getFlashAttributes().get("success").toString())
                    .contains("Áudio de teste enviado");
        }
    }

    @Test
    @DisplayName("testAzureTts: exceção → flash error")
    void testAzureTts_excecao() {
        when(azureTtsClient.synthesizeFullText(anyString()))
                .thenThrow(new RuntimeException("TTS down"));
        RedirectAttributes attrs = newRedirectAttributes();

        String view = controller.testAzureTts(attrs);

        assertThat(view).isEqualTo("redirect:/admin-web");
        assertThat(attrs.getFlashAttributes().get("error").toString()).contains("Erro interno");
    }

    // =========================================================================
    // COPA — habilitado/desabilitado
    // =========================================================================

    @Test
    @DisplayName("testWorldCup: desabilitado → flash error")
    void testWorldCup_desabilitado() {
        ReflectionTestUtils.setField(controller, "worldcupEnabled", false);
        RedirectAttributes attrs = newRedirectAttributes();

        String view = controller.testWorldCup(attrs);

        assertThat(view).isEqualTo("redirect:/admin-web");
        assertThat(attrs.getFlashAttributes().get("error")).isEqualTo("Copa desabilitada.");
        verifyNoInteractions(worldCupSchedulerService);
    }

    @Test
    @DisplayName("testWorldCup: habilitado → flash success")
    void testWorldCup_habilitado() {
        RedirectAttributes attrs = newRedirectAttributes();

        String view = controller.testWorldCup(attrs);

        assertThat(view).isEqualTo("redirect:/admin-web");
        assertThat(attrs.getFlashAttributes().get("success").toString())
                .contains("Envio manual da Copa disparado");
        verify(worldCupSchedulerService).sendManualTest();
    }

    @Test
    @DisplayName("testWorldCupShowcase: desabilitado → flash error")
    void testWorldCupShowcase_desabilitado() {
        ReflectionTestUtils.setField(controller, "worldcupEnabled", false);
        RedirectAttributes attrs = newRedirectAttributes();

        controller.testWorldCupShowcase(123L, attrs);

        assertThat(attrs.getFlashAttributes().get("error")).isEqualTo("Copa desabilitada.");
        verifyNoInteractions(worldCupSchedulerService);
    }

    @Test
    @DisplayName("testWorldCupShowcase: chatId explícito")
    void testWorldCupShowcase_chatId() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.testWorldCupShowcase(777L, attrs);

        verify(worldCupSchedulerService).sendManualTestToChat(777L);
        assertThat(attrs.getFlashAttributes().get("success").toString()).contains("777");
    }

    @Test
    @DisplayName("testWorldCupShowcase: sem chatId usa ownerId")
    void testWorldCupShowcase_semChatId() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.testWorldCupShowcase(null, attrs);

        verify(worldCupSchedulerService).sendManualTestToChat(999L);
    }

    @Test
    @DisplayName("testWorldCupNoon: habilitado → dispara")
    void testWorldCupNoon_habilitado() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.testWorldCupNoon(attrs);

        verify(worldCupSchedulerService).sendNoonMatches();
        assertThat(attrs.getFlashAttributes().get("success").toString()).contains("meio-dia");
    }

    @Test
    @DisplayName("testWorldCupNoon: desabilitado → flash error")
    void testWorldCupNoon_desabilitado() {
        ReflectionTestUtils.setField(controller, "worldcupEnabled", false);
        RedirectAttributes attrs = newRedirectAttributes();

        controller.testWorldCupNoon(attrs);

        assertThat(attrs.getFlashAttributes().get("error")).isEqualTo("Copa desabilitada.");
    }

    @Test
    @DisplayName("testWorldCupEvening: habilitado → dispara")
    void testWorldCupEvening_habilitado() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.testWorldCupEvening(attrs);

        verify(worldCupSchedulerService).sendEveningMatches();
    }

    @Test
    @DisplayName("testWorldCupNoonShowcase: dispara para chat explícito")
    void testWorldCupNoonShowcase_chatId() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.testWorldCupNoonShowcase(888L, attrs);

        verify(worldCupSchedulerService).sendNoonMatchesToChat(888L);
    }

    @Test
    @DisplayName("testWorldCupEveningShowcase: dispara para chat explícito")
    void testWorldCupEveningShowcase_chatId() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.testWorldCupEveningShowcase(888L, attrs);

        verify(worldCupSchedulerService).sendEveningMatchesToChat(888L);
    }

    @Test
    @DisplayName("reloadWorldCup: recarrega e flash success")
    void reloadWorldCup_sucesso() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.reloadWorldCup(attrs);

        verify(staticWorldCupService).reload();
        assertThat(attrs.getFlashAttributes().get("success").toString()).contains("recarregados");
    }

    @Test
    @DisplayName("reloadWorldCupShowcase: envia notificação para chat")
    void reloadWorldCupShowcase_sucesso() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.reloadWorldCupShowcase(123L, attrs);

        verify(staticWorldCupService).reload();
        verify(telegramFacade).enviarMensagemHtml(eq(123L), anyString());
        assertThat(attrs.getFlashAttributes().get("success").toString()).contains("123");
    }

    @Test
    @DisplayName("reloadWorldCupShowcase: HttpClientErrorException é silenciada")
    void reloadWorldCupShowcase_erroHttp() {
        doThrow(
                        new org.springframework.web.client.HttpClientErrorException(
                                org.springframework.http.HttpStatus.FORBIDDEN))
                .when(telegramFacade)
                .enviarMensagemHtml(anyLong(), anyString());

        RedirectAttributes attrs = newRedirectAttributes();
        controller.reloadWorldCupShowcase(123L, attrs);

        assertThat(attrs.getFlashAttributes().get("success")).isNotNull();
    }

    @Test
    @DisplayName("testWorldCupResults: data inválida → flash error")
    void testWorldCupResults_dataInvalida() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.testWorldCupResults("data-muito-invalida-xyz", 123L, attrs);

        assertThat(attrs.getFlashAttributes().get("error")).isEqualTo("Data inválida.");
        verifyNoInteractions(worldCupSchedulerService);
    }

    @Test
    @DisplayName("testWorldCupResults: 'ontem' → envia")
    void testWorldCupResults_ontem() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.testWorldCupResults("ontem", 123L, attrs);

        verify(worldCupSchedulerService).sendResultsToChat(eq(123L), any());
        assertThat(attrs.getFlashAttributes().get("success")).isNotNull();
    }

    // =========================================================================
    // DIGEST / RELEASES
    // =========================================================================

    @Test
    @DisplayName("testMorningDigest: dispara e flash success")
    void testMorningDigest_sucesso() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.testMorningDigest(attrs);

        verify(dailyDigestService).generateMorningDigest();
        assertThat(attrs.getFlashAttributes().get("success").toString()).contains("manhã");
    }

    @Test
    @DisplayName("testEveningDigest: dispara e flash success")
    void testEveningDigest_sucesso() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.testEveningDigest(attrs);

        verify(dailyDigestService).generateEveningDigest();
    }

    @Test
    @DisplayName("customDigest: datas válidas → flash success")
    void customDigest_datasValidas() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.customDigest("2026-01-01", "2026-01-31", 123L, attrs);

        verify(dailyDigestService).generateDigestCustom(any(), any(), eq(123L));
        assertThat(attrs.getFlashAttributes().get("success")).isNotNull();
    }

    @Test
    @DisplayName("customDigest: data inválida → flash error")
    void customDigest_dataInvalida() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.customDigest("data-invalida", "2026-01-31", 123L, attrs);

        assertThat(attrs.getFlashAttributes().get("error").toString()).contains("Formato de data");
        verifyNoInteractions(dailyDigestService);
    }

    @Test
    @DisplayName("customDigest: exceção do service → flash error")
    void customDigest_excecao() {
        doThrow(new RuntimeException("boom"))
                .when(dailyDigestService)
                .generateDigestCustom(any(), any(), any());

        RedirectAttributes attrs = newRedirectAttributes();
        controller.customDigest("2026-01-01", "2026-01-31", 123L, attrs);

        assertThat(attrs.getFlashAttributes().get("error").toString()).contains("Erro interno");
    }

    @Test
    @DisplayName("testDailyReleases: dispara e flash success")
    void testDailyReleases_sucesso() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.testDailyReleases(attrs);

        verify(dailyReleasesService).sendHourlyReleases();
    }

    @Test
    @DisplayName("testWeeklyDigest: dispara e flash success")
    void testWeeklyDigest_sucesso() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.testWeeklyDigest(attrs);

        verify(dailyReleasesService).sendWeeklyDigest();
    }

    // =========================================================================
    // LEMBRETES
    // =========================================================================

    @Test
    @DisplayName("testWeeklyReminder: dispara e flash success")
    void testWeeklyReminder_sucesso() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.testWeeklyReminder(attrs);

        verify(weeklyReminderService).sendWednesdayReminder();
    }

    @Test
    @DisplayName("testWeeklyReminderShowcase: chatId explícito")
    void testWeeklyReminderShowcase_chatId() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.testWeeklyReminderShowcase(123L, attrs);

        verify(weeklyReminderService).sendReminderToChat(123L);
    }

    // =========================================================================
    // AUTO-RESPONSE
    // =========================================================================

    @Test
    @DisplayName("testAutoResponse: encontrada, sem animation → enviarMensagemHtml")
    void testAutoResponse_semAnimation() throws Exception {
        AutoResponseOverride response = new AutoResponseOverride("Olá!", null);
        when(autoResponseService.getResponseRule(any(), eq("oi"), any()))
                .thenReturn(Optional.of(response));

        mockMvc.perform(
                        post("/admin-web/test-auto-response")
                                .param("message", "oi")
                                .param("chatId", "123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Resposta automática enviada")));

        verify(telegramFacade).enviarMensagemHtml(eq(123L), anyString());
    }

    @Test
    @DisplayName("testAutoResponse: encontrada, com animation válida → enviarMidia")
    void testAutoResponse_comAnimation() throws Exception {
        AutoResponseOverride response =
                new AutoResponseOverride("Olá!", "https://example.com/a.mp4");
        when(autoResponseService.getResponseRule(any(), eq("oi"), any()))
                .thenReturn(Optional.of(response));

        mockMvc.perform(
                        post("/admin-web/test-auto-response")
                                .param("message", "oi")
                                .param("chatId", "123"))
                .andExpect(status().isOk());

        verify(telegramFacade).enviarMidia(eq(123L), eq("https://example.com/a.mp4"), anyString());
    }

    @Test
    @DisplayName("testAutoResponse: não encontrada → mensagem informativa")
    void testAutoResponse_naoEncontrada() throws Exception {
        when(autoResponseService.getResponseRule(any(), anyString(), any()))
                .thenReturn(Optional.empty());

        mockMvc.perform(
                        post("/admin-web/test-auto-response")
                                .param("message", "xyz")
                                .param("chatId", "123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nenhuma resposta")));
    }

    @Test
    @DisplayName("testAutoResponse: mensagem vazia → 400")
    void testAutoResponse_mensagemVazia() throws Exception {
        mockMvc.perform(post("/admin-web/test-auto-response").param("message", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("testAutoResponse: exceção → 500 MSG_ERRO_INTERNO")
    void testAutoResponse_excecao() throws Exception {
        when(autoResponseService.getResponseRule(any(), anyString(), any()))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(
                        post("/admin-web/test-auto-response")
                                .param("message", "oi")
                                .param("chatId", "123"))
                .andExpect(status().is5xxServerError())
                .andExpect(content().string(containsString("Erro interno")));
    }

    @Test
    @DisplayName("debugAutoResponse: encontrada")
    void debugAutoResponse_encontrada() throws Exception {
        AutoResponseOverride response = new AutoResponseOverride("Olá!", null);
        when(autoResponseService.getResponseRule(any(), eq("oi"), any()))
                .thenReturn(Optional.of(response));

        mockMvc.perform(get("/admin-web/debug-auto-response").param("message", "oi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.response").value("Olá!"));
    }

    @Test
    @DisplayName("debugAutoResponse: não encontrada")
    void debugAutoResponse_naoEncontrada() throws Exception {
        when(autoResponseService.getResponseRule(any(), anyString(), any()))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/admin-web/debug-auto-response").param("message", "xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(false));
    }

    @Test
    @DisplayName("listAutoResponseRules: retorna contagem e summary")
    void listAutoResponseRules_sucesso() throws Exception {
        when(autoResponseService.getRulesCount()).thenReturn(5);
        when(autoResponseService.getRulesSummary()).thenReturn(Map.of("bom dia", "response=x"));

        mockMvc.perform(get("/admin-web/auto-response-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRules").value(5));
    }

    // =========================================================================
    // ADMIN — limpeza/recarregamento
    // =========================================================================

    @Test
    @DisplayName("clearReleases: sucesso → flash success")
    void clearReleases_sucesso() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.clearReleases(attrs);

        verify(releaseNotifiedRepository).clearAll();
        assertThat(attrs.getFlashAttributes().get("success")).isNotNull();
    }

    @Test
    @DisplayName("clearReleases: exceção → flash error")
    void clearReleases_excecao() {
        doThrow(new RuntimeException("boom")).when(releaseNotifiedRepository).clearAll();
        RedirectAttributes attrs = newRedirectAttributes();

        controller.clearReleases(attrs);

        assertThat(attrs.getFlashAttributes().get("error").toString()).contains("Erro interno");
    }

    @Test
    @DisplayName("clearAllData: sucesso com contagem")
    void clearAllData_sucesso() {
        when(releaseNotifiedRepository.deleteAll()).thenReturn(7);
        RedirectAttributes attrs = newRedirectAttributes();

        controller.clearAllData(attrs);

        assertThat(attrs.getFlashAttributes().get("success").toString()).contains("7");
    }

    @Test
    @DisplayName("clearAllData: exceção → flash error")
    void clearAllData_excecao() {
        doThrow(new RuntimeException("boom")).when(releaseNotifiedRepository).deleteAll();
        RedirectAttributes attrs = newRedirectAttributes();

        controller.clearAllData(attrs);

        assertThat(attrs.getFlashAttributes().get("error")).isNotNull();
    }

    @Test
    @DisplayName("reloadAutoResponses: chama service e flash success")
    void reloadAutoResponses_sucesso() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.reloadAutoResponses(attrs);

        verify(autoResponseService).reload();
        assertThat(attrs.getFlashAttributes().get("success").toString()).contains("recarregadas");
    }

    @Test
    @DisplayName("reloadEasterEggs: chama service e flash success")
    void reloadEasterEggs_sucesso() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.reloadEasterEggs(attrs);

        verify(easterEggService).reload();
    }

    // =========================================================================
    // PODCAST
    // =========================================================================

    @Test
    @DisplayName("testPodcast: datas válidas → flash success e chama async")
    void testPodcast_datasValidas() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.testPodcast("2026-01-01", "2026-01-31", 123L, attrs);

        // Async pode demorar; só verifica flash imediato
        assertThat(attrs.getFlashAttributes().get("success").toString())
                .contains("Podcast agendado");
    }

    @Test
    @DisplayName("testPodcast: data inválida → flash error")
    void testPodcast_dataInvalida() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.testPodcast("nao-e-data", "2026-01-31", 123L, attrs);

        assertThat(attrs.getFlashAttributes().get("error").toString()).contains("Formato de data");
    }

    @Test
    @DisplayName("testPodcast: start > end → flash error")
    void testPodcast_startDepoisDeEnd() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.testPodcast("2026-12-31", "2026-01-01", 123L, attrs);

        assertThat(attrs.getFlashAttributes().get("error").toString()).contains("posterior");
    }

    @Test
    @DisplayName("testPodcastLatest: usa últimos 7 dias")
    void testPodcastLatest_sucesso() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.testPodcastLatest(123L, attrs);

        assertThat(attrs.getFlashAttributes().get("success")).isNotNull();
    }

    @Test
    @DisplayName("testPodcastDays: dias inválidos (0) → flash error")
    void testPodcastDays_zero() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.testPodcastDays(0, 123L, attrs);

        assertThat(attrs.getFlashAttributes().get("error").toString()).contains("entre 1 e 30");
    }

    @Test
    @DisplayName("testPodcastDays: dias inválidos (31) → flash error")
    void testPodcastDays_maiorQue30() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.testPodcastDays(31, 123L, attrs);

        assertThat(attrs.getFlashAttributes().get("error")).isNotNull();
    }

    @Test
    @DisplayName("testPodcastDays: 7 dias → flash success")
    void testPodcastDays_sete() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.testPodcastDays(7, 123L, attrs);

        assertThat(attrs.getFlashAttributes().get("success")).isNotNull();
    }

    // =========================================================================
    // ANIVERSÁRIOS
    // =========================================================================

    @Test
    @DisplayName("testBirthday: dia/mês inválidos → flash error")
    void testBirthday_invalido() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.testBirthday(0, 13, attrs);

        assertThat(attrs.getFlashAttributes().get("error").toString()).contains("Dia/mês");
    }

    @Test
    @DisplayName("testBirthday: sucesso → flash success")
    void testBirthday_sucesso() {
        when(birthdayService.enviarParabensPara(10, 5)).thenReturn(3);
        RedirectAttributes attrs = newRedirectAttributes();

        controller.testBirthday(10, 5, attrs);

        assertThat(attrs.getFlashAttributes().get("success").toString()).contains("3");
    }

    @Test
    @DisplayName("testBirthday: exceção → flash error")
    void testBirthday_excecao() {
        when(birthdayService.enviarParabensPara(anyInt(), anyInt()))
                .thenThrow(new RuntimeException("boom"));
        RedirectAttributes attrs = newRedirectAttributes();

        controller.testBirthday(10, 5, attrs);

        assertThat(attrs.getFlashAttributes().get("error")).isNotNull();
    }

    @Test
    @DisplayName("testBirthdayToday: sucesso → flash success")
    void testBirthdayToday_sucesso() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.testBirthdayToday(attrs);

        verify(birthdayService).enviarParabensDoDia();
        assertThat(attrs.getFlashAttributes().get("success")).isNotNull();
    }

    @Test
    @DisplayName("deleteBirthday: nenhum deletado → flash error")
    void deleteBirthday_zero() {
        when(birthdayRepository.deleteByUserId(123L)).thenReturn(0);
        RedirectAttributes attrs = newRedirectAttributes();

        controller.deleteBirthday(123L, attrs);

        assertThat(attrs.getFlashAttributes().get("error").toString()).contains("123");
    }

    @Test
    @DisplayName("deleteBirthday: sucesso → flash success")
    void deleteBirthday_sucesso() {
        when(birthdayRepository.deleteByUserId(123L)).thenReturn(1);
        RedirectAttributes attrs = newRedirectAttributes();

        controller.deleteBirthday(123L, attrs);

        assertThat(attrs.getFlashAttributes().get("success")).isNotNull();
    }

    @Test
    @DisplayName("clearBirthdays: sucesso com contagem")
    void clearBirthdays_sucesso() {
        when(birthdayRepository.deleteAll()).thenReturn(5);
        RedirectAttributes attrs = newRedirectAttributes();

        controller.clearBirthdays(attrs);

        assertThat(attrs.getFlashAttributes().get("success").toString()).contains("5");
    }

    @Test
    @DisplayName("birthdaysJson: retorna total + lista")
    void birthdaysJson_sucesso() throws Exception {
        when(birthdayRepository.count()).thenReturn(3);
        when(birthdayRepository.findAll()).thenReturn(List.<Birthday>of());

        mockMvc.perform(get("/admin-web/birthdays-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3));
    }

    // =========================================================================
    // MIGRAÇÃO
    // =========================================================================

    @Test
    @DisplayName("migrateFromSqlite: sucesso → flash success")
    void migrateFromSqlite_sucesso() {
        MigrationResult result =
                new MigrationResult("SUCCESS", null, null, 100L, Map.of("users", 5), null);
        when(migrationService.migrateAll(false)).thenReturn(result);

        RedirectAttributes attrs = newRedirectAttributes();
        controller.migrateFromSqlite(false, attrs);

        assertThat(attrs.getFlashAttributes().get("success").toString()).contains("SUCCESS");
    }

    @Test
    @DisplayName("migrateFromSqlite: IllegalStateException → flash error")
    void migrateFromSqlite_illegalState() {
        when(migrationService.migrateAll(anyBoolean()))
                .thenThrow(new IllegalStateException("migration.enabled=false"));

        RedirectAttributes attrs = newRedirectAttributes();
        controller.migrateFromSqlite(false, attrs);

        assertThat(attrs.getFlashAttributes().get("error").toString()).contains("migration");
    }

    @Test
    @DisplayName("previewMigration: retorna contadores")
    void previewMigration_sucesso() throws Exception {
        when(migrationService.previewCounts()).thenReturn(Map.of("users", 10, "posts", 20));

        mockMvc.perform(get("/admin-web/migrate-sqlite/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(30))
                .andExpect(jsonPath("$.contadores.users").value(10));
    }

    @Test
    @DisplayName("previewMigration: IllegalStateException → 412")
    void previewMigration_illegalState() throws Exception {
        when(migrationService.previewCounts())
                .thenThrow(new IllegalStateException("migration disabled"));

        mockMvc.perform(get("/admin-web/migrate-sqlite/preview"))
                .andExpect(status().isPreconditionFailed());
    }

    // =========================================================================
    // FEATURE FLAGS
    // =========================================================================

    @Test
    @DisplayName("listFeatures: retorna mapa")
    void listFeatures_sucesso() throws Exception {
        Map<String, Map<String, Object>> flags = new LinkedHashMap<>();
        flags.put("worldcup.enabled", Map.of("enabled", true, "readOnly", false));
        when(featureFlagAdminService.listAsMap()).thenReturn(flags);

        mockMvc.perform(get("/admin-web/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['worldcup.enabled'].enabled").value(true));
    }

    @Test
    @DisplayName("toggleFeature: flag desconhecida → flash error")
    void toggleFeature_flagDesconhecida() {
        doThrow(new IllegalArgumentException("Flag desconhecida: xyz"))
                .when(featureFlagAdminService)
                .toggle(eq("xyz"), anyBoolean());

        RedirectAttributes attrs = newRedirectAttributes();
        controller.toggleFeature("xyz", true, attrs);

        assertThat(attrs.getFlashAttributes().get("error").toString())
                .contains("Flag desconhecida");
    }

    @Test
    @DisplayName("toggleFeature: flag read-only → flash error")
    void toggleFeature_readOnly() {
        when(featureFlagAdminService.isEnabled("migration.enabled")).thenReturn(false);
        doThrow(new IllegalStateException("Flag 'migration.enabled' é read-only"))
                .when(featureFlagAdminService)
                .toggle(eq("migration.enabled"), anyBoolean());

        RedirectAttributes attrs = newRedirectAttributes();
        controller.toggleFeature("migration.enabled", true, attrs);

        assertThat(attrs.getFlashAttributes().get("error").toString()).contains("read-only");
    }

    @Test
    @DisplayName("toggleFeature: sucesso → flash success")
    void toggleFeature_sucesso() {
        when(featureFlagAdminService.isEnabled("worldcup.enabled")).thenReturn(false, true);

        RedirectAttributes attrs = newRedirectAttributes();
        controller.toggleFeature("worldcup.enabled", true, attrs);

        assertThat(attrs.getFlashAttributes().get("success").toString()).contains("ativada");
    }

    @Test
    @DisplayName("toggleFeature: sem mudança → flash informativo")
    void toggleFeature_semMudanca() {
        when(featureFlagAdminService.isEnabled("worldcup.enabled")).thenReturn(true);

        RedirectAttributes attrs = newRedirectAttributes();
        controller.toggleFeature("worldcup.enabled", true, attrs);

        assertThat(attrs.getFlashAttributes().get("success").toString()).contains("já estava");
    }

    // =========================================================================
    // MONITORAMENTO
    // =========================================================================

    @Test
    @DisplayName("cacheStats: retorna stats")
    void cacheStats_sucesso() throws Exception {
        when(cacheService.getStats()).thenReturn(Map.of("hits", 10L, "misses", 2L));

        mockMvc.perform(get("/admin-web/cache-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits").value(10));
    }

    @Test
    @DisplayName("debugCache: fileId inexistente → 404")
    void debugCache_naoEncontrado() throws Exception {
        when(cacheService.get("abc")).thenReturn(null);

        mockMvc.perform(get("/admin-web/debug/cache/abc")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("properties: retorna mapa de propriedades")
    void properties_sucesso() throws Exception {
        when(environment.getProperty(anyString())).thenReturn("");

        mockMvc.perform(get("/admin-web/properties")).andExpect(status().isOk());
    }

    // =========================================================================
    // CONFIG FILES
    // =========================================================================

    @Test
    @DisplayName("configFiles: retorna mapa com 3 arquivos")
    void configFiles_sucesso() throws Exception {
        // Resource que não existe → todos retornam "Arquivo não encontrado"
        Resource resource = mock(Resource.class);
        when(resource.exists()).thenReturn(false);
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(environment.getProperty(anyString(), anyString())).thenReturn("classpath:foo.json");

        mockMvc.perform(get("/admin-web/config-files"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$['easter-eggs.json']").value(containsString("não encontrado")));
    }

    // =========================================================================
    // RESOLVE TARGET CHAT ID — testes indiretos via endpoints
    // =========================================================================

    @Test
    @DisplayName("resolveTargetChatId: chatId explícito tem prioridade")
    void resolve_chatIdExplicito() {
        RedirectAttributes attrs = newRedirectAttributes();

        controller.testWeeklyReminderShowcase(777L, attrs);

        verify(weeklyReminderService).sendReminderToChat(777L);
    }

    @Test
    @DisplayName("resolveTargetChatId: sem chatId, sem owner, usa showcase")
    void resolve_fallbackShowcase() {
        ReflectionTestUtils.setField(controller, "ownerId", 0L);
        ReflectionTestUtils.setField(controller, "digestChatIdsStr", "");

        RedirectAttributes attrs = newRedirectAttributes();
        controller.testWeeklyReminderShowcase(null, attrs);

        verify(weeklyReminderService).sendReminderToChat(AdminUtils.SHOWCASE_CHAT_ID);
    }

    @Test
    @DisplayName("resolveTargetChatId: sem chatId, sem owner, usa primeiro digestChatId")
    void resolve_primeiroDigestChat() {
        ReflectionTestUtils.setField(controller, "ownerId", 0L);
        ReflectionTestUtils.setField(controller, "digestChatIdsStr", "123,456");

        RedirectAttributes attrs = newRedirectAttributes();
        controller.testWeeklyReminderShowcase(null, attrs);

        verify(weeklyReminderService).sendReminderToChat(123L);
    }

    @Test
    @DisplayName(
            "POST /admin-web/reload-prompts - Deve recarregar os prompts e redirecionar para"
                    + " /admin-web")
    void shouldReloadPromptsAndRedirectToAdminWeb() throws Exception {
        doNothing().when(promptRegistryService).reload();

        mockMvc.perform(post("/admin-web/reload-prompts"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin-web"))
                .andExpect(
                        flash().attribute(
                                        "success", "Prompts e personas recarregados com sucesso."));

        verify(promptRegistryService).reload();
    }

    // =========================================================================
    // HELPERS DE ASSERT — imports faltantes
    // =========================================================================

    // Este bloco existe apenas para lembrar que `assertThat` é do AssertJ
    // Se não estiver no classpath, adicione:
    // import static org.assertj.core.api.Assertions.assertThat;
    // import static org.mockito.Mockito.mockStatic;
    // import static org.mockito.ArgumentMatchers.contains;
    // import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
}
