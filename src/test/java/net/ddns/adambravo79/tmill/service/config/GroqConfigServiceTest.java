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

class GroqConfigServiceTest {

    private JsonConfigLoader loader;
    private GroqConfigService service;

    @BeforeEach
    void setUp() {
        loader = mock(JsonConfigLoader.class);
        service = new GroqConfigService(loader);
    }

    // =========================================================================
    // CARREGAMENTO FELIZ
    // =========================================================================

    @Test
    @DisplayName("init: carrega digest e refinement do JSON")
    void init_jsonCompleto() {
        doReturn(Optional.of(jsonCompleto()))
                .when(loader)
                .loadConfig(eq("groq-config.json"), eq(Map.class), any());

        service.init();

        GroqConfigService.DigestSpec digest = service.getDigestSpec();
        assertThat(digest.temperature()).isEqualTo(0.7);
        assertThat(digest.maxTokens()).isEqualTo(2500);

        GroqConfigService.RefinementSpec refinement = service.getRefinementSpec();
        assertThat(refinement.temperature()).isEqualTo(0.10);
        assertThat(refinement.maxTokens()).isEqualTo(6000);
    }

    // =========================================================================
    // FALLBACKS
    // =========================================================================

    @Test
    @DisplayName("init: JSON ausente mantém os defaults hardcoded")
    void init_jsonAusente_mantemDefaults() {
        doReturn(Optional.empty())
                .when(loader)
                .loadConfig(eq("groq-config.json"), eq(Map.class), any());

        service.init();

        GroqConfigService.DigestSpec digest = service.getDigestSpec();
        assertThat(digest.temperature()).isEqualTo(0.5);
        assertThat(digest.maxTokens()).isEqualTo(2200);

        GroqConfigService.RefinementSpec refinement = service.getRefinementSpec();
        assertThat(refinement.temperature()).isEqualTo(0.18);
        assertThat(refinement.maxTokens()).isEqualTo(4000);
    }

    @Test
    @DisplayName("init: JSON válido sem chave 'digest' mantém default do digest")
    void init_semDigest_mantemDefault() {
        Map<String, Object> refinement = new HashMap<>();
        refinement.put("temperature", 0.10);
        refinement.put("max-tokens", 6000);

        Map<String, Object> json = new HashMap<>();
        json.put("refinement", refinement);

        doReturn(Optional.of(json))
                .when(loader)
                .loadConfig(eq("groq-config.json"), eq(Map.class), any());

        service.init();

        // digest permanece default
        assertThat(service.getDigestSpec().temperature()).isEqualTo(0.5);
        assertThat(service.getDigestSpec().maxTokens()).isEqualTo(2200);

        // refinement foi sobrescrito
        assertThat(service.getRefinementSpec().temperature()).isEqualTo(0.10);
        assertThat(service.getRefinementSpec().maxTokens()).isEqualTo(6000);
    }

    @Test
    @DisplayName("init: JSON válido sem chave 'refinement' mantém default do refinement")
    void init_semRefinement_mantemDefault() {
        Map<String, Object> digest = new HashMap<>();
        digest.put("temperature", 0.7);
        digest.put("max-tokens", 2500);

        Map<String, Object> json = new HashMap<>();
        json.put("digest", digest);

        doReturn(Optional.of(json))
                .when(loader)
                .loadConfig(eq("groq-config.json"), eq(Map.class), any());

        service.init();

        assertThat(service.getDigestSpec().temperature()).isEqualTo(0.7);
        assertThat(service.getDigestSpec().maxTokens()).isEqualTo(2500);

        assertThat(service.getRefinementSpec().temperature()).isEqualTo(0.18);
        assertThat(service.getRefinementSpec().maxTokens()).isEqualTo(4000);
    }

    @Test
    @DisplayName("init: chave 'digest' inválida (não-Map) mantém default")
    void init_digestInvalido_mantemDefault() {
        Map<String, Object> json = new HashMap<>();
        json.put("digest", "não é um mapa");

        doReturn(Optional.of(json))
                .when(loader)
                .loadConfig(eq("groq-config.json"), eq(Map.class), any());

        service.init();

        assertThat(service.getDigestSpec().temperature()).isEqualTo(0.5);
        assertThat(service.getDigestSpec().maxTokens()).isEqualTo(2200);
    }

    // =========================================================================
    // CAMPOS PARCIAIS / MALFORMADOS
    // =========================================================================

    @Test
    @DisplayName("init: digest com apenas temperature mantém default no max-tokens")
    void init_digestParcial() {
        Map<String, Object> digest = new HashMap<>();
        digest.put("temperature", 0.8);
        // max-tokens ausente

        Map<String, Object> json = new HashMap<>();
        json.put("digest", digest);

        doReturn(Optional.of(json))
                .when(loader)
                .loadConfig(eq("groq-config.json"), eq(Map.class), any());

        service.init();

        assertThat(service.getDigestSpec().temperature()).isEqualTo(0.8);
        assertThat(service.getDigestSpec().maxTokens()).isEqualTo(2200); // default
    }

