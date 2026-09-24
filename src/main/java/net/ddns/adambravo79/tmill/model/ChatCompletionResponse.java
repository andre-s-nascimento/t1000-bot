/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionResponse(List<Choice> choices, @JsonProperty("usage") Usage usage) {

    /**
     * Construtor de conveniência para uso em testes (sem usage).
     *
     * @param choices lista de opções retornadas pelo modelo
     */
    public ChatCompletionResponse(List<Choice> choices) {
        this(choices, null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens) {}
}
