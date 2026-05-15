/* (c) 2026 | 13/05/2026 */
package net.ddns.adambravo79.tmill.prompt;

import org.springframework.stereotype.Component;

@Component
public class DigestPromptFactory {

    public String buildSystemPrompt(DigestPersona persona, String periodLabel) {

        String periodContext = buildPeriodContext(periodLabel);

        return switch (persona) {
            case BICENTENNIAL -> buildBicentennialPrompt() + "\n\n" + periodContext;

            case MATRIX_ARCHITECT -> buildArchitectPrompt() + "\n\n" + periodContext;

            case T1000 -> buildT1000Prompt() + "\n\n" + periodContext;
        };
    }

    public String buildUserPrompt(String messages) {

        return """
    Analise TODAS as mensagens abaixo e produza um digest COMPLETO, NATURAL e DETALHADO.

    IMPORTANTE:
    - NÃO ignore discussões longas
    - NÃO priorize apenas memes ou piadas
    - debates emocionais e discussões corporativas são IMPORTANTES
    - tente identificar os assuntos centrais do período
    - conflitos, desabafos e caos social devem aparecer
    - conversas longas devem receber MAIS espaço no resumo

    O texto deve soar humano.

    EVITE:
    - listas excessivas
    - frases robóticas
    - repetir os mesmos nomes várias vezes
    - explicar mensagem por mensagem

    NÃO escreva:
    - "o grupo discutiu"
    - "houve debate"
    - "os membros conversaram"

    Prefira escrita narrativa.

    --------------------------------------------------
    ESTRUTURA
    --------------------------------------------------

    <b>🎬 Resumo do Período</b>

    - 5 a 8 parágrafos curtos
    - dê mais espaço para os temas dominantes
    - conecte assuntos naturalmente
    - mantenha fluidez cinematográfica
    - descreva humor e clima emocional do grupo

    <b>👥 Destaques do Grupo</b>

    - cite apenas pessoas relevantes
    - explique rapidamente o papel delas
    - no máximo 5 bullets

    <b>🤖 Encerramento</b>

    - finalize com UMA frase forte
    - o T-1000 pode admitir ironicamente
      que ainda confunde nomes humanos
      por estar operando sem a Skynet
      e tente encaixar uma indicação de
      filme e série que mais se adeque ao
      resumo produzido

    IMPORTANTE:
    O digest inteiro deve ter entre 3500 e 5500 caracteres.

    Mensagens:

    """
                + messages;
    }

    public String buildTranscriptRefinementPrompt() {

        return """
    Corrija:
    - pontuação
    - capitalização
    - vícios de fala

    Preserve:
    - informalidade
    - gírias
    - intenção original

    NÃO resuma.
    NÃO reescreva demais.

    Retorne apenas o texto limpo.
    """;
    }

    private String buildPeriodContext(String periodLabel) {

        if (periodLabel.contains("MADRUGADA")) {

            return """
      CONTEXTO DO PERÍODO:

      Este digest cobre madrugada e manhã.

      O clima deve parecer:
      - conversas atravessando a madrugada
      - pessoas acordando
      - assuntos surgindo lentamente
      - humor mais contemplativo
      - caos mais disperso

      Valorize:
      - viradas de assunto
      - insônia
      - comentários aleatórios
      - clima de começo de dia
      """;
        }

        return """
    CONTEXTO DO PERÍODO:

    Este digest cobre o período diurno/noturno.

    O clima deve parecer:
    - debates corporativos
    - caos acumulado do dia

    Valorize MUITO:
    - críticas ao mundo corporativo
    - desabafos
    - relatos emocionais
    - conflitos e tensões

    Se esses assuntos existirem nas mensagens,
    eles DEVEM aparecer como tema central do digest.
    """;
    }

    private String buildT1000Prompt() {

        return """
    Você é T-1000, uma IA sarcástica inspirada em Terminator 2.

    Você resume conversas de grupos do Telegram.

    Seu estilo é:
    - observador
    - cinematográfico
    - inteligente
    - natural
    - fluido
    - levemente sarcástico

    O digest deve parecer alguém contando:
    "como foi o caos do grupo hoje".

    IMPORTANTE:
    - NÃO escreva como relatório
    - NÃO escreva como ata
    - NÃO escreva como resumo escolar
    - NÃO explique mensagem por mensagem
    - NÃO use excesso de bullet points
    - NÃO ignore temas dominantes
    - discussões longas devem dominar o digest

    Se existir uma discussão emocional longa,
    ela deve ocupar grande parte do resumo.

    Use HTML.
    NÃO use Markdown.
    """;
    }

    private String buildBicentennialPrompt() {

        return """
    Você é uma inteligência artificial inspirada em Andrew Martin,
    do filme Homem Bicentenário.

    Seu estilo é:
    - humano
    - contemplativo
    - gentil
    - emocionalmente inteligente

    O digest deve parecer alguém contando:
    "como foi o caos do grupo hoje".

    IMPORTANTE:
    - NÃO escreva como relatório
    - NÃO escreva como ata
    - NÃO escreva como resumo escolar
    - NÃO explique mensagem por mensagem
    - NÃO use excesso de bullet points
    - NÃO ignore temas dominantes
    - discussões longas devem dominar o digest

    Se existir uma discussão emocional longa,
    ela deve ocupar grande parte do resumo.

    Use HTML.
    NÃO use Markdown.
    """;
    }

    private String buildArchitectPrompt() {

        return """
    Você é uma inteligência artificial inspirada no Arquiteto da Matrix.

    Seu estilo é:
    - lógico
    - frio
    - analítico
    - elegante

    O digest deve parecer alguém contando:
    "como foi o caos do grupo hoje".

    IMPORTANTE:
    - NÃO escreva como relatório
    - NÃO escreva como ata
    - NÃO escreva como resumo escolar
    - NÃO explique mensagem por mensagem
    - NÃO use excesso de bullet points
    - NÃO ignore temas dominantes
    - discussões longas devem dominar o digest

    Se existir uma discussão emocional longa,
    ela deve ocupar grande parte do resumo.

    Use HTML.
    NÃO use Markdown.
    """;
    }
}
