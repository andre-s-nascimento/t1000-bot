/* (c) 2026 | 30/09/2026 */
package net.ddns.adambravo79.tmill.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConfigLoadExceptionTest {

    @Test
    void construtorComCause_deveConfigurarMensagemResourcePathECause() {
        String resourcePath = "/tmp/config.json";
        String message = "JSON malformado";
        Throwable cause = new IllegalArgumentException("JSON inválido");

        ConfigLoadException exception = new ConfigLoadException(resourcePath, message, cause);

        assertThat(exception.getResourcePath()).isEqualTo(resourcePath);

        assertThat(exception.getMessage())
                .isEqualTo("Falha ao carregar config [/tmp/config.json]: JSON malformado");

        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    void construtorSemCause_deveConfigurarMensagemEResourcePath() {
        String resourcePath = "classpath:test.json";
        String message = "Arquivo não encontrado";

        ConfigLoadException exception = new ConfigLoadException(resourcePath, message);

        assertThat(exception.getResourcePath()).isEqualTo(resourcePath);

        assertThat(exception.getMessage())
                .isEqualTo(
                        "Falha ao carregar config [classpath:test.json]: Arquivo não encontrado");

        assertThat(exception.getCause()).isNull();
    }

    @Test
    void getResourcePath_deveRetornarCaminhoInformado() {
        ConfigLoadException exception =
                new ConfigLoadException("config/prompts.json", "Falha ao ler configuração");

        assertThat(exception.getResourcePath()).isEqualTo("config/prompts.json");
    }
}
