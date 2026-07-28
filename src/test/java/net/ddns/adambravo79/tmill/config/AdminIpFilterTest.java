package net.ddns.adambravo79.tmill.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class AdminIpFilterTest {

    private AdminIpFilter adminIpFilter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        adminIpFilter = new AdminIpFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
        // Configura apenas allowedIpsStr
        ReflectionTestUtils.setField(adminIpFilter, "allowedIpsStr", "127.0.0.1, 192.168.0.1");
        // Não seta adminPath - assume que o valor padrão é "/admin"
        adminIpFilter.initFilterBean();
    }

    // ========================================================================
    // TESTES DO FILTRO
    // ========================================================================

    @Test
    void doFilterInternal_comIpPermitido_eRequisicaoAdmin_deveContinuar()
            throws ServletException, IOException {
        // Arrange
        // Assume que adminPath é "/admin" (valor padrão)
        ((MockHttpServletRequest) request).setRemoteAddr("127.0.0.1");
        ((MockHttpServletRequest) request).setRequestURI("/admin/usuarios");

        // Act
        adminIpFilter.doFilterInternal(request, response, chain);

        // Assert
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_comIpNaoPermitido_eRequisicaoAdmin_deveRetornar403()
            throws ServletException, IOException {
        // Arrange
        ((MockHttpServletRequest) request).setRemoteAddr("10.0.0.5");
        ((MockHttpServletRequest) request).setRequestURI("/admin/config");

        // Act
        adminIpFilter.doFilterInternal(request, response, chain);

        // Assert
        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void doFilterInternal_comIpNaoPermitido_masRequisicaoNaoAdmin_deveContinuar()
            throws ServletException, IOException {
        // Arrange
        ((MockHttpServletRequest) request).setRemoteAddr("10.0.0.5");
        ((MockHttpServletRequest) request).setRequestURI("/public/page");

        // Act
        adminIpFilter.doFilterInternal(request, response, chain);

        // Assert
        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isNotEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void doFilterInternal_comIpPermitido_eRequisicaoNaoAdmin_deveContinuar()
            throws ServletException, IOException {
        // Arrange
        ((MockHttpServletRequest) request).setRemoteAddr("192.168.0.1");
        ((MockHttpServletRequest) request).setRequestURI("/public/page");

        // Act
        adminIpFilter.doFilterInternal(request, response, chain);

        // Assert
        verify(chain).doFilter(request, response);
    }

    // ========================================================================
    // TESTES DE INICIALIZAÇÃO
    // ========================================================================

    @Test
    void initFilterBean_comListaValida_devePopularLista() {
        ReflectionTestUtils.setField(adminIpFilter, "allowedIpsStr", "10.0.0.1, 10.0.0.2");
        adminIpFilter.initFilterBean();

        @SuppressWarnings("unchecked")
        List<String> allowedIps =
                (List<String>) ReflectionTestUtils.getField(adminIpFilter, "allowedIps");
        assertThat(allowedIps).containsExactly("10.0.0.1", "10.0.0.2");
    }

    @Test
    void initFilterBean_comListaVazia_deveCriarListaVazia() {
        ReflectionTestUtils.setField(adminIpFilter, "allowedIpsStr", "");
        adminIpFilter.initFilterBean();

        @SuppressWarnings("unchecked")
        List<String> allowedIps =
                (List<String>) ReflectionTestUtils.getField(adminIpFilter, "allowedIps");
        assertThat(allowedIps).isEmpty();
    }

    @Test
    void initFilterBean_comEspacosEVirgulas_deveTrim() {
        ReflectionTestUtils.setField(adminIpFilter, "allowedIpsStr", " 10.0.0.1 , 10.0.0.2 ");
        adminIpFilter.initFilterBean();

        @SuppressWarnings("unchecked")
        List<String> allowedIps =
                (List<String>) ReflectionTestUtils.getField(adminIpFilter, "allowedIps");
        assertThat(allowedIps).containsExactly("10.0.0.1", "10.0.0.2");
    }

    @Test
    void initFilterBean_quandoAllowedIpsStrForNull_deveManterListaVazia() {
        ReflectionTestUtils.setField(adminIpFilter, "allowedIpsStr", null);
        adminIpFilter.initFilterBean();

        @SuppressWarnings("unchecked")
        List<String> allowedIps =
                (List<String>) ReflectionTestUtils.getField(adminIpFilter, "allowedIps");
        assertThat(allowedIps).isEmpty();
    }
}
