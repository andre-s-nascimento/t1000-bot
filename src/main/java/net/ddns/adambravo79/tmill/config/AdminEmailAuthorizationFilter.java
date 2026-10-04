/* (c) 2026 | 15/09/2026 */
package net.ddns.adambravo79.tmill.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AdminEmailAuthorizationFilter extends OncePerRequestFilter {

    @Value("${admin.allowed-emails:}")
    private String allowedEmailsStr;

    private List<String> allowedEmails;

    @Override
    @SuppressWarnings(
            "null") // 🔧 FIX: Silencia o JDT Null Type Safety para priorizar o Method Reference do
    // Sonar
    protected void initFilterBean() {
        allowedEmails =
                (allowedEmailsStr == null || allowedEmailsStr.isBlank())
                        ? List.of()
                        : Arrays.stream(allowedEmailsStr.split(","))
                                .map(String::trim) // 🔧 FIX: Restaurado para Method Reference
                                // agradando o Sonar (java:S1612)
                                .filter(s -> !s.isEmpty())
                                .toList();
        log.info("🔧 AdminEmailAuthorizationFilter inicializado. allowedEmails={}", allowedEmails);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Não processa rotas de OAuth2/login/well-known
        if (isIgnoredPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Só protege /admin e /admin-web
        if (!isProtectedPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // LOG DE DIAGNÓSTICO — sempre loga pra /admin e /admin-web
        String sessionId =
                request.getSession(false) != null ? request.getSession().getId() : "sem-sessão";
        log.info("🔍 path={} auth={} sessionId={}", path, formatAuthDetails(auth), sessionId);

        if (auth == null || !auth.isAuthenticated()) {
            log.info("ℹ️ auth null ou não autenticado. Deixando o Spring Security tratar.");
            chain.doFilter(request, response);
            return;
        }

        if (auth.getPrincipal() instanceof OAuth2User oauth2User) {
            String email = oauth2User.getAttribute("email");
            boolean autorizado = allowedEmails.isEmpty() || allowedEmails.contains(email);

            log.info("🔍 email={} autorizado={}", email, autorizado);

            if (!autorizado) {
                log.warn("⛔ Bloqueando acesso em tempo real: email={} path={}", email, path);
                handleUnauthorizedAccess(request, response);
                return;
            }

            log.info("✅ Acesso autorizado: email={} path={}", email, path);
        } else {
            log.warn(
                    "⚠️ Principal não é OAuth2User: {}. Deixando passar.",
                    auth.getPrincipal().getClass().getName());
        }

        chain.doFilter(request, response);
    }

    // =========================================================================
    // MÉTODOS AUXILIARES (Refatoração para java:S3776 - Complexidade Cognitiva)
    // =========================================================================

    private boolean isIgnoredPath(String path) {
        return path.startsWith("/oauth2")
                || path.startsWith("/login")
                || path.startsWith("/.well-known");
    }

    private boolean isProtectedPath(String path) {
        return path.startsWith("/admin") || path.startsWith("/admin-web");
    }

    private String formatAuthDetails(Authentication auth) {
        if (auth == null) {
            return "null";
        }
        return String.format(
                "%s(authenticated=%s, principal=%s)",
                auth.getClass().getSimpleName(),
                auth.isAuthenticated(),
                auth.getPrincipal().getClass().getSimpleName());
    }

    private void handleUnauthorizedAccess(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) {
            request.getSession().invalidate();
        }

        // Remove o cookie JSESSIONID
        Cookie cookie = new Cookie("JSESSIONID", null);
        cookie.setMaxAge(0);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        response.addCookie(cookie);

        // Redireciona direto pro Google, forçando a escolha de conta
        response.sendRedirect("/oauth2/authorization/google");
    }
}
