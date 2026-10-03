/* (c) 2026 | 01/10/2026 */
package net.ddns.adambravo79.tmill.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.client.GroqClient;
import net.ddns.adambravo79.tmill.service.prompt.PromptRegistryService;

@Service
@Slf4j
@RequiredArgsConstructor
public class PodcastScriptService {

    private final JdbcTemplate jdbcTemplate;
    private final GroqClient groqClient;
    private final PromptRegistryService promptRegistryService;

    @Value("${podcast.target.user-id}")
    private long targetUserId;

    @Value("${podcast.script.max-tokens:3000}")
    private int maxTokens;

    @Value("${groq.model.digest}")
    private String digestModel;

    private static final int MAX_PROMPT_CHARS = 12000;
    private static final int MAX_MESSAGES = 20;

    public String generateScript(LocalDate start, LocalDate end) {
        String sql =
                """
                    SELECT text FROM transcripts
                    WHERE user_id = ? AND DATE(timestamp) BETWEEN ? AND ?
                    AND text IS NOT NULL AND TRIM(text) != ''
                    ORDER BY timestamp ASC
                """;

        List<String> messages =
                jdbcTemplate.queryForList(sql, String.class, targetUserId, start, end);

        if (messages.isEmpty()) {
            return null;
        }

        if (messages.size() > MAX_MESSAGES) {
            messages = messages.subList(messages.size() - MAX_MESSAGES, messages.size());
            log.info(
                    "📊 Limitando a {} mensagens mais recentes (total: {})",
                    MAX_MESSAGES,
                    messages.size());
        }

        String combined = String.join("\n---\n", messages);

        // ... (código anterior mantido) ...
        if (combined.length() > MAX_PROMPT_CHARS) {
            combined =
                    combined.substring(0, MAX_PROMPT_CHARS) + "... [corte por limite de contexto]";
            log.info("✂️ Prompt truncado para {} caracteres.", MAX_PROMPT_CHARS);
        }

        String systemPrompt = promptRegistryService.getPodcastSystemPrompt();
        String userPrompt = promptRegistryService.getPodcastUserPrompt(combined);

        log.info(
                "🎙️ Gerando roteiro do podcast para semana de {} a {} ({} caracteres)",
                start,
                end,
                combined.length());

        String script =
                groqClient.chatCompletion(systemPrompt, userPrompt, digestModel, 0.7, maxTokens);

        log.info("✅ Roteiro gerado com {} caracteres.", script.length());
        return script;
    }
}
