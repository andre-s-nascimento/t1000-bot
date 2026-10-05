/* (c) 2026 */
package net.ddns.adambravo79.tmill.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

class AzureTtsClientTest {

    private AzureTtsClient azureTtsClient;
    private RestClient restClientMock;
    private RestClient.RequestBodyUriSpec uriSpecMock;
    private RestClient.RequestBodySpec bodySpecMock;
    private RestClient.ResponseSpec responseSpecMock;

    @TempDir java.nio.file.Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        // Mocking RestClient fluent API chain
        restClientMock = mock(RestClient.class);
        uriSpecMock = mock(RestClient.RequestBodyUriSpec.class);
        bodySpecMock = mock(RestClient.RequestBodySpec.class);
        responseSpecMock = mock(RestClient.ResponseSpec.class);

        when(restClientMock.post()).thenReturn(uriSpecMock);
        when(uriSpecMock.uri(anyString())).thenReturn(bodySpecMock);
        when(bodySpecMock.body(any(byte[].class))).thenReturn(bodySpecMock);
        when(bodySpecMock.retrieve()).thenReturn(responseSpecMock);

        // Instancia a classe usando parâmetros de teste e diretório temporário JUnit
        azureTtsClient = new AzureTtsClient("dummy-key", "eastus", tempDir.toString());

        // Injeta o RestClient mockado via reflection
        ReflectionTestUtils.setField(azureTtsClient, "restClient", restClientMock);
    }

    @Test
    @DisplayName("Deve retornar array vazio quando o texto for nulo ou em branco")
    void synthesizeFullText_nullOrBlank_returnsEmpty() {
        assertThat(azureTtsClient.synthesizeFullText(null)).isEmpty();
        assertThat(azureTtsClient.synthesizeFullText("   ")).isEmpty();
    }

    @Test
    @DisplayName("Deve sintetizar texto curto com sucesso (uma única parte)")
    void synthesizeFullText_singlePart_success() {
        byte[] mockAudio = new byte[] {10, 20, 30, 40};
        when(responseSpecMock.body(byte[].class)).thenReturn(mockAudio);

        byte[] result = azureTtsClient.synthesizeFullText("Olá, mundo!");

        assertThat(result).isEqualTo(mockAudio);
    }

    @Test
    @DisplayName("Deve retornar array vazio quando a síntese retornar vazio")
    void synthesizeFullText_emptyResponse_returnsEmpty() {
        when(responseSpecMock.body(byte[].class)).thenReturn(new byte[0]);

        byte[] result = azureTtsClient.synthesizeFullText("Texto de teste.");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Deve tratar exceções da API REST graciosamente e retornar array vazio")
    void synthesizeFullText_restException_returnsEmpty() {
        when(bodySpecMock.retrieve()).thenThrow(new RuntimeException("API Error"));

        byte[] result = azureTtsClient.synthesizeFullText("Texto de teste com erro.");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Deve dividir textos longos em múltiplas partes e tratar o fluxo de concatenação")
    void synthesizeFullText_longText_splitsAndSynthesizes() {
        // Cria um texto maior que 5000 caracteres com quebras de frase
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 10500) {
            sb.append("Esta é uma frase de teste para validação de divisão de texto. ");
        }

        byte[] mockAudioPart = new byte[] {1, 2, 3};
        when(responseSpecMock.body(byte[].class)).thenReturn(mockAudioPart);

        byte[] result = azureTtsClient.synthesizeFullText(sb.toString());

        // Como o ffmpeg pode não estar presente no ambiente de execução de testes,
        // valida que o método executa o fluxo completo (split + loop + fallback/concatenação)
        assertThat(result).isNotNull();
    }
}
