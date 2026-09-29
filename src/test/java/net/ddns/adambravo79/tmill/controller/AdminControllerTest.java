package net.ddns.adambravo79.tmill.controller;

import static net.ddns.adambravo79.tmill.constant.BotMessages.WORLD_CUP_NOT_AVAILABLE;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import lombok.SneakyThrows;
import net.ddns.adambravo79.tmill.client.AzureTtsClient;
import net.ddns.adambravo79.tmill.model.AutoResponseOverride;
import net.ddns.adambravo79.tmill.repository.BirthdayRepository;
import net.ddns.adambravo79.tmill.repository.ReleaseNotifiedRepository;
import net.ddns.adambravo79.tmill.service.*;
import net.ddns.adambravo79.tmill.service.cache.FileTranscriptionCacheService;
import net.ddns.adambravo79.tmill.service.feature.FeatureFlagAdminService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import tools.jackson.databind.ObjectMapper;

class AdminControllerTest {

    private AdminController adminController;
    private ObjectMapper objectMapperMock;
    private Resource emptyResource;
    private MockMvc mockMvc;

    private static final long SHOWCASE_CHAT_ID = -5283244164L;

    @Mock private EasterEggService easterEggService;
    @Mock private DailyDigestService dailyDigestService;
    @Mock private FileTranscriptionCacheService fileTranscriptionCacheService;
    @Mock private WeeklyReminderService weeklyReminderService;
    @Mock private AutoResponseService autoResponseService;
    @Mock private WorldCupSchedulerService worldCupSchedulerService;
    @Mock private StaticWorldCupService staticWorldCupService;
    @Mock private TelegramFacade telegramFacade;
    @Mock private Environment environment;
    @Mock private ResourceLoader resourceLoader;
    @Mock private DailyReleasesService dailyReleasesService;
    @Mock private ReleaseNotifiedRepository releaseNotifiedRepository;
    @Mock private AzureTtsClient azureTtsClient;
    @Mock private PodcastPublisherService podcastPublisherService;
    @Mock private TempDirService tempDirService;
    @Mock private BirthdayService birthdayService;
    @Mock private BirthdayRepository birthdayRepository;
    @Mock private MigrationService migrationService;
    @Mock private FeatureFlagAdminService featureFlagAdminService;

    @BeforeEach
    @SneakyThrows
    void setup() {
        MockitoAnnotations.openMocks(this);

        objectMapperMock = mock(ObjectMapper.class);

        // Mocks pré-criados — evita UnfinishedStubbingException
        emptyResource = mockResource(false);

        adminController =
                new AdminController(
                        easterEggService,
                        dailyDigestService,
                        fileTranscriptionCacheService,
                        weeklyReminderService,
                        autoResponseService,
                        worldCupSchedulerService,
                        staticWorldCupService,
                        telegramFacade,
                        environment,
                        resourceLoader,
                        objectMapperMock,
                        dailyReleasesService,
                        releaseNotifiedRepository,
                        azureTtsClient,
                        podcastPublisherService,
                        tempDirService,
                        birthdayService,
                        birthdayRepository,
                        migrationService,
                        featureFlagAdminService);

        ReflectionTestUtils.setField(adminController, "worldcupEnabled", true);

        // Stub padrão: NUNCA retorna null. Se o arquivo real não existe no classpath,
        // retorna mockResource(false). Isso garante que AdminUtils.loadConfigFile
        // caia nos fallbacks sem NPE.
        lenient()
                .when(resourceLoader.getResource(anyString()))
                .thenAnswer(
                        invocation -> {
                            String path = invocation.getArgument(0, String.class);

                            if (path.startsWith("classpath:")) {
                                String fileName = path.substring("classpath:".length());
                                ClassPathResource cpr = new ClassPathResource(fileName);
                                return cpr.exists() ? cpr : emptyResource;
                            }

                            return emptyResource;
                        });

        this.mockMvc = MockMvcBuilders.standaloneSetup(adminController).build();
    }

    // ========================= HELPERS =========================

    private Resource mockResource(boolean exists) {
        Resource res = mock(Resource.class);
        lenient().when(res.exists()).thenReturn(exists);
        return res;
    }

    // ========================= LIMPEZA =========================

