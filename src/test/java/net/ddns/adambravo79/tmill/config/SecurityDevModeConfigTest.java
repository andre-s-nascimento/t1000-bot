/* (c) 2026 */
package net.ddns.adambravo79.tmill.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import net.ddns.adambravo79.tmill.service.feature.FeatureFlagAdminService;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;

@WebMvcTest(controllers = TestAdminController.class)
@Import({SecurityConfig.class, AdminFilterConfig.class})
@TestPropertySource(
        properties = {
            "admin.security.disabled=true",
            "admin.google.client-id=",
            "admin.google.client-secret=",
            "admin.allowed-emails="
        })
class SecurityDevModeConfigTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private MetricsService metricsService;
    @MockitoBean private FeatureFlagAdminService featureFlagAdminService;

    @Test
    @DisplayName("Modo dev ativado: /admin/** acessível sem autenticação")
    void devMode_permiteAcessoAdmin() throws Exception {
        mockMvc.perform(get("/admin/test"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    @DisplayName("Modo dev ativado: /admin-web/** também acessível")
    void devMode_permiteAcessoAdminWeb() throws Exception {
        mockMvc.perform(get("/admin-web/test"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }
}
