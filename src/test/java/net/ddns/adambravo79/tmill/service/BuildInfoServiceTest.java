package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BuildInfoServiceTest {

    private final BuildInfoService service = new BuildInfoService();

    @Test
    void logBuildInfo_deveExecutarSemExcecao() {
        // O método não lança exceção, apenas loga.
        // Verificamos que ele executa sem lançar exceção.
        assertThatCode(() -> service.logBuildInfo()).doesNotThrowAnyException();
    }
}
