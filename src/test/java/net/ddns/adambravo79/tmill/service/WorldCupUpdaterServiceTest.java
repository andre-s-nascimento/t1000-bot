package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.SimpleTriggerContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import net.ddns.adambravo79.tmill.service.config.JsonConfigLoader;
import net.ddns.adambravo79.tmill.service.feature.FeatureFlagAdminService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorldCupUpdaterServiceTest {

    @Mock private StaticWorldCupService worldCupService;
    @Mock private RestClient restClient;
    @Mock private RestClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;
    @Mock private RestClient.RequestHeadersSpec<?> requestHeadersSpec;
    @Mock private RestClient.ResponseSpec responseSpec;
    @Mock private FeatureFlagAdminService featureFlags;
    @Mock private JsonConfigLoader jsonConfigLoader;

    @Spy @InjectMocks private WorldCupUpdaterService service;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        // Flag de update da Copa habilitada por padrão em todos os testes.
        lenient().when(featureFlags.isEnabled("worldcup.update.enabled")).thenReturn(true);

        // @Value fields — não injetados por Mockito
        ReflectionTestUtils.setField(service, "updateUrl", "https://test.com/worldcup.json");
        ReflectionTestUtils.setField(
                service, "destinationPath", tempDir.resolve("worldcup.json").toString());

        // Cadeia fluent do RestClient
        doReturn(requestHeadersUriSpec).when(restClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(anyString());
        doReturn(responseSpec).when(requestHeadersSpec).retrieve();
    }

    // =========================
    // INICIALIZAÇÃO
    // =========================

    @Test
    void init_deveLogarQuandoUpdateEnabledTrue() {
        assertThatCode(() -> service.init()).doesNotThrowAnyException();
    }

    @Test
    void init_deveLogarQuandoUpdateEnabledFalse() {
        when(featureFlags.isEnabled("worldcup.update.enabled")).thenReturn(false);
        assertThatCode(() -> service.init()).doesNotThrowAnyException();
    }

    // =========================
    // ATUALIZAÇÃO COM SUCESSO
    // =========================

    @Test
    void updateWorldCupData_deveBaixarSalvarERecarregar() throws Exception {
        byte[] dados = "{\"matches\":[{\"id\":1}]}".getBytes();
        doReturn(dados).when(responseSpec).body(byte[].class);

        service.updateWorldCupData();

        Path destPath =
                Paths.get(ReflectionTestUtils.getField(service, "destinationPath").toString());
        assertThat(Files.exists(destPath)).isTrue();
        assertThat(Files.readString(destPath)).isEqualTo(new String(dados));
        verify(worldCupService).loadMatches();
    }

    // =========================
    // UPDATE DESATIVADO
    // =========================

    @Test
    void updateWorldCupData_deveIgnorarQuandoUpdateEnabledFalse() {
        when(featureFlags.isEnabled("worldcup.update.enabled")).thenReturn(false);
        service.updateWorldCupData();
        verifyNoInteractions(restClient);
        verifyNoInteractions(worldCupService);
    }

    // =========================
    // DADOS INVÁLIDOS
    // =========================

    @Test
    void updateWorldCupData_deveIgnorarQuandoDadosNulos() {
        doReturn(null).when(responseSpec).body(byte[].class);

        service.updateWorldCupData();

        Path destPath =
                Paths.get(ReflectionTestUtils.getField(service, "destinationPath").toString());
        assertThat(Files.exists(destPath)).isFalse();
        verify(worldCupService, never()).loadMatches();
    }

    @Test
    void updateWorldCupData_deveIgnorarQuandoDadosVazios() {
        doReturn(new byte[0]).when(responseSpec).body(byte[].class);

        service.updateWorldCupData();

        Path destPath =
                Paths.get(ReflectionTestUtils.getField(service, "destinationPath").toString());
        assertThat(Files.exists(destPath)).isFalse();
        verify(worldCupService, never()).loadMatches();
    }

    // =========================
    // EXCEÇÕES
    // =========================

    @Test
    void updateWorldCupData_deveCapturarRestClientException() {
        doThrow(new RestClientException("Erro de rede")).when(responseSpec).body(byte[].class);

        service.updateWorldCupData();

        Path destPath =
                Paths.get(ReflectionTestUtils.getField(service, "destinationPath").toString());
        assertThat(Files.exists(destPath)).isFalse();
        verify(worldCupService, never()).loadMatches();
    }

    @Test
    void updateWorldCupData_deveCapturarExceptionGenerica() {
        doThrow(new RuntimeException("Erro inesperado")).when(responseSpec).body(byte[].class);

        service.updateWorldCupData();

        Path destPath =
                Paths.get(ReflectionTestUtils.getField(service, "destinationPath").toString());
        assertThat(Files.exists(destPath)).isFalse();
        verify(worldCupService, never()).loadMatches();
    }

    // =========================
    // FORCE UPDATE
    // =========================

    @Test
    void forceUpdate_deveChamarUpdateWorldCupData() {
        doNothing().when(service).updateWorldCupData();

        service.forceUpdate();

        verify(service).updateWorldCupData();
    }

    @Test
    @DisplayName("configureTasks: deve registrar as tasks e invocar getCron cobrindo a lambda")
    void configureTasks_deveRegistrarAsTasksECobrirLambda() {
        // Preparamos o mock do jsonConfigLoader com todas as chaves possíveis
        // para que este teste seja reaproveitável em todas as classes
        lenient()
                .when(jsonConfigLoader.loadConfig(anyString(), eq(Map.class), anyString()))
                .thenReturn(
                        Optional.of(
                                Map.of(
                                        "cron", "0 0 12 * * *",
                                        "updateCron", "0 0 12 * * *",
                                        "hourlyCron", "0 0 12 * * *",
                                        "weeklyCron", "0 0 12 * * *",
                                        "morningCron", "0 0 12 * * *",
                                        "eveningCron", "0 0 12 * * *",
                                        "noonCron", "0 0 12 * * *",
                                        "checkCron", "0 0 12 * * *",
                                        "cleanCron", "0 0 12 * * *")));

        ScheduledTaskRegistrar taskRegistrar = mock(ScheduledTaskRegistrar.class);

        // Aciona o método que registra as tarefas
        service.configureTasks(taskRegistrar);

        // Captura as triggers que foram adicionadas
        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(taskRegistrar, atLeastOnce())
                .addTriggerTask(any(Runnable.class), triggerCaptor.capture());

        // Aciona a lambda de cada trigger para cobrir o código do getCron()
        // 🔧 FIX: Usar SimpleTriggerContext em vez de mock(TriggerContext.class)
        SimpleTriggerContext ctx = new SimpleTriggerContext();
        for (Trigger trigger : triggerCaptor.getAllValues()) {
            trigger.nextExecution(ctx);
        }
    }
}
