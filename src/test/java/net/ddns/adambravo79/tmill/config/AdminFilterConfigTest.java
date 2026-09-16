package net.ddns.adambravo79.tmill.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

class AdminFilterConfigTest {

    @Test
    void deveCriarFiltro() {
        AdminFilterConfig config = new AdminFilterConfig();
        AdminEmailAuthorizationFilter filter = config.adminEmailAuthorizationFilter();
        assertThat(filter).isNotNull();
    }

    @Test
    void registrationDeveEstarDesabilitado() {
        AdminFilterConfig config = new AdminFilterConfig();
        AdminEmailAuthorizationFilter filter = config.adminEmailAuthorizationFilter();
        FilterRegistrationBean<AdminEmailAuthorizationFilter> registration =
                config.adminEmailAuthorizationFilterRegistration(filter);

        assertThat(registration).isNotNull();
        assertThat(registration.isEnabled()).isFalse();
    }
}
