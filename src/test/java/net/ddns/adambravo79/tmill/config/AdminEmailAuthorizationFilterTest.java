/* 04/10/2026 (c) 2026 */
package net.ddns.adambravo79.tmill.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.FilterChain;

class AdminEmailAuthorizationFilterTest {

    private AdminEmailAuthorizationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new AdminEmailAuthorizationFilter();
        ReflectionTestUtils.setField(
                filter, "allowedEmailsStr", "autorizado@gmail.com,outro@gmail.com");
        filter.initFilterBean();

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);

        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ===================== ROTAS IGNORADAS =====================

    @ParameterizedTest(name = "Deve ignorar rotas não protegidas ou de sistema: {0}")
    @ValueSource(
            strings = {
                "/oauth2/authorization/google",
                "/login/oauth2/code/google",
                "/.well-known/appspecific/com.chrome.devtools.json",
                "/public/page"
            })
    @DisplayName("Deve ignorar rotas de OAuth2, login, well-known e públicas")
    void deveIgnorarRotas(String requestUri) throws Exception {
        request.setRequestURI(requestUri);
        filter.doFilterInternal(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    // ===================== SEM AUTENTICAÇÃO =====================

    @Test
    @DisplayName("Sem autenticação em rota protegida: delega para o Spring Security")
    void semAuth_deixaSpringSecurityTratar() throws Exception {
        request.setRequestURI("/admin-web");
        filter.doFilterInternal(request, response, chain);
        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // ===================== AUTENTICADO E AUTORIZADO =====================

    @Test
    @DisplayName("Usuário OAuth2 com email autorizado em /admin-web: deixa passar")
    void autenticadoAutorizado_deixaPassar() throws Exception {
        setOAuth2User("autorizado@gmail.com");

        request.setRequestURI("/admin-web");
        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Usuário OAuth2 com email autorizado em /admin/: deixa passar")
    void autenticadoAutorizadoEmAdminApi_deixaPassar() throws Exception {
        setOAuth2User("outro@gmail.com");

        request.setRequestURI("/admin/birthdays");
        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ===================== AUTENTICADO E NÃO AUTORIZADO =====================

    @Test
    @DisplayName("Usuário OAuth2 sem permissão em /admin-web: bloqueia, limpa sessão e redireciona")
    void autenticadoNaoAutorizado_bloqueiaERedireciona() throws Exception {
        setOAuth2User("intruso@gmail.com");
        request.setSession(new org.springframework.mock.web.MockHttpSession());

        request.setRequestURI("/admin-web");
        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getRedirectedUrl()).isEqualTo("/oauth2/authorization/google");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getCookie("JSESSIONID")).isNotNull();
    }

    // ===================== CASOS DE CONFIGURAÇÃO VAZIA =====================

    @Test
    @DisplayName("Filtro sem emails permitidos configurados: libera geral")
    void semAllowedEmails_liberaGeral() throws Exception {
        AdminEmailAuthorizationFilter emptyFilter = new AdminEmailAuthorizationFilter();
        ReflectionTestUtils.setField(emptyFilter, "allowedEmailsStr", "");
        emptyFilter.initFilterBean();

        setOAuth2User("qualquer@gmail.com");

        request.setRequestURI("/admin-web");
        emptyFilter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Filtro com allowedEmailsStr nulo: inicializa lista vazia corretamente")
    void allowedEmailsNulo_inicializaVazio() throws Exception {
        AdminEmailAuthorizationFilter nullFilter = new AdminEmailAuthorizationFilter();
        ReflectionTestUtils.setField(nullFilter, "allowedEmailsStr", null);
        nullFilter.initFilterBean();

        setOAuth2User("qualquer@gmail.com");
        request.setRequestURI("/admin");
        nullFilter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ===================== PRINCIPAL NÃO É OAUTH2USER =====================

    @Test
    @DisplayName("Principal genérico (não OAuth2): loga aviso e deixa passar")
    void principalNaoOAuth2User_deixaPassar() throws Exception {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("some-string-principal");
        SecurityContextHolder.getContext().setAuthentication(auth);

        request.setRequestURI("/admin-web");
        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ===================== HELPERS =====================

    private void setOAuth2User(String email) {
        OAuth2User oauth2User =
                new DefaultOAuth2User(List.of(), Map.of("email", email, "sub", "12345"), "sub");
        OAuth2AuthenticationToken token =
                new OAuth2AuthenticationToken(oauth2User, List.of(), "google");
        SecurityContextHolder.getContext().setAuthentication(token);
    }
}
