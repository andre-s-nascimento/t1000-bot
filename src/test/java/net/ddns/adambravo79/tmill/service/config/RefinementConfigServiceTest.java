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

class RefinementConfigServiceTest {

    private JsonConfigLoader loader;
    private RefinementConfigService service;

    @BeforeEach
    void setUp() {
        loader = mock(JsonConfigLoader.class);
        service = new RefinementConfigService(loader);
    }

    // =========================================================================
    // CARREGAMENTO FELIZ
    // =========================================================================

    @Test
    @DisplayName("init: carrega todos os campos do JSON")
    void init_jsonCompleto() {
        doReturn(Optional.of(jsonCompleto()))
                .when(loader)
                .loadConfig(eq("refinement-config.json"), eq(Map.class), any());

        service.init();

        RefinementConfigService.RefinementSpec spec = service.getSpec();
        assertThat(spec.maxRefinamentoReducao()).isEqualTo(0.25);
        assertThat(spec.minLengthParaChecarTruncamento()).isEqualTo(150);
        assertThat(spec.refinementMaxTokens()).isEqualTo(4000);
        assertThat(spec.refinementTemperature()).isEqualTo(0.18);
    }

    // =========================================================================
    // FALLBACKS
    // =========================================================================

    @Test
    @DisplayName("init: JSON ausente mantém defaults")
    void init_jsonAusente_mantemDefaults() {
        doReturn(Optional.empty())
                .when(loader)
                .loadConfig(eq("refinement-config.json"), eq(Map.class), any());

        service.init();

        RefinementConfigService.RefinementSpec spec = service.getSpec();
        assertThat(spec.maxRefinamentoReducao()).isEqualTo(0.30);
        assertThat(spec.minLengthParaChecarTruncamento()).isEqualTo(200);
        assertThat(spec.refinementMaxTokens()).isEqualTo(4000);
        assertThat(spec.refinementTemperature()).isEqualTo(0.18);
    }

    @Test
    @DisplayName("init: JSON válido com apenas um campo mantém os demais defaults")
    void init_campoParcial() {
        Map<String, Object> json = new HashMap<>();
        json.put("max-refinamento-reducao", 0.40);

        doReturn(Optional.of(json))
                .when(loader)
                .loadConfig(eq("refinement-config.json"), eq(Map.class), any());

        service.init();

        RefinementConfigService.RefinementSpec spec = service.getSpec();
        assertThat(spec.maxRefinamentoReducao()).isEqualTo(0.40);
        assertThat(spec.minLengthParaChecarTruncamento()).isEqualTo(200); // default
        assertThat(spec.refinementMaxTokens()).isEqualTo(4000); // default
        assertThat(spec.refinementTemperature()).isEqualTo(0.18); // default
    }

    // =========================================================================
    // VALORES MALFORMADOS
    // =========================================================================

    @Test
    @DisplayName("init: valores inválidos caem no default campo a campo")
    void init_valoresMalformados() {
        Map<String, Object> json = new HashMap<>();
        json.put("max-refinamento-reducao", "not-a-double"); // inválido → default
        json.put("min-length-para-checar-truncamento", "abc"); // inválido → default
        json.put("refinement-max-tokens", "xyz"); // inválido → default
        json.put("refinement-temperature", "não é número"); // inválido → default

        doReturn(Optional.of(json))
                .when(loader)
                .loadConfig(eq("refinement-config.json"), eq(Map.class), any());

        service.init();

        RefinementConfigService.RefinementSpec spec = service.getSpec();
        assertThat(spec.maxRefinamentoReducao()).isEqualTo(0.30);
        assertThat(spec.minLengthParaChecarTruncamento()).isEqualTo(200);
        assertThat(spec.refinementMaxTokens()).isEqualTo(4000);
        assertThat(spec.refinementTemperature()).isEqualTo(0.18);
    }

