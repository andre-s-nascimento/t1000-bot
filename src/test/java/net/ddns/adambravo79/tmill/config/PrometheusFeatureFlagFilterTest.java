package net.ddns.adambravo79.tmill.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import net.ddns.adambravo79.tmill.service.feature.FeatureFlagAdminService;

@ExtendWith(MockitoExtension.class)
class PrometheusFeatureFlagFilterTest {

    @Mock private FeatureFlagAdminService featureFlags;
    @Mock private FilterChain chain;

    @InjectMocks private PrometheusFeatureFlagFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        // Default: flag habilitada (não bloqueia)
        lenient().when(featureFlags.isEnabled("metrics.prometheus.enabled")).thenReturn(true);
    }

    // =========================================================================
    // Deve deixar passar (chain.doFilter chamado)
    // =========================================================================

    static Stream<Arguments> deveDeixarPassar() {
        return Stream.of(
                // (flagEnabled, requestUri, descrição)
                Arguments.of(true, "/actuator/prometheus", "flag on + /actuator/prometheus"),
                Arguments.of(true, "/actuator/prometheus/subpath", "flag on + subpath"),
                Arguments.of(false, "/actuator/health", "flag off + /actuator/health"),
                Arguments.of(false, "/admin/test", "flag off + /admin/test"),
                Arguments.of(true, "/actuator/health", "flag on + /actuator/health"));
    }

    @ParameterizedTest(name = "[{index}] {2}: deixa passar")
    @MethodSource("deveDeixarPassar")
    @DisplayName("Filtro deve deixar passar rotas que não sejam prometheus bloqueado")
    void deveDeixarPassar(boolean flagEnabled, String requestUri, String descricao)
            throws ServletException, IOException {

        when(featureFlags.isEnabled("metrics.prometheus.enabled")).thenReturn(flagEnabled);
        request.setRequestURI(requestUri);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(any(Integer.class));
    }

    // =========================================================================
    // Deve bloquear com 404 (chain.doFilter NÃO chamado)
    // =========================================================================

    static Stream<Arguments> deveBloquear() {
        return Stream.of(
                Arguments.of("/actuator/prometheus", "flag off + /actuator/prometheus"),
                Arguments.of("/actuator/prometheus/subpath", "flag off + subpath"));
    }

    @ParameterizedTest(name = "[{index}] {1}: retorna 404")
    @MethodSource("deveBloquear")
    @DisplayName("Filtro deve retornar 404 quando a flag está off e a rota é prometheus")
    void deveBloquear(String requestUri, String descricao) throws ServletException, IOException {

        when(featureFlags.isEnabled("metrics.prometheus.enabled")).thenReturn(false);
        request.setRequestURI(requestUri);

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(404);
        verify(chain, never()).doFilter(request, response);
    }
}
