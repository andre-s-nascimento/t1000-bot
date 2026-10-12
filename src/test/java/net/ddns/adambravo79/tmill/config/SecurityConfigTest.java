/* (c) 2026 */
package net.ddns.adambravo79.tmill.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import net.ddns.adambravo79.tmill.telegram.util.MetricsService;

class SecurityConfigTest {

    // =========================================================================
    // TESTES DE INTEGRAÇÃO (MockMvc) — segurança ativa
    // =========================================================================

    @Nested
    @WebMvcTest(controllers = TestAdminController.class)
    @Import({SecurityConfig.class, AdminFilterConfig.class})
    @TestPropertySource(
            properties = {
                "admin.security.disabled=false",
                "admin.google.client-id=test-client-id",
                "admin.google.client-secret=test-client-secret",
                "admin.allowed-emails=admin@test.com",
                "admin.allowed-ips="
            })
    @DisplayName("Com credenciais Google configuradas")
    class StandardSecurityTest {

        @Autowired private MockMvc mockMvc;
        @Autowired private SecurityConfig securityConfig;

        @MockitoBean private MetricsService metricsService;

        @Test
        @DisplayName("GET /admin/** sem auth → 401 JSON")
        void adminGetSemAuth_retorna401Json() throws Exception {
            mockMvc.perform(get("/admin/test"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.erro").value("Não autenticado. Faça login novamente."));
        }

        @Test
        @DisplayName("POST /admin/** sem auth → 401 JSON")
        void adminPostSemAuth_retorna401Json() throws Exception {
            mockMvc.perform(post("/admin/test").contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.erro").value("Não autenticado. Faça login novamente."));
        }

        @Test
        @DisplayName("GET /admin/features/* sem auth → 401 JSON")
        void adminFeaturesSemAuth_retorna401Json() throws Exception {
            mockMvc.perform(get("/admin/features/transcription.enabled"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.erro").exists());
        }

        @Test
        @DisplayName("ClientRegistrationRepository cria registro do Google corretamente")
        void clientRegistrationRepository_comCredenciais() {
            ClientRegistrationRepository repo = securityConfig.clientRegistrationRepository();
            assertThat(repo).isNotNull();
            ClientRegistration reg = repo.findByRegistrationId("google");
            assertThat(reg).isNotNull();
            assertThat(reg.getClientId()).isEqualTo("test-client-id");
        }
    }

    // =========================================================================
    // TESTES DE INTEGRAÇÃO (MockMvc) — sem credenciais
    // =========================================================================

    @Nested
    @WebMvcTest(controllers = TestAdminController.class)
    @Import({SecurityConfig.class, AdminFilterConfig.class})
    @TestPropertySource(
            properties = {
                "admin.security.disabled=false",
                "admin.google.client-id=",
                "admin.google.client-secret=",
                "admin.allowed-emails="
            })
    @DisplayName("Sem credenciais Google")
    class BlankCredentialsSecurityTest {

        @Autowired private SecurityConfig securityConfig;

        @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;
        @MockitoBean private MetricsService metricsService;

        @Test
        @DisplayName("ClientRegistrationRepository retorna null quando credenciais vazias")
        void clientRegistrationRepository_vazio() {
            ClientRegistrationRepository repo = securityConfig.clientRegistrationRepository();
            assertThat(repo).isNotNull();
            assertThat(repo.findByRegistrationId("google")).isNull();
        }
    }

    // =========================================================================
    // TESTES UNITÁRIOS (sem Spring context)
    // =========================================================================

    @Test
    @DisplayName("Validação de segurança: lança exceção se securityDisabled=true no profile prod")
    void validateSecurityConfig_prodError() {
        Environment env = mock(Environment.class);
        when(env.acceptsProfiles(any(Profiles.class))).thenReturn(true);

        AdminEmailAuthorizationFilter filter = mock(AdminEmailAuthorizationFilter.class);
        SecurityConfig config = new SecurityConfig(filter, env);
        org.springframework.test.util.ReflectionTestUtils.setField(
                config, "securityDisabled", true);

        assertThatThrownBy(config::validateSecurityConfig)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FALHA CRÍTICA");
    }

    @Test
    @DisplayName("successHandler redireciona para /admin-web e loga o IP")
    void testSuccessHandlerAndIpExtraction() throws Exception {
        Environment env = mock(Environment.class);
        when(env.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        AdminEmailAuthorizationFilter filter = mock(AdminEmailAuthorizationFilter.class);
        SecurityConfig config = new SecurityConfig(filter, env);

        org.springframework.security.core.Authentication auth =
                mock(org.springframework.security.core.Authentication.class);
        OAuth2User oauth2User =
                new DefaultOAuth2User(
                        java.util.List.of(),
                        Map.of("email", "admin@test.com", "sub", "123"),
                        "sub");
        when(auth.getPrincipal()).thenReturn(oauth2User);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18");
        MockHttpServletResponse response = new MockHttpServletResponse();

        var successHandler =
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                        config, "successHandler");
        assertThat(successHandler).isNotNull();

        ((AuthenticationSuccessHandler) successHandler)
                .onAuthenticationSuccess(request, response, auth);
        assertThat(response.getRedirectedUrl()).isEqualTo("/admin-web");
    }
}
