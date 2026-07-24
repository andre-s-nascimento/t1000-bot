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
        groqClient = new GroqClient(restClient, 5000, promptFactory);

        ReflectionTestUtils.setField(groqClient, "transcriptionModel", "whisper-large-v3");
        ReflectionTestUtils.setField(groqClient, "refinementModel", "llama-3.1-8b-instant");
        ReflectionTestUtils.setField(
                groqClient, "digestModel", "meta-llama/llama-4-scout-17b-16e-instruct");
        ReflectionTestUtils.setField(groqClient, "refinementMaxTokens", 4000);

        when(restClient.post()).thenReturn(uriSpec);
        lenient().when(uriSpec.uri("/openai/v1/audio/transcriptions")).thenReturn(bodySpec);
        lenient().when(bodySpec.contentType(MediaType.MULTIPART_FORM_DATA)).thenReturn(bodySpec);
        lenient().when(bodySpec.body(any(MultiValueMap.class))).thenReturn(bodySpec);
        lenient().when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        lenient().when(bodySpec.retrieve()).thenReturn(responseSpec);
    }

    private void stubChatUri() {
        when(uriSpec.uri("/openai/v1/chat/completions")).thenReturn(bodySpec);
        when(bodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(bodySpec);
    }

    // =========================
    // 🧪 TRANSCRIÇÃO
    // =========================

    @Test
    void transcrever_deveRetornarTexto() {
        TranscriptionResponse mockResponse = mock(TranscriptionResponse.class);
        when(mockResponse.text()).thenReturn("Texto transcrito");
        when(responseSpec.body(TranscriptionResponse.class)).thenReturn(mockResponse);

        String result = groqClient.transcrever(new File("audio.wav"));
        assertThat(result).isEqualTo("Texto transcrito");
    }

    @Test
    void transcrever_deveLancarExcecaoQuandoRespostaNula() {
        when(responseSpec.body(TranscriptionResponse.class)).thenReturn(null);

        assertThatThrownBy(() -> groqClient.transcrever(new File("audio.wav")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Falha na transcrição");
    }

    @Test
    void transcrever_deveLancarExcecaoQuandoTextNulo() {
        TranscriptionResponse mockResponse = mock(TranscriptionResponse.class);
        when(mockResponse.text()).thenReturn(null);
        when(responseSpec.body(TranscriptionResponse.class)).thenReturn(mockResponse);

        assertThatThrownBy(() -> groqClient.transcrever(new File("audio.wav")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Falha na transcrição");
    }

    // =========================
    // 🧪 REFINAMENTO
    // =========================

    @Test
    void refinarTexto_deveRetornarTextoRefinado() {
        stubChatUri();
        ChatCompletionResponse response =
                new ChatCompletionResponse(
                        List.of(new Choice(new Message("assistant", "Texto refinado"))));
        when(responseSpec.body(ChatCompletionResponse.class)).thenReturn(response);

        String result = groqClient.refinarTexto("texto bruto");
        assertThat(result).isEqualTo("Texto refinado");
    }

    @Test
    void refinarTexto_comTextoNuloOuVazio_retornaVazio() {
        assertThat(groqClient.refinarTexto(null)).isEmpty();
        assertThat(groqClient.refinarTexto("")).isEmpty();
        assertThat(groqClient.refinarTexto("   ")).isEmpty();
        // Garante que não chamou a API
        verify(restClient, never()).post();
    }

    // =========================
    // 🧪 CHAT COMPLETION
    // =========================

    @Test
    void chatCompletion_deveRetornarResposta() {
        stubChatUri();
        ChatCompletionResponse response =
                new ChatCompletionResponse(
                        List.of(new Choice(new Message("assistant", "Resposta gerada"))));
        when(responseSpec.body(ChatCompletionResponse.class)).thenReturn(response);

        String result = groqClient.chatCompletion("system", "user", "llama", 0.5, 100);
        assertThat(result).isEqualTo("Resposta gerada");
    }

    @Test
    void chatCompletion_deveLancarExcecaoQuandoRespostaNula() {
        stubChatUri();
        when(responseSpec.body(ChatCompletionResponse.class)).thenReturn(null);

        assertThatThrownBy(() -> groqClient.chatCompletion("system", "user", "llama", 0.5, 100))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Resposta inválida");
    }

    @Test
    void chatCompletion_deveLancarExcecaoQuandoChoicesVazio() {
        stubChatUri();
        ChatCompletionResponse response = new ChatCompletionResponse(List.of());
        when(responseSpec.body(ChatCompletionResponse.class)).thenReturn(response);

        assertThatThrownBy(() -> groqClient.chatCompletion("system", "user", "llama", 0.5, 100))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Resposta inválida");
    }

    @Test
    void chatCompletion_comSystemPromptNulo_trataComoVazio() {
        stubChatUri();
        ChatCompletionResponse response =
                new ChatCompletionResponse(List.of(new Choice(new Message("assistant", "OK"))));
        when(responseSpec.body(ChatCompletionResponse.class)).thenReturn(response);

        // Não deve lançar exceção
        String result = groqClient.chatCompletion(null, "user", "llama", 0.5, 100);
        assertThat(result).isEqualTo("OK");
        // Verifica que a chamada aconteceu (o corpo foi construído)
        verify(bodySpec, atLeastOnce()).body(any());
    }

    @Test
    void chatCompletion_comUserPromptNulo_trataComoVazio() {
        stubChatUri();
        ChatCompletionResponse response =
                new ChatCompletionResponse(List.of(new Choice(new Message("assistant", "OK"))));
        when(responseSpec.body(ChatCompletionResponse.class)).thenReturn(response);

        String result = groqClient.chatCompletion("system", null, "llama", 0.5, 100);
        assertThat(result).isEqualTo("OK");
    }

    @Test
    void chatCompletion_comPromptGrande_naoFalha() {
        stubChatUri();
        // Cria um prompt grande (maior que maxPromptLength = 5000)
        String largePrompt = "a".repeat(6000);
        ChatCompletionResponse response =
                new ChatCompletionResponse(List.of(new Choice(new Message("assistant", "OK"))));
        when(responseSpec.body(ChatCompletionResponse.class)).thenReturn(response);

        // Não deve lançar exceção (apenas log)
        String result = groqClient.chatCompletion(largePrompt, "user", "llama", 0.5, 100);
        assertThat(result).isEqualTo("OK");
    }

    // =========================
    // 🧪 GERAR RESUMO DIGEST
    // =========================

    @Test
    void gerarResumoDigest_deveRetornarResumo() {
        stubChatUri();
        ChatCompletionResponse response =
                new ChatCompletionResponse(
                        List.of(new Choice(new Message("assistant", "Resumo do digest"))));
        when(responseSpec.body(ChatCompletionResponse.class)).thenReturn(response);

        String result = groqClient.gerarResumoDigest("mensagens", DigestPersona.T1000, "manhã");
        assertThat(result).isEqualTo("Resumo do digest");
    }

    @Test
    void gerarResumoDigest_comPeriodLabelNulo_usaPadrao() {
        stubChatUri();
        ChatCompletionResponse response =
                new ChatCompletionResponse(List.of(new Choice(new Message("assistant", "Resumo"))));
        when(responseSpec.body(ChatCompletionResponse.class)).thenReturn(response);

        // Não deve lançar exceção
        String result = groqClient.gerarResumoDigest("mensagens", DigestPersona.T1000, null);
        assertThat(result).isEqualTo("Resumo");
    }

    // =========================
    // 🧪 CONSTRUTOR ORIGINAL
    // =========================

    @Test
    void construtorOriginal_deveFuncionar() {
        // Apenas para cobertura do construtor real
        GroqClient client =
                new GroqClient(
                        "fake-key",
                        10000,
                        java.time.Duration.ofSeconds(5),
                        java.time.Duration.ofSeconds(30),
                        new DigestPromptFactory());
        assertThat(client).isNotNull();
    }
}
