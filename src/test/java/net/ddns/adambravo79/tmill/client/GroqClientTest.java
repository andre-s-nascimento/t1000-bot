/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.client;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import net.ddns.adambravo79.tmill.model.ChatCompletionResponse;
import net.ddns.adambravo79.tmill.model.Choice;
import net.ddns.adambravo79.tmill.model.Message;
import net.ddns.adambravo79.tmill.model.TranscriptionResponse;
import net.ddns.adambravo79.tmill.prompt.DigestPersona;
import net.ddns.adambravo79.tmill.prompt.DigestPromptFactory;

class GroqClientTest {

    private RestClient restClient;
    private RestClient.RequestBodyUriSpec uriSpec;
    private RestClient.RequestBodySpec bodySpec;
    private RestClient.ResponseSpec responseSpec;

    private DigestPromptFactory promptFactory;

    @BeforeEach
    void setUp() {

        restClient = mock(RestClient.class);

        uriSpec = mock(RestClient.RequestBodyUriSpec.class);

        bodySpec = mock(RestClient.RequestBodySpec.class);

        responseSpec = mock(RestClient.ResponseSpec.class);

        promptFactory = new DigestPromptFactory();

        when(restClient.post()).thenReturn(uriSpec);

        lenient().doReturn(bodySpec).when(bodySpec).body(any(MultiValueMap.class));

        lenient().doReturn(bodySpec).when(bodySpec).body(any(Object.class));

        lenient().doReturn(responseSpec).when(bodySpec).retrieve();
    }

    private GroqClient buildClient() {

        return new GroqClient(restClient, 5000, promptFactory);
    }

    private void stubTranscricaoUri() {

        when(uriSpec.uri("/openai/v1/audio/transcriptions")).thenReturn(bodySpec);

        lenient().doReturn(bodySpec).when(bodySpec).contentType(MediaType.MULTIPART_FORM_DATA);
    }

    private void stubChatUri() {

        when(uriSpec.uri("/openai/v1/chat/completions")).thenReturn(bodySpec);

        lenient().doReturn(bodySpec).when(bodySpec).contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void deveTranscreverComSucesso() {

        stubTranscricaoUri();

        var resp = mock(TranscriptionResponse.class);

        when(resp.text()).thenReturn("Texto transcrito");

        when(responseSpec.body(TranscriptionResponse.class)).thenReturn(resp);

        String resultado = buildClient().transcrever(new File("teste.wav"));

        assertThat(resultado).isEqualTo("Texto transcrito");
    }

    @Test
    void deveFalharQuandoTranscricaoInvalida() {

        stubTranscricaoUri();

        when(responseSpec.body(TranscriptionResponse.class)).thenReturn(null);

        GroqClient client = buildClient();
        File testFile = new File("teste.wav"); // ← criado fora do lambda

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> client.transcrever(testFile))
                .withMessageContaining("Falha na transcrição");
    }

    @Test
    void deveExecutarChatCompletionComSucesso() {

        stubChatUri();

        var response =
                new ChatCompletionResponse(
                        List.of(new Choice(new Message("assistant", "Resposta gerada"))));

        when(responseSpec.body(ChatCompletionResponse.class)).thenReturn(response);

        String resultado = buildClient().chatCompletion("system", "user", "llama", 0.2, 100);

        assertThat(resultado).isEqualTo("Resposta gerada");
    }

    @Test
    void deveFalharQuandoChatCompletionRetornaVazio() {

        stubChatUri();

        when(responseSpec.body(ChatCompletionResponse.class)).thenReturn(null);

        GroqClient client = buildClient();
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> client.chatCompletion("system", "user", "llama", 0.2, 100))
                .withMessageContaining("Resposta inválida");
    }

    @Test
    void deveGerarResumoDigestComPersona() {

        stubChatUri();

        var response =
                new ChatCompletionResponse(
                        List.of(new Choice(new Message("assistant", "Digest gerado"))));

        when(responseSpec.body(ChatCompletionResponse.class)).thenReturn(response);

        String resultado =
                buildClient().gerarResumoDigest("mensagens teste", DigestPersona.T1000, "");

        assertThat(resultado).isEqualTo("Digest gerado");
    }
}
