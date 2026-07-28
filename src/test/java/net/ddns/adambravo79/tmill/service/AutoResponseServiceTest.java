package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.*;
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
    private ObjectMapper objectMapper = new ObjectMapper();

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

    // JSON para testar novos formatos e casos extremos
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

    @BeforeEach
    void setUp() {
        service = new AutoResponseService(resourceLoader, objectMapper);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "configFile", "classpath:auto-responses-test.json");
    }

    // =========================
    // 1. init() com enabled = false
    // =========================
    @Test
    void init_quandoEnabledFalse_naoCarregaRegras() {
        ReflectionTestUtils.setField(service, "enabled", false);
        // Não mockamos resourceLoader, mas verificamos que loadResponses não é chamado
        // Para isso, podemos espionar o service e verificar que loadResponses não foi invocado.
        AutoResponseService spy = spy(service);
        spy.init();
        verify(spy, never()).loadResponses();
    }

    // =========================
    // 2. loadResponses() com config = null
    // =========================
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

    // =========================
    // 3. Regras com triggers ou response nulos
    // =========================
    @Test
    void loadResponses_quandoRegraComTriggersNulo_ignora() throws Exception {
        InputStream is = new ByteArrayInputStream(JSON_COM_USER_OVERRIDES.getBytes());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(is);

        service.loadResponses();

        // Apenas a regra "nova" deve ser carregada (as outras são ignoradas)
        assertThat(service.getRulesCount()).isEqualTo(1);
        Optional<AutoResponseOverride> resultado = service.getResponseRule(1L, "teste");
        assertThat(resultado).isPresent();
        assertThat(resultado.get().response()).isEqualTo("Resposta padrão");

        // "sem_triggers" e "sem_response" não devem existir
        assertThat(service.getResponseRule(1L, "vazio")).isEmpty();
        assertThat(service.getResponseRule(1L, "sem_triggers")).isEmpty();
    }

    // =========================
    // 4. Novo formato userOverrides
    // =========================
    @Test
    void responseRule_deveAplicarUserOverridesNovoFormato() throws Exception {
        InputStream is = new ByteArrayInputStream(JSON_COM_USER_OVERRIDES.getBytes());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(is);
        service.loadResponses();

        // Usuário com override
        Optional<AutoResponseOverride> resultado = service.getResponseRule(999L, "teste");
        assertThat(resultado).isPresent();
        assertThat(resultado.get().response()).isEqualTo("Resposta especial");
        assertThat(resultado.get().animation()).isEqualTo("https://exemplo.com/especial.gif");

        // Usuário sem override
        Optional<AutoResponseOverride> resultado2 = service.getResponseRule(888L, "teste");
        assertThat(resultado2).isPresent();
        assertThat(resultado2.get().response()).isEqualTo("Resposta padrão");
        assertThat(resultado2.get().animation()).isNull();
    }

    // =========================
    // 5. getResponseRule com time = null
    // =========================
    @Test
    void responseRule_comTimeNull_usaHorarioAtual() {
        // Carrega regras com timeRange para testar
        try {
            carregarRegras();
        } catch (Exception e) {
            fail("Falha ao carregar regras", e);
        }

        // Para garantir que o horário atual (que pode ser qualquer um) não influencie,
        // usamos uma regra sem timeRange (ex: "bom dia") – sempre ativa.
        // Chamamos o método de 3 args com time=null
        Optional<AutoResponseOverride> resultado = service.getResponseRule(1L, "bom dia", null);
        assertThat(resultado).isPresent();
        assertThat(resultado.get().response()).isEqualTo("Olá! Bom dia/tarde para você!");
    }

    // =========================
    // 6. getResponseRule com userId = null
    // =========================
    @Test
    void responseRule_comUserIdNull_retornaDefault() throws Exception {
        carregarRegras();
        Optional<AutoResponseOverride> resultado = service.getResponseRule(null, "obrigado");
        assertThat(resultado).isPresent();
        // Deve retornar a resposta padrão (não a personalizada)
        assertThat(resultado.get().response()).isEqualTo("De nada!");
        assertThat(resultado.get().animation()).isNull();
    }

    // =========================
    // 7. Trigger curto (length < 3) é filtrado
    // =========================
    @Test
    void responseRule_triggerCurtoNaoAtiva() throws Exception {
        carregarRegras();
        // "oi" tem length 2, deve ser filtrado
        Optional<AutoResponseOverride> resultado = service.getResponseRule(1L, "oi");
        assertThat(resultado).isEmpty();
    }

    // =========================
    // 8. Horário fora do intervalo
    // =========================
    @Test
    void responseRule_foraDoIntervaloNaoAtiva() throws Exception {
        carregarRegras();
        // "tchau" só funciona entre 18:00 e 23:59
        // Simular horário 12:00 (fora)
        LocalTime fora = LocalTime.of(12, 0);
        Optional<AutoResponseOverride> resultado = service.getResponseRule(1L, "tchau", fora);
        assertThat(resultado).isEmpty();

        // Simular horário 20:00 (dentro)
        LocalTime dentro = LocalTime.of(20, 0);
        Optional<AutoResponseOverride> resultado2 = service.getResponseRule(1L, "tchau", dentro);
        assertThat(resultado2).isPresent();
        assertThat(resultado2.get().response()).isEqualTo("Até logo!");
    }

    // =========================
    // 9. getRulesSummary()
    // =========================
    @Test
    void getRulesSummary_retornaResumoDasRegras() throws Exception {
        carregarRegras();
        var summary = service.getRulesSummary();
        assertThat(summary).isNotEmpty();
        assertThat(summary).containsKeys("bom dia", "boa tarde", "tchau", "obrigado", "oi", "olá");
        // Verifica se o formato contém 'response='
        assertThat(summary.values()).allMatch(s -> s.contains("response="));
    }

    // =========================
    // 10. isEnabled()
    // =========================
    @Test
    void isEnabled_retornaValorConfigurado() {
        ReflectionTestUtils.setField(service, "enabled", true);
        assertThat(service.isEnabled()).isTrue();

        ReflectionTestUtils.setField(service, "enabled", false);
        assertThat(service.isEnabled()).isFalse();
    }

    // =========================
    // 11. reload()
    // =========================
    @Test
    void reload_chamaLoadResponses() {
        AutoResponseService spy = spy(service);
        spy.reload();
        verify(spy, times(1)).loadResponses();
    }

    // =========================
    // TESTES EXISTENTES (mantidos)
    // =========================

    @Test
    void deveCarregarRegrasComSucesso() throws Exception {
        InputStream is = new ByteArrayInputStream(JSON_VALIDO.getBytes());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(is);

        service.loadResponses();

        Optional<AutoResponseOverride> resultado = service.getResponseRule(1L, "bom dia");
        assertThat(resultado).isPresent();
        assertThat(resultado.get().response()).isEqualTo("Olá! Bom dia/tarde para você!");
        assertThat(resultado.get().animation()).isEqualTo("https://exemplo.com/gif.gif");
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

    @Test
    void responseRule_deveRetornarRespostaParaTriggerExato() throws Exception {
        carregarRegras();
        Optional<AutoResponseOverride> resultado = service.getResponseRule(1L, "bom dia");
        assertThat(resultado).isPresent();
        assertThat(resultado.get().response()).isEqualTo("Olá! Bom dia/tarde para você!");
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
    void loadResponses_quandoRulesNulo_naoFalha() throws Exception {
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
    void responseRule_deveRetornarEmptyParaMensagemNull() {
        try {
            carregarRegras();
        } catch (Exception e) {
            fail("Falha ao carregar regras: " + e.getMessage());
        }

        Optional<AutoResponseOverride> resultado = service.getResponseRule(123L, null);
        assertThat(resultado).isEmpty();
    }

    @Test
    void responseRule_deveAplicarUserOverride() throws Exception {
        carregarRegras();

        Optional<AutoResponseOverride> resultado1 = service.getResponseRule(123L, "obrigado");
        assertThat(resultado1).isPresent();
        assertThat(resultado1.get().response()).isEqualTo("Por nada, amigo!");
        assertThat(resultado1.get().animation()).isEqualTo("https://exemplo.com/amigo.gif");

        Optional<AutoResponseOverride> resultado2 = service.getResponseRule(456L, "obrigado");
        assertThat(resultado2).isPresent();
        assertThat(resultado2.get().response()).isEqualTo("Disponha!");
        assertThat(resultado2.get().animation()).isNull();

        Optional<AutoResponseOverride> resultado3 = service.getResponseRule(789L, "obrigado");
        assertThat(resultado3).isPresent();
        assertThat(resultado3.get().response()).isEqualTo("De nada!");
        assertThat(resultado3.get().animation()).isNull();
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
        assertThat(resultado).isPresent();
        assertThat(resultado.get().response()).isEqualTo("Resposta longa");
    }

    // =========================
    // HELPER
    // =========================

    private void carregarRegras() throws Exception {
        InputStream is = new ByteArrayInputStream(JSON_VALIDO.getBytes());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(is);
        service.loadResponses();
    }
}
