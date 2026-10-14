package net.ddns.adambravo79.tmill.config;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import net.ddns.adambravo79.tmill.service.feature.FeatureFlagAdminService;

@Component
@RequiredArgsConstructor
public class PrometheusFeatureFlagFilter extends OncePerRequestFilter {

    private final FeatureFlagAdminService featureFlags;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (request.getRequestURI().startsWith("/actuator/prometheus")
                && !featureFlags.isEnabled("metrics.prometheus.enabled")) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(request, response);
    }
}
