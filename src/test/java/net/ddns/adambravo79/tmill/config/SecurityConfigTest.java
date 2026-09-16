package net.ddns.adambravo79.tmill.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.test.util.ReflectionTestUtils;

class SecurityConfigTest {

    @Test
    void clientRegistrationRepository_comCredenciais_retornaGoogle() {
        SecurityConfig config = new SecurityConfig(null);
        ReflectionTestUtils.setField(config, "googleClientId", "fake-client-id");
        ReflectionTestUtils.setField(config, "googleClientSecret", "fake-secret");

        ClientRegistrationRepository repo = config.clientRegistrationRepository();

        assertThat(repo).isNotNull();
        assertThat(repo).isInstanceOf(InMemoryClientRegistrationRepository.class);

        ClientRegistration google =
                ((InMemoryClientRegistrationRepository) repo).findByRegistrationId("google");
        assertThat(google).isNotNull();
        assertThat(google.getClientId()).isEqualTo("fake-client-id");
        assertThat(google.getScopes()).contains("openid", "profile", "email");
        assertThat(google.getProviderDetails().getAuthorizationUri())
                .contains("accounts.google.com")
                .contains("prompt=select_account");
    }

    @Test
    void clientRegistrationRepository_semCredenciais_retornaVazio() {
        SecurityConfig config = new SecurityConfig(null);
        ReflectionTestUtils.setField(config, "googleClientId", "");
        ReflectionTestUtils.setField(config, "googleClientSecret", "");

        ClientRegistrationRepository repo = config.clientRegistrationRepository();

        assertThat(repo).isNotNull();
        assertThat(repo.findByRegistrationId("google")).isNull();
    }

    @Test
    void clientRegistrationRepository_semClientSecret_retornaVazio() {
        SecurityConfig config = new SecurityConfig(null);
        ReflectionTestUtils.setField(config, "googleClientId", "fake-client-id");
        ReflectionTestUtils.setField(config, "googleClientSecret", "");

        ClientRegistrationRepository repo = config.clientRegistrationRepository();
        assertThat(repo.findByRegistrationId("google")).isNull();
    }

    @Test
    void parseAllowedEmails_comLista_retornaLista() throws Exception {
        SecurityConfig config = new SecurityConfig(null);
        ReflectionTestUtils.setField(config, "allowedEmailsStr", "a@x.com, b@x.com ,c@x.com");

        java.lang.reflect.Method m = SecurityConfig.class.getDeclaredMethod("parseAllowedEmails");
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<String> emails = (java.util.List<String>) m.invoke(config);

        assertThat(emails).containsExactly("a@x.com", "b@x.com", "c@x.com");
    }

    @Test
    void parseAllowedEmails_vazio_retornaListaVazia() throws Exception {
        SecurityConfig config = new SecurityConfig(null);
        ReflectionTestUtils.setField(config, "allowedEmailsStr", "");

        java.lang.reflect.Method m = SecurityConfig.class.getDeclaredMethod("parseAllowedEmails");
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<String> emails = (java.util.List<String>) m.invoke(config);

        assertThat(emails).isEmpty();
    }

    @Test
    void parseAllowedEmails_nulo_retornaListaVazia() throws Exception {
        SecurityConfig config = new SecurityConfig(null);
        ReflectionTestUtils.setField(config, "allowedEmailsStr", null);

        java.lang.reflect.Method m = SecurityConfig.class.getDeclaredMethod("parseAllowedEmails");
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<String> emails = (java.util.List<String>) m.invoke(config);

        assertThat(emails).isEmpty();
    }
}
