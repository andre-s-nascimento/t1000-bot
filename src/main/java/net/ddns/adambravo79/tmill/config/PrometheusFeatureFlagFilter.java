package net.ddns.adambravo79.tmill.config;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import net.ddns.adambravo79.tmill.service.feature.FeatureFlagAdminService;

/**
 * Filtro que bloqueia o endpoint {@code /actuator/prometheus} quando a flag {@code
 * metrics.prometheus.enabled} está desabilitada.
 *
 * <p><b>Importante:</b> este filtro <b>não</b> é anotado com {@code @Component} — ele é
 * registrado explicitamente pelo {@link SecurityConfig} via {@code addFilterBefore}. Isso evita
 * que o Spring Boot o registre automaticamente como filtro servlet global (fora da cadeia de
 * segurança), o que causaria dupla execução e interferência em outras rotas.
 *
 * <p>É criado como bean pelo {@link SecurityConfig} para permitir injeção de dependências.
 */
@RequiredArgsConstructor
public class PrometheusFeatureFlagFilter extends OncePerRequestFilter {

    private static final String PROMETHEUS_PATH = "/actuator/prometheus";
    private static final String FLAG_METRICS_PROMETHEUS = "metrics.prometheus.enabled";

    private final FeatureFlagAdminService featureFlags;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (request.getRequestURI().startsWith(PROMETHEUS_PATH)
                && !featureFlags.isEnabled(FLAG_METRICS_PROMETHEUS)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(request, response);
    }
}
