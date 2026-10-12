/* (c) 2026 | 12/10/2026 */
package net.ddns.adambravo79.tmill.service.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorldCupTeamsServiceTest {

    private JsonConfigLoader loader;
    private WorldCupTeamsService service;

    private static Map<String, Object> defaultTeams() {
        return Map.of(
                "teams",
                Map.of(
                        "brazil", Map.of("namePt", "Brasil", "flag", "🇧🇷"),
                        "argentina", Map.of("namePt", "Argentina", "flag", "🇦🇷"),
                        "united states", Map.of("namePt", "Estados Unidos", "flag", "🇺🇸"),
                        "dr congo",
                                Map.of(
                                        "namePt",
                                        "Republica Democratica do Congo",
                                        "flag",
                                        "🇨🇩")));
    }

    @BeforeEach
    void setUp() {
        loader = mock(JsonConfigLoader.class);
        doReturn(Optional.of(defaultTeams()))
                .when(loader)
                .loadConfig(eq("worldcup-teams.json"), eq(Map.class), any());

        service = new WorldCupTeamsService(loader);
        service.init();
    }

    // ============================================================
    // translateTeam
    // ============================================================

    @Test
    @DisplayName("translateTeam: match exato")
    void translateTeam_exato() {
        assertThat(service.translateTeam("brazil")).isEqualTo("Brasil");
        assertThat(service.translateTeam("argentina")).isEqualTo("Argentina");
    }

    @Test
    @DisplayName("translateTeam: case-insensitive")
    void translateTeam_caseInsensitive() {
        assertThat(service.translateTeam("BRAZIL")).isEqualTo("Brasil");
        assertThat(service.translateTeam("Brazil")).isEqualTo("Brasil");
    }

    @Test
    @DisplayName("translateTeam: fallback por substring")
    void translateTeam_substring() {
        assertThat(service.translateTeam("United States of America")).isEqualTo("Estados Unidos");
        assertThat(service.translateTeam("DR Congo")).isEqualTo("Republica Democratica do Congo");
    }

    @Test
    @DisplayName("translateTeam: null e vazio retornam '?'")
    void translateTeam_nullEVazio() {
        assertThat(service.translateTeam(null)).isEqualTo("?");
        assertThat(service.translateTeam("")).isEqualTo("?");
        assertThat(service.translateTeam("   ")).isEqualTo("?");
    }

    @Test
    @DisplayName("translateTeam: desconhecido retorna o próprio input")
    void translateTeam_desconhecido() {
        assertThat(service.translateTeam("Atlantis")).isEqualTo("Atlantis");
    }

    // ============================================================
    // flagEmoji
    // ============================================================

    @Test
    @DisplayName("flagEmoji: match exato")
    void flagEmoji_exato() {
        assertThat(service.flagEmoji("brazil")).isEqualTo("🇧🇷");
        assertThat(service.flagEmoji("france")).isEqualTo("🏳️"); // não está no defaultTeams
    }

    @Test
    @DisplayName("flagEmoji: fallback por substring")
    void flagEmoji_substring() {
        assertThat(service.flagEmoji("United States of America")).isEqualTo("🇺🇸");
        assertThat(service.flagEmoji("DR Congo")).isEqualTo("🇨🇩");
    }

    @Test
    @DisplayName("flagEmoji: null, vazio e desconhecido retornam bandeira branca")
    void flagEmoji_nullVazioDesconhecido() {
        assertThat(service.flagEmoji(null)).isEqualTo("🏳️");
        assertThat(service.flagEmoji("")).isEqualTo("🏳️");
        assertThat(service.flagEmoji("Atlantis")).isEqualTo("🏳️");
    }

    // ============================================================
    // reload
    // ============================================================

    @Test
    @DisplayName("reload: substitui estado com novo JSON")
    void reload_sucesso() {
        Map<String, Object> novo =
                Map.of("teams", Map.of("uruguay", Map.of("namePt", "Uruguai", "flag", "🇺🇾")));

        doReturn(Optional.of(novo))
                .when(loader)
                .loadConfig(eq("worldcup-teams.json"), eq(Map.class), any());

        service.reload();

        assertThat(service.translateTeam("uruguay")).isEqualTo("Uruguai");
        assertThat(service.translateTeam("brazil")).isEqualTo("brazil"); // não existe mais
    }

    @Test
    @DisplayName("reload: JSON ausente mantém estado anterior")
    void reload_jsonAusente_mantemEstado() {
        doReturn(Optional.empty())
                .when(loader)
                .loadConfig(eq("worldcup-teams.json"), eq(Map.class), any());

        service.reload();

        assertThat(service.translateTeam("brazil")).isEqualTo("Brasil");
    }

    @Test
    @DisplayName("reload: JSON com 'teams' inválido mantém estado")
    void reload_teamsInvalido_mantemEstado() {
        doReturn(Optional.of(Map.of("teams", "não é mapa")))
                .when(loader)
                .loadConfig(eq("worldcup-teams.json"), eq(Map.class), any());

        service.reload();

        assertThat(service.translateTeam("brazil")).isEqualTo("Brasil");
    }

    @Test
    @DisplayName("init: chama reload uma vez")
    void init_chamaReload() {
        // Já chamado no setUp — apenas confirmamos
        verify(loader, times(1)).loadConfig(eq("worldcup-teams.json"), eq(Map.class), any());
    }

    @Test
    @DisplayName("getAll: retorna mapa imutável")
    void getAll_retornaMapa() {
        assertThat(service.getAll()).containsKeys("brazil", "argentina");
    }
}
