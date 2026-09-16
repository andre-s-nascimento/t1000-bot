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
    protected void initFilterBean() {
        allowedEmails =
                (allowedEmailsStr == null || allowedEmailsStr.isBlank())
                        ? List.of()
                        : Arrays.stream(allowedEmailsStr.split(","))
                                .map(String::trim)
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
        if (path.startsWith("/oauth2")
                || path.startsWith("/login")
                || path.startsWith("/.well-known")) {
            chain.doFilter(request, response);
            return;
        }

        // Só protege /admin e /admin-web
        if (!path.startsWith("/admin") && !path.startsWith("/admin-web")) {
            chain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // LOG DE DIAGNÓSTICO — sempre loga pra /admin e /admin-web
        log.info(
                "🔍 path={} auth={} sessionId={}",
                path,
                auth != null
                        ? auth.getClass().getSimpleName()
                                + "(authenticated="
                                + auth.isAuthenticated()
                                + ", principal="
                                + auth.getPrincipal().getClass().getSimpleName()
                                + ")"
                        : "null",
                request.getSession(false) != null ? request.getSession().getId() : "sem-sessão");

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
                SecurityContextHolder.clearContext();
                if (request.getSession(false) != null) {
                    request.getSession().invalidate();
                }
                // Remove o cookie JSESSIONID
                Cookie cookie = new Cookie("JSESSIONID", null);
                cookie.setMaxAge(0);
                cookie.setPath("/");
                response.addCookie(cookie);

                // Redireciona direto pro Google, forçando a escolha de conta
                response.sendRedirect("/oauth2/authorization/google");
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
}
