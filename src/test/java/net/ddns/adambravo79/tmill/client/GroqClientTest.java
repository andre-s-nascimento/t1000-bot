package net.ddns.adambravo79.tmill.client;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import net.ddns.adambravo79.tmill.model.ChatCompletionResponse;
import net.ddns.adambravo79.tmill.model.Choice;
import net.ddns.adambravo79.tmill.model.Message;
import net.ddns.adambravo79.tmill.model.TranscriptionResponse;
import net.ddns.adambravo79.tmill.prompt.DigestPersona;
import net.ddns.adambravo79.tmill.service.config.GroqConfigService;
import net.ddns.adambravo79.tmill.service.config.RefinementConfigService;
import net.ddns.adambravo79.tmill.service.digest.DigestPromptFactory;
import net.ddns.adambravo79.tmill.service.prompt.PromptRegistryService;

@ExtendWith(MockitoExtension.class)
class GroqClientTest {

    @Mock private RestClient restClient;
    @Mock private RestClient.RequestBodyUriSpec uriSpec;
    @Mock private RestClient.RequestBodySpec bodySpec;
    @Mock private RestClient.ResponseSpec responseSpec;
    @Mock private PromptRegistryService promptRegistryService;
    // 🔧 Onda 3.4: novos mocks para os serviços de config
    @Mock private GroqConfigService groqConfig;
    @Mock private RefinementConfigService refinementConfig;

    private DigestPromptFactory promptFactory;
    private GroqClient groqClient;

    @BeforeEach
    void setUp() {
        promptFactory = new DigestPromptFactory(promptRegistryService);
        // 🔧 Onda 3.4: usa o construtor de teste — groqConfig/refinementConfig ficam null
        // e os fallbacks internos do GroqClient assumem os valores default.
        groqClient = new GroqClient(restClient, 5000, promptFactory);

        ReflectionTestUtils.setField(groqClient, "transcriptionModel", "whisper-large-v3");
        ReflectionTestUtils.setField(groqClient, "refinementModel", "llama-3.1-8b-instant");
        ReflectionTestUtils.setField(
                groqClient, "digestModel", "meta-llama/llama-4-scout-17b-16e-instruct");

        lenient().when(restClient.post()).thenReturn(uriSpec);
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
        File audioFile = new File("audio.wav");

        assertThatThrownBy(() -> groqClient.transcrever(audioFile))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Falha na transcrição");
    }

    @Test
    void transcrever_deveLancarExcecaoQuandoTextNulo() {
        TranscriptionResponse mockResponse = mock(TranscriptionResponse.class);
        when(mockResponse.text()).thenReturn(null);
        when(responseSpec.body(TranscriptionResponse.class)).thenReturn(mockResponse);
        File audioFile = new File("audio.wav");

        assertThatThrownBy(() -> groqClient.transcrever(audioFile))
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
                        List.of(new Choice(new Message("assistant", "Texto refinado"), "stop")));
        when(responseSpec.body(ChatCompletionResponse.class)).thenReturn(response);

