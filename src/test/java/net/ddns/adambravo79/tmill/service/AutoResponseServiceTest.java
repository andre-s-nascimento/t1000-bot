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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import net.ddns.adambravo79.tmill.model.AutoResponseOverride;
import net.ddns.adambravo79.tmill.model.AutoResponseRule;

@ExtendWith(MockitoExtension.class)
class AutoResponseServiceTest {

    @Mock private ResourceLoader resourceLoader;
    @Mock private Resource resource;

    @InjectMocks private AutoResponseService service;

    private static final String JSON_VALIDO =
            """
      {
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
      """;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "configFile", "classpath:auto-responses-test.json");
    }

    // =========================
    // 🧪 TESTES DE CARREGAMENTO
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
    void deveIgnorarArquivoInexistente() throws Exception {
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

    // =========================
    // 🧪 TESTES DE containsExactWord
    // =========================

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

    // =========================
    // 🧪 TESTES DE isTimeInRange
    // =========================

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

    // =========================
    // 🧪 TESTES DE responseRule
    // =========================

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
    void responseRule_deveRetornarEmptyParaMensagemNull() throws Exception {
        carregarRegras();
        Optional<AutoResponseOverride> resultado = service.getResponseRule(1L, null);
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
    void responseRule_devePriorizarTriggerMaisEspecifico() throws Exception {
        AutoResponseService service2 = new AutoResponseService(resourceLoader);
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
    // 🧪 HELPER
    // =========================

    private void carregarRegras() throws Exception {
        InputStream is = new ByteArrayInputStream(JSON_VALIDO.getBytes());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(is);
        service.loadResponses();
    }
}
