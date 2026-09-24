package net.ddns.adambravo79.tmill.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${admin.google.client-id:}")
    private String googleClientId;

    @Value("${admin.google.client-secret:}")
    private String googleClientSecret;

    @Value("${admin.allowed-emails:}")
    private String allowedEmailsStr;

    // 👈 NOVO — flag de dev
    @Value("${admin.security.disabled:false}")
    private boolean securityDisabled;

    private final AdminEmailAuthorizationFilter adminEmailAuthorizationFilter;
    private final Environment environment; // 👈 NOVO

    public SecurityConfig(
            AdminEmailAuthorizationFilter adminEmailAuthorizationFilter,
            Environment environment) { // 👈 NOVO
        this.adminEmailAuthorizationFilter = adminEmailAuthorizationFilter;
        this.environment = environment;
    }

    /**
     * 👈 NOVO — Falha o boot se a flag de desabilitar segurança for usada em produção. Isso evita
     * deploy acidental de um ambiente sem autenticação.
     */
    @PostConstruct
    public void validateSecurityConfig() {
        if (securityDisabled) {
            boolean isProd = environment.acceptsProfiles(Profiles.of("prod"));

            if (isProd) {
                throw new IllegalStateException(
                        "🚨 FALHA CRÍTICA: `admin.security.disabled=true` NÃO é permitido no"
                                + " profile `prod`. Desative a flag ou remova o profile `prod`.");
            }

            log.warn("");
            log.warn("╔════════════════════════════════════════════════════════════════╗");
            log.warn("║  ⚠️  MODO DEV ATIVADO — SPRING SECURITY DESABILITADO          ║");
            log.warn("║                                                                ║");
            log.warn("║  Endpoints /admin/** e /admin-web/** estão SEM AUTENTICAÇÃO.   ║");
            log.warn("║                                                                ║");
            log.warn("║  ❌ NUNCA use isso em produção!                                ║");
            log.warn("║  ❌ NUNCA exponha essa porta publicamente!                     ║");
            log.warn("║                                                                ║");
            log.warn("║  Para desligar: ADMIN_SECURITY_DISABLED=false (ou remova).     ║");
            log.warn("╚════════════════════════════════════════════════════════════════╝");
            log.warn("");
        }
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        // 👈 NOVO — caminho "dev unlock": libera tudo, sem OAuth2, sem CSRF
        if (securityDisabled) {
            log.warn("🔓 SecurityFilterChain: modo dev ativo — todas as rotas liberadas");

            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .csrf(csrf -> csrf.disable())
                    .headers(headers -> headers.frameOptions(frame -> frame.disable()));

            return http.build();
        }

        // ===================== CAMINHO NORMAL (produção) =====================

        List<String> allowedEmails = parseAllowedEmails();

        http.authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/",
                                                "/actuator/health",
                                                "/favicon.ico",
                                                "/access-denied")
                                        .permitAll()
                                        .requestMatchers("/admin/**", "/admin-web/**")
                                        .authenticated()
                                        .anyRequest()
                                        .permitAll())
                .oauth2Login(
                        oauth2 ->
                                oauth2.successHandler(successHandler())
                                        .failureHandler(
                                                (request, response, exception) -> {
                                                    log.warn(
                                                            "❌ Falha no login OAuth2: {}",
                                                            exception.getMessage());
                                                    response.sendRedirect("/login?error");
                                                }))
                .logout(
                        logout ->
                                logout.logoutSuccessUrl("/")
                                        .invalidateHttpSession(true)
                                        .clearAuthentication(true)
                                        .deleteCookies("JSESSIONID"))
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository()))
                .sessionManagement(sm -> sm.sessionFixation().changeSessionId());

        // 👈 NOVO — ignora CSRF para /admin/** (API interna consumida por scripts)
        http.csrf(csrf -> csrf.ignoringRequestMatchers("/admin/**"));

        log.info("🛡️ SecurityConfig carregado. E-mails autorizados: {}", allowedEmails);

        http.addFilterAfter(
                adminEmailAuthorizationFilter,
                org.springframework.security.web.access.intercept.AuthorizationFilter.class);

        return http.build();
    }

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        if (googleClientId == null
                || googleClientId.isBlank()
                || googleClientSecret == null
                || googleClientSecret.isBlank()) {
            log.warn(
                    "⚠️ Google OAuth2 não configurado (client-id ou client-secret vazios). Login"
                            + " Google indisponível.");
            return registrationId -> null;
        }

        ClientRegistration google =
                ClientRegistration.withRegistrationId("google")
                        .clientId(googleClientId)
                        .clientSecret(googleClientSecret)
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                        .scope("openid", "profile", "email")
                        .authorizationUri(
                                "https://accounts.google.com/o/oauth2/v2/auth?prompt=select_account")
                        .tokenUri("https://www.googleapis.com/oauth2/v4/token")
                        .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                        .userNameAttributeName("sub")
                        .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                        .issuerUri("https://accounts.google.com")
                        .clientName("Google")
                        .build();

        log.info(
                "✅ ClientRegistrationRepository configurado para Google (clientId={}...)",
                googleClientId.substring(0, Math.min(12, googleClientId.length())));

        return new InMemoryClientRegistrationRepository(google);
    }

    private AuthenticationSuccessHandler successHandler() {
        return (request, response, authentication) -> {
            if (authentication.getPrincipal() instanceof OAuth2User oauth2User) {
                String email = oauth2User.getAttribute("email");
                String ip = extractClientIp(request);
                log.info("✅ Login admin bem-sucedido: email={} ip={}", email, ip);
            }
            response.sendRedirect("/admin-web");
        };
    }

    private List<String> parseAllowedEmails() {
        if (allowedEmailsStr == null || allowedEmailsStr.isBlank()) {
            return List.of();
        }
        return Arrays.stream(allowedEmailsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private String extractClientIp(jakarta.servlet.http.HttpServletRequest request) {
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