        String result = groqClient.refinarTexto("texto bruto");
        assertThat(result).isEqualTo("Texto refinado");
    }

    @Test
    void refinarTexto_comTextoNuloOuVazio_retornaVazio() {
        assertThat(groqClient.refinarTexto(null)).isEmpty();
        assertThat(groqClient.refinarTexto("")).isEmpty();
        assertThat(groqClient.refinarTexto("   ")).isEmpty();
        verify(restClient, never()).post();
    }

    @Test
    void refinarTexto_comTextoValido_deveChamarGroqComParametrosCorretos() {
        stubChatUri();
        ChatCompletionResponse response =
                new ChatCompletionResponse(
                        List.of(new Choice(new Message("assistant", "Texto refinado"), "stop")));
        when(responseSpec.body(ChatCompletionResponse.class)).thenReturn(response);

        groqClient.refinarTexto("texto bruto");

        // 🔧 Onda 3.4: com o construtor de teste, os fallbacks internos
        // aplicam temperature=0.18 e maxTokens=4000.
        verify(bodySpec)
                .body(
                        argThat(
                                (ArgumentMatcher<Map<String, Object>>)
                                        payload ->
                                                "llama-3.1-8b-instant".equals(payload.get("model"))
                                                        && 4000 == (int) payload.get("max_tokens")
                                                        && 0.18
                                                                == (double)
                                                                        payload.get(
                                                                                "temperature")));
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

        String result = groqClient.chatCompletion(null, "user", "llama", 0.5, 100);
        assertThat(result).isEqualTo("OK");
        verify(bodySpec).body(any(Object.class));
    }

    @Test
    void chatCompletion_comUserPromptNulo_trataComoVazio() {
        stubChatUri();
        ChatCompletionResponse response =
                new ChatCompletionResponse(List.of(new Choice(new Message("assistant", "OK"))));
        when(responseSpec.body(ChatCompletionResponse.class)).thenReturn(response);

        String result = groqClient.chatCompletion("system", null, "llama", 0.5, 100);
        assertThat(result).isEqualTo("OK");
        verify(bodySpec).body(any(Object.class));
    }

    @Test
    void chatCompletion_comPromptGrande_naoFalha() {
        stubChatUri();
        String largePrompt = "a".repeat(6000);
        ChatCompletionResponse response =
                new ChatCompletionResponse(List.of(new Choice(new Message("assistant", "OK"))));
        when(responseSpec.body(ChatCompletionResponse.class)).thenReturn(response);

        String result = groqClient.chatCompletion(largePrompt, "user", "llama", 0.5, 100);
        assertThat(result).isEqualTo("OK");
    }

    @Test
    void chatCompletion_deveLancarExcecaoQuandoHttpError() {
        stubChatUri();
        HttpClientErrorException.TooManyRequests ex =
                (HttpClientErrorException.TooManyRequests)
                        HttpClientErrorException.create(
                                HttpStatusCode.valueOf(429), "Too Many Requests", null, null, null);
        when(responseSpec.body(ChatCompletionResponse.class)).thenThrow(ex);

        assertThatThrownBy(() -> groqClient.chatCompletion("system", "user", "llama", 0.5, 100))
                .isInstanceOf(HttpClientErrorException.TooManyRequests.class);
    }

    @Test
    void chatCompletion_deveLancarExcecaoQuandoResourceAccessError() {
        stubChatUri();
        when(responseSpec.body(ChatCompletionResponse.class))
                .thenThrow(new ResourceAccessException("Conectividade"));

        assertThatThrownBy(() -> groqClient.chatCompletion("system", "user", "llama", 0.5, 100))
                .isInstanceOf(ResourceAccessException.class);
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

        String result = groqClient.gerarResumoDigest("mensagens", DigestPersona.T1000, "Período");
        assertThat(result).isEqualTo("Resumo");
    }

    @Test
    void gerarResumoDigest_deveLancarExcecaoQuandoGroqFalha() {
        stubChatUri();
        when(responseSpec.body(ChatCompletionResponse.class))
                .thenThrow(new RuntimeException("Erro Groq"));

        assertThatThrownBy(
                        () ->
                                groqClient.gerarResumoDigest(
                                        "mensagens", DigestPersona.T1000, "manhã"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void refinarTexto_comRespostaVaziaGroq_usaTextoBrutoComoFallback() {
        stubChatUri();
        ChatCompletionResponse response =
                new ChatCompletionResponse(
                        List.of(new Choice(new Message("assistant", ""), "stop")));
        when(responseSpec.body(ChatCompletionResponse.class)).thenReturn(response);

        String resultado = groqClient.refinarTexto("Texto bruto original");

        assertThat(resultado).isEqualTo("Texto bruto original");
    }

    @Test
    void refinarTexto_comFinishReasonLength_usaTextoBrutoComoFallback() {
        stubChatUri();
        ChatCompletionResponse response =
                new ChatCompletionResponse(
                        List.of(new Choice(new Message("assistant", ""), "length")));
        when(responseSpec.body(ChatCompletionResponse.class)).thenReturn(response);

        String resultado = groqClient.refinarTexto("Texto bruto original");

        assertThat(resultado).isEqualTo("Texto bruto original");
    }

    @Test
    void chatCompletion_comFinishReasonLength_logaAviso() {
        stubChatUri();
        ChatCompletionResponse response =
                new ChatCompletionResponse(
                        List.of(new Choice(new Message("assistant", "Texto curto"), "length")));
        when(responseSpec.body(ChatCompletionResponse.class)).thenReturn(response);

        String result = groqClient.chatCompletion("system", "user", "llama", 0.5, 100);
        assertThat(result).isEqualTo("Texto curto");
    }

    @Test
    void chatCompletion_comContentFilter_retornaVazio() {
        stubChatUri();
        ChatCompletionResponse response =
                new ChatCompletionResponse(
                        List.of(new Choice(new Message("assistant", ""), "content_filter")));
        when(responseSpec.body(ChatCompletionResponse.class)).thenReturn(response);

        String result = groqClient.chatCompletion("system", "user", "llama", 0.5, 100);
        assertThat(result).isEmpty();
    }

    // =========================
    // 🧪 CONSTRUTOR ORIGINAL
    // =========================

    @Test
    void construtorOriginal_deveFuncionar() {
        GroqClient client =
                new GroqClient(
                        "fake-key",
                        10000,
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(30),
                        new DigestPromptFactory(promptRegistryService),
                        groqConfig,
                        refinementConfig);
        assertThat(client).isNotNull();
    }

    // =========================================================================
    // 🆕 ONDA 3.4: configs externalizadas (temperature/maxTokens e regras de pipeline)
    // =========================================================================

    /**
     * Cria um `GroqClient` **usando o construtor de teste** (`RestClient` mockado) e injeta os
     * services de config via {@code ReflectionTestUtils}.
     *
     * <p>⚠️ **Importante**: não podemos usar o construtor de produção (com `String apiKey`), porque
     * ele cria o próprio `RestClient` internamente — apontando para `api.groq.com` real. Os testes
     * precisam usar o `restClient` mockado do `@BeforeEach`.
     */
    private GroqClient newGroqClientWithConfig(
            GroqConfigService groqCfg, RefinementConfigService refinementCfg) {
        GroqClient client = new GroqClient(restClient, 5000, promptFactory);

        ReflectionTestUtils.setField(client, "transcriptionModel", "whisper-large-v3");
        ReflectionTestUtils.setField(client, "refinementModel", "llama-3.1-8b-instant");
        ReflectionTestUtils.setField(
                client, "digestModel", "meta-llama/llama-4-scout-17b-16e-instruct");

        // 🔧 Injeta os services de config por reflexão
        ReflectionTestUtils.setField(client, "groqConfig", groqCfg);
        ReflectionTestUtils.setField(client, "refinementConfig", refinementCfg);

        return client;
    }

    @Test
    @DisplayName("refinarTexto: temperature e maxTokens vêm do GroqConfigService")
    void refinarTexto_usaTemperatureMaxTokensDoGroqConfig() {
        stubChatUri();
        when(groqConfig.getRefinementSpec())
                .thenReturn(new GroqConfigService.RefinementSpec(0.42, 7777));
        when(refinementConfig.getSpec())
                .thenReturn(new RefinementConfigService.RefinementSpec(0.30, 200, 4000, 0.18));

        ChatCompletionResponse response =
                new ChatCompletionResponse(
                        List.of(new Choice(new Message("assistant", "ok"), "stop")));
        when(responseSpec.body(ChatCompletionResponse.class)).thenReturn(response);

        GroqClient client = newGroqClientWithConfig(groqConfig, refinementConfig);

        client.refinarTexto("texto bruto");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(bodySpec).body(captor.capture());

        // 🔧 FIX S5838: asserts individuais em vez de encadeamento
        Map<String, Object> payload = captor.getValue();
        assertThat(payload).containsEntry("temperature", 0.42).containsEntry("max_tokens", 7777);
    }

    @Test
    @DisplayName("gerarResumoDigest: temperature e maxTokens vêm do GroqConfigService")
    void gerarResumoDigest_usaTemperatureMaxTokensDoGroqConfig() {
        stubChatUri();
        when(groqConfig.getDigestSpec()).thenReturn(new GroqConfigService.DigestSpec(0.77, 1234));

        ChatCompletionResponse response =
                new ChatCompletionResponse(
                        List.of(new Choice(new Message("assistant", "resumo"), "stop")));
        when(responseSpec.body(ChatCompletionResponse.class)).thenReturn(response);

        GroqClient client = newGroqClientWithConfig(groqConfig, refinementConfig);

        client.gerarResumoDigest("mensagens", DigestPersona.T1000, "manhã");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(bodySpec).body(captor.capture());

        // 🔧 FIX S5838: asserts individuais em vez de encadeamento
        Map<String, Object> payload = captor.getValue();
        assertThat(payload).containsEntry("temperature", 0.77).containsEntry("max_tokens", 1234);
    }

    @Test
    @DisplayName("refinarTexto: maxReducao customizado do RefinementConfigService é aplicado")
    void refinarTexto_maxReducaoCustomizado_aplica() {
        stubChatUri();
        when(groqConfig.getRefinementSpec())
                .thenReturn(new GroqConfigService.RefinementSpec(0.18, 4000));
        // maxReducao = 10% e minLength = 50
        when(refinementConfig.getSpec())
                .thenReturn(new RefinementConfigService.RefinementSpec(0.10, 50, 4000, 0.18));

        GroqClient client = newGroqClientWithConfig(groqConfig, refinementConfig);

        String textoBruto = "a".repeat(200); // 200 chars
        // Groq devolve 100 chars → redução de 50% > 10% → deve cair no fallback
        String textoRefinado = "b".repeat(100);
        ChatCompletionResponse response =
                new ChatCompletionResponse(
                        List.of(new Choice(new Message("assistant", textoRefinado), "stop")));
        when(responseSpec.body(ChatCompletionResponse.class)).thenReturn(response);

        String resultado = client.refinarTexto(textoBruto);

        // Como a redução excedeu o maxReducao customizado, retorna o texto bruto
        assertThat(resultado).isEqualTo(textoBruto);
    }

    @Test
    @DisplayName("refinarTexto: minLength customizado — texto curto pula checagem de truncamento")
    void refinarTexto_minLengthCustomizado_pulaChecagem() {
        stubChatUri();
        when(groqConfig.getRefinementSpec())
                .thenReturn(new GroqConfigService.RefinementSpec(0.18, 4000));
        // minLength = 500 → qualquer input < 500 chars não passa pela checagem
        when(refinementConfig.getSpec())
                .thenReturn(new RefinementConfigService.RefinementSpec(0.30, 500, 4000, 0.18));

        GroqClient client = newGroqClientWithConfig(groqConfig, refinementConfig);

        String textoBruto = "a".repeat(200); // 200 < 500 → pula checagem
        String textoRefinado = "b".repeat(50); // redução de 75%, mas não é checada
        ChatCompletionResponse response =
                new ChatCompletionResponse(
                        List.of(new Choice(new Message("assistant", textoRefinado), "stop")));
        when(responseSpec.body(ChatCompletionResponse.class)).thenReturn(response);

        String resultado = client.refinarTexto(textoBruto);

        // Passa direto porque está abaixo do minLengthParaChecarTruncamento
        assertThat(resultado).isEqualTo(textoRefinado);
    }

    @Test
    @DisplayName("Construtor de teste (null configs): fallbacks preservam comportamento legado")
    void construtorDeTeste_fallbacksPreservamLegado() {
        stubChatUri();
        // groqClient foi criado no @BeforeEach com o construtor de teste
        // (groqConfig == null e refinementConfig == null), então:
        //   - temperature = 0.18 (fallback)
        //   - maxTokens   = 4000 (fallback)
        //   - maxReducao  = 0.30 (fallback)
        //   - minLength   = 200  (fallback)

        String textoBruto = "a".repeat(1000);
        String textoRefinado = "b".repeat(100); // redução de 90% > 30% → fallback do bruto
        ChatCompletionResponse response =
                new ChatCompletionResponse(
                        List.of(new Choice(new Message("assistant", textoRefinado), "stop")));
        when(responseSpec.body(ChatCompletionResponse.class)).thenReturn(response);

        String resultado = groqClient.refinarTexto(textoBruto);

        assertThat(resultado).isEqualTo(textoBruto);
    }
}
