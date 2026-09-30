package net.ddns.adambravo79.tmill.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import net.ddns.adambravo79.tmill.telegram.util.MetricsService;

/**
 * Testes de integração do {@link SecurityConfig}.
 *
 * <p>Usa {@link WebMvcTest} com um {@code @RestController} de teste para exercitar o {@code
 * securityFilterChain} real e cobrir as lambdas de {@code AuthenticationEntryPoint} e {@code
 * AccessDeniedHandler}.
 *
 * <p><b>⚠️ {@code @TestPropertySource} é obrigatório:</b> o {@code SecurityConfig} usa
 * {@code @Value("${admin.security.disabled:false}")}, mas {@code application.properties} declara
 * {@code admin.security.disabled=${ADMIN_SECURITY_DISABLED}} <b>sem default</b>. Sem o
 * {@code @TestPropertySource} definindo o valor, o Spring tenta converter o literal {@code
 * "${ADMIN_SECURITY_DISABLED}"} para boolean e falha com {@code Invalid boolean value
 * [${ADMIN_SECURITY_DISABLED}]}.
 */
@WebMvcTest(controllers = SecurityConfigTest.TestAdminController.class)
@Import({SecurityConfig.class, AdminFilterConfig.class})
@TestPropertySource(
        properties = {
            // 🔧 OBRIGATÓRIO: resolver o placeholder de application.properties
            "admin.security.disabled=false",
            // Evita que o OAuth2 tente inicializar sem credenciais (degrada para no-op)
            "admin.google.client-id=",
            "admin.google.client-secret=",
            "admin.allowed-emails=",
            "admin.allowed-ips="
        })
class SecurityConfigTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean private MetricsService metricsService;

    // ============================================================
    // 401 JSON para /admin/** sem autenticação
    // ============================================================

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

    // ============================================================
    // Rotas públicas
    // ============================================================

    @Test
    @DisplayName("GET /actuator/health não é bloqueado por /admin/**")
    void actuatorHealth_naoBloqueado() throws Exception {
        int status = mockMvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(401);
    }

    // ============================================================
    // Controller de teste (só para o @WebMvcTest subir)
    // ============================================================

    @RestController
    static class TestAdminController {

        @GetMapping("/admin/test")
        public String get() {
            return "ok";
        }

        @PostMapping("/admin/test")
        public String post() {
            return "ok";
        }

        @GetMapping("/admin/features/transcription.enabled")
        public String features() {
            return "ok";
        }

        @GetMapping("/actuator/health")
        public String health() {
            return "{\"status\":\"UP\"}";
        }
    }
}
