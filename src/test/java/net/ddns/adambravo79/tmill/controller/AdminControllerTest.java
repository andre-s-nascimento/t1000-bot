package net.ddns.adambravo79.tmill.controller;

import static net.ddns.adambravo79.tmill.constant.BotMessages.WORLD_CUP_NOT_AVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import net.ddns.adambravo79.tmill.dto.MigrationResult;
import net.ddns.adambravo79.tmill.model.AutoResponseOverride;
import net.ddns.adambravo79.tmill.repository.BirthdayRepository;
import net.ddns.adambravo79.tmill.repository.ReleaseNotifiedRepository;
import net.ddns.adambravo79.tmill.service.*;
import net.ddns.adambravo79.tmill.service.cache.FileTranscriptionCacheService;
import net.ddns.adambravo79.tmill.service.feature.FeatureFlagAdminService;
import net.ddns.adambravo79.tmill.service.feature.FeatureFlagState;
import net.ddns.adambravo79.tmill.service.prompt.PromptRegistryService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import tools.jackson.databind.ObjectMapper;

class AdminControllerTest {

    private AdminController adminController;
    private ObjectMapper objectMapperMock;
    private Resource emptyResource;
    private MockMvc mockMvc;

    private static final long SHOWCASE_CHAT_ID = -1003703557250L;

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
    @Mock private PromptRegistryService promptRegistryService;

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
                        featureFlagAdminService,
                        promptRegistryService);

        ReflectionTestUtils.setField(adminController, "worldcupEnabled", true);
        ReflectionTestUtils.setField(adminController, "migrationSqlitePath", "./data/t1000.db");

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
    @DisplayName("POST /admin/reload-prompts - Deve recarregar os prompts e retornar HTTP 200 OK")
    void shouldReloadPromptsAndReturn200() throws Exception {
        doNothing().when(promptRegistryService).reload();

        mockMvc.perform(post("/admin/reload-prompts"))
                .andExpect(status().isOk())
                .andExpect(content().string("Prompts e personas recarregados com sucesso"));

        verify(promptRegistryService).reload();
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
                                                        + " -1003703557250")));

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
                                                        + " -1003703557250")));

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
                                                        + " -1003703557250")));

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
                                                        + " -1003703557250")));

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

    // ========================= FEATURE FLAGS =========================

    @Test
    void listFeatures_deveRetornarLista() throws Exception {
        FeatureFlagState state = new FeatureFlagState("a.enabled", true, "Desc", false);
        when(featureFlagAdminService.list()).thenReturn(List.of(state));

        mockMvc.perform(get("/admin/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("a.enabled"))
                .andExpect(jsonPath("$[0].enabled").value(true))
                .andExpect(jsonPath("$[0].description").value("Desc"))
                .andExpect(jsonPath("$[0].readOnly").value(false));
    }

    @Test
    void listFeatures_deveRetornarListaVazia() throws Exception {
        when(featureFlagAdminService.list()).thenReturn(List.of());

        mockMvc.perform(get("/admin/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void toggleFeature_deveAlterarERetornarChangedTrue() throws Exception {
        when(featureFlagAdminService.isEnabled("a.enabled")).thenReturn(false, true);
        doNothing().when(featureFlagAdminService).toggle("a.enabled", true);

        mockMvc.perform(post("/admin/features/a.enabled").param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("a.enabled"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.changed").value(true));

        verify(featureFlagAdminService).toggle("a.enabled", true);
    }

    @Test
    void toggleFeature_valorIgual_retornaChangedFalse() throws Exception {
        when(featureFlagAdminService.isEnabled("a.enabled")).thenReturn(true);
        doNothing().when(featureFlagAdminService).toggle("a.enabled", true);

        mockMvc.perform(post("/admin/features/a.enabled").param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(false));
    }

    @Test
    void toggleFeature_flagDesconhecida_retornaBadRequest() throws Exception {
        doThrow(new IllegalArgumentException("Flag desconhecida: foo.bar"))
                .when(featureFlagAdminService)
                .toggle("foo.bar", true);

        mockMvc.perform(post("/admin/features/foo.bar").param("enabled", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Flag desconhecida: foo.bar"));
    }

    @Test
    void toggleFeature_flagReadOnly_retornaConflict() throws Exception {
        doThrow(new IllegalStateException("Flag 'migration.enabled' é read-only"))
                .when(featureFlagAdminService)
                .toggle("migration.enabled", true);

        mockMvc.perform(post("/admin/features/migration.enabled").param("enabled", "true"))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.erro")
                                .value(org.hamcrest.Matchers.containsString("read-only")));
    }

    @Test
    void toggleFeature_erroInesperado_retornaInternalServerError() throws Exception {
        doThrow(new RuntimeException("boom"))
                .when(featureFlagAdminService)
                .toggle("a.enabled", true);

        mockMvc.perform(post("/admin/features/a.enabled").param("enabled", "true"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.erro").exists());
    }

    // ========================= BIRTHDAYS =========================

    @Test
    void listBirthdays_deveRetornarLista() throws Exception {
        when(birthdayRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/birthdays"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void birthdaysCount_deveRetornarTotal() throws Exception {
        when(birthdayRepository.count()).thenReturn(5);

        mockMvc.perform(get("/admin/birthdays/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5));
    }

    @Test
    void testBirthday_comDiaMesValidos_deveEnviar() throws Exception {
        when(birthdayService.enviarParabensPara(5, 10)).thenReturn(3);

        mockMvc.perform(post("/admin/birthdays/test/5/10"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Enviados: 3")));

        verify(birthdayService).enviarParabensPara(5, 10);
    }

    @Test
    void testBirthday_comDiaInvalido_retornaBadRequest() throws Exception {
        mockMvc.perform(post("/admin/birthdays/test/0/5"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        content()
                                .string(org.hamcrest.Matchers.containsString("Dia/mês inválidos")));

        verifyNoInteractions(birthdayService);
    }

    @Test
    void testBirthday_comMesInvalido_retornaBadRequest() throws Exception {
        mockMvc.perform(post("/admin/birthdays/test/5/13")).andExpect(status().isBadRequest());

        verifyNoInteractions(birthdayService);
    }

    @Test
    void testBirthdayToday_deveEnviar() throws Exception {
        doNothing().when(birthdayService).enviarParabensDoDia();

        mockMvc.perform(post("/admin/birthdays/test-today"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hoje")));

        verify(birthdayService).enviarParabensDoDia();
    }

    @Test
    void deleteBirthday_quandoExiste_retornaOk() throws Exception {
        when(birthdayRepository.deleteByUserId(42L)).thenReturn(1);

        mockMvc.perform(delete("/admin/birthdays/42"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("removido")));
    }

    @Test
    void deleteBirthday_quandoNaoExiste_retornaNotFound() throws Exception {
        when(birthdayRepository.deleteByUserId(99L)).thenReturn(0);

        mockMvc.perform(delete("/admin/birthdays/99")).andExpect(status().isNotFound());
    }

    @Test
    void clearBirthdays_deveRetornarNumeroDeletado() throws Exception {
        when(birthdayRepository.deleteAll()).thenReturn(7);

        mockMvc.perform(post("/admin/birthdays/clear"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("7 aniversário")));
    }

    // ========================= DEBUG CACHE =========================

    @Test
    void debugCache_quandoEncontrado_retornaDetalhes() throws Exception {
        var entry =
                new net.ddns.adambravo79.tmill.model.TranscriptionCacheEntry(
                        "texto bruto", "texto refinado", System.currentTimeMillis());
        when(fileTranscriptionCacheService.get("file-id")).thenReturn(entry);

        mockMvc.perform(get("/admin/debug/cache/file-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value("file-id"))
                .andExpect(jsonPath("$.brutoLength").value("texto bruto".length()))
                .andExpect(jsonPath("$.refinadoLength").value("texto refinado".length()))
                .andExpect(jsonPath("$.brutoVazio").value(false))
                .andExpect(jsonPath("$.refinadoVazio").value(false));
    }

    @Test
    void debugCache_quandoNaoEncontrado_retornaNotFound() throws Exception {
        when(fileTranscriptionCacheService.get("nao-existe")).thenReturn(null);

        mockMvc.perform(get("/admin/debug/cache/nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    void debugCache_comTextoVazio_retornaFlagsTrue() throws Exception {
        var entry =
                new net.ddns.adambravo79.tmill.model.TranscriptionCacheEntry(
                        "", null, System.currentTimeMillis());
        when(fileTranscriptionCacheService.get("vazio")).thenReturn(entry);

        mockMvc.perform(get("/admin/debug/cache/vazio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brutoVazio").value(true))
                .andExpect(jsonPath("$.refinadoVazio").value(true));
    }

    // ========================= PODCAST =========================

    @Test
    void testPodcast_days_deveAceitar() throws Exception {
        doNothing().when(podcastPublisherService).generateAndSendPodcast(any(), any(), anyLong());

        mockMvc.perform(get("/admin/test-podcast-days").param("days", "3"))
                .andExpect(status().isAccepted());
    }

    @Test
    void testPodcast_semParametros_usaSemanaPassada() throws Exception {
        doNothing().when(podcastPublisherService).generateAndSendPodcast(any(), any(), anyLong());

        mockMvc.perform(get("/admin/test-podcast")).andExpect(status().isAccepted());
    }

    @Test
    void testPodcast_daysInvalido_retornaBadRequest() throws Exception {
        mockMvc.perform(get("/admin/test-podcast-days").param("days", "0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/admin/test-podcast-days").param("days", "31"))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 🧪 ONDA 3c — Cobertura do AdminController (REST)
    // =========================================================================

    // -------------------------------------------------------------------------
    // falaT1000
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("falaT1000 com chatId explícito envia mensagem HTML")
    void falaT1000_chatIdExplicito() throws Exception {
        doNothing().when(telegramFacade).enviarMensagemHtml(anyLong(), anyString());

        mockMvc.perform(
                        post("/admin/fala-t1000")
                                .param("message", "olá mundo")
                                .param("chatId", "999")
                                .param("parseMode", "HTML"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("999")));

        verify(telegramFacade).enviarMensagemHtml(eq(999L), eq("olá mundo"));
    }

    @Test
    @DisplayName("falaT1000 sem chatId usa ownerId")
    void falaT1000_usaOwnerId() throws Exception {
        ReflectionTestUtils.setField(adminController, "ownerId", 42L);
        doNothing().when(telegramFacade).enviarMensagemHtml(anyLong(), anyString());

        mockMvc.perform(post("/admin/fala-t1000").param("message", "oi"))
                .andExpect(status().isOk());

        verify(telegramFacade).enviarMensagemHtml(eq(42L), eq("oi"));
    }

    @Test
    @DisplayName("falaT1000 com parseMode TEXT usa enviarMensagem")
    void falaT1000_parseModeTexto() throws Exception {
        doNothing().when(telegramFacade).enviarMensagem(anyLong(), anyString());

        mockMvc.perform(
                        post("/admin/fala-t1000")
                                .param("message", "texto")
                                .param("chatId", "100")
                                .param("parseMode", "TEXT"))
                .andExpect(status().isOk());

        verify(telegramFacade).enviarMensagem(eq(100L), eq("texto"));
        verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
    }

    @Test
    @DisplayName("falaT1000 sem mensagem retorna 400")
    void falaT1000_semMensagem() throws Exception {
        mockMvc.perform(post("/admin/fala-t1000").param("message", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("falaT1000 sem chatId e ownerId 0 retorna 400")
    void falaT1000_semChatIdNemOwner() throws Exception {
        ReflectionTestUtils.setField(adminController, "ownerId", 0L);
        // Força digestChatIds vazio
        ReflectionTestUtils.setField(adminController, "digestChatIds", java.util.Set.of());

        mockMvc.perform(post("/admin/fala-t1000").param("message", "oi"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.containsString(
                                                "Nenhum chatId informado")));
    }

    @Test
    @DisplayName("falaT1000 com Exception genérica retorna 500")
    void falaT1000_excecao() throws Exception {
        doThrow(new RuntimeException("boom"))
                .when(telegramFacade)
                .enviarMensagemHtml(anyLong(), anyString());

        mockMvc.perform(post("/admin/fala-t1000").param("message", "oi").param("chatId", "100"))
                .andExpect(status().isInternalServerError());
    }

    // -------------------------------------------------------------------------
    // testAzureTts (GET /admin/test-azure-tts)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("testAzureTts com publishChatId 0 retorna 400")
    void testAzureTts_publishChatIdZero() throws Exception {
        ReflectionTestUtils.setField(adminController, "publishChatId", 0L);

        mockMvc.perform(get("/admin/test-azure-tts"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.containsString(
                                                "podcast.publish.chat-id não configurado")));
    }

    @Test
    @DisplayName("testAzureTts com áudio vazio retorna 500")
    void testAzureTts_audioVazio() throws Exception {
        ReflectionTestUtils.setField(adminController, "publishChatId", -200L);
        when(azureTtsClient.synthesizeFullText(anyString())).thenReturn(new byte[0]);

        mockMvc.perform(get("/admin/test-azure-tts"))
                .andExpect(status().isInternalServerError())
                .andExpect(
                        content().string(org.hamcrest.Matchers.containsString("Falha na síntese")));
    }

    @Test
    @DisplayName("testAzureTts com sucesso envia mídia")
    void testAzureTts_sucesso() throws Exception {
        ReflectionTestUtils.setField(adminController, "publishChatId", -200L);
        when(azureTtsClient.synthesizeFullText(anyString())).thenReturn(new byte[] {1, 2, 3});

        var temp = java.nio.file.Files.createTempFile("test_azure_", ".mp3");
        when(tempDirService.createTempFile(anyString(), anyString())).thenReturn(temp);
        doNothing().when(telegramFacade).enviarMidia(anyLong(), anyString(), anyString());

        mockMvc.perform(get("/admin/test-azure-tts"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("-200")));

        verify(telegramFacade).enviarMidia(eq(-200L), anyString(), eq("Teste Azure TTS"));
    }

    // -------------------------------------------------------------------------
    // falaT1000Tts
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("falaT1000Tts com sucesso envia áudio")
    void falaT1000Tts_sucesso() throws Exception {
        ReflectionTestUtils.setField(adminController, "ownerId", 42L);
        when(azureTtsClient.synthesizeFullText(anyString())).thenReturn(new byte[] {1, 2, 3});
        var temp = java.nio.file.Files.createTempFile("tts_", ".mp3");
        when(tempDirService.createTempFile(anyString(), anyString())).thenReturn(temp);
        doNothing().when(telegramFacade).enviarMidia(anyLong(), anyString(), anyString());

        mockMvc.perform(
                        post("/admin/fala-t1000-tts")
                                .param("message", "olá")
                                .param("chatId", "100"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("sucesso")));

        verify(telegramFacade)
                .enviarMidia(eq(100L), anyString(), eq("🔊 Áudios para a futura Skynet"));
    }

    @Test
    @DisplayName("falaT1000Tts sem mensagem retorna 400")
    void falaT1000Tts_semMensagem() throws Exception {
        mockMvc.perform(post("/admin/fala-t1000-tts").param("message", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("falaT1000Tts sem chatId e ownerId 0 retorna 400")
    void falaT1000Tts_semChatId() throws Exception {
        ReflectionTestUtils.setField(adminController, "ownerId", 0L);
        ReflectionTestUtils.setField(adminController, "digestChatIds", java.util.Set.of());

        mockMvc.perform(post("/admin/fala-t1000-tts").param("message", "oi"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("falaT1000Tts com áudio vazio retorna 500")
    void falaT1000Tts_audioVazio() throws Exception {
        when(azureTtsClient.synthesizeFullText(anyString())).thenReturn(new byte[0]);

        mockMvc.perform(post("/admin/fala-t1000-tts").param("message", "oi").param("chatId", "100"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("falaT1000Tts com Exception na síntese retorna 500")
    void falaT1000Tts_excecao() throws Exception {
        when(azureTtsClient.synthesizeFullText(anyString()))
                .thenThrow(new RuntimeException("Azure down"));

        mockMvc.perform(post("/admin/fala-t1000-tts").param("message", "oi").param("chatId", "100"))
                .andExpect(status().isInternalServerError());
    }

    // -------------------------------------------------------------------------
    // testPodcast
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("testPodcast com start e end válidos retorna 202")
    void testPodcast_datasValidas() throws Exception {
        mockMvc.perform(
                        get("/admin/test-podcast")
                                .param("chatId", "999")
                                .param("start", "2026-09-01")
                                .param("end", "2026-09-07"))
                .andExpect(status().isAccepted());

        // Aguarda o CompletableFuture.runAsync executar (até 2s)
        org.awaitility.Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(2))
                .untilAsserted(
                        () ->
                                verify(podcastPublisherService, org.mockito.Mockito.atLeastOnce())
                                        .generateAndSendPodcast(any(), any(), eq(999L)));
    }

    @Test
    @DisplayName("testPodcast com periodo válido retorna 202")
    void testPodcast_comPeriodo() throws Exception {
        mockMvc.perform(get("/admin/test-podcast").param("chatId", "999").param("periodo", "3"))
                .andExpect(status().isAccepted());

        org.awaitility.Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(2))
                .untilAsserted(
                        () ->
                                verify(podcastPublisherService, org.mockito.Mockito.atLeastOnce())
                                        .generateAndSendPodcast(any(), any(), eq(999L)));
    }

    @Test
    @DisplayName("testPodcast sem parâmetros usa última semana completa")
    void testPodcast_semParametros() throws Exception {
        mockMvc.perform(get("/admin/test-podcast").param("chatId", "999"))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("testPodcast com data inválida retorna 400")
    void testPodcast_dataInvalida() throws Exception {
        mockMvc.perform(
                        get("/admin/test-podcast")
                                .param("chatId", "999")
                                .param("start", "invalido")
                                .param("end", "2026-09-07"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        content().string(org.hamcrest.Matchers.containsString("Formato de data")));
    }

    @Test
    @DisplayName("testPodcast com start > end retorna 400")
    void testPodcast_startDepoisDeEnd() throws Exception {
        mockMvc.perform(
                        get("/admin/test-podcast")
                                .param("chatId", "999")
                                .param("start", "2026-09-10")
                                .param("end", "2026-09-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("testPodcast sem chatId usa SHOWCASE")
    void testPodcast_semChatId_usaShowcase() throws Exception {
        mockMvc.perform(get("/admin/test-podcast")).andExpect(status().isAccepted());
    }

    // -------------------------------------------------------------------------
    // testPodcastLatest
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("testPodcastLatest retorna 202")
    void testPodcastLatest_sucesso() throws Exception {
        mockMvc.perform(get("/admin/test-podcast-latest").param("chatId", "999"))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("testPodcastLatest sem chatId retorna 202")
    void testPodcastLatest_semChatId() throws Exception {
        mockMvc.perform(get("/admin/test-podcast-latest")).andExpect(status().isAccepted());
    }

    // -------------------------------------------------------------------------
    // migrateFromSqlite (POST)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("migrateFromSqlite com SUCCESS retorna 200")
    void migrateFromSqlite_sucesso() throws Exception {
        MigrationResult result =
                MigrationResult.success(
                        java.time.Instant.now(), java.time.Instant.now(), Map.of("messages", 10));
        when(migrationService.migrateAll(false)).thenReturn(result);

        mockMvc.perform(post("/admin/migrate-sqlite").param("dryRun", "false"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("migrateFromSqlite com PARTIAL retorna 207")
    void migrateFromSqlite_partial() throws Exception {
        MigrationResult result =
                MigrationResult.partial(
                        java.time.Instant.now(),
                        java.time.Instant.now(),
                        Map.of("messages", 5),
                        java.util.List.of("Erro 1", "Erro 2"));
        when(migrationService.migrateAll(false)).thenReturn(result);

        mockMvc.perform(post("/admin/migrate-sqlite").param("dryRun", "false"))
                .andExpect(status().isMultiStatus());
    }

    @Test
    @DisplayName("migrateFromSqlite com FAILED retorna 500")
    void migrateFromSqlite_failed() throws Exception {
        MigrationResult result =
                MigrationResult.failed(
                        java.time.Instant.now(),
                        java.time.Instant.now(),
                        java.util.List.of("Erro crítico"));
        when(migrationService.migrateAll(false)).thenReturn(result);

        mockMvc.perform(post("/admin/migrate-sqlite").param("dryRun", "false"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("migrateFromSqlite com IllegalStateException retorna 412")
    void migrateFromSqlite_illegalState() throws Exception {
        when(migrationService.migrateAll(true))
                .thenThrow(new IllegalStateException("Migração desabilitada"));

        mockMvc.perform(post("/admin/migrate-sqlite").param("dryRun", "true"))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    @DisplayName("migrateFromSqlite com Exception genérica retorna 500")
    void migrateFromSqlite_excecaoGenerica() throws Exception {
        when(migrationService.migrateAll(false)).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(post("/admin/migrate-sqlite").param("dryRun", "false"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("migrateFromSqlite com dryRun default false")
    void migrateFromSqlite_defaultDryRun() throws Exception {
        MigrationResult result =
                MigrationResult.success(java.time.Instant.now(), java.time.Instant.now(), Map.of());
        when(migrationService.migrateAll(false)).thenReturn(result);

        mockMvc.perform(post("/admin/migrate-sqlite")).andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // previewMigration (GET)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("previewMigration retorna contadores e total")
    void previewMigration_sucesso() throws Exception {
        when(migrationService.previewCounts()).thenReturn(Map.of("messages", 5, "transcripts", 3));

        mockMvc.perform(get("/admin/migrate-sqlite/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arquivo").value("./data/t1000.db"))
                .andExpect(jsonPath("$.contadores.messages").value(5))
                .andExpect(jsonPath("$.total").value(8));
    }

    @Test
    @DisplayName("previewMigration com IllegalStateException retorna 412")
    void previewMigration_illegalState() throws Exception {
        when(migrationService.previewCounts())
                .thenThrow(new IllegalStateException("Arquivo não encontrado"));

        mockMvc.perform(get("/admin/migrate-sqlite/preview"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.erro").value("Arquivo não encontrado"));
    }

    // -------------------------------------------------------------------------
    // initChatIds (helpers privados via reflexão)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("initChatIds com string em branco não adiciona nada")
    void initChatIds_stringVazia() throws Exception {
        ReflectionTestUtils.setField(adminController, "digestChatIds", new java.util.HashSet<>());
        ReflectionTestUtils.setField(adminController, "digestChatIdsStr", "");

        adminController.initChatIds();

        @SuppressWarnings("unchecked")
        var ids =
                (java.util.Set<Long>)
                        ReflectionTestUtils.getField(adminController, "digestChatIds");
        assertThat(ids).isEmpty();
    }

    @Test
    @DisplayName("initChatIds com string null não adiciona nada")
    void initChatIds_stringNull() throws Exception {
        ReflectionTestUtils.setField(adminController, "digestChatIds", new java.util.HashSet<>());
        ReflectionTestUtils.setField(adminController, "digestChatIdsStr", null);

        adminController.initChatIds();

        @SuppressWarnings("unchecked")
        var ids =
                (java.util.Set<Long>)
                        ReflectionTestUtils.getField(adminController, "digestChatIds");
        assertThat(ids).isEmpty();
    }

    @Test
    @DisplayName("initChatIds com IDs mistos ignora os inválidos")
    void initChatIds_idsMistos() {
        ReflectionTestUtils.setField(adminController, "digestChatIds", new java.util.HashSet<>());
        ReflectionTestUtils.setField(adminController, "digestChatIdsStr", "-100,abc,-200");

        adminController.initChatIds();

        @SuppressWarnings("unchecked")
        var ids =
                (java.util.Set<Long>)
                        ReflectionTestUtils.getField(adminController, "digestChatIds");
        assertThat(ids).containsExactlyInAnyOrder(-100L, -200L);
    }

    // -------------------------------------------------------------------------
    // Branches parciais — getConfigFiles
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getConfigFiles com IOException retorna 'Arquivo não encontrado'")
    void getConfigFiles_ioException() throws Exception {
        when(resourceLoader.getResource(anyString()))
                .thenAnswer(
                        invocation -> {
                            var resource = mock(org.springframework.core.io.Resource.class);
                            when(resource.exists()).thenReturn(false);
                            return resource;
                        });

        mockMvc.perform(get("/admin/config-files"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$['easter-eggs.json']")
                                .value(
                                        org.hamcrest.Matchers.containsString(
                                                "Arquivo não encontrado")));
    }

    // -------------------------------------------------------------------------
    // Branches parciais — testWorldCup*Showcase (chatId == null)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("testWorldCupNoonShowcase sem chatId usa SHOWCASE")
    void testWorldCupNoonShowcase_semChatId() throws Exception {
        doNothing().when(worldCupSchedulerService).sendNoonMatchesToChat(anyLong());

        mockMvc.perform(post("/admin/test-worldcup-noon-showcase"))
                .andExpect(status().isOk())
                .andExpect(
                        content().string(org.hamcrest.Matchers.containsString("-1003703557250")));

        verify(worldCupSchedulerService).sendNoonMatchesToChat(-1003703557250L);
    }

    @Test
    @DisplayName("testWorldCupEveningShowcase sem chatId usa SHOWCASE")
    void testWorldCupEveningShowcase_semChatId() throws Exception {
        doNothing().when(worldCupSchedulerService).sendEveningMatchesToChat(anyLong());

        mockMvc.perform(post("/admin/test-worldcup-evening-showcase"))
                .andExpect(status().isOk())
                .andExpect(
                        content().string(org.hamcrest.Matchers.containsString("-1003703557250")));

        verify(worldCupSchedulerService).sendEveningMatchesToChat(-1003703557250L);
    }

    @Test
    @DisplayName("testWorldCupResultsShowcase sem chatId usa SHOWCASE")
    void testWorldCupResultsShowcase_semChatId() throws Exception {
        doNothing()
                .when(worldCupSchedulerService)
                .sendResultsToChat(eq(-1003703557250L), any(LocalDate.class));

        mockMvc.perform(post("/admin/test-worldcup-results-showcase").param("dateParam", "hoje"))
                .andExpect(status().isOk());

        verify(worldCupSchedulerService)
                .sendResultsToChat(eq(-1003703557250L), any(LocalDate.class));
    }

    // -------------------------------------------------------------------------
    // testAutoResponse — branches parciais
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("testAutoResponse com animation inválida envia apenas texto")
    void testAutoResponse_animationInvalida() throws Exception {
        AutoResponseOverride response = new AutoResponseOverride("Resposta", "nao-e-url-valida");
        when(autoResponseService.getResponseRule(any(), anyString(), any()))
                .thenReturn(java.util.Optional.of(response));

        mockMvc.perform(
                        post("/admin/test-auto-response")
                                .param("message", "teste")
                                .param("chatId", "999"))
                .andExpect(status().isOk());

        verify(telegramFacade, never()).enviarMidia(anyLong(), anyString(), anyString());
        verify(telegramFacade).enviarMensagemHtml(anyLong(), anyString());
    }

    // -------------------------------------------------------------------------
    // debugAutoResponse — branch com regra encontrada
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("debugAutoResponse com regra encontrada retorna response + animation")
    void debugAutoResponse_encontrada() throws Exception {
        AutoResponseOverride response =
                new AutoResponseOverride("Resposta", "https://exemplo.com/anim.gif");
        when(autoResponseService.getResponseRule(any(), anyString(), any()))
                .thenReturn(java.util.Optional.of(response));

        mockMvc.perform(get("/admin/debug-auto-response").param("message", "teste"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.response").value("Resposta"))
                .andExpect(jsonPath("$.animation").value("https://exemplo.com/anim.gif"));
    }
}