    @Test
    @DisplayName("init: valores como String são aceitos")
    void init_stringNumbers() {
        Map<String, Object> json = new HashMap<>();
        json.put("max-refinamento-reducao", "0.15");
        json.put("min-length-para-checar-truncamento", "300");
        json.put("refinement-max-tokens", "8000");
        json.put("refinement-temperature", "0.25");

        doReturn(Optional.of(json))
                .when(loader)
                .loadConfig(eq("refinement-config.json"), eq(Map.class), any());

        service.init();

        RefinementConfigService.RefinementSpec spec = service.getSpec();
        assertThat(spec.maxRefinamentoReducao()).isEqualTo(0.15);
        assertThat(spec.minLengthParaChecarTruncamento()).isEqualTo(300);
        assertThat(spec.refinementMaxTokens()).isEqualTo(8000);
        assertThat(spec.refinementTemperature()).isEqualTo(0.25);
    }

    @Test
    @DisplayName("init: maxReducao = 0 é aceito (não confundir com default)")
    void init_maxReducaoZero() {
        Map<String, Object> json = new HashMap<>();
        json.put("max-refinamento-reducao", 0.0);

        doReturn(Optional.of(json))
                .when(loader)
                .loadConfig(eq("refinement-config.json"), eq(Map.class), any());

        service.init();

        assertThat(service.getSpec().maxRefinamentoReducao()).isZero();
    }

    @Test
    @DisplayName("init: minLength = 0 é aceito")
    void init_minLengthZero() {
        Map<String, Object> json = new HashMap<>();
        json.put("min-length-para-checar-truncamento", 0);

        doReturn(Optional.of(json))
                .when(loader)
                .loadConfig(eq("refinement-config.json"), eq(Map.class), any());

        service.init();

        assertThat(service.getSpec().minLengthParaChecarTruncamento()).isZero();
    }

    // =========================================================================
    // RELOAD
    // =========================================================================

    @Test
    @DisplayName("reload: substitui estado com novo JSON")
    void reload_substitui() {
        doReturn(Optional.of(jsonCompleto()))
                .when(loader)
                .loadConfig(eq("refinement-config.json"), eq(Map.class), any());
        service.init();
        assertThat(service.getSpec().maxRefinamentoReducao()).isEqualTo(0.25);

        Map<String, Object> json = new HashMap<>();
        json.put("max-refinamento-reducao", 0.50);
        json.put("min-length-para-checar-truncamento", 500);

        doReturn(Optional.of(json))
                .when(loader)
                .loadConfig(eq("refinement-config.json"), eq(Map.class), any());
        service.reload();

        RefinementConfigService.RefinementSpec spec = service.getSpec();
        assertThat(spec.maxRefinamentoReducao()).isEqualTo(0.50);
        assertThat(spec.minLengthParaChecarTruncamento()).isEqualTo(500);
        // Campos ausentes voltam ao default
        assertThat(spec.refinementMaxTokens()).isEqualTo(4000);
        assertThat(spec.refinementTemperature()).isEqualTo(0.18);
    }

    @Test
    @DisplayName("reload: JSON ausente preserva estado anterior")
    void reload_jsonAusente_preservaEstadoAnterior() {
        doReturn(Optional.of(jsonCompleto()))
                .when(loader)
                .loadConfig(eq("refinement-config.json"), eq(Map.class), any());
        service.init();

        double reducaoAntes = service.getSpec().maxRefinamentoReducao();
        int minLenAntes = service.getSpec().minLengthParaChecarTruncamento();

        doReturn(Optional.empty())
                .when(loader)
                .loadConfig(eq("refinement-config.json"), eq(Map.class), any());
        service.reload();

        assertThat(service.getSpec().maxRefinamentoReducao()).isEqualTo(reducaoAntes);
        assertThat(service.getSpec().minLengthParaChecarTruncamento()).isEqualTo(minLenAntes);
    }

    // =========================================================================
    // INIT
    // =========================================================================

    @Test
    @DisplayName("init: chama loadConfig uma vez")
    void init_chamaLoadConfig() {
        doReturn(Optional.empty())
                .when(loader)
                .loadConfig(eq("refinement-config.json"), eq(Map.class), any());

        service.init();

        verify(loader, times(1)).loadConfig(eq("refinement-config.json"), eq(Map.class), any());
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private static Map<String, Object> jsonCompleto() {
        Map<String, Object> json = new HashMap<>();
        json.put("max-refinamento-reducao", 0.25);
        json.put("min-length-para-checar-truncamento", 150);
        json.put("refinement-max-tokens", 4000);
        json.put("refinement-temperature", 0.18);
        return json;
    }
}
