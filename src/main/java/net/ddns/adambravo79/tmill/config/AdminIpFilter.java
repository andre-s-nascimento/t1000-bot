package net.ddns.adambravo79.tmill.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Filtro de segurança para endpoints /admin. Bloqueia requisições de IPs não autorizados. Se
 * admin.allowed-ips estiver vazio, permite tudo (modo dev).
 */
@Slf4j
@Component
@Order(1)
public class AdminIpFilter extends OncePerRequestFilter {

    @Value("${admin.allowed-ips:}")
    private String allowedIpsStr;

    private List<String> allowedIps;

    @Override
    @SuppressWarnings("null")
    protected void initFilterBean() {
        if (allowedIpsStr != null && !allowedIpsStr.isBlank()) {
            allowedIps = Arrays.stream(allowedIpsStr.split(",")).map(String::trim).toList();
            log.info("🛡️ Admin endpoints restritos aos IPs: {}", allowedIps);
        } else {
            allowedIps = List.of();
            log.warn("🛡️ Admin endpoints SEM restrição de IP (admin.allowed-ips não configurado)");
        }
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!path.startsWith("/admin")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Se não houver IPs configurados, permite (modo dev)
        if (allowedIps.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = extractClientIp(request);
        if (allowedIps.contains(clientIp)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("⛔ Acesso negado ao /admin de IP: {} | path: {}", clientIp, path);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write("⛔ Acesso negado. IP não autorizado.");
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-Ip");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
