/* (c) 2026 | 30/09/2026 */
package net.ddns.adambravo79.tmill.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.ObjectMapper;

class AppConfigTest {

    private AppConfig appConfig;

    @BeforeEach
    void setUp() {
        appConfig = new AppConfig();

        ReflectionTestUtils.setField(appConfig, "botToken", "test-token-123");
    }

    @Test
    void botToken_deveRetornarTokenConfigurado() {
        String token = appConfig.botToken();

        assertThat(token).isEqualTo("test-token-123");
    }

    @Test
    void botToken_deveRetornarTokenNaoNulo() {
        String token = appConfig.botToken();

        assertThat(token).isNotNull();
    }

    @Test
    void applicationTaskExecutor_deveCriarTaskExecutor() {
        AsyncTaskExecutor executor = appConfig.applicationTaskExecutor();

        assertThat(executor).isNotNull().isInstanceOf(TaskExecutorAdapter.class);
    }

    @Test
    void applicationTaskExecutor_deveExecutarUmaTarefa() {
        AsyncTaskExecutor executor = appConfig.applicationTaskExecutor();

        assertDoesNotThrow(() -> executor.execute(() -> {}));
    }

    @Test
    void restClient_deveCriarRestClient() {
        RestClient restClient = appConfig.restClient();

        assertThat(restClient).isNotNull();
    }

    @Test
    void toolsObjectMapper_deveCriarObjectMapper() {
        ObjectMapper objectMapper = appConfig.toolsObjectMapper();

        assertThat(objectMapper).isNotNull();
    }
}
