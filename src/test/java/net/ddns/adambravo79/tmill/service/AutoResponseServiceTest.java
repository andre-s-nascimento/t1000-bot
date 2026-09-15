package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import net.ddns.adambravo79.tmill.model.AutoResponseConfig;
import net.ddns.adambravo79.tmill.model.AutoResponseOverride;
import net.ddns.adambravo79.tmill.model.AutoResponseRule;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AutoResponseServiceTest {

    @Mock private ResourceLoader resourceLoader;
    @Mock private Resource resource;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private AutoResponseService service;

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

    private static final String JSON_RULES_VAZIO =
            """
            { "rules": {} }
            """;

    @BeforeEach
    void setUp() {
        service = new AutoResponseService(resourceLoader, objectMapper);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "configFile", "classpath:auto-responses-test.json");
    }

    // ========================================================================
    // TESTES DE INICIALIZAÇÃO E CARREGAMENTO
    // ========================================================================

    @Test
    void init_quandoEnabledFalse_naoCarregaRegras() {
        ReflectionTestUtils.setField(service, "enabled", false);
        AutoResponseService spy = spy(service);
        spy.init();
        verify(spy, never()).loadResponses();
    }

    @Test
    void loadResponses_quandoConfigNull_naoFalha() throws Exception {
        ObjectMapper mockMapper = mock(ObjectMapper.class);
        ReflectionTestUtils.setField(service, "objectMapper", mockMapper);

        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(mock(InputStream.class));
        when(mockMapper.readValue(any(InputStream.class), eq(AutoResponseConfig.class)))
                .thenReturn(null);

        assertThatCode(() -> service.loadResponses()).doesNotThrowAnyException();
        assertThat(service.getRulesCount()).isZero();
    }

    @Test
    void loadResponses_quandoRulesVazio_naoCarregaNada() throws Exception {
        InputStream is = new ByteArrayInputStream(JSON_RULES_VAZIO.getBytes());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(is);

        service.loadResponses();
        assertThat(service.getRulesCount()).isZero();
    }

    @Test
    void loadResponses_quandoRegraComTriggersNulo_ignora() throws Exception {
        InputStream is = new ByteArrayInputStream(JSON_COM_USER_OVERRIDES.getBytes());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(is);

        service.loadResponses();

        assertThat(service.getRulesCount()).isEqualTo(1);

        Optional<AutoResponseOverride> resultado = service.getResponseRule(1L, "teste");
        assertThat(resultado)
                .isPresent()
                .get()
                .satisfies(
                        ov -> {
                            assertThat(ov.response()).isEqualTo("Resposta padrão");
                            assertThat(ov.animation()).isNull();
                        });

        assertThat(service.getResponseRule(1L, "vazio")).isEmpty();
        assertThat(service.getResponseRule(1L, "sem_triggers")).isEmpty();
    }

    @Test
    void loadResponses_comTimeRangeIncompleto_usaStartEEndNull() throws Exception {
        InputStream is = new ByteArrayInputStream(JSON_COM_TIMERANGE_INCOMPLETO.getBytes());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(is);

        service.loadResponses();

        Optional<AutoResponseOverride> resultado = service.getResponseRule(1L, "teste");
        assertThat(resultado)
                .isPresent()
                .get()
                .satisfies(ov -> assertThat(ov.response()).isEqualTo("resposta com start apenas"));
    }

    @Test
    void loadResponses_quandoRulesNull_naoFalha() throws Exception {
        ObjectMapper mockMapper = mock(ObjectMapper.class);
        ReflectionTestUtils.setField(service, "objectMapper", mockMapper);

        Resource mockResource = mock(Resource.class);
        when(resourceLoader.getResource(anyString())).thenReturn(mockResource);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.getInputStream()).thenReturn(mock(InputStream.class));

        AutoResponseConfig config = mock(AutoResponseConfig.class);
        when(config.rules()).thenReturn(null);
        when(mockMapper.readValue(any(InputStream.class), eq(AutoResponseConfig.class)))
                .thenReturn(config);

        assertThatCode(() -> service.loadResponses()).doesNotThrowAnyException();
        assertThat(service.getRulesCount()).isZero();
    }

    @Test
    void deveCarregarRegrasComSucesso() throws Exception {
        InputStream is = new ByteArrayInputStream(JSON_VALIDO.getBytes());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(is);

        service.loadResponses();

        Optional<AutoResponseOverride> resultado = service.getResponseRule(1L, "bom dia");
        assertThat(resultado)
                .isPresent()
                .get()
                .satisfies(
                        ov -> {
                            assertThat(ov.response()).isEqualTo("Olá! Bom dia/tarde para você!");
                            assertThat(ov.animation()).isEqualTo("https://exemplo.com/gif.gif");
                        });
    }

    @Test
    void deveIgnorarArquivoInexistente() {
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(false);

        service.loadResponses();

        Optional<AutoResponseOverride> resultado = service.getResponseRule(1L, "bom dia");
        assertThat(resultado).isEmpty();
    }

    @Test
    void deveTratarErroAoCarregarJson() throws Exception {
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenThrow(new RuntimeException("Erro de leitura"));

        service.loadResponses();

        Optional<AutoResponseOverride> resultado = service.getResponseRule(1L, "bom dia");
        assertThat(resultado).isEmpty();
    }

    // ========================================================================
    // TESTES DE MÉTODOS PRIVADOS (via Reflection)
    // ========================================================================

    @Test
    void containsExactWord_deveRetornarTrueParaPalavraExata() {
        Boolean result =
                ReflectionTestUtils.invokeMethod(
                        service, "containsExactWord", "bom dia pessoal", "dia");
        assertThat(result).isTrue();
    }

    @Test
    void containsExactWord_deveRetornarFalseParaSubstring() {
        Boolean result =
                ReflectionTestUtils.invokeMethod(service, "containsExactWord", "bom dia", "di");
        assertThat(result).isFalse();
    }

    @Test
    void containsExactWord_deveIgnorarCase() {
        Boolean result =
                ReflectionTestUtils.invokeMethod(service, "containsExactWord", "BOM DIA", "dia");
        assertThat(result).isTrue();
    }

    @Test
    void isTimeInRange_deveRetornarTrueDentroDoIntervalo() {
        LocalTime now = LocalTime.of(20, 0);
        LocalTime start = LocalTime.of(18, 0);
        LocalTime end = LocalTime.of(23, 59);
        Boolean result =
                ReflectionTestUtils.invokeMethod(service, "isTimeInRange", now, start, end);
        assertThat(result).isTrue();
    }

    @Test
    void isTimeInRange_deveRetornarFalseForaDoIntervalo() {
        LocalTime now = LocalTime.of(10, 0);
        LocalTime start = LocalTime.of(18, 0);
        LocalTime end = LocalTime.of(23, 59);
        Boolean result =
                ReflectionTestUtils.invokeMethod(service, "isTimeInRange", now, start, end);
        assertThat(result).isFalse();
    }

    @Test
    void isTimeInRange_deveRetornarTrueQuandoStartEndNull() {
        Boolean result =
                ReflectionTestUtils.invokeMethod(
                        service, "isTimeInRange", LocalTime.now(), null, null);
        assertThat(result).isTrue();
    }

    @Test
    void isTimeInRange_startIgualEnd_retornaTrueApenasNoExatoMomento() {
        LocalTime now = LocalTime.of(12, 0);
        Boolean result = ReflectionTestUtils.invokeMethod(service, "isTimeInRange", now, now, now);
        assertThat(result).isTrue();
    }

    @Test
    void isTimeInRange_deveFuncionarComIntervaloQueCruzaMeiaNoite() {
        LocalTime start = LocalTime.of(22, 0);
        LocalTime end = LocalTime.of(2, 0);

        Boolean result1 =
                ReflectionTestUtils.invokeMethod(
                        service, "isTimeInRange", LocalTime.of(23, 0), start, end);
        assertThat(result1).isTrue();

        Boolean result2 =
                ReflectionTestUtils.invokeMethod(
                        service, "isTimeInRange", LocalTime.of(1, 0), start, end);
        assertThat(result2).isTrue();

        Boolean result3 =
                ReflectionTestUtils.invokeMethod(
                        service, "isTimeInRange", LocalTime.of(12, 0), start, end);
        assertThat(result3).isFalse();
    }

    // ========================================================================
    // TESTES DA LÓGICA DE RESPOSTA
    // ========================================================================

    @Test
    void responseRule_deveRetornarRespostaParaTriggerExato() throws Exception {
        carregarRegras();
        Optional<AutoResponseOverride> resultado = service.getResponseRule(1L, "bom dia");
        assertThat(resultado)
                .isPresent()
                .get()
                .satisfies(
                        ov -> assertThat(ov.response()).isEqualTo("Olá! Bom dia/tarde para você!"));
    }

    @Test
    void responseRule_deveIgnorarMaiusculasMinusculas() throws Exception {
        carregarRegras();
        Optional<AutoResponseOverride> resultado = service.getResponseRule(1L, "BOM DIA");
        assertThat(resultado).isPresent();
    }

    @Test
    void responseRule_deveRetornarEmptyQuandoTriggerNaoExiste() throws Exception {
        carregarRegras();
        Optional<AutoResponseOverride> resultado = service.getResponseRule(1L, "inexistente");
        assertThat(resultado).isEmpty();
    }

    @Test
    void responseRule_deveRetornarEmptyQuandoServicoDesativado() throws Exception {
        ReflectionTestUtils.setField(service, "enabled", false);
        carregarRegras();
        Optional<AutoResponseOverride> resultado = service.getResponseRule(1L, "bom dia");
        assertThat(resultado).isEmpty();
    }

    @Test
    void responseRule_deveRetornarEmptyParaMensagemNull() {
        assertDoesNotThrow(this::carregarRegras);
        Optional<AutoResponseOverride> resultado = service.getResponseRule(123L, null);
        assertThat(resultado).isEmpty();
    }

    @Test
    void responseRule_deveAplicarUserOverride() throws Exception {
        carregarRegras();

        Optional<AutoResponseOverride> resultado1 = service.getResponseRule(123L, "obrigado");
        assertThat(resultado1)
                .isPresent()
                .get()
                .satisfies(
                        ov -> {
                            assertThat(ov.response()).isEqualTo("Por nada, amigo!");
                            assertThat(ov.animation()).isEqualTo("https://exemplo.com/amigo.gif");
                        });

        Optional<AutoResponseOverride> resultado2 = service.getResponseRule(456L, "obrigado");
        assertThat(resultado2)
                .isPresent()
                .get()
                .satisfies(
                        ov -> {
                            assertThat(ov.response()).isEqualTo("Disponha!");
                            assertThat(ov.animation()).isNull();
                        });

        Optional<AutoResponseOverride> resultado3 = service.getResponseRule(789L, "obrigado");
        assertThat(resultado3)
                .isPresent()
                .get()
                .satisfies(
                        ov -> {
                            assertThat(ov.response()).isEqualTo("De nada!");
                            assertThat(ov.animation()).isNull();
                        });
    }

    @Test
    void responseRule_deveAplicarUserOverridesNovoFormato() throws Exception {
        InputStream is = new ByteArrayInputStream(JSON_COM_USER_OVERRIDES.getBytes());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(is);
        service.loadResponses();

        Optional<AutoResponseOverride> resultado = service.getResponseRule(999L, "teste");
        assertThat(resultado)
                .isPresent()
                .get()
                .satisfies(
                        ov -> {
                            assertThat(ov.response()).isEqualTo("Resposta especial");
                            assertThat(ov.animation())
                                    .isEqualTo("https://exemplo.com/especial.gif");
                        });

        Optional<AutoResponseOverride> resultado2 = service.getResponseRule(888L, "teste");
        assertThat(resultado2)
                .isPresent()
                .get()
                .satisfies(
                        ov -> {
                            assertThat(ov.response()).isEqualTo("Resposta padrão");
                            assertThat(ov.animation()).isNull();
                        });
    }

    @Test
    void responseRule_comUserIdNull_retornaDefault() throws Exception {
        carregarRegras();
        Optional<AutoResponseOverride> resultado = service.getResponseRule(null, "obrigado");
        assertThat(resultado)
                .isPresent()
                .get()
                .satisfies(
                        ov -> {
                            assertThat(ov.response()).isEqualTo("De nada!");
                            assertThat(ov.animation()).isNull();
                        });
    }

    @Test
    void responseRule_comTimeNull_usaHorarioAtual() {
        assertDoesNotThrow(this::carregarRegras);
        Optional<AutoResponseOverride> resultado = service.getResponseRule(1L, "bom dia", null);
        assertThat(resultado)
                .isPresent()
                .get()
                .satisfies(
                        ov -> assertThat(ov.response()).isEqualTo("Olá! Bom dia/tarde para você!"));
    }

    @Test
    void responseRule_triggerCurtoNaoAtiva() throws Exception {
        carregarRegras();
        Optional<AutoResponseOverride> resultado = service.getResponseRule(1L, "oi");
        assertThat(resultado).isEmpty();
    }

    @Test
    void responseRule_foraDoIntervaloNaoAtiva() throws Exception {
        carregarRegras();
        LocalTime fora = LocalTime.of(12, 0);
        Optional<AutoResponseOverride> resultado = service.getResponseRule(1L, "tchau", fora);
        assertThat(resultado).isEmpty();

        LocalTime dentro = LocalTime.of(20, 0);
        Optional<AutoResponseOverride> resultado2 = service.getResponseRule(1L, "tchau", dentro);
        assertThat(resultado2)
                .isPresent()
                .get()
                .satisfies(ov -> assertThat(ov.response()).isEqualTo("Até logo!"));
    }

    @Test
    void responseRule_devePriorizarTriggerMaisEspecifico() {
        AutoResponseService service2 = new AutoResponseService(resourceLoader, objectMapper);
        ReflectionTestUtils.setField(service2, "enabled", true);
        AutoResponseRule rule1 = new AutoResponseRule("Resposta curta", null, null, null, null);
        AutoResponseRule rule2 = new AutoResponseRule("Resposta longa", null, null, null, null);
        java.util.Map<String, AutoResponseRule> map = new java.util.HashMap<>();
        map.put("oi", rule1);
        map.put("oi tudo bem", rule2);
        ReflectionTestUtils.setField(service2, "triggerToRule", map);

        Optional<AutoResponseOverride> resultado = service2.getResponseRule(1L, "oi tudo bem");
        assertThat(resultado)
                .isPresent()
                .get()
                .satisfies(ov -> assertThat(ov.response()).isEqualTo("Resposta longa"));
    }

    // ========================================================================
    // TESTES DE UTILITÁRIOS E ESTATÍSTICA
    // ========================================================================

    @Test
    void isEnabled_retornaValorConfigurado() {
        ReflectionTestUtils.setField(service, "enabled", true);
        assertThat(service.isEnabled()).isTrue();

        ReflectionTestUtils.setField(service, "enabled", false);
        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    void getRulesSummary_retornaResumoDasRegras() throws Exception {
        carregarRegras();
        var summary = service.getRulesSummary();
        assertThat(summary)
                .isNotEmpty()
                .containsKeys("bom dia", "boa tarde", "tchau", "obrigado", "oi", "olá")
                .allSatisfy((key, value) -> assertThat(value).contains("response="));
    }

    @Test
    void reload_chamaLoadResponses() {
        AutoResponseService spy = spy(service);
        spy.reload();
        verify(spy, times(1)).loadResponses();
    }

    // ========================================================================
    // HELPER
    // ========================================================================

    private void carregarRegras() throws Exception {
        InputStream is = new ByteArrayInputStream(JSON_VALIDO.getBytes());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(is);
        service.loadResponses();
    }
}
