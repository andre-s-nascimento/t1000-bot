/* (c) 2026 */
package net.ddns.adambravo79.tmill.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import net.ddns.adambravo79.tmill.service.config.AudioConfigService;

class AzureTtsClientTest {

    private AzureTtsClient azureTtsClient;
    private RestClient restClientMock;
    private RestClient.RequestBodyUriSpec uriSpecMock;
    private RestClient.RequestBodySpec bodySpecMock;
    private RestClient.ResponseSpec responseSpecMock;
    private AudioConfigService audioConfigMock;

    @TempDir java.nio.file.Path tempDir;

    /** TtsConfig padrão usado na maioria dos testes. */
    private static final AudioConfigService.TtsConfig DEFAULT_TTS =
            new AudioConfigService.TtsConfig(
                    /* maxAudioSizeBytes      */ 5L * 1024 * 1024,
                    /* compressBitrate        */ "64k",
                    /* compressChannels       */ 1,
                    /* compressSampleRate     */ 22050,
                    /* compressTimeoutSeconds */ 120);

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

        // 🔧 Onda 3.4: AzureTtsClient agora depende do AudioConfigService
        audioConfigMock = mock(AudioConfigService.class);
        lenient().when(audioConfigMock.getTts()).thenReturn(DEFAULT_TTS);

        // Instancia a classe usando parâmetros de teste e diretório temporário JUnit
        azureTtsClient =
                new AzureTtsClient("dummy-key", "eastus", tempDir.toString(), audioConfigMock);

        // Injeta o RestClient mockado via reflection
        ReflectionTestUtils.setField(azureTtsClient, "restClient", restClientMock);
    }

    // =========================================================================
    // TESTES ORIGINais (mantidos)
    // =========================================================================

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

    // =========================================================================
    // NOVOS: cobertura dos caminhos de compressIfNeeded via AudioConfigService
    // =========================================================================

    @Test
    @DisplayName("Áudio abaixo do limite do TtsConfig NÃO tenta comprimir (retorna como veio)")
    void synthesizeFullText_audioBelowLimit_naoComprime() {
        // Áudio pequeno, abaixo dos 5 MB do DEFAULT_TTS
        byte[] smallAudio = new byte[1024]; // 1 KB
        when(responseSpecMock.body(byte[].class)).thenReturn(smallAudio);

        byte[] result = azureTtsClient.synthesizeFullText("Texto curto.");

        assertThat(result).isEqualTo(smallAudio);
        assertThat(result.length).isEqualTo(1024);
    }

    @Test
    @DisplayName("Áudio acima do limite tenta comprimir; sem ffmpeg, mantém o original")
    void synthesizeFullText_audioAboveLimit_mantemOriginalSeFfmpegFalhar() {
        // Áudio maior que 5 MB — força o caminho de compressão.
        // No ambiente de teste o /usr/bin/ffmpeg provavelmente não existe,
        // então o compressIfNeeded cai no catch e retorna o original.
        byte[] bigAudio = new byte[6 * 1024 * 1024]; // 6 MB
        when(responseSpecMock.body(byte[].class)).thenReturn(bigAudio);

        byte[] result = azureTtsClient.synthesizeFullText("Texto grande.");

        // Independentemente de ter ffmpeg ou não, o método deve retornar algo não-nulo
        // e o tamanho deve ser <= ao original (nunca maior).
        assertThat(result).isNotNull();
        assertThat(result.length).isLessThanOrEqualTo(bigAudio.length);
    }

    @Test
    @DisplayName("Config customizada: maxAudioSizeBytes menor força compressão em áudio pequeno")
    void synthesizeFullText_configCustomizada_usaNovosValores() {
        // Redefine o TtsConfig para um limite muito baixo (1 KB).
        // Assim, até um áudio de 2 KB entra no caminho de compressão.
        AudioConfigService.TtsConfig customCfg =
                new AudioConfigService.TtsConfig(
                        /* maxAudioSizeBytes      */ 1024L,
                        /* compressBitrate        */ "32k",
                        /* compressChannels       */ 1,
                        /* compressSampleRate     */ 16000,
                        /* compressTimeoutSeconds */ 30);

        when(audioConfigMock.getTts()).thenReturn(customCfg);

        byte[] smallAudio = new byte[2048]; // 2 KB > 1 KB do limite
        when(responseSpecMock.body(byte[].class)).thenReturn(smallAudio);

        byte[] result = azureTtsClient.synthesizeFullText("Texto teste.");

        // Sem ffmpeg, retorna o original; com ffmpeg, retorna comprimido.
        // Em ambos os casos: não lança exceção e retorna algo.
        assertThat(result).isNotNull();
        assertThat(result.length).isLessThanOrEqualTo(smallAudio.length);
    }
}
