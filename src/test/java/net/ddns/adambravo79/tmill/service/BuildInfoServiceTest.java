package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.core.io.ClassPathResource;

class BuildInfoServiceTest {

    private final BuildInfoService service = new BuildInfoService();

    @Test
    void logBuildInfo_quandoArquivoExiste_deveLogarPropriedades() {
        assertThatCode(service::logBuildInfo).doesNotThrowAnyException();
    }

    @Test
    void logBuildInfo_quandoFalhaAoCarregarArquivo_deveLogarWarn() {
        // Usa mockConstruction para interceptar a criação do ClassPathResource
        try (MockedConstruction<ClassPathResource> mocked =
                mockConstruction(
                        ClassPathResource.class,
                        (mock, context) -> {
                            // Força o exists() a retornar true
                            when(mock.exists()).thenReturn(true);
                            // Lança exceção ao chamar getInputStream()
                            try {
                                when(mock.getInputStream())
                                        .thenThrow(new IOException("Simulated IO error"));
                            } catch (IOException e) {
                                // nunca deve acontecer aqui
                            }
                        })) {

            // Executa o método, que deve capturar a exceção e logar warn
            assertThatCode(service::logBuildInfo).doesNotThrowAnyException();
        }
    }
}
