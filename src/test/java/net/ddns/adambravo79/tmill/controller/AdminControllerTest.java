package net.ddns.adambravo79.tmill.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.dao.DataAccessException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import lombok.SneakyThrows;
import net.ddns.adambravo79.tmill.model.AutoResponseOverride;
import net.ddns.adambravo79.tmill.repository.ReleaseNotifiedRepository;
import net.ddns.adambravo79.tmill.service.*;
import net.ddns.adambravo79.tmill.service.cache.FileTranscriptionCacheService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import tools.jackson.databind.ObjectMapper;

class AdminControllerTest {

    private MockMvc mockMvc;

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

    private AdminController adminController;

    private static final long SHOWCASE_CHAT_ID = -5283244164L;

    @BeforeEach
    @SneakyThrows
    void setup() {
        MockitoAnnotations.openMocks(this);

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
                        new ObjectMapper(),
                        dailyReleasesService,
                        releaseNotifiedRepository);

        ReflectionTestUtils.setField(adminController, "worldcupEnabled", true);

        when(resourceLoader.getResource(anyString()))
                .thenAnswer(
                        invocation -> {
                            String path = invocation.getArgument(0);
                            String fileName = path.replace("classpath:", "");
                            Resource res;
                            switch (fileName) {
                                case "easter-eggs.json":
                                    res = new ClassPathResource("easter-eggs-test.json");
                                    break;
                                case "auto-responses.json":
                                    res = new ClassPathResource("auto-responses-test.json");
                                    break;
                                case "worldcup2026.json":
                                    res = new ClassPathResource("worldcup2026-test.json");
                                    break;
                                default:
                                    res = mock(Resource.class);
                                    when(res.exists()).thenReturn(false);
                            }
                            return res;
                        });

        this.mockMvc = MockMvcBuilders.standaloneSetup(adminController).build();
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
                        new org.springframework.web.client.HttpClientErrorException(
                                org.springframework.http.HttpStatus.valueOf(403)))
                .when(telegramFacade)
                .enviarMensagemHtml(eq(SHOWCASE_CHAT_ID), anyString());

        mockMvc.perform(post("/admin/reload-worldcup-showcase"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dados da Copa recarregados")));

        verify(staticWorldCupService).reload();
        verify(telegramFacade).enviarMensagemHtml(eq(SHOWCASE_CHAT_ID), anyString());
    }

    @Test
    void testWorldCupResultsShowcase_comDataValida_deveEnviarResultados() throws Exception {
        doNothing()
                .when(worldCupSchedulerService)
                .sendResultsToChat(eq(SHOWCASE_CHAT_ID), any(LocalDate.class));

        mockMvc.perform(
                        post("/admin/test-worldcup-results-showcase")
                                .param("dateParam", "2026-05-07"))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        containsString(
                                                "Resultados enviados para o chat -5283244164")));

        verify(worldCupSchedulerService)
                .sendResultsToChat(eq(SHOWCASE_CHAT_ID), any(LocalDate.class));
    }

    @Test
    void testWorldCupResultsShowcase_comDataInvalida_deveRetornarBadRequest() throws Exception {
        mockMvc.perform(
                        post("/admin/test-worldcup-results-showcase")
                                .param("dateParam", "invalido"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Data invalida")));

        verifyNoInteractions(worldCupSchedulerService);
    }

    @Test
    void testWorldCupResultsShowcase_quandoWorldcupDisabled_deveRetornarMensagem()
            throws Exception {
        ReflectionTestUtils.setField(adminController, "worldcupEnabled", false);

        mockMvc.perform(
                        post("/admin/test-worldcup-results-showcase")
                                .param("dateParam", "2026-05-07"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Servico de Copa desativado")));

        verifyNoInteractions(worldCupSchedulerService);
    }

    // ========================= PROPERTIES =========================

    @Test
    void getProperties_deveRetornarPropriedadesMascaradas() throws Exception {
        // 1. Stub genérico primeiro: retorna vazio para qualquer propriedade não explicitamente
        // stubbed
        when(environment.getProperty(anyString())).thenReturn("");

        // 2. Stubs específicos sobrescrevem os genéricos para essas chaves
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
        // Stub genérico primeiro
        when(environment.getProperty(anyString())).thenReturn("");

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
        mockMvc.perform(get("/admin/config-files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*").isNotEmpty());
    }

    @Test
    void getConfigFiles_quandoArquivoNaoEncontrado_deveRetornarMensagemErro() throws Exception {
        // Força o ResourceLoader a retornar um Resource que não existe
        when(resourceLoader.getResource(anyString()))
                .thenAnswer(
                        invocation -> {
                            Resource res = mock(Resource.class);
                            when(res.exists()).thenReturn(false);
                            return res;
                        });

        mockMvc.perform(get("/admin/config-files"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$['easter-eggs.json']")
                                .value(containsString("Arquivo não encontrado")));
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
        // Não há verificação de conteúdo porque o corpo está vazio
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
        when(autoResponseService.getRulesSummary()).thenReturn(Map.of()); // retorna mapa vazio

        mockMvc.perform(get("/admin/auto-response-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRules").value(5));
    }
}
