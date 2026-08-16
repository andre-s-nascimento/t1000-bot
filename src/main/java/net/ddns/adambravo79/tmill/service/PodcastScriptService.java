package net.ddns.adambravo79.tmill.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.client.GroqClient;

@Service
@Slf4j
@RequiredArgsConstructor
public class PodcastScriptService {

    private final JdbcTemplate jdbcTemplate;
    private final GroqClient groqClient;

    @Value("${podcast.target.user-id}")
    private long targetUserId;

    @Value("${podcast.script.max-tokens:3000}")
    private int maxTokens;

    @Value("${groq.model.digest}") // ← usa o modelo configurado
    private String digestModel;

    // 🔥 Limite de caracteres para o prompt
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

        // 🔥 Limita para as últimas 20 mensagens (mais recentes)
        if (messages.size() > MAX_MESSAGES) {
            messages = messages.subList(messages.size() - MAX_MESSAGES, messages.size());
            log.info(
                    "📊 Limitando a {} mensagens mais recentes (total: {})",
                    MAX_MESSAGES,
                    messages.size());
        }

        // Junta tudo
        String combined = String.join("\n---\n", messages);

        // 🔥 Trunca para 12.000 caracteres
        if (combined.length() > MAX_PROMPT_CHARS) {
            combined =
                    combined.substring(0, MAX_PROMPT_CHARS) + "... [corte por limite de contexto]";
            log.info("✂️ Prompt truncado para {} caracteres.", MAX_PROMPT_CHARS);
        }

        // 🔥 Prompt mais conciso para reduzir saída
        String systemPrompt =
                """
Você é T-1000 e apresenta o "Silas Cast", resumo semanal dos áudios do Silas Bezerra.
Crie um roteiro NARRADO e FLUIDO para ser lido em voz alta (TTS).

REGRAS IMPORTANTES:
- O áudio final deve ter NO MÁXIMO 8-10 MINUTOS (cerca de 600-800 palavras).
- Use linguagem natural, coloquial e envolvente.
- NÃO use asteriscos (*), markdown ou formatação especial.
- NÃO use tópicos numerados ou bullet points.
- Escreva como se estivesse contando uma história.
- Inclua introdução breve e encerramento.
- Encerre com: "E caso eu não veja vocês, bom dia, boa noite e boa noite!"
- Resuma os temas principais, não repita mensagem por mensagem.
- SEJA CONCISO. Prefira qualidade à quantidade.
""";

        String userPrompt = "Aqui estão as mensagens da semana passada:\n\n" + combined;

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