    @Test
    void clearReleases_deveLimparERetornarOk() throws Exception {
        doNothing().when(releaseNotifiedRepository).clearAll();

        mockMvc.perform(post("/admin/clear-releases"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tabela de lançamentos limpa")));

        verify(releaseNotifiedRepository).clearAll();
    }

    @Test
    void clearReleases_quandoDataAccessException_deveRetornarInternalServerError()
            throws Exception {
        doThrow(new DataAccessException("DB error") {}).when(releaseNotifiedRepository).clearAll();

        mockMvc.perform(post("/admin/clear-releases"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString("Erro ao limpar tabela")));
    }

    @Test
    void clearAllData_deveLimparERetornarOk() throws Exception {
        when(releaseNotifiedRepository.deleteAll()).thenReturn(10);

        mockMvc.perform(post("/admin/clear-all-data"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dados removidos: 10 lançamentos")));

        verify(releaseNotifiedRepository).deleteAll();
    }

    @Test
    void clearAllData_quandoDataAccessException_deveRetornarInternalServerError() throws Exception {
        doThrow(new DataAccessException("DB error") {}).when(releaseNotifiedRepository).deleteAll();

        mockMvc.perform(post("/admin/clear-all-data"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString("Erro ao limpar dados")));
    }

    // ========================= RECARREGAMENTO =========================

    @Test
    void reloadAutoResponses_deveRecarregarERetornarOk() throws Exception {
        doNothing().when(autoResponseService).reload();

        mockMvc.perform(post("/admin/reload-auto-responses"))
                .andExpect(status().isOk())
                .andExpect(content().string("Respostas automáticas recarregadas"));

        verify(autoResponseService).reload();
    }

    @Test
    void reloadEasterEggs_deveRecarregarERetornarOk() throws Exception {
        doNothing().when(easterEggService).reload();

        mockMvc.perform(post("/admin/reload-easter-eggs"))
                .andExpect(status().isOk())
                .andExpect(content().string("Easter eggs recarregados"));

        verify(easterEggService).reload();
    }

    @Test
    void reloadWorldCup_deveRecarregarERetornarOk() throws Exception {
        doNothing().when(staticWorldCupService).reload();

        mockMvc.perform(post("/admin/reload-worldcup"))
                .andExpect(status().isOk())
                .andExpect(content().string("Dados da Copa recarregados do arquivo JSON"));

        verify(staticWorldCupService).reload();
    }

    // ========================= LEMBRETES =========================

    @Test
    void testWeeklyReminder_deveDispararERetornarOk() throws Exception {
        doNothing().when(weeklyReminderService).sendWednesdayReminder();

        mockMvc.perform(post("/admin/test-weekly-reminder"))
                .andExpect(status().isOk())
                .andExpect(content().string("Lembrete semanal disparado manualmente."));

        verify(weeklyReminderService).sendWednesdayReminder();
    }

    @Test
    void testWeeklyReminderShowcase_deveEnviarParaShowcase() throws Exception {
        doNothing().when(weeklyReminderService).sendReminderToChat(SHOWCASE_CHAT_ID);

        mockMvc.perform(post("/admin/test-weekly-reminder-showcase"))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        containsString(
                                                "Lembrete semanal enviado para o chat"
                                                        + " -5283244164")));

        verify(weeklyReminderService).sendReminderToChat(SHOWCASE_CHAT_ID);
    }

    @Test
    void testWeeklyReminderShowcase_comChatIdPersonalizado() throws Exception {
        doNothing().when(weeklyReminderService).sendReminderToChat(999L);

        mockMvc.perform(post("/admin/test-weekly-reminder-showcase").param("chatId", "999"))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        containsString(
                                                "Lembrete semanal enviado para o chat 999")));

        verify(weeklyReminderService).sendReminderToChat(999L);
    }

    // ========================= DIGEST =========================

    @Test
    void testMorningDigest_deveDispararERetornarOk() throws Exception {
        doNothing().when(dailyDigestService).generateMorningDigest();

        mockMvc.perform(get("/admin/test-morning-digest"))
                .andExpect(status().isOk())
                .andExpect(content().string("Resumo da manhã disparado."));

        verify(dailyDigestService).generateMorningDigest();
    }

    @Test
    void testEveningDigest_deveDispararERetornarOk() throws Exception {
        doNothing().when(dailyDigestService).generateEveningDigest();

        mockMvc.perform(get("/admin/test-evening-digest"))
                .andExpect(status().isOk())
                .andExpect(content().string("Resumo da noite disparado."));

        verify(dailyDigestService).generateEveningDigest();
    }

    // ========================= CACHE STATS =========================

    @Test
    void getCacheStats_deveRetornarStats() throws Exception {
        Map<String, Long> stats = Map.of("hits", 10L, "misses", 2L, "size", 5L);
        when(fileTranscriptionCacheService.getStats()).thenReturn(stats);

        mockMvc.perform(get("/admin/cache-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits").value(10))
                .andExpect(jsonPath("$.misses").value(2))
                .andExpect(jsonPath("$.size").value(5));
    }

    // ========================= CUSTOM DIGEST =========================

