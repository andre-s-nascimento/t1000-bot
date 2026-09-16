package net.ddns.adambravo79.tmill.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    // ===================== ROTAS NÃO PROTEGIDAS =====================

    @Test
    void deveIgnorarRotasDeOauth2() throws Exception {
        request.setRequestURI("/oauth2/authorization/google");
        filter.doFilterInternal(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    void deveIgnorarRotasDeLogin() throws Exception {
        request.setRequestURI("/login/oauth2/code/google");
        filter.doFilterInternal(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    void deveIgnorarRotasWellKnown() throws Exception {
        request.setRequestURI("/.well-known/appspecific/com.chrome.devtools.json");
        filter.doFilterInternal(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    void deveIgnorarRotasNaoAdmin() throws Exception {
        request.setRequestURI("/public/page");
        filter.doFilterInternal(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    // ===================== SEM AUTENTICAÇÃO =====================

    @Test
    void semAuth_deixaSpringSecurityTratar() throws Exception {
        request.setRequestURI("/admin-web");
        filter.doFilterInternal(request, response, chain);
        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // ===================== AUTENTICADO E AUTORIZADO =====================

    @Test
    void autenticadoAutorizado_deixaPassar() throws Exception {
        setOAuth2User("autorizado@gmail.com");

        request.setRequestURI("/admin-web");
        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void autenticadoAutorizadoEmAdmin_Api_deixaPassar() throws Exception {
        setOAuth2User("outro@gmail.com");

        request.setRequestURI("/admin/birthdays");
        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ===================== AUTENTICADO E NÃO AUTORIZADO =====================

    @Test
    void autenticadoNaoAutorizado_bloqueiaERedireciona() throws Exception {
        setOAuth2User("intruso@gmail.com");

        request.setRequestURI("/admin-web");
        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getRedirectedUrl()).isEqualTo("/oauth2/authorization/google");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void autenticadoNaoAutorizadoEmAdminApi_bloqueia() throws Exception {
        setOAuth2User("intruso@gmail.com");

        request.setRequestURI("/admin/birthdays");
        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getRedirectedUrl()).isEqualTo("/oauth2/authorization/google");
    }

    // ===================== CASO SEM ALLOWED EMAILS CONFIGURADO =====================

    @Test
    void semAllowedEmails_liberaGeral() throws Exception {
        AdminEmailAuthorizationFilter emptyFilter = new AdminEmailAuthorizationFilter();
        ReflectionTestUtils.setField(emptyFilter, "allowedEmailsStr", "");
        emptyFilter.initFilterBean();

        setOAuth2User("qualquer@gmail.com");

        request.setRequestURI("/admin-web");
        emptyFilter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ===================== PRINCIPAL NÃO É OAUTH2USER =====================

    @Test
    void principalNaoOAuth2User_deixaPassar() throws Exception {
        // Simula um Authentication genérico (ex: usuário de teste) sem ser OAuth2User
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("some-principal");
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