    @Test
    @DisplayName("init: valores inválidos caem no default campo a campo")
    void init_valoresMalformados() {
        Map<String, Object> digest = new HashMap<>();
        digest.put("temperature", "abc"); // inválido → default
        digest.put("max-tokens", "xyz"); // inválido → default

        Map<String, Object> refinement = new HashMap<>();
        refinement.put("temperature", "not-a-number");
        refinement.put("max-tokens", "not-an-int");

        Map<String, Object> json = new HashMap<>();
        json.put("digest", digest);
        json.put("refinement", refinement);

        doReturn(Optional.of(json))
                .when(loader)
                .loadConfig(eq("groq-config.json"), eq(Map.class), any());

        service.init();

        assertThat(service.getDigestSpec().temperature()).isEqualTo(0.5);
        assertThat(service.getDigestSpec().maxTokens()).isEqualTo(2200);
        assertThat(service.getRefinementSpec().temperature()).isEqualTo(0.18);
        assertThat(service.getRefinementSpec().maxTokens()).isEqualTo(4000);
    }

    @Test
    @DisplayName("init: números como String são aceitos")
    void init_stringNumbers() {
        Map<String, Object> digest = new HashMap<>();
        digest.put("temperature", "0.65");
        digest.put("max-tokens", "3000");

        Map<String, Object> refinement = new HashMap<>();
        refinement.put("temperature", "0.22");
        refinement.put("max-tokens", "5000");

        Map<String, Object> json = new HashMap<>();
        json.put("digest", digest);
        json.put("refinement", refinement);

        doReturn(Optional.of(json))
                .when(loader)
                .loadConfig(eq("groq-config.json"), eq(Map.class), any());

        service.init();

        assertThat(service.getDigestSpec().temperature()).isEqualTo(0.65);
        assertThat(service.getDigestSpec().maxTokens()).isEqualTo(3000);
        assertThat(service.getRefinementSpec().temperature()).isEqualTo(0.22);
        assertThat(service.getRefinementSpec().maxTokens()).isEqualTo(5000);
    }

    @Test
    @DisplayName("init: temperature = 0.0 é aceita (não confundir com ausente)")
    void init_temperatureZero() {
        Map<String, Object> digest = new HashMap<>();
        digest.put("temperature", 0.0);
        digest.put("max-tokens", 1000);

        Map<String, Object> json = new HashMap<>();
        json.put("digest", digest);

        doReturn(Optional.of(json))
                .when(loader)
                .loadConfig(eq("groq-config.json"), eq(Map.class), any());

        service.init();

        assertThat(service.getDigestSpec().temperature()).isZero();
        assertThat(service.getDigestSpec().maxTokens()).isEqualTo(1000);
    }

    // =========================================================================
    // RELOAD
    // =========================================================================

    @Test
    @DisplayName("reload: substitui estado com novo JSON")
    void reload_substitui() {
        doReturn(Optional.of(jsonCompleto()))
                .when(loader)
                .loadConfig(eq("groq-config.json"), eq(Map.class), any());
        service.init();
        assertThat(service.getDigestSpec().temperature()).isEqualTo(0.7);
        // Guarda o valor ANTES para garantir que o refinement não foi tocado no reload
        double refinementTempAntes = service.getRefinementSpec().temperature();

        Map<String, Object> digest = new HashMap<>();
        digest.put("temperature", 0.9);
        digest.put("max-tokens", 4000);

        Map<String, Object> json = new HashMap<>();
        json.put("digest", digest);

        doReturn(Optional.of(json))
                .when(loader)
                .loadConfig(eq("groq-config.json"), eq(Map.class), any());
        service.reload();

        // Digest foi atualizado
        assertThat(service.getDigestSpec().temperature()).isEqualTo(0.9);
        assertThat(service.getDigestSpec().maxTokens()).isEqualTo(4000);

        // 🔧 FIX: como o JSON novo NÃO tem 'refinement', o GroqConfigService
        //       mantém o valor anterior (não reseta para default).
        //       Comportamento consistente com AudioConfigService/RetryConfigService.
        assertThat(service.getRefinementSpec().temperature()).isEqualTo(refinementTempAntes);
    }

    @Test
    @DisplayName("reload: JSON ausente preserva estado anterior")
    void reload_jsonAusente_preservaEstadoAnterior() {
        doReturn(Optional.of(jsonCompleto()))
                .when(loader)
                .loadConfig(eq("groq-config.json"), eq(Map.class), any());
        service.init();

        double digestTempAntes = service.getDigestSpec().temperature();
        int digestTokensAntes = service.getDigestSpec().maxTokens();

        doReturn(Optional.empty())
                .when(loader)
                .loadConfig(eq("groq-config.json"), eq(Map.class), any());
        service.reload();

        assertThat(service.getDigestSpec().temperature()).isEqualTo(digestTempAntes);
        assertThat(service.getDigestSpec().maxTokens()).isEqualTo(digestTokensAntes);
    }

    // =========================================================================
    // INIT
    // =========================================================================

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
        Map<String, Object> digest = new HashMap<>();
        digest.put("temperature", 0.7);
        digest.put("max-tokens", 2500);

        Map<String, Object> refinement = new HashMap<>();
        refinement.put("temperature", 0.10);
        refinement.put("max-tokens", 6000);

        Map<String, Object> json = new HashMap<>();
        json.put("digest", digest);
        json.put("refinement", refinement);
        return json;
    }
}
