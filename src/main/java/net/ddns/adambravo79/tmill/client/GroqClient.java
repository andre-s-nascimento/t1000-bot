/* (c) 2026 | 17/05/2026 */
package net.ddns.adambravo79.tmill.client;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.model.ChatCompletionResponse;
import net.ddns.adambravo79.tmill.model.TranscriptionResponse;
import net.ddns.adambravo79.tmill.prompt.DigestPersona;
import net.ddns.adambravo79.tmill.prompt.DigestPromptFactory;

@Slf4j
@Component
public class GroqClient {

    private static final String MODEL = "model";
    private static final String CONTENT = "content";
    private static final String SYSTEM_PROMPT_REFINAMENTO =
            "Corrija a pontuação e remova vícios de fala. Retorne apenas o texto limpo.";

    private final RestClient restClient;
    private final DigestPromptFactory promptFactory;
    private final int maxPromptLength;

    @Autowired
    public GroqClient(
            @Value("${groq.api.key}") String apiKey,
            @Value("${groq.max-prompt-length:32000}") int maxPromptLength,
            @Value("${groq.connect-timeout:5}") int connectTimeoutSeconds,
            @Value("${groq.read-timeout:30}") int readTimeoutSeconds,
            DigestPromptFactory promptFactory) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

        this.restClient =
                RestClient.builder()
                        .baseUrl("https://api.groq.com")
                        .defaultHeader("Authorization", "Bearer " + apiKey)
                        .requestFactory(factory)
                        .build();

        this.maxPromptLength = maxPromptLength;
        this.promptFactory = promptFactory;
    }

    // Construtor para testes
    public GroqClient(
            RestClient restClient, int maxPromptLength, DigestPromptFactory promptFactory) {
        this.restClient = restClient;
        this.promptFactory = promptFactory;
        this.maxPromptLength = maxPromptLength;
    }

    @Retryable(
            includes = Exception.class,
            maxRetries = 2,
            delay = 1000,
            multiplier = 2,
            maxDelay = 5000)
    public String transcrever(File wavFile) {
        log.info("🎙️ Transcrevendo arquivo={}", wavFile.getName());

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new org.springframework.core.io.FileSystemResource(wavFile));
        builder.part(MODEL, "whisper-large-v3");

        TranscriptionResponse response =
                restClient
                        .post()
                        .uri("/openai/v1/audio/transcriptions")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(builder.build())
                        .retrieve()
                        .body(TranscriptionResponse.class);

        if (response == null || response.text() == null) {
            throw new IllegalStateException("Falha na transcrição.");
        }

        return response.text();
    }

    // Método de refinamento de texto (usado pelo AudioPipelineService)
    @Retryable(includes = Exception.class, maxRetries = 1, delay = 500, multiplier = 2)
    public String refinarTexto(String textoBruto) {
        if (textoBruto == null || textoBruto.isBlank()) {
            return "";
        }
        return chatCompletion(
                SYSTEM_PROMPT_REFINAMENTO, textoBruto, "llama-3.1-8b-instant", 0.2, 1200);
    }

    // Método para gerar resumo do digest
    public String gerarResumoDigest(String messages, DigestPersona persona, String periodLabel) {
        String systemPrompt = promptFactory.buildSystemPrompt(persona, periodLabel);
        String userPrompt = promptFactory.buildUserPrompt(messages);
        return chatCompletion(
                systemPrompt, userPrompt, "meta-llama/llama-4-scout-17b-16e-instruct", 0.7, 2200);
    }

    @Retryable(
            includes = Exception.class,
            maxRetries = 2,
            delay = 1000,
            multiplier = 2,
            maxDelay = 5000)
    public String chatCompletion(
            String systemPrompt,
            String userPrompt,
            String model,
            double temperature,
            int maxTokens) {

        systemPrompt = systemPrompt == null ? "" : systemPrompt;
        userPrompt = userPrompt == null ? "" : userPrompt;

        int totalSize = systemPrompt.length() + userPrompt.length();
        log.info("🤖 ChatCompletion model={} size={}", model, totalSize);

        if (totalSize > maxPromptLength) {
            log.warn("⚠️ Prompt acima do limite size={} limit={}", totalSize, maxPromptLength);
        }

        var payload =
                Map.of(
                        MODEL,
                        model,
                        "messages",
                        List.of(
                                Map.of("role", "system", CONTENT, systemPrompt),
                                Map.of("role", "user", CONTENT, userPrompt)),
                        "temperature",
                        temperature,
                        "max_tokens",
                        maxTokens);

        ChatCompletionResponse response =
                restClient
                        .post()
                        .uri("/openai/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .body(ChatCompletionResponse.class);

        if (response == null || response.choices().isEmpty()) {
            throw new IllegalStateException("Resposta inválida da Groq.");
        }

        return response.choices().get(0).message().content();
    }

    // Fallback opcional
    public String transcreverComFallback(File wavFile) {
        try {
            return transcrever(wavFile);
        } catch (Exception e) {
            log.error("Falha definitiva ao transcrever arquivo {}", wavFile.getName(), e);
            return "";
        }
    }
}
