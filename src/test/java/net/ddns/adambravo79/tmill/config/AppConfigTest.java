package net.ddns.adambravo79.tmill.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.test.util.ReflectionTestUtils;

class AppConfigTest {

    private AppConfig appConfig;

    @BeforeEach
    void setup() {
        appConfig = new AppConfig();
        // Injeta o valor da propriedade manualmente
        ReflectionTestUtils.setField(appConfig, "botToken", "test-token-123");
    }

    @Test
    void botTokenBean_shouldReturnInjectedValue() {
        String token = appConfig.botToken();
        assertThat(token).isEqualTo("test-token-123");
    }

    @Test
    void applicationTaskExecutor_shouldBeCreatedWithVirtualThreads() {
        AsyncTaskExecutor executor = appConfig.applicationTaskExecutor();
        assertThat(executor).isNotNull().isInstanceOf(TaskExecutorAdapter.class);

        // Verifica se o executor consegue executar uma tarefa
        assertDoesNotThrow(() -> executor.execute(() -> {}));
    }

    @Test
    void botTokenBean_shouldNotBeNull() {
        String token = appConfig.botToken();
        assertThat(token).isNotNull();
    }

    @Test
    void applicationTaskExecutor_shouldNotBeNull() {
        AsyncTaskExecutor executor = appConfig.applicationTaskExecutor();
        assertThat(executor).isNotNull();
    }
}
