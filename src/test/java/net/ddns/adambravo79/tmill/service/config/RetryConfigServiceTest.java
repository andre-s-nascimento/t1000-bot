/* (c) 2026 | 13/10/2026 */
package net.ddns.adambravo79.tmill.service.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RetryConfigServiceTest {

    private JsonConfigLoader loader;
    private RetryConfigService service;

    @BeforeEach
    void setUp() {
        loader = mock(JsonConfigLoader.class);
        service = new RetryConfigService(loader);
    }

    // =========================================================================
    // CARREGAMENTO FELIZ
    // =========================================================================

    @Test
    @DisplayName("init: carrega múltiplos specs do JSON")
    void init_jsonCompleto() {
        doReturn(Optional.of(jsonCompleto()))
                .when(loader)
                .loadConfig(eq("groq-config.json"), eq(Map.class), any());

        service.init();

        RetryConfigService.RetrySpec transcription = service.get("transcription");
        assertThat(transcription.maxRetries()).isEqualTo(2);
        assertThat(transcription.delayMs()).isEqualTo(1000L);
        assertThat(transcription.multiplier()).isEqualTo(2);
        assertThat(transcription.maxDelayMs()).isEqualTo(5000L);

        RetryConfigService.RetrySpec refinement = service.get("refinement");
        assertThat(refinement.maxRetries()).isEqualTo(3);
        assertThat(refinement.delayMs()).isEqualTo(2000L);
        assertThat(refinement.multiplier()).isEqualTo(3);
        assertThat(refinement.maxDelayMs()).isEqualTo(30000L);

        RetryConfigService.RetrySpec chat = service.get("chat-completion");
        assertThat(chat.maxRetries()).isEqualTo(4);
        assertThat(chat.maxDelayMs()).isEqualTo(60000L);
    }

    // =========================================================================
    // FALLBACKS
    // =========================================================================

    @Test
    @DisplayName("init: JSON ausente não popula specs — get() cai no default interno")
    void init_jsonAusente_getCaiNoDefault() {
        doReturn(Optional.empty())
                .when(loader)
                .loadConfig(eq("groq-config.json"), eq(Map.class), any());

        service.init();

        // Como retrySpecsRef começa vazio e o init não popula nada,
        // get() retorna o default hardcoded do método get():
        // (3, 1000, 2, 30000)
        RetryConfigService.RetrySpec spec = service.get("transcription");
        assertThat(spec.maxRetries()).isEqualTo(3);
        assertThat(spec.delayMs()).isEqualTo(1000L);
        assertThat(spec.multiplier()).isEqualTo(2);
        assertThat(spec.maxDelayMs()).isEqualTo(30000L);
    }

    @Test
    @DisplayName("init: JSON sem chave 'retry' não popula specs — get() cai no default interno")
    void init_semChaveRetry_getCaiNoDefault() {
        Map<String, Object> json = new HashMap<>();
        json.put("models", Map.of("digest", "gpt-oss"));

        doReturn(Optional.of(json))
                .when(loader)
                .loadConfig(eq("groq-config.json"), eq(Map.class), any());

        service.init();

        assertThat(service.get("refinement").maxRetries()).isEqualTo(3);
        assertThat(service.get("refinement").delayMs()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("init: 'retry' inválido (não-Map) não popula specs — get() cai no default interno")
    void init_retryInvalido_getCaiNoDefault() {
        Map<String, Object> json = new HashMap<>();
        json.put("retry", "isso não é um mapa");

        doReturn(Optional.of(json))
                .when(loader)
                .loadConfig(eq("groq-config.json"), eq(Map.class), any());

        service.init();

        assertThat(service.get("refinement").maxRetries()).isEqualTo(3);
    }

    // =========================================================================
    // CAMPOS PARCIAIS
    // =========================================================================

    @Test
    @DisplayName("init: spec com apenas alguns campos usa default nos demais")
    void init_camposParciais() {
        Map<String, Object> custom = new HashMap<>();
        custom.put("max-retries", 5);
        // delay, multiplier e maxDelay ausentes

        Map<String, Object> retry = new HashMap<>();
        retry.put("custom", custom);

        Map<String, Object> json = new HashMap<>();
        json.put("retry", retry);

        doReturn(Optional.of(json))
                .when(loader)
                .loadConfig(eq("groq-config.json"), eq(Map.class), any());

        service.init();

        RetryConfigService.RetrySpec spec = service.get("custom");
        assertThat(spec.maxRetries()).isEqualTo(5);
        assertThat(spec.delayMs()).isEqualTo(1000L); // default do parseSpec
        assertThat(spec.multiplier()).isEqualTo(2); // default do parseSpec
        assertThat(spec.maxDelayMs()).isEqualTo(30000L); // default do parseSpec
    }

    @Test
    @DisplayName("init: valores como String são aceitos")
    void init_stringNumbers() {
        Map<String, Object> custom = new HashMap<>();
        custom.put("max-retries", "7");
        custom.put("delay-ms", "1500");
        custom.put("multiplier", "5");
        custom.put("max-delay-ms", "45000");

        Map<String, Object> retry = new HashMap<>();
        retry.put("custom", custom);

        Map<String, Object> json = new HashMap<>();
        json.put("retry", retry);

        doReturn(Optional.of(json))
                .when(loader)
                .loadConfig(eq("groq-config.json"), eq(Map.class), any());

        service.init();

        RetryConfigService.RetrySpec spec = service.get("custom");
        assertThat(spec.maxRetries()).isEqualTo(7);
        assertThat(spec.delayMs()).isEqualTo(1500L);
        assertThat(spec.multiplier()).isEqualTo(5);
        assertThat(spec.maxDelayMs()).isEqualTo(45000L);
    }

    // =========================================================================
    // get() — comportamento com chave desconhecida
    // =========================================================================

    @Test
    @DisplayName("get: chave desconhecida retorna spec default (3, 1000, 2, 30000)")
    void get_chaveDesconhecida_retornaDefault() {
        doReturn(Optional.empty())
                .when(loader)
                .loadConfig(eq("groq-config.json"), eq(Map.class), any());
        service.init();

        RetryConfigService.RetrySpec spec = service.get("nao-existe");

        assertThat(spec.maxRetries()).isEqualTo(3);
        assertThat(spec.delayMs()).isEqualTo(1000L);
        assertThat(spec.multiplier()).isEqualTo(2);
        assertThat(spec.maxDelayMs()).isEqualTo(30000L);
    }

    // =========================================================================
    // RELOAD — agora preserva estado anterior em JSON ausente
    // =========================================================================

    @Test
    @DisplayName("reload: substitui estado com novo JSON")
    void reload_substitui() {
        doReturn(Optional.of(jsonCompleto()))
                .when(loader)
                .loadConfig(eq("groq-config.json"), eq(Map.class), any());
        service.init();
        assertThat(service.get("refinement").maxRetries()).isEqualTo(3);

        Map<String, Object> custom = new HashMap<>();
        custom.put("max-retries", 10);
        custom.put("delay-ms", 500);
        custom.put("multiplier", 1);
        custom.put("max-delay-ms", 10000);

        Map<String, Object> retry = new HashMap<>();
        retry.put("refinement", custom);

        Map<String, Object> json = new HashMap<>();
        json.put("retry", retry);

        doReturn(Optional.of(json))
                .when(loader)
                .loadConfig(eq("groq-config.json"), eq(Map.class), any());
        service.reload();

        assertThat(service.get("refinement").maxRetries()).isEqualTo(10);
        // como o JSON só tem "refinement", outras chaves caem no default do get()
        assertThat(service.get("transcription").maxRetries()).isEqualTo(3);
    }

    @Test
    @DisplayName("reload: JSON ausente preserva estado anterior (não sobrescreve com default)")
    void reload_jsonAusente_preservaEstadoAnterior() {
        doReturn(Optional.of(jsonCompleto()))
                .when(loader)
                .loadConfig(eq("groq-config.json"), eq(Map.class), any());
        service.init();

        int antes = service.get("refinement").maxRetries(); // 3
        long delayAntes = service.get("refinement").delayMs(); // 2000

        doReturn(Optional.empty())
                .when(loader)
                .loadConfig(eq("groq-config.json"), eq(Map.class), any());
        service.reload();

        // Como o reload com JSON ausente faz `return` silencioso,
        // o estado carregado no init() permanece intacto.
        assertThat(service.get("refinement").maxRetries()).isEqualTo(antes);
        assertThat(service.get("refinement").delayMs()).isEqualTo(delayAntes);
    }

    @Test
    @DisplayName("init: chama loadConfig uma vez")
    void init_chamaLoadConfig() {
        doReturn(Optional.empty())
                .when(loader)
                .loadConfig(eq("groq-config.json"), eq(Map.class), any());

        service.init();

        verify(loader, times(1)).loadConfig(eq("groq-config.json"), eq(Map.class), any());
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private static Map<String, Object> jsonCompleto() {
        Map<String, Object> transcription = new HashMap<>();
        transcription.put("max-retries", 2);
        transcription.put("delay-ms", 1000);
        transcription.put("multiplier", 2);
        transcription.put("max-delay-ms", 5000);

        Map<String, Object> refinement = new HashMap<>();
        refinement.put("max-retries", 3);
        refinement.put("delay-ms", 2000);
        refinement.put("multiplier", 3);
        refinement.put("max-delay-ms", 30000);

        Map<String, Object> chat = new HashMap<>();
        chat.put("max-retries", 4);
        chat.put("delay-ms", 2000);
        chat.put("multiplier", 3);
        chat.put("max-delay-ms", 60000);

        Map<String, Object> retry = new HashMap<>();
        retry.put("transcription", transcription);
        retry.put("refinement", refinement);
        retry.put("chat-completion", chat);

        Map<String, Object> json = new HashMap<>();
        json.put("retry", retry);
        return json;
    }
}
