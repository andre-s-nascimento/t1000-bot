package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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
    @Mock private RestClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;
    @Mock private RestClient.RequestHeadersSpec<?> requestHeadersSpec;
    @Mock private RestClient.ResponseSpec responseSpec;

    @Spy @InjectMocks private WorldCupUpdaterService service;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {

        ReflectionTestUtils.setField(service, "restClient", restClient);
        ReflectionTestUtils.setField(service, "updateEnabled", true);
        ReflectionTestUtils.setField(service, "updateUrl", "https://test.com/worldcup.json");
        ReflectionTestUtils.setField(
                service, "destinationPath", tempDir.resolve("worldcup.json").toString());

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
        ReflectionTestUtils.setField(service, "updateEnabled", false);
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
}
