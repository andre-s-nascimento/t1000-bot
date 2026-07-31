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

    public String generateScript(LocalDate start, LocalDate end) {
        // Busca textos da semana
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

        // Junta tudo (limita para não estourar o contexto do Groq)
        String combined = String.join("\n---\n", messages);
        if (combined.length() > 20000) {
            combined = combined.substring(0, 20000) + "... [corte por limite de contexto]";
        }

        // Prompt para o Groq
        String systemPrompt =
                """
        Você é um apresentador de podcast chamado "T-1000 Cast".
        Crie um roteiro narrado e fluido baseado nas mensagens abaixo.
        O texto deve ser escrito para ser lido em voz alta (TTS).
        Regras:
        - Use uma linguagem natural, coloquial e envolvente.
        - NÃO use asteriscos (*), underscores (_), markdown ou formatação especial.
        - NÃO use tópicos numerados ou bullet points.
        - Escreva como se estivesse contando uma história ou comentando os assuntos da semana.
        - Inclua uma introdução ("Olá, ouvintes! Esta é a edição semanal...") e um encerramento.
        - Resuma os temas principais sem repetir mensagem por mensagem.
        - Mantenha o texto entre 1000 e 2500 palavras.
        """;

        String userPrompt = "Aqui estão as mensagens da semana passada:\n\n" + combined;

        log.info("🎙️ Gerando roteiro do podcast para semana de {} a {}", start, end);
        String script =
                groqClient.chatCompletion(
                        systemPrompt, userPrompt, "llama-3.1-8b-instant", 0.7, maxTokens);
        log.info("✅ Roteiro gerado com {} caracteres.", script.length());

        return script;
    }
}
