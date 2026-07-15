package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorldCupUpdaterServiceTest {

    @Mock private StaticWorldCupService worldCupService;
    @Mock private RestClient restClient;
    @Mock private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock private RestClient.RequestHeadersSpec requestHeadersSpec;
    @Mock private RestClient.ResponseSpec responseSpec;

    private WorldCupUpdaterService service;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        service = new WorldCupUpdaterService(worldCupService);
        ReflectionTestUtils.setField(service, "restClient", restClient);
        ReflectionTestUtils.setField(service, "updateEnabled", true);
        ReflectionTestUtils.setField(service, "updateUrl", "https://test.com/worldcup.json");
        ReflectionTestUtils.setField(
                service, "destinationPath", tempDir.resolve("worldcup.json").toString());

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }

    // =========================
    // INICIALIZAÇÃO
    // =========================

    @Test
    void init_deveLogarQuandoUpdateEnabledTrue() {
        service.init();
        // Não lança exceção
    }

    @Test
    void init_deveLogarQuandoUpdateEnabledFalse() {
        ReflectionTestUtils.setField(service, "updateEnabled", false);
        service.init();
        // Não lança exceção
    }

    // =========================
    // ATUALIZAÇÃO COM SUCESSO
    // =========================

    @Test
    void updateWorldCupData_deveBaixarSalvarERecarregar() throws Exception {
        byte[] dados = "{\"matches\":[{\"id\":1}]}".getBytes();
        when(responseSpec.body(byte[].class)).thenReturn(dados);

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
        ReflectionTestUtils.setField(service, "updateEnabled", false);
        service.updateWorldCupData();
        verifyNoInteractions(restClient);
        verifyNoInteractions(worldCupService);
    }

    // =========================
    // DADOS INVÁLIDOS
    // =========================

    @Test
    void updateWorldCupData_deveIgnorarQuandoDadosNulos() {
        when(responseSpec.body(byte[].class)).thenReturn(null);

        service.updateWorldCupData();

        Path destPath =
                Paths.get(ReflectionTestUtils.getField(service, "destinationPath").toString());
        assertThat(Files.exists(destPath)).isFalse();
        verify(worldCupService, never()).loadMatches();
    }

    @Test
    void updateWorldCupData_deveIgnorarQuandoDadosVazios() {
        when(responseSpec.body(byte[].class)).thenReturn(new byte[0]);

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
        when(responseSpec.body(byte[].class)).thenThrow(new RestClientException("Erro de rede"));

        service.updateWorldCupData();

        Path destPath =
                Paths.get(ReflectionTestUtils.getField(service, "destinationPath").toString());
        assertThat(Files.exists(destPath)).isFalse();
        verify(worldCupService, never()).loadMatches();
    }

    @Test
    void updateWorldCupData_deveCapturarExceptionGenerica() {
        when(responseSpec.body(byte[].class)).thenThrow(new RuntimeException("Erro inesperado"));

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
        WorldCupUpdaterService spyService = spy(service);
        doNothing().when(spyService).updateWorldCupData();

        spyService.forceUpdate();

        verify(spyService).updateWorldCupData();
    }
}
