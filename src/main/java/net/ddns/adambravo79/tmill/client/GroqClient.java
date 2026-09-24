/* (c) 2026 | 22/07/2026 */
package net.ddns.adambravo79.tmill.client;

import java.io.File;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.model.ChatCompletionResponse;
import net.ddns.adambravo79.tmill.model.Choice;
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

    /**
     * Quando o refinamento perde mais que este percentual do texto, consideramos que foi truncado
     * (finish_reason=length). Um refinamento bem feito preserva ~95% do texto, então uma redução de
     * mais de 30% é sinal claro de corte.
     */
    private static final double MAX_REFINAMENTO_REDUCAO = 0.30;

    /** Textos muito curtos geram falso positivo na checagem de truncamento. */
    private static final int MIN_LENGTH_PARA_CHECAR_TRUNCAMENTO = 200;

    private final RestClient restClient;
    private final DigestPromptFactory promptFactory;
    private final int maxPromptLength;

    @Value("${groq.model.transcription:whisper-large-v3}")
    private String transcriptionModel;

    @Value("${groq.model.refinement:llama-3.1-8b-instant}")
    private String refinementModel;

    @Value("${groq.model.digest:openai/gpt-oss-120b}")
    private String digestModel;

    @Value("${groq.model.refinement.max-tokens:4000}")
    private int refinementMaxTokens;

    /**
     * Liga/desliga reasoning_effort=low para modelos de raciocínio (ex.: gpt-oss-20b, gpt-oss-120b).
     * Sem isso, o modelo gasta a maior parte do max_tokens "pensando" e o output volta truncado
     * (finish_reason=length).
     */
    @Value("${groq.model.reasoning-effort:low}")
    private String reasoningEffort;

    @Autowired
    public GroqClient(
            @Value("${groq.api.key}") String apiKey,
            @Value("${groq.max-prompt-length:32000}") int maxPromptLength,
            @Value("${groq.connect-timeout:5s}") Duration connectTimeout,
            @Value("${groq.read-timeout:30s}") Duration readTimeout,
            DigestPromptFactory promptFactory) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

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

    // ========================= TRANSCRIÇÃO =========================

    @Retryable(
            includes = {java.io.IOException.class, HttpClientErrorException.class},
            maxRetries = 2,
            delay = 1000,
            multiplier = 2,
            maxDelay = 5000)
    public String transcrever(File wavFile) {
        log.info("🎙️ Transcrevendo arquivo={}", wavFile.getName());

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new org.springframework.core.io.FileSystemResource(wavFile));
        builder.part(MODEL, transcriptionModel);

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

    // ========================= REFINAMENTO =========================

    @Retryable(
            includes = {java.io.IOException.class, HttpClientErrorException.class},
            maxRetries = 3,
            delay = 2000,
            multiplier = 3,
            maxDelay = 30000)
    public String refinarTexto(String textoBruto) {
        if (textoBruto == null || textoBruto.isBlank()) {
            return "";
        }

        log.info("🤖 Iniciando refinamento (input: {} chars)", textoBruto.length());

        String resultado;
        try {
            resultado =
                    chatCompletion(
                            SYSTEM_PROMPT_REFINAMENTO,
                            textoBruto,
                            refinementModel,
                            0.18,
                            refinementMaxTokens);
        } catch (Exception e) {
            log.error(
                    "❌ Erro no refinamento ({}), usando texto bruto como fallback",
                    e.getMessage(),
                    e);
            return textoBruto;
        }

        // FIX 1: refinamento vazio → usa bruto
        if (resultado == null || resultado.isBlank()) {
            log.warn(
                    "⚠️ Refinamento retornou VAZIO (model={}, input={} chars). Usando texto bruto"
                            + " como fallback.",
                    refinementModel,
                    textoBruto.length());
            return textoBruto;
        }

        // FIX 2: refinamento truncado (redução > 30%) → usa bruto
        // Só checa em textos com tamanho mínimo para evitar falso positivo em inputs curtos.
        if (textoBruto.length() >= MIN_LENGTH_PARA_CHECAR_TRUNCAMENTO) {
            double reductionRatio = 1.0 - (resultado.length() / (double) textoBruto.length());
            if (reductionRatio > MAX_REFINAMENTO_REDUCAO) {
                log.warn(
                        "⚠️ Refinamento reduziu {}% do texto (input={} chars, output={} chars)."
                                + " Provavelmente TRUNCADO. Usando texto bruto como fallback.",
                        (int) (reductionRatio * 100), textoBruto.length(), resultado.length());
                return textoBruto;
            }
            log.info(
                    "✅ Refinamento concluído (input: {} chars, output: {} chars, redução: {}%)",
                    textoBruto.length(), resultado.length(), (int) (reductionRatio * 100));
        } else {
            log.info(
                    "✅ Refinamento concluído (input: {} chars, output: {} chars — input curto, sem"
                            + " checagem de truncamento)",
                    textoBruto.length(),
                    resultado.length());
        }

        return resultado;
    }

    // ========================= DIGEST =========================

    public String gerarResumoDigest(String messages, DigestPersona persona, String periodLabel) {
        String systemPrompt = promptFactory.buildSystemPrompt(persona, periodLabel);
        String userPrompt = promptFactory.buildUserPrompt(messages);
        return chatCompletion(systemPrompt, userPrompt, digestModel, 0.5, 2200);
    }

    // ========================= CHAT COMPLETION =========================

    @Retryable(
            includes = {java.io.IOException.class, HttpClientErrorException.class},
            maxRetries = 4,
            delay = 2000,
            multiplier = 3,
            maxDelay = 60000)
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

        // LinkedHashMap para permitir adicionar reasoning_effort condicionalmente.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(MODEL, model);
        payload.put(
                "messages",
                List.of(
                        Map.of("role", "system", CONTENT, systemPrompt),
                        Map.of("role", "user", CONTENT, userPrompt)));
        payload.put("temperature", temperature);
        payload.put("max_tokens", maxTokens);

        // reasoning_effort=low para modelos de raciocínio (gpt-oss-*)
        if (reasoningEffort != null
                && !reasoningEffort.isBlank()
                && model != null
                && model.contains("gpt-oss")) {
            payload.put("reasoning_effort", reasoningEffort);
            log.debug("🧠 reasoning_effort={} aplicado em model={}", reasoningEffort, model);
        }

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

        Choice choice = response.choices().get(0);
        String content = choice.message().content();
        String finishReason = choice.finishReason();
        int contentLength = content == null ? 0 : content.length();

        if (response.usage() != null) {
            log.info(
                    "✅ Groq respondeu: model={}, finish_reason={}, content={} chars,"
                            + " tokens=(prompt={}, completion={}, total={})",
                    model,
                    finishReason,
                    contentLength,
                    response.usage().promptTokens(),
                    response.usage().completionTokens(),
                    response.usage().totalTokens());
        } else {
            log.info(
                    "✅ Groq respondeu: model={}, finish_reason={}, content={} chars (sem usage)",
                    model,
                    finishReason,
                    contentLength);
        }

        if ("length".equals(finishReason)) {
            log.warn(
                    "⚠️ Modelo TRUNCOU a resposta (finish_reason=length). Considere aumentar"
                            + " max_tokens (atual={}) ou usar reasoning_effort=low.",
                    maxTokens);
        } else if ("content_filter".equals(finishReason)) {
            log.warn(
                    "⚠️ Modelo BLOQUEOU conteúdo (finish_reason=content_filter). Texto pode ter"
                            + " sido considerado inapropriado.");
        } else if (contentLength == 0) {
            log.warn(
                    "⚠️ Modelo retornou conteúdo VAZIO (finish_reason={}). Possível problema com o"
                            + " modelo {} ou com o prompt.",
                    finishReason,
                    model);
        }

        return content;
    }
}
