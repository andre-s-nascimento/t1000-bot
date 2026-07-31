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

    public String generateScript(LocalDate start, LocalDate end) {
        // Busca textos da semana (ordenados por data)
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

        // --- NOVO: Limita o número de mensagens e o tamanho total ---
        // Pega apenas as últimas 30 mensagens (as mais recentes)
        if (messages.size() > 30) {
            messages = messages.subList(messages.size() - 30, messages.size());
            log.info("📊 Limitando a 30 mensagens mais recentes (total: {})", messages.size());
        }

        // Junta tudo e trunca para no máximo 18.000 caracteres
        String combined = String.join("\n---\n", messages);
        if (combined.length() > 18000) {
            combined = combined.substring(0, 20000) + "... [corte por limite de contexto]";
            log.info("✂️ Prompt truncado para 18.000 caracteres.");
        }

        // Prompt do sistema (mais conciso para economizar tokens)
        String systemPrompt =
                """
        Você é T-1000 e é apresentador do podcast chamado "Silas Cast",
        que faz um resumo dos áudios do nosso querido Silas Bezerra.
        Crie um roteiro narrado e fluido baseado nas mensagens abaixo.
        O texto deve ser escrito para ser lido em voz alta (TTS).
        Regras:
        - Use linguagem natural, coloquial e envolvente.
        - NÃO use asteriscos (*), underscores (_), markdown ou formatação especial.
        - NÃO use tópicos numerados ou bullet points.
        - Escreva como se estivesse contando uma história ou comentando os assuntos da semana.
        - Inclua uma introdução e um encerramento.
        - Encerre sempre com uma variação da citação do Show de Truman: "E caso eu não veja vocês, bom dia, boa noite e boa noite!"
        - Resuma os temas principais sem repetir mensagem por mensagem.
        - Mantenha o texto entre 800 e 1500 palavras (cerca de 4000 caracteres).
        """;

        String userPrompt = "Aqui estão as mensagens da semana passada:\n\n" + combined;

        log.info(
                "🎙️ Gerando roteiro do podcast para semana de {} a {} ({} caracteres)",
                start,
                end,
                combined.length());

        String script =
                groqClient.chatCompletion(
                        systemPrompt,
                        userPrompt,
                        digestModel,
                        0.7,
                        maxTokens // max tokens de saída (reduzido)
                        );

        log.info("✅ Roteiro gerado com {} caracteres.", script.length());
        return script;
    }
}
