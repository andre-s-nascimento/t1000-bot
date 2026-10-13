/* (c) 2026 | 01/10/2026 */
package net.ddns.adambravo79.tmill.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
        return generateScript(start, end, null, null);
    }

    /**
     * Gera o roteiro do podcast filtrando por intervalo preciso de {@link LocalDateTime}.
     *
     * <p>Se {@code from} e {@code to} forem nulos, cai no filtro por data inteira ({@code DATE}).
     * Caso contrário, filtra por {@code timestamp >= from} e {@code timestamp <= to}.
     *
     * <p>Usado pelo {@code PodcastPublisherService} para respeitar o intervalo exato
     * "sexta 12:00 → sexta 11:59" definido pelo {@code AdminUtils.calcularPeriodoPodcast}.
     */
    public String generateScript(
            LocalDate start, LocalDate end, LocalDateTime from, LocalDateTime to) {

        String sql;
        Object[] params;

        if (from != null && to != null) {
            sql =
                    """
                        SELECT text FROM transcripts
                        WHERE user_id = ?
                          AND timestamp >= ?
                          AND timestamp <= ?
                          AND text IS NOT NULL AND TRIM(text) != ''
                        ORDER BY timestamp ASC
                    """;
            params = new Object[] {targetUserId, from, to};
        } else {
            sql =
                    """
                        SELECT text FROM transcripts
                        WHERE user_id = ? AND DATE(timestamp) BETWEEN ? AND ?
                        AND text IS NOT NULL AND TRIM(text) != ''
                        ORDER BY timestamp ASC
                    """;
            params = new Object[] {targetUserId, start, end};
        }

        List<String> messages = jdbcTemplate.queryForList(sql, String.class, params);

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

        if (combined.length() > MAX_PROMPT_CHARS) {
            combined =
                    combined.substring(0, MAX_PROMPT_CHARS) + "... [corte por limite de contexto]";
            log.info("✂️ Prompt truncado para {} caracteres.", MAX_PROMPT_CHARS);
        }

        String systemPrompt = promptRegistryService.getPodcastSystemPrompt();
        String userPrompt = promptRegistryService.getPodcastUserPrompt(combined);

        log.info(
                "🎙️ Gerando roteiro do podcast para {} a {} ({} caracteres)",
                (from != null ? from : start + " 00:00"),
                (to != null ? to : end + " 23:59"),
                combined.length());

        double temperature = promptRegistryService.getPodcastTemperature();
        String script =
                groqClient.chatCompletion(
                        systemPrompt, userPrompt, digestModel, temperature, maxTokens);

        log.info("✅ Roteiro gerado com {} caracteres.", script.length());
        return script;
    }
}
