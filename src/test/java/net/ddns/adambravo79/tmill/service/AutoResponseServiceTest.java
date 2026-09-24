package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import net.ddns.adambravo79.tmill.model.AutoResponseConfig;
import net.ddns.adambravo79.tmill.model.AutoResponseOverride;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AutoResponseServiceTest {

    @Mock private ResourceLoader resourceLoader;
    @Mock private Resource resource;
    @Mock private MetricsService metricsService; // 👈 NOVO

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AutoResponseService service;

    // ============================================================
    // JSON DE TESTE
    // ============================================================

    private static final String JSON_VALIDO =
            """
            {
              "rules": {
                "regra1": {
                  "triggers": ["bom dia", "boa tarde"],
                  "response": "Olá! Bom dia/tarde para você!",
                  "animation": "https://exemplo.com/gif.gif"
                },
                "regra2": {
                  "triggers": ["tchau"],
                  "response": "Até logo!",
                  "timeRange": { "start": "18:00", "end": "23:59" }
                },
                "regra3": {
                  "triggers": ["obrigado"],
                  "response": "De nada!",
                  "userResponse": {
                    "123": "Por nada, amigo!",
                    "456": "Disponha!"
                  },
                  "userAnimation": {
                    "123": "https://exemplo.com/amigo.gif"
                  }
                },
                "regra4": {
                  "triggers": ["oi", "olá"],
                  "response": "Oi! Como posso ajudar?"
                }
              }
            }
            """;

    private static final String JSON_COM_USER_OVERRIDES =
            """
            {
              "rules": {
                "nova": {
                  "triggers": ["teste"],
                  "response": "Resposta padrão",
                  "userOverrides": {
                    "999": {
                      "response": "Resposta especial",
                      "animation": "https://exemplo.com/especial.gif"
                    }
                  }
                },
                "sem_triggers": {
                  "triggers": null,
                  "response": "não deve aparecer"
                },
                "sem_response": {
                  "triggers": ["vazio"],
                  "response": null
                }
              }
            }
            """;

    private static final String JSON_COM_TIMERANGE_INCOMPLETO =
            """
            {
              "rules": {
                "regra_com_timerange_incompleto": {
                  "triggers": ["teste"],
                  "response": "resposta com start apenas",
                  "timeRange": { "start": "18:00" }
                }
              }
            }
            """;

    private static final String JSON_RULES_VAZIO = "{ \"rules\": {} }";

    @BeforeEach
    void setUp() {
        service = new AutoResponseService(resourceLoader, objectMapper, metricsService);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "configFile", "classpath:auto-responses-test.json");
        ReflectionTestUtils.setField(service, "oncePerDayTriggersRaw", "bom dia");
        ReflectionTestUtils.invokeMethod(service, "parseOncePerDayTriggers");
    }

    // ============================================================
    // CARREGAMENTO DE REGRAS
    // ============================================================

    @Test
    @DisplayName("loadResponses: config nula não falha nem registra métrica")
    void loadResponses_configNull_naoFalha() throws Exception {
        ObjectMapper mockMapper = mock(ObjectMapper.class);
        ReflectionTestUtils.setField(service, "objectMapper", mockMapper);

        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(mock(InputStream.class));
        when(mockMapper.readValue(any(InputStream.class), eq(AutoResponseConfig.class)))
                .thenReturn(null);

        assertThatCode(() -> service.loadResponses()).doesNotThrowAnyException();
        assertThat(service.getRulesCount()).isZero();
        verifyNoInteractions(metricsService);
    }

    @Test
    @DisplayName("loadResponses: rules vazio não carrega e não registra métrica")
    void loadResponses_rulesVazio() throws Exception {
        InputStream is = new ByteArrayInputStream(JSON_RULES_VAZIO.getBytes());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(is);

        service.loadResponses();
        assertThat(service.getRulesCount()).isZero();
        verifyNoInteractions(metricsService);
    }

    @Test
    @DisplayName("loadResponses: regra com triggers null é ignorada")
    void loadResponses_triggersNull_ignora() throws Exception {
        InputStream is = new ByteArrayInputStream(JSON_COM_USER_OVERRIDES.getBytes());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(is);

        service.loadResponses();

        assertThat(service.getRulesCount()).isEqualTo(1);
        assertThat(service.getResponseRule(1L, "teste")).isPresent();
        assertThat(service.getResponseRule(1L, "vazio")).isEmpty();
        assertThat(service.getResponseRule(1L, "sem_triggers")).isEmpty();
    }

    @Test
    @DisplayName("loadResponses: timeRange incompleto usa start/end null")
    void loadResponses_timeRangeIncompleto() throws Exception {
        InputStream is = new ByteArrayInputStream(JSON_COM_TIMERANGE_INCOMPLETO.getBytes());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(is);

        service.loadResponses();

        Optional<AutoResponseOverride> r = service.getResponseRule(1L, "teste");
        assertThat(r).isPresent();
        assertThat(r.get().response()).isEqualTo("resposta com start apenas");
    }

    @Test
    @DisplayName("loadResponses: rules null não falha")
    void loadResponses_rulesNull() throws Exception {
        ObjectMapper mockMapper = mock(ObjectMapper.class);
        ReflectionTestUtils.setField(service, "objectMapper", mockMapper);

        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(mock(InputStream.class));

        AutoResponseConfig config = mock(AutoResponseConfig.class);
        when(config.rules()).thenReturn(null);
        when(mockMapper.readValue(any(InputStream.class), eq(AutoResponseConfig.class)))
                .thenReturn(config);

        assertThatCode(() -> service.loadResponses()).doesNotThrowAnyException();
        assertThat(service.getRulesCount()).isZero();
    }

    @Test
    @DisplayName("loadResponses: arquivo inexistente é ignorado")
    void loadResponses_arquivoInexistente() {
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(false);

        service.loadResponses();
        assertThat(service.getResponseRule(1L, "bom dia")).isEmpty();
    }

    @Test
    @DisplayName("loadResponses: erro de leitura é capturado")
    void loadResponses_erroLeitura() throws Exception {
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenThrow(new RuntimeException("Erro"));

        service.loadResponses();
        assertThat(service.getResponseRule(1L, "bom dia")).isEmpty();
    }

    @Test
    @DisplayName("loadResponses: JSON válido carrega regras")
    void loadResponses_jsonValido() throws Exception {
        InputStream is = new ByteArrayInputStream(JSON_VALIDO.getBytes());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(is);

        service.loadResponses();

        Optional<AutoResponseOverride> r = service.getResponseRule(1L, "bom dia");
        assertThat(r).isPresent();
        assertThat(r.get().response()).isEqualTo("Olá! Bom dia/tarde para você!");
    }

    // ============================================================
    // MÉTRICAS — disparo com sucesso
    // ============================================================

    @Test
    @DisplayName("Trigger ativado: registra success('auto_response_disparada')")
    void triggerAtivado_registraMetricaDisparada() throws Exception {
        carregarRegras();

        Optional<AutoResponseOverride> r = service.getResponseRule(1L, "bom dia");

        assertThat(r).isPresent();
        verify(metricsService).success("auto_response_disparada");
        verify(metricsService, never()).success("auto_response_suprimida");
    }

    @Test
    @DisplayName("Trigger ativado com userOverride: registra métrica")
    void triggerComUserOverride_registraMetrica() throws Exception {
        carregarRegras();

        assertThat(service.getResponseRule(123L, "obrigado")).isPresent();
        assertThat(service.getResponseRule(123L, "obrigado")).isPresent();
        verify(metricsService, times(2)).success("auto_response_disparada");
    }

    @Test
    @DisplayName("Trigger com case-insensitive: registra métrica")
    void triggerCaseInsensitive_registraMetrica() throws Exception {
        carregarRegras();

        service.getResponseRule(1L, "BOM DIA");

        verify(metricsService).success("auto_response_disparada");
    }

    // ============================================================
    // MÉTRICAS — supressão
    // ============================================================

    @Test
    @DisplayName("Trigger suprimido no mesmo dia: registra success('auto_response_suprimida')")
    void triggerSuprimido_registraMetricaSuprimida() throws Exception {
        carregarRegras();

        // Primeira chamada: dispara
        assertThat(service.getResponseRule(1L, "bom dia")).isPresent();
        verify(metricsService, times(1)).success("auto_response_disparada");

        // Segunda chamada no mesmo dia: suprime
        assertThat(service.getResponseRule(1L, "bom dia")).isEmpty();

        verify(metricsService, times(1)).success("auto_response_suprimida");
        verify(metricsService, times(1)).success("auto_response_disparada");
    }

    @Test
    @DisplayName("Trigger suprimido não chama 'disparada' novamente")
    void triggerSuprimido_naoChamaDisparadaNovamente() throws Exception {
        carregarRegras();

        service.getResponseRule(1L, "bom dia");
        service.getResponseRule(1L, "bom dia");
        service.getResponseRule(1L, "bom dia");

        verify(metricsService, times(1)).success("auto_response_disparada");
        verify(metricsService, times(2)).success("auto_response_suprimida");
    }

    // ============================================================
    // MÉTRICAS — não dispara (nada acontece)
    // ============================================================

    @Test
    @DisplayName("Sem trigger: não registra nenhuma métrica de auto-response")
    void semTrigger_nenhumaMetrica() throws Exception {
        carregarRegras();

        Optional<AutoResponseOverride> r = service.getResponseRule(1L, "mensagem aleatória xyz");

        assertThat(r).isEmpty();
        verify(metricsService, never()).success("auto_response_disparada");
        verify(metricsService, never()).success("auto_response_suprimida");
    }

    @Test
    @DisplayName("Serviço desabilitado: não registra métrica")
    void desabilitado_nenhumaMetrica() throws Exception {
        ReflectionTestUtils.setField(service, "enabled", false);
        carregarRegras();

        assertThat(service.getResponseRule(1L, "bom dia")).isEmpty();
        verifyNoInteractions(metricsService);
    }

    @Test
    @DisplayName("Mensagem null: não registra métrica")
    void mensagemNull_nenhumaMetrica() throws Exception {
        carregarRegras();

        assertThat(service.getResponseRule(1L, null)).isEmpty();
        verifyNoInteractions(metricsService);
    }

    @Test
    @DisplayName("Mensagem vazia: não registra métrica")
    void mensagemVazia_nenhumaMetrica() throws Exception {
        carregarRegras();

        assertThat(service.getResponseRule(1L, "   ")).isEmpty();
        verifyNoInteractions(metricsService);
    }

    @Test
    @DisplayName("Trigger curto (<3 chars): não registra métrica")
    void triggerComMenosDe3Chars_nenhumaMetrica() throws Exception {
        carregarRegras();
        assertThat(service.getResponseRule(1L, "oi")).isEmpty();
        verifyNoInteractions(metricsService);
    }

    @Test
    @DisplayName("Fora do timeRange: não registra métrica")
    void foraTimeRange_nenhumaMetrica() throws Exception {
        carregarRegras();

        assertThat(service.getResponseRule(1L, "tchau", LocalTime.of(12, 0))).isEmpty();
        verifyNoInteractions(metricsService);
    }

    @Test
    @DisplayName("Dentro do timeRange: registra métrica")
    void dentroTimeRange_registraMetrica() throws Exception {
        carregarRegras();

        assertThat(service.getResponseRule(1L, "tchau", LocalTime.of(20, 0))).isPresent();
        verify(metricsService).success("auto_response_disparada");
    }

    // ============================================================
    // MÉTRICAS — múltiplos triggers
    // ============================================================

    @Test
    @DisplayName("Triggers diferentes do mesmo usuário: cada um registra métrica")
    void triggersDiferentes_registramCadaUm() throws Exception {
        carregarRegras();

        service.getResponseRule(1L, "bom dia");
        service.getResponseRule(1L, "obrigado");

        verify(metricsService, times(2)).success("auto_response_disparada");
        verify(metricsService, never()).success("auto_response_suprimida");
    }

    @Test
    @DisplayName("Usuários diferentes com mesmo trigger: cada um registra métrica")
    void usuariosDiferentesMesmoTrigger() throws Exception {
        carregarRegras();

        service.getResponseRule(1L, "bom dia");
        service.getResponseRule(2L, "bom dia");
        service.getResponseRule(3L, "bom dia");

        verify(metricsService, times(3)).success("auto_response_disparada");
        verify(metricsService, never()).success("auto_response_suprimida");
    }

    // ============================================================
    // MÉTODOS PRIVADOS (reflexão)
    // ============================================================

    @Test
    @DisplayName("containsExactWord: palavra exata → true")
    void containsExactWord_exata() {
        Boolean r =
                ReflectionTestUtils.invokeMethod(
                        service, "containsExactWord", "bom dia pessoal", "dia");
        assertThat(r).isTrue();
    }

    @Test
    @DisplayName("containsExactWord: substring → false")
    void containsExactWord_substring() {
        Boolean r = ReflectionTestUtils.invokeMethod(service, "containsExactWord", "bom dia", "di");
        assertThat(r).isFalse();
    }

    @Test
    @DisplayName("containsExactWord: case-insensitive → true")
    void containsExactWord_caseInsensitive() {
        Boolean r =
                ReflectionTestUtils.invokeMethod(service, "containsExactWord", "BOM DIA", "dia");
        assertThat(r).isTrue();
    }

    @Test
    @DisplayName("isTimeInRange: dentro do intervalo")
    void isTimeInRange_dentro() {
        Boolean r =
                ReflectionTestUtils.invokeMethod(
                        service,
                        "isTimeInRange",
                        LocalTime.of(20, 0),
                        LocalTime.of(18, 0),
                        LocalTime.of(23, 59));
        assertThat(r).isTrue();
    }

    @Test
    @DisplayName("isTimeInRange: fora do intervalo")
    void isTimeInRange_fora() {
        Boolean r =
                ReflectionTestUtils.invokeMethod(
                        service,
                        "isTimeInRange",
                        LocalTime.of(10, 0),
                        LocalTime.of(18, 0),
                        LocalTime.of(23, 59));
        assertThat(r).isFalse();
    }

    @Test
    @DisplayName("isTimeInRange: null sempre true")
    void isTimeInRange_null() {
        Boolean r =
                ReflectionTestUtils.invokeMethod(
                        service, "isTimeInRange", LocalTime.now(), null, null);
        assertThat(r).isTrue();
    }

    @Test
    @DisplayName("isTimeInRange: start igual end")
    void isTimeInRange_startIgualEnd() {
        LocalTime t = LocalTime.of(12, 0);
        Boolean r = ReflectionTestUtils.invokeMethod(service, "isTimeInRange", t, t, t);
        assertThat(r).isTrue();
    }

    @Test
    @DisplayName("isTimeInRange: intervalo que cruza meia-noite")
    void isTimeInRange_cruzaMeiaNoite() {
        LocalTime start = LocalTime.of(22, 0);
        LocalTime end = LocalTime.of(2, 0);

        assertThat(
                        (Boolean)
                                ReflectionTestUtils.invokeMethod(
                                        service, "isTimeInRange", LocalTime.of(23, 0), start, end))
                .isTrue();
        assertThat(
                        (Boolean)
                                ReflectionTestUtils.invokeMethod(
                                        service, "isTimeInRange", LocalTime.of(1, 0), start, end))
                .isTrue();
        assertThat(
                        (Boolean)
                                ReflectionTestUtils.invokeMethod(
                                        service, "isTimeInRange", LocalTime.of(12, 0), start, end))
                .isFalse();
    }

    // ============================================================
    // USER OVERRIDE
    // ============================================================

    @Test
    @DisplayName("userOverride aplica corretamente")
    void userOverride() throws Exception {
        carregarRegras();

        Optional<AutoResponseOverride> r1 = service.getResponseRule(123L, "obrigado");
        assertThat(r1).isPresent();
        assertThat(r1.get().response()).isEqualTo("Por nada, amigo!");
        assertThat(r1.get().animation()).isEqualTo("https://exemplo.com/amigo.gif");

        Optional<AutoResponseOverride> r2 = service.getResponseRule(456L, "obrigado");
        assertThat(r2).isPresent();
        assertThat(r2.get().response()).isEqualTo("Disponha!");

        Optional<AutoResponseOverride> r3 = service.getResponseRule(789L, "obrigado");
        assertThat(r3).isPresent();
        assertThat(r3.get().response()).isEqualTo("De nada!");
    }

    @Test
    @DisplayName("userOverrides novo formato funciona")
    void userOverridesNovoFormato() throws Exception {
        InputStream is = new ByteArrayInputStream(JSON_COM_USER_OVERRIDES.getBytes());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(is);
        service.loadResponses();

        Optional<AutoResponseOverride> r1 = service.getResponseRule(999L, "teste");
        assertThat(r1).isPresent();
        assertThat(r1.get().response()).isEqualTo("Resposta especial");
        assertThat(r1.get().animation()).isEqualTo("https://exemplo.com/especial.gif");

        Optional<AutoResponseOverride> r2 = service.getResponseRule(888L, "teste");
        assertThat(r2).isPresent();
        assertThat(r2.get().response()).isEqualTo("Resposta padrão");
    }

    @Test
    @DisplayName("userId null usa resposta default")
    void userIdNull() throws Exception {
        carregarRegras();

        Optional<AutoResponseOverride> r = service.getResponseRule(null, "obrigado");
        assertThat(r).isPresent();
        assertThat(r.get().response()).isEqualTo("De nada!");
    }

    // ============================================================
    // UTILITÁRIOS
    // ============================================================

    @Test
    @DisplayName("isEnabled: retorna valor configurado")
    void isEnabled_retornaValor() {
        ReflectionTestUtils.setField(service, "enabled", true);
        assertThat(service.isEnabled()).isTrue();
        ReflectionTestUtils.setField(service, "enabled", false);
        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("getRulesSummary: retorna resumo")
    void getRulesSummary() throws Exception {
        carregarRegras();

        Map<String, String> summary = service.getRulesSummary();

        assertThat(summary).isNotEmpty().containsKeys("bom dia", "tchau", "obrigado");
        summary.values().forEach(v -> assertThat(v).contains("response="));
    }

    @Test
    @DisplayName("reload: chama loadResponses")
    void reload_chamaLoad() {
        AutoResponseService spy = spy(service);
        spy.reload();
        verify(spy, times(1)).loadResponses();
    }

    // ============================================================
    // INICIALIZAÇÃO
    // ============================================================

    @Test
    @DisplayName("init: enabled=false não carrega")
    void init_enabledFalse() {
        ReflectionTestUtils.setField(service, "enabled", false);
        service.init();
        assertThat(service.getRulesCount()).isZero();
    }

    @Test
    @DisplayName("init: enabled=true carrega regras")
    void init_enabledTrue() throws Exception {
        InputStream is = new ByteArrayInputStream(JSON_VALIDO.getBytes());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(is);

        service.init();

        assertThat(service.getRulesCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Trigger fora da lista once-per-day responde sempre no mesmo dia")
    void triggerForaDaLista_respondeSempre() throws Exception {
        carregarRegras();

        assertThat(service.getResponseRule(1L, "obrigado")).isPresent();
        assertThat(service.getResponseRule(1L, "obrigado")).isPresent();
        assertThat(service.getResponseRule(1L, "obrigado")).isPresent();

        verify(metricsService, times(3)).success("auto_response_disparada");
        verify(metricsService, never()).success("auto_response_suprimida");
    }

    @Test
    @DisplayName("isOncePerDayTrigger: 'bom dia família' casa por prefixo + espaço")
    void isOncePerDayTrigger_prefixoComEspaco() {
        ReflectionTestUtils.setField(service, "oncePerDayTriggersRaw", "bom dia");
        ReflectionTestUtils.invokeMethod(service, "parseOncePerDayTriggers");

        Boolean exato = ReflectionTestUtils.invokeMethod(service, "isOncePerDayTrigger", "bom dia");
        Boolean comSufixo =
                ReflectionTestUtils.invokeMethod(service, "isOncePerDayTrigger", "bom dia família");
        Boolean falsoPositivo =
                ReflectionTestUtils.invokeMethod(service, "isOncePerDayTrigger", "bom diabo");

        assertThat(exato).isTrue();
        assertThat(comSufixo).isTrue();
        assertThat(falsoPositivo).isFalse();
    }

    @Test
    @DisplayName("recordResponse só grava para triggers once-per-day")
    void recordResponse_soGravaOncePerDay() throws Exception {
        carregarRegras();

        // "obrigado" NÃO é once-per-day → não deve gravar
        service.getResponseRule(1L, "obrigado");
        Map<Long, Map<String, LocalDate>> cooldown =
                (Map<Long, Map<String, LocalDate>>)
                        ReflectionTestUtils.getField(service, "userTriggerCooldown");
        assertThat(cooldown).isEmpty();

        // "bom dia" É once-per-day → deve gravar
        service.getResponseRule(1L, "bom dia");
        assertThat(cooldown).containsKey(1L);
        assertThat(cooldown.get(1L)).containsKey("bom dia");
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private void carregarRegras() throws Exception {
        InputStream is = new ByteArrayInputStream(JSON_VALIDO.getBytes());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(is);
        service.loadResponses();
    }
}
