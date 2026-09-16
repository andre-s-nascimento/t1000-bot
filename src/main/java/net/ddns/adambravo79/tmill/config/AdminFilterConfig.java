/* (c) 2026 | 15/09/2026 */
package net.ddns.adambravo79.tmill.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdminFilterConfig {

    @Bean
    public AdminEmailAuthorizationFilter adminEmailAuthorizationFilter() {
        return new AdminEmailAuthorizationFilter();
    }

    /**
     * Desabilita o registro automático do filtro como filtro de servlet.
     *
     * <p>Sem isso, o Spring Boot registra o filtro na cadeia de servlet ANTES do Spring Security, o
     * que faz o filtro rodar antes do SecurityContextHolder ser populado. O filtro deve ser
     * registrado APENAS na cadeia do Spring Security, via {@code addFilterAfter(...)}.
     */
    @Bean
    public FilterRegistrationBean<AdminEmailAuthorizationFilter>
            adminEmailAuthorizationFilterRegistration(AdminEmailAuthorizationFilter filter) {
        FilterRegistrationBean<AdminEmailAuthorizationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
