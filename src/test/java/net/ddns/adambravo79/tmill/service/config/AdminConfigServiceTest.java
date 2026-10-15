/* (c) 2026 | 12/10/2026 */
package net.ddns.adambravo79.tmill.service.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdminConfigServiceTest {

    private JsonConfigLoader loader;
    private AdminConfigService service;

    @BeforeEach
    void setUp() {
        loader = mock(JsonConfigLoader.class);
        service = new AdminConfigService(loader);
    }

    @Test
    @DisplayName("init: lê showcaseChatId numérico do JSON")
    void init_leShowcaseNumerico() {
        doReturn(Optional.of(Map.of("showcaseChatId", -100L)))
                .when(loader)
                .loadConfig(eq("admin-config.json"), eq(Map.class), any());

        service.init();

        assertThat(service.getShowcaseChatId()).isEqualTo(-100L);
    }

    @Test
    @DisplayName("init: aceita showcaseChatId como String")
    void init_leShowcaseComoString() {
        doReturn(Optional.of(Map.of("showcaseChatId", "-999")))
                .when(loader)
                .loadConfig(eq("admin-config.json"), eq(Map.class), any());

        service.init();

        assertThat(service.getShowcaseChatId()).isEqualTo(-999L);
    }

    @Test
    @DisplayName("init: JSON ausente usa fallback")
    void init_jsonAusente_usaFallback() {
        doReturn(Optional.empty())
                .when(loader)
                .loadConfig(eq("admin-config.json"), eq(Map.class), any());

        service.init();

        assertThat(service.getShowcaseChatId())
                .isEqualTo(AdminConfigService.FALLBACK_SHOWCASE_CHAT_ID);
    }

    @Test
    @DisplayName("init: chave ausente usa fallback")
    void init_chaveAusente_usaFallback() {
        doReturn(Optional.of(Map.of("outraChave", "valor")))
                .when(loader)
                .loadConfig(eq("admin-config.json"), eq(Map.class), any());

        service.init();

        assertThat(service.getShowcaseChatId())
                .isEqualTo(AdminConfigService.FALLBACK_SHOWCASE_CHAT_ID);
    }

    @Test
    @DisplayName("init: valor inválido usa fallback")
    void init_valorInvalido_usaFallback() {
        doReturn(Optional.of(Map.of("showcaseChatId", "not-a-number")))
                .when(loader)
                .loadConfig(eq("admin-config.json"), eq(Map.class), any());

        service.init();

        assertThat(service.getShowcaseChatId())
                .isEqualTo(AdminConfigService.FALLBACK_SHOWCASE_CHAT_ID);
    }

    @Test
    @DisplayName("reload: substitui valor anterior")
    void reload_substitui() {
        doReturn(Optional.of(Map.of("showcaseChatId", -100L)))
                .when(loader)
                .loadConfig(eq("admin-config.json"), eq(Map.class), any());

        service.init();
        assertThat(service.getShowcaseChatId()).isEqualTo(-100L);

        doReturn(Optional.of(Map.of("showcaseChatId", -200L)))
                .when(loader)
                .loadConfig(eq("admin-config.json"), eq(Map.class), any());

        service.reload();
        assertThat(service.getShowcaseChatId()).isEqualTo(-200L);
    }

    @Test
    @DisplayName("reload: erro no loader mantém valor anterior")
    void reload_erroMantemValor() {
        doReturn(Optional.of(Map.of("showcaseChatId", -100L)))
                .when(loader)
                .loadConfig(eq("admin-config.json"), eq(Map.class), any());

        service.init();

        doReturn(Optional.empty())
                .when(loader)
                .loadConfig(eq("admin-config.json"), eq(Map.class), any());

        service.reload();

        assertThat(service.getShowcaseChatId()).isEqualTo(-100L);
    }
}
