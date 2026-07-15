package net.ddns.adambravo79.tmill.client;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import net.ddns.adambravo79.tmill.model.ChatCompletionResponse;
import net.ddns.adambravo79.tmill.model.Choice;
import net.ddns.adambravo79.tmill.model.Message;
import net.ddns.adambravo79.tmill.model.TranscriptionResponse;
import net.ddns.adambravo79.tmill.prompt.DigestPersona;
import net.ddns.adambravo79.tmill.prompt.DigestPromptFactory;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroqClientTest {

    @Mock private RestClient restClient;
    @Mock private RestClient.RequestBodyUriSpec uriSpec;
    @Mock private RestClient.RequestBodySpec bodySpec;
    @Mock private RestClient.ResponseSpec responseSpec;

    private DigestPromptFactory promptFactory;
    private GroqClient groqClient;

    @BeforeEach
    void setUp() {
        promptFactory = new DigestPromptFactory();

        // Cria uma instância do GroqClient usando o construtor de teste
        groqClient = new GroqClient(restClient, 5000, promptFactory);

        // Injeta os valores dos modelos via Reflection
        ReflectionTestUtils.setField(groqClient, "transcriptionModel", "whisper-large-v3");
        ReflectionTestUtils.setField(groqClient, "refinementModel", "llama-3.1-8b-instant");
        ReflectionTestUtils.setField(
                groqClient, "digestModel", "meta-llama/llama-4-scout-17b-16e-instruct");

        when(restClient.post()).thenReturn(uriSpec);
        lenient().doReturn(bodySpec).when(bodySpec).body(any(MultiValueMap.class));
        lenient().doReturn(bodySpec).when(bodySpec).body(any(Object.class));
        lenient().doReturn(responseSpec).when(bodySpec).retrieve();
    }

    private void stubTranscricaoUri() {
        when(uriSpec.uri("/openai/v1/audio/transcriptions")).thenReturn(bodySpec);
        lenient().doReturn(bodySpec).when(bodySpec).contentType(MediaType.MULTIPART_FORM_DATA);
    }

    private void stubChatUri() {
        when(uriSpec.uri("/openai/v1/chat/completions")).thenReturn(bodySpec);
        lenient().doReturn(bodySpec).when(bodySpec).contentType(MediaType.APPLICATION_JSON);
    }

    // =========================
    // 🧪 TRANSCRIÇÃO
    // =========================

    @Test
    void deveTranscreverComSucesso() {
        stubTranscricaoUri();

        TranscriptionResponse resp = mock(TranscriptionResponse.class);
        when(resp.text()).thenReturn("Texto transcrito");
        when(responseSpec.body(TranscriptionResponse.class)).thenReturn(resp);

        String resultado = groqClient.transcrever(new File("teste.wav"));
        assertThat(resultado).isEqualTo("Texto transcrito");
    }

    @Test
    void deveFalharQuandoTranscricaoInvalida() {
        stubTranscricaoUri();
        when(responseSpec.body(TranscriptionResponse.class)).thenReturn(null);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> groqClient.transcrever(new File("teste.wav")))
                .withMessageContaining("Falha na transcrição");
    }

    // =========================
    // 🧪 CHAT COMPLETION
    // =========================

    @Test
    void deveExecutarChatCompletionComSucesso() {
        stubChatUri();

        ChatCompletionResponse response =
                new ChatCompletionResponse(
                        List.of(new Choice(new Message("assistant", "Resposta gerada"))));
        when(responseSpec.body(ChatCompletionResponse.class)).thenReturn(response);

        String resultado = groqClient.chatCompletion("system", "user", "llama", 0.2, 100);
        assertThat(resultado).isEqualTo("Resposta gerada");
    }

    @Test
    void deveFalharQuandoChatCompletionRetornaVazio() {
        stubChatUri();
        when(responseSpec.body(ChatCompletionResponse.class)).thenReturn(null);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> groqClient.chatCompletion("system", "user", "llama", 0.2, 100))
                .withMessageContaining("Resposta inválida");
    }

    @Test
    void deveGerarResumoDigestComPersona() {
        stubChatUri();

        ChatCompletionResponse response =
                new ChatCompletionResponse(
                        List.of(new Choice(new Message("assistant", "Digest gerado"))));
        when(responseSpec.body(ChatCompletionResponse.class)).thenReturn(response);

        String resultado = groqClient.gerarResumoDigest("mensagens teste", DigestPersona.T1000, "");
        assertThat(resultado).isEqualTo("Digest gerado");
    }
}