    @Test
    void customDigest_comDatasValidasSemChatId_deveGerarParaTodos() throws Exception {
        mockMvc.perform(
                        get("/admin/custom-digest")
                                .param("start", "2026-05-07")
                                .param("end", "2026-05-08"))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        containsString(
                                                "Resumo personalizado gerado para período:"
                                                        + " 2026-05-07 até 2026-05-08 (enviado para"
                                                        + " todos os chats configurados)")));

        verify(dailyDigestService)
                .generateDigestCustom(any(LocalDateTime.class), any(LocalDateTime.class), isNull());
    }

    @Test
    void customDigest_comDatasValidasComChatId_deveGerarParaChatEspecifico() throws Exception {
        mockMvc.perform(
                        get("/admin/custom-digest")
                                .param("start", "2026-05-07")
                                .param("end", "2026-05-08")
                                .param("chatId", "12345"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("enviado apenas para o chat 12345")));

        verify(dailyDigestService)
                .generateDigestCustom(
                        any(LocalDateTime.class), any(LocalDateTime.class), eq(12345L));
    }

    @Test
    void customDigest_comDatasInvalida_deveRetornarBadRequest() throws Exception {
        mockMvc.perform(
                        get("/admin/custom-digest")
                                .param("start", "invalid")
                                .param("end", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Formato inválido")));

        verifyNoInteractions(dailyDigestService);
    }

    @Test
    void customDigest_comParametrosAusentes_deveRetornarBadRequest() throws Exception {
        mockMvc.perform(get("/admin/custom-digest")).andExpect(status().isBadRequest());
        verifyNoInteractions(dailyDigestService);
    }

    @Test
    void customDigest_comParametrosVazios_deveRetornarBadRequest() throws Exception {
        mockMvc.perform(get("/admin/custom-digest").param("start", "").param("end", "2026-05-08"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        content()
                                .string(
                                        containsString(
                                                "Parâmetros 'start' e 'end' são obrigatórios")));
    }

    @Test
    void customDigest_comDatasNoFormatoDDMMYYYY_deveAceitar() throws Exception {
        mockMvc.perform(
                        get("/admin/custom-digest")
                                .param("start", "07-05-2026")
                                .param("end", "08-05-2026"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Resumo personalizado gerado")));

        verify(dailyDigestService)
                .generateDigestCustom(any(LocalDateTime.class), any(LocalDateTime.class), isNull());
    }

    @Test
    void customDigest_quandoIllegalArgumentException_deveRetornarBadRequest() throws Exception {
        doThrow(new IllegalArgumentException("Período inválido"))
                .when(dailyDigestService)
                .generateDigestCustom(any(LocalDateTime.class), any(LocalDateTime.class), any());

        mockMvc.perform(
                        get("/admin/custom-digest")
                                .param("start", "2026-05-07")
                                .param("end", "2026-05-08"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Parâmetros inválidos")));
    }

    @Test
    void customDigest_quandoRuntimeException_deveRetornarInternalServerError() throws Exception {
        doThrow(new RuntimeException("Erro interno"))
                .when(dailyDigestService)
                .generateDigestCustom(any(LocalDateTime.class), any(LocalDateTime.class), any());

        mockMvc.perform(
                        get("/admin/custom-digest")
                                .param("start", "2026-05-07")
                                .param("end", "2026-05-08"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString("Erro interno do servidor")));
    }

    // ========================= WORLD CUP =========================

    @Test
    void testWorldCup_deveDispararTesteManual() throws Exception {
        doNothing().when(worldCupSchedulerService).sendManualTest();

        mockMvc.perform(post("/admin/test-worldcup"))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        containsString(
                                                "Envio manual disparado! Verifique os logs.")));

        verify(worldCupSchedulerService).sendManualTest();
    }

    @Test
    void testWorldCup_quandoWorldcupDisabled_deveRetornarMensagem() throws Exception {
        ReflectionTestUtils.setField(adminController, "worldcupEnabled", false);

        mockMvc.perform(post("/admin/test-worldcup"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Servico de Copa desativado")));

        verifyNoInteractions(worldCupSchedulerService);
    }

    @Test
    void testWorldCupShowcase_deveDispararParaShowcase() throws Exception {
        doNothing().when(worldCupSchedulerService).sendManualTestToChat(SHOWCASE_CHAT_ID);

        mockMvc.perform(post("/admin/test-worldcup-showcase"))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        containsString(
                                                "Teste manual da Copa enviado para o chat"
                                                        + " -5283244164")));

        verify(worldCupSchedulerService).sendManualTestToChat(SHOWCASE_CHAT_ID);
    }

    @Test
    void testWorldCupShowcase_comChatIdPersonalizado() throws Exception {
        doNothing().when(worldCupSchedulerService).sendManualTestToChat(999L);

        mockMvc.perform(post("/admin/test-worldcup-showcase").param("chatId", "999"))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        containsString(
                                                "Teste manual da Copa enviado para o chat 999")));

        verify(worldCupSchedulerService).sendManualTestToChat(999L);
    }

    @Test
    void testWorldCupShowcase_quandoWorldcupDisabled_deveRetornarMensagem() throws Exception {
        ReflectionTestUtils.setField(adminController, "worldcupEnabled", false);

        mockMvc.perform(post("/admin/test-worldcup-showcase"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Servico de Copa desativado")));

        verifyNoInteractions(worldCupSchedulerService);
    }

    static Stream<Arguments> chatIdProvider() {
        return Stream.of(Arguments.of(null, SHOWCASE_CHAT_ID), Arguments.of(999L, 999L));
    }

    @ParameterizedTest
    @MethodSource("chatIdProvider")
    void testWorldCupShowcase(Long chatIdParam, long expectedChatId) throws Exception {
        doNothing().when(worldCupSchedulerService).sendManualTestToChat(expectedChatId);

        MockHttpServletRequestBuilder request = post("/admin/test-worldcup-showcase");
        if (chatIdParam != null) {
            request.param("chatId", String.valueOf(chatIdParam));
        }

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(String.valueOf(expectedChatId))));

        verify(worldCupSchedulerService).sendManualTestToChat(expectedChatId);
    }

    @Test
    void testWorldCupNoon_deveDisparar() throws Exception {
        doNothing().when(worldCupSchedulerService).sendNoonMatches();

        mockMvc.perform(post("/admin/test-worldcup-noon"))
                .andExpect(status().isOk())
                .andExpect(
                        content().string(containsString("Envio de jogos do meio-dia executado")));

        verify(worldCupSchedulerService).sendNoonMatches();
    }

    @Test
    void testWorldCupEvening_deveDisparar() throws Exception {
        doNothing().when(worldCupSchedulerService).sendEveningMatches();

        mockMvc.perform(post("/admin/test-worldcup-evening"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Envio de jogos da noite executado")));

        verify(worldCupSchedulerService).sendEveningMatches();
    }

    @Test
    void testWorldCupNoon_quandoServicoNull_deveRetornarServiceUnavailable() throws Exception {
        ReflectionTestUtils.setField(adminController, "worldCupSchedulerService", null);

        mockMvc.perform(post("/admin/test-worldcup-noon"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString(WORLD_CUP_NOT_AVAILABLE)));
    }

    @Test
    void testWorldCupEvening_quandoServicoNull_deveRetornarServiceUnavailable() throws Exception {
        ReflectionTestUtils.setField(adminController, "worldCupSchedulerService", null);

        mockMvc.perform(post("/admin/test-worldcup-evening"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString(WORLD_CUP_NOT_AVAILABLE)));
    }

    @Test
    void testWorldCupNoonShowcase_deveEnviarParaShowcase() throws Exception {
        doNothing().when(worldCupSchedulerService).sendNoonMatchesToChat(SHOWCASE_CHAT_ID);

        mockMvc.perform(post("/admin/test-worldcup-noon-showcase"))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        containsString(
                                                "Envio do meio-dia da Copa enviado para o chat"
                                                        + " -5283244164")));

        verify(worldCupSchedulerService).sendNoonMatchesToChat(SHOWCASE_CHAT_ID);
    }

    @Test
    void testWorldCupEveningShowcase_deveEnviarParaShowcase() throws Exception {
        doNothing().when(worldCupSchedulerService).sendEveningMatchesToChat(SHOWCASE_CHAT_ID);

        mockMvc.perform(post("/admin/test-worldcup-evening-showcase"))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        containsString(
                                                "Envio da noite da Copa enviado para o chat"
                                                        + " -5283244164")));

        verify(worldCupSchedulerService).sendEveningMatchesToChat(SHOWCASE_CHAT_ID);
    }

    @Test
    void reloadWorldCupShowcase_deveRecarregarDadosEEnviarMensagem() throws Exception {
        doNothing().when(staticWorldCupService).reload();

        mockMvc.perform(post("/admin/reload-worldcup-showcase"))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        containsString(
                                                "Dados da Copa recarregados do arquivo JSON")));

        verify(staticWorldCupService).reload();
        verify(telegramFacade).enviarMensagemHtml(eq(SHOWCASE_CHAT_ID), anyString());
    }

    @Test
    void reloadWorldCupShowcase_comChatIdPersonalizado() throws Exception {
        doNothing().when(staticWorldCupService).reload();

        mockMvc.perform(post("/admin/reload-worldcup-showcase").param("chatId", "999"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("enviado para o chat 999")));

        verify(staticWorldCupService).reload();
        verify(telegramFacade).enviarMensagemHtml(eq(999L), anyString());
    }

    @Test
    void reloadWorldCupShowcase_quandoHttpClientErrorException_deveLogarApenas() throws Exception {
        doNothing().when(staticWorldCupService).reload();
        doThrow(
                        HttpClientErrorException.create(
                                HttpStatus.FORBIDDEN, "Forbidden", null, null, null))
                .when(telegramFacade)
                .enviarMensagemHtml(eq(SHOWCASE_CHAT_ID), anyString());

        mockMvc.perform(post("/admin/reload-worldcup-showcase"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dados da Copa recarregados")));

        verify(staticWorldCupService).reload();
        verify(telegramFacade).enviarMensagemHtml(eq(SHOWCASE_CHAT_ID), anyString());
    }

    @Test
    void reloadWorldCupShowcase_quandoResourceAccessException_deveLogarApenas() throws Exception {
        doNothing().when(staticWorldCupService).reload();
        doThrow(new ResourceAccessException("Timeout"))
                .when(telegramFacade)
                .enviarMensagemHtml(eq(SHOWCASE_CHAT_ID), anyString());

        mockMvc.perform(post("/admin/reload-worldcup-showcase"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dados da Copa recarregados")));

        verify(staticWorldCupService).reload();
        verify(telegramFacade).enviarMensagemHtml(eq(SHOWCASE_CHAT_ID), anyString());
    }

    @Test
    void testWorldCupResultsShowcase_comParamOntem_deveEnviarResultados() throws Exception {
        doNothing()
                .when(worldCupSchedulerService)
                .sendResultsToChat(eq(SHOWCASE_CHAT_ID), any(LocalDate.class));

        mockMvc.perform(post("/admin/test-worldcup-results-showcase").param("dateParam", "ontem"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Resultados enviados")));

        verify(worldCupSchedulerService)
                .sendResultsToChat(eq(SHOWCASE_CHAT_ID), any(LocalDate.class));
    }

    @Test
    void testWorldCupResultsShowcase_comParamHoje_deveEnviarResultados() throws Exception {
        doNothing()
                .when(worldCupSchedulerService)
                .sendResultsToChat(eq(SHOWCASE_CHAT_ID), any(LocalDate.class));

        mockMvc.perform(post("/admin/test-worldcup-results-showcase").param("dateParam", "hoje"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Resultados enviados")));

        verify(worldCupSchedulerService)
                .sendResultsToChat(eq(SHOWCASE_CHAT_ID), any(LocalDate.class));
    }

    @Test
    void testWorldCupResultsShowcase_comParamInvalido_deveRetornarBadRequest() throws Exception {
        mockMvc.perform(post("/admin/test-worldcup-results-showcase").param("dateParam", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Data invalida")));
    }

    // ========================= PROPERTIES =========================

    @Test
    void getProperties_deveRetornarPropriedadesMascaradas() throws Exception {
        lenient().when(environment.getProperty(anyString())).thenReturn("");
        when(environment.getProperty("spring.application.name")).thenReturn("tmill-bot");
        when(environment.getProperty("server.port")).thenReturn("8080");
        when(environment.getProperty("spring.threads.virtual.enabled")).thenReturn("true");
        when(environment.getProperty("telegram.bot.username")).thenReturn("tmill_bot");
        when(environment.getProperty("telegram.bot.token"))
                .thenReturn("1234567890:ABCdefGHIjklMNOpqrsTUVwxyz");

        mockMvc.perform(get("/admin/properties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['spring.application.name']").value("tmill-bot"))
                .andExpect(jsonPath("$['telegram.bot.token']").value("1234...wxyz"));
    }

    @Test
    void maskToken_deveMascararTokenCorretamente() throws Exception {
        lenient().when(environment.getProperty(anyString())).thenReturn("");
        when(environment.getProperty("telegram.bot.token"))
                .thenReturn("1234567890:ABCdefGHIjklMNOpqrsTUVwxyz");
        when(environment.getProperty("spring.application.name")).thenReturn("test");

        mockMvc.perform(get("/admin/properties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['telegram.bot.token']").value("1234...wxyz"));
    }

    @Test
    void maskToken_comTokenCurto_deveRetornarAsteriscos() throws Exception {
        when(environment.getProperty("telegram.bot.token")).thenReturn("12345");
        when(environment.getProperty("spring.application.name")).thenReturn("test");

        mockMvc.perform(get("/admin/properties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['telegram.bot.token']").value("***"));
    }

    // ========================= CONFIG FILES =========================

    @Test
    void getConfigFiles_deveRetornarConteudoDosJSONs() throws Exception {
        // setUp já cobre: retorna ClassPathResource se existir, senão mock(false).
        // Não importa o que retorne, desde que não seja null.
        mockMvc.perform(get("/admin/config-files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*").isNotEmpty());
    }

    @Test
    void getConfigFiles_quandoArquivoNaoEncontrado_deveRetornarMensagemErro() throws Exception {
        when(resourceLoader.getResource(anyString())).thenReturn(emptyResource);

        mockMvc.perform(get("/admin/config-files"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$['easter-eggs.json']")
                                .value(containsString("Arquivo não encontrado")));
    }

    @Test
    void getConfigFiles_quandoJsonInvalido_deveRetornarMensagemErro() throws Exception {
        reset(resourceLoader);

        // Resource existe e devolve JSON inválido
        Resource invalidJsonResource = mock(Resource.class);
        when(invalidJsonResource.exists()).thenReturn(true);
        when(invalidJsonResource.getInputStream())
                .thenReturn(new ByteArrayInputStream("not-a-valid-json".getBytes()));

        when(resourceLoader.getResource(anyString())).thenReturn(invalidJsonResource);

        // ObjectMapper real → vai lançar JsonProcessingException naturalmente
        ReflectionTestUtils.setField(adminController, "objectMapper", new ObjectMapper());

        mockMvc.perform(get("/admin/config-files"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$['easter-eggs.json']")
                                .value(containsString("Erro ao parsear JSON")));
    }

    @Test
    void getConfigFiles_quandoPropertyApontaParaFile_deveCarregar() throws Exception {
        reset(resourceLoader);

        // fileResource responde com JSON válido
        Resource fileResource = mock(Resource.class);
        when(fileResource.exists()).thenReturn(true);
        when(fileResource.getInputStream())
                .thenReturn(new ByteArrayInputStream("{\"ok\":true}".getBytes()));

        // classpath retorna mock(false) → força AdminUtils a cair no file:
        when(resourceLoader.getResource(startsWith("classpath:"))).thenReturn(emptyResource);

        // file:/app/config/ retorna fileResource (o JSON válido!)
        when(resourceLoader.getResource(startsWith("file:/app/config/"))).thenReturn(fileResource);

        // file:./config/ retorna emptyResource
        when(resourceLoader.getResource(startsWith("file:./config/"))).thenReturn(emptyResource);

        when(environment.getProperty(eq("easter-egg.file"), anyString()))
                .thenReturn("file:/app/config/easter-eggs.json");

        mockMvc.perform(get("/admin/config-files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['easter-eggs.json']").exists());
    }

    // ========================= DAILY RELEASES =========================

    @Test
    void testDailyReleases_deveDispararERetornarOk() throws Exception {
        doNothing().when(dailyReleasesService).sendHourlyReleases();

        mockMvc.perform(post("/admin/test-daily-releases"))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        containsString(
                                                "Verificação horária de lançamentos executada")));

        verify(dailyReleasesService).sendHourlyReleases();
    }

    @Test
    void testWeeklyDigest_deveDispararERetornarOk() throws Exception {
        doNothing().when(dailyReleasesService).sendWeeklyDigest();

        mockMvc.perform(post("/admin/test-weekly-digest"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Giro semanal executado")));

        verify(dailyReleasesService).sendWeeklyDigest();
    }

    // ========================= AUTO-RESPONSE =========================

    @Test
    void testAutoResponse_comMensagemValida_deveRetornarResposta() throws Exception {
        AutoResponseOverride response = new AutoResponseOverride("Resposta teste", null);
        when(autoResponseService.getResponseRule(any(), anyString(), any()))
                .thenReturn(java.util.Optional.of(response));

        mockMvc.perform(post("/admin/test-auto-response").param("message", "teste"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Resposta enviada para o chat")));

        verify(telegramFacade).enviarMensagemHtml(eq(SHOWCASE_CHAT_ID), anyString());
    }

    @Test
    void testAutoResponse_comMensagemValidaComAnimation_deveEnviarMidia() throws Exception {
        AutoResponseOverride response =
                new AutoResponseOverride("Resposta com animação", "https://example.com/video.mp4");
        when(autoResponseService.getResponseRule(any(), anyString(), any()))
                .thenReturn(java.util.Optional.of(response));

        mockMvc.perform(post("/admin/test-auto-response").param("message", "teste"))
                .andExpect(status().isOk());

        verify(telegramFacade)
                .enviarMidia(
                        eq(SHOWCASE_CHAT_ID), eq("https://example.com/video.mp4"), anyString());
    }

    @Test
    void testAutoResponse_comAnimationUrlInvalida_deveEnviarApenasTexto() throws Exception {
        AutoResponseOverride response =
                new AutoResponseOverride("Resposta com animação", "not_a_valid_url");
        when(autoResponseService.getResponseRule(any(), anyString(), any()))
                .thenReturn(java.util.Optional.of(response));

        mockMvc.perform(post("/admin/test-auto-response").param("message", "teste"))
                .andExpect(status().isOk());

        verify(telegramFacade, never()).enviarMidia(anyLong(), anyString(), anyString());
        verify(telegramFacade).enviarMensagemHtml(eq(SHOWCASE_CHAT_ID), anyString());
    }

    @Test
    void testAutoResponse_comAnimationVazia_deveEnviarApenasTexto() throws Exception {
        AutoResponseOverride response = new AutoResponseOverride("Resposta", "");
        when(autoResponseService.getResponseRule(any(), anyString(), any()))
                .thenReturn(java.util.Optional.of(response));

        mockMvc.perform(post("/admin/test-auto-response").param("message", "teste"))
                .andExpect(status().isOk());

        verify(telegramFacade, never()).enviarMidia(anyLong(), anyString(), anyString());
        verify(telegramFacade).enviarMensagemHtml(eq(SHOWCASE_CHAT_ID), anyString());
    }

    @Test
    void testAutoResponse_comEnvioMidiaFalhaHttp_retornaFallback() throws Exception {
        AutoResponseOverride response =
                new AutoResponseOverride("Resposta com animação", "https://example.com/video.mp4");
        when(autoResponseService.getResponseRule(any(), anyString(), any()))
                .thenReturn(java.util.Optional.of(response));

        doThrow(
                        HttpClientErrorException.create(
                                HttpStatus.FORBIDDEN, "Forbidden", null, null, null))
                .when(telegramFacade)
                .enviarMidia(eq(SHOWCASE_CHAT_ID), anyString(), anyString());

        mockMvc.perform(post("/admin/test-auto-response").param("message", "teste"))
                .andExpect(status().isOk());

        verify(telegramFacade)
                .enviarMidia(
                        eq(SHOWCASE_CHAT_ID), eq("https://example.com/video.mp4"), anyString());
        verify(telegramFacade).enviarMensagemHtml(eq(SHOWCASE_CHAT_ID), anyString());
    }

    @Test
    void testAutoResponse_comEnvioMidiaFalhaTimeout_retornaFallback() throws Exception {
        AutoResponseOverride response =
                new AutoResponseOverride("Resposta com animação", "https://example.com/video.mp4");
        when(autoResponseService.getResponseRule(any(), anyString(), any()))
                .thenReturn(java.util.Optional.of(response));

        doThrow(new ResourceAccessException("Timeout"))
                .when(telegramFacade)
                .enviarMidia(eq(SHOWCASE_CHAT_ID), anyString(), anyString());

        mockMvc.perform(post("/admin/test-auto-response").param("message", "teste"))
                .andExpect(status().isOk());

        verify(telegramFacade)
                .enviarMidia(
                        eq(SHOWCASE_CHAT_ID), eq("https://example.com/video.mp4"), anyString());
        verify(telegramFacade).enviarMensagemHtml(eq(SHOWCASE_CHAT_ID), anyString());
    }

    @Test
    void testAutoResponse_comFallbackFalhaHttp_naoLancaExcecao() throws Exception {
        AutoResponseOverride response = new AutoResponseOverride("Resposta teste", null);
        when(autoResponseService.getResponseRule(any(), anyString(), any()))
                .thenReturn(java.util.Optional.of(response));

        doThrow(
                        HttpClientErrorException.create(
                                HttpStatus.FORBIDDEN, "Forbidden", null, null, null))
                .when(telegramFacade)
                .enviarMensagemHtml(eq(SHOWCASE_CHAT_ID), anyString());

        mockMvc.perform(post("/admin/test-auto-response").param("message", "teste"))
                .andExpect(status().isOk());

        verify(telegramFacade).enviarMensagemHtml(eq(SHOWCASE_CHAT_ID), anyString());
    }

    @Test
    void testAutoResponse_comFallbackFalhaTimeout_naoLancaExcecao() throws Exception {
        AutoResponseOverride response = new AutoResponseOverride("Resposta teste", null);
        when(autoResponseService.getResponseRule(any(), anyString(), any()))
                .thenReturn(java.util.Optional.of(response));

        doThrow(new ResourceAccessException("Timeout"))
                .when(telegramFacade)
                .enviarMensagemHtml(eq(SHOWCASE_CHAT_ID), anyString());

        mockMvc.perform(post("/admin/test-auto-response").param("message", "teste"))
                .andExpect(status().isOk());

        verify(telegramFacade).enviarMensagemHtml(eq(SHOWCASE_CHAT_ID), anyString());
    }

    @Test
    void testAutoResponse_semRegraEncontrada_retornaMensagem() throws Exception {
        when(autoResponseService.getResponseRule(any(), anyString(), any()))
                .thenReturn(java.util.Optional.empty());

        mockMvc.perform(post("/admin/test-auto-response").param("message", "teste"))
                .andExpect(status().isOk())
                .andExpect(
                        content().string(containsString("Nenhuma resposta automática encontrada")));
    }

    @Test
    void testAutoResponse_comParametroMessageAusente_retornaBadRequest() throws Exception {
        mockMvc.perform(post("/admin/test-auto-response")).andExpect(status().isBadRequest());
    }

    @Test
    void testAutoResponse_comChatIdPersonalizado() throws Exception {
        AutoResponseOverride response = new AutoResponseOverride("Resposta teste", null);
        when(autoResponseService.getResponseRule(any(), anyString(), any()))
                .thenReturn(java.util.Optional.of(response));

        mockMvc.perform(
                        post("/admin/test-auto-response")
                                .param("message", "teste")
                                .param("chatId", "999"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Resposta enviada para o chat 999")));

        verify(telegramFacade).enviarMensagemHtml(eq(999L), anyString());
    }

    @Test
    void testAutoResponse_comTimeSimulado() throws Exception {
        AutoResponseOverride response = new AutoResponseOverride("Resposta com horário", null);
        when(autoResponseService.getResponseRule(any(), anyString(), any(LocalTime.class)))
                .thenReturn(java.util.Optional.of(response));

        mockMvc.perform(
                        post("/admin/test-auto-response")
                                .param("message", "teste")
                                .param("time", "14:30"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Resposta enviada")));
    }

    @Test
    void testAutoResponse_comTimeVazio_deveUsarHorarioAtual() throws Exception {
        AutoResponseOverride response =
                new AutoResponseOverride("Resposta com horário atual", null);
        when(autoResponseService.getResponseRule(any(), anyString(), isNull()))
                .thenReturn(java.util.Optional.of(response));

        mockMvc.perform(
                        post("/admin/test-auto-response")
                                .param("message", "teste")
                                .param("time", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Resposta enviada")));

        verify(autoResponseService).getResponseRule(any(), anyString(), isNull());
    }

    @Test
    void testAutoResponse_comTimeInvalido_deveUsarHorarioAtual() throws Exception {
        AutoResponseOverride response =
                new AutoResponseOverride("Resposta com horário atual", null);
        when(autoResponseService.getResponseRule(any(), anyString(), isNull()))
                .thenReturn(java.util.Optional.of(response));

        mockMvc.perform(
                        post("/admin/test-auto-response")
                                .param("message", "teste")
                                .param("time", "25:00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Resposta enviada")));

        verify(autoResponseService).getResponseRule(any(), anyString(), isNull());
    }

    @Test
    void testAutoResponse_comUserIdNull_deveMostrarNaoDefinido() throws Exception {
        AutoResponseOverride response = new AutoResponseOverride("Resposta para usuário", null);
        when(autoResponseService.getResponseRule(isNull(), anyString(), any()))
                .thenReturn(java.util.Optional.of(response));

        mockMvc.perform(post("/admin/test-auto-response").param("message", "teste"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Resposta enviada")));

        verify(autoResponseService).getResponseRule(isNull(), anyString(), any());
        verify(telegramFacade)
                .enviarMensagemHtml(anyLong(), argThat(msg -> msg.contains("NÃO DEFINIDO")));
    }

    @Test
    void debugAutoResponse_deveRetornarInfoDebug() throws Exception {
        AutoResponseOverride response = new AutoResponseOverride("Resposta debug", null);
        when(autoResponseService.getResponseRule(any(), anyString(), any()))
                .thenReturn(java.util.Optional.of(response));

        mockMvc.perform(get("/admin/debug-auto-response").param("message", "teste"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.response").value("Resposta debug"));
    }

    @Test
    void debugAutoResponse_semRegra_retornaNaoEncontrado() throws Exception {
        when(autoResponseService.getResponseRule(any(), anyString(), any()))
                .thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/admin/debug-auto-response").param("message", "teste"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(false))
                .andExpect(jsonPath("$.response").value("Nenhuma regra encontrada"));
    }

    @Test
    void debugAutoResponse_semMessage_retornaBadRequest() throws Exception {
        mockMvc.perform(get("/admin/debug-auto-response")).andExpect(status().isBadRequest());
    }

    @Test
    void listAutoResponseRules_deveRetornarRegras() throws Exception {
        when(autoResponseService.getRulesCount()).thenReturn(5);
        when(autoResponseService.getRulesSummary()).thenReturn(Map.of());

        mockMvc.perform(get("/admin/auto-response-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRules").value(5));
    }
}
