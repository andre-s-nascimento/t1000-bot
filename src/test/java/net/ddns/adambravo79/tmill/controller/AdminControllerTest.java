package net.ddns.adambravo79.tmill.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.ddns.adambravo79.tmill.service.*;
import net.ddns.adambravo79.tmill.service.cache.FileTranscriptionCacheService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;

class AdminControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
    @Mock private Resource resource;

    @InjectMocks private AdminController adminController;

    private static final long SHOWCASE_CHAT_ID = -5283244164L;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(adminController, "worldcupEnabled", true);
        this.mockMvc = MockMvcBuilders.standaloneSetup(adminController).build();
    }

    // =========================
    // RECARREGAMENTO
    // =========================

    @Test
    void reloadAutoResponses_deveRecarregarERetornarOk() throws Exception {
        mockMvc.perform(post("/admin/reload-auto-responses"))
                .andExpect(status().isOk())
                .andExpect(content().string("Respostas automáticas recarregadas"));
        verify(autoResponseService).reload();
    }

    @Test
    void reloadEasterEggs_deveRecarregarERetornarOk() throws Exception {
        mockMvc.perform(post("/admin/reload-easter-eggs"))
                .andExpect(status().isOk())
                .andExpect(content().string("Easter eggs recarregados"));
        verify(easterEggService).reload();
    }

    @Test
    void reloadWorldCup_deveRecarregarERetornarOk() throws Exception {
        mockMvc.perform(post("/admin/reload-worldcup"))
                .andExpect(status().isOk())
                .andExpect(content().string("Dados da Copa recarregados do arquivo JSON"));
        verify(staticWorldCupService).reload();
    }

    // =========================
    // LEMBRETES
    // =========================

    @Test
    void testWeeklyReminder_deveDispararERetornarOk() throws Exception {
        mockMvc.perform(post("/admin/test-weekly-reminder"))
                .andExpect(status().isOk())
                .andExpect(content().string("Lembrete semanal disparado manualmente."));
        verify(weeklyReminderService).sendWednesdayReminder();
    }

    @Test
    void testWeeklyReminderShowcase_deveEnviarParaShowcase() throws Exception {
        mockMvc.perform(post("/admin/test-weekly-reminder-showcase"))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        containsString(
                                                "Lembrete semanal enviado para o grupo showcase")));
        verify(weeklyReminderService).sendReminderToChat(SHOWCASE_CHAT_ID);
    }

    // =========================
    // DIGEST
    // =========================

    @Test
    void testMorningDigest_deveDispararERetornarOk() throws Exception {
        mockMvc.perform(get("/admin/test-morning-digest"))
                .andExpect(status().isOk())
                .andExpect(content().string("Resumo da manhã disparado."));
        verify(dailyDigestService).generateMorningDigest();
    }

    @Test
    void testEveningDigest_deveDispararERetornarOk() throws Exception {
        mockMvc.perform(get("/admin/test-evening-digest"))
                .andExpect(status().isOk())
                .andExpect(content().string("Resumo da noite disparado."));
        verify(dailyDigestService).generateEveningDigest();
    }

    // =========================
    // CACHE STATS
    // =========================

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

    // =========================
    // CUSTOM DIGEST
    // =========================

    @Test
    void customDigest_comDatasValidas_deveGerarERetornarOk() throws Exception {
        mockMvc.perform(
                        get("/admin/custom-digest")
                                .param("start", "2026-05-07")
                                .param("end", "2026-05-08")
                                .param("chatId", "12345"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Resumo personalizado gerado")));
        verify(dailyDigestService)
                .generateDigestCustom(
                        any(LocalDateTime.class), any(LocalDateTime.class), eq(12345L));
    }

    @Test
    void customDigest_comDataInvalida_deveRetornarBadRequest() throws Exception {
        mockMvc.perform(
                        get("/admin/custom-digest")
                                .param("start", "invalid")
                                .param("end", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Formato inválido")));
        verifyNoInteractions(dailyDigestService);
    }

    // =========================
    // COPA
    // =========================

    @Test
    void testWorldCup_deveDispararTesteManual() throws Exception {
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
    void testWorldCupShowcase_deveDispararParaShowcase() throws Exception {
        mockMvc.perform(post("/admin/test-worldcup-showcase"))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        containsString(
                                                "Teste manual da Copa enviado para o showcase.")));
        verify(worldCupSchedulerService).sendManualTestToChat(SHOWCASE_CHAT_ID);
    }

    @Test
    void testWorldCupNoonShowcase_deveEnviarJogosMeioDiaParaShowcase() throws Exception {
        mockMvc.perform(post("/admin/test-worldcup-noon-showcase"))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        containsString(
                                                "Envio do meio-dia da Copa enviado para o"
                                                        + " showcase")));
        verify(worldCupSchedulerService).sendNoonMatchesToChat(SHOWCASE_CHAT_ID);
    }

    @Test
    void testWorldCupEveningShowcase_deveEnviarJogosNoiteParaShowcase() throws Exception {
        mockMvc.perform(post("/admin/test-worldcup-evening-showcase"))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        containsString(
                                                "Envio da noite da Copa enviado para o showcase")));
        verify(worldCupSchedulerService).sendEveningMatchesToChat(SHOWCASE_CHAT_ID);
    }

    @Test
    void reloadWorldCupShowcase_deveRecarregarDadosEEnviarMensagem() throws Exception {
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
    void testWorldCupResultsShowcase_comDataValida_deveEnviarResultados() throws Exception {
        mockMvc.perform(
                        post("/admin/test-worldcup-results-showcase")
                                .param("dateParam", "2026-05-07"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Resultados enviados para o showcase")));
        verify(worldCupSchedulerService)
                .sendResultsToChat(eq(SHOWCASE_CHAT_ID), any(LocalDate.class));
    }

    @Test
    void testWorldCupResultsShowcase_comDataInvalida_deveRetornarBadRequest() throws Exception {
        mockMvc.perform(
                        post("/admin/test-worldcup-results-showcase")
                                .param("dateParam", "invalido"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Data inválida")));
        verifyNoInteractions(worldCupSchedulerService);
    }

    // =========================
    // PROPERTIES E CONFIG FILES
    // =========================

    @Test
    void getProperties_deveRetornarPropriedadesMascaradas() throws Exception {
        // Mock do Environment
        when(environment.getProperty(anyString())).thenReturn("");
        when(environment.getProperty("spring.application.name")).thenReturn("tmill-bot");
        when(environment.getProperty("telegram.bot.token"))
                .thenReturn("1234567890:ABCdefGHIjklMNOpqrsTUVwxyz");

        mockMvc.perform(get("/admin/properties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['spring.application.name']").value("tmill-bot"))
                .andExpect(jsonPath("$['telegram.bot.token']").value("1234...wxyz"));
    }

    @Test
    void getConfigFiles_deveRetornarConteudoDosJSONs() throws Exception {
        mockMvc.perform(get("/admin/config-files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*").isNotEmpty());
    }
}
