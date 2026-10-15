package net.ddns.adambravo79.tmill.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.ddns.adambravo79.tmill.service.feature.FeatureFlagAdminService;

@Configuration
public class AdminFilterConfig {

    @Bean
    public AdminEmailAuthorizationFilter adminEmailAuthorizationFilter() {
        return new AdminEmailAuthorizationFilter();
    }

    @Bean
    public FilterRegistrationBean<AdminEmailAuthorizationFilter>
            adminEmailAuthorizationFilterRegistration(AdminEmailAuthorizationFilter filter) {
        FilterRegistrationBean<AdminEmailAuthorizationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Cria o filtro de Prometheus como bean (injetando {@link FeatureFlagAdminService}).
     *
     * <p>Não é {@code @Component} de propósito: se fosse, o Spring Boot o registraria
     * automaticamente como filtro servlet global, fora da cadeia do Spring Security, causando
     * dupla execução e interferência em {@code @WebMvcTest}.
     */
    @Bean
    public PrometheusFeatureFlagFilter prometheusFeatureFlagFilter(
            FeatureFlagAdminService featureFlagAdminService) {
        return new PrometheusFeatureFlagFilter(featureFlagAdminService);
    }

    /**
     * Desabilita o registro automático do {@link PrometheusFeatureFlagFilter} como filtro servlet.
     */
    @Bean
    public FilterRegistrationBean<PrometheusFeatureFlagFilter> prometheusFilterRegistration(
            PrometheusFeatureFlagFilter filter) {
        FilterRegistrationBean<PrometheusFeatureFlagFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
