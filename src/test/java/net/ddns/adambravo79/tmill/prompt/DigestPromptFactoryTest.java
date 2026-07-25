package net.ddns.adambravo79.tmill.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DigestPromptFactoryTest {

    private final DigestPromptFactory factory = new DigestPromptFactory();

    // ===================== buildSystemPrompt =====================

    @Test
    void buildSystemPrompt_comPersonaT1000_deveIncluirPromptEContexto() {
        String result = factory.buildSystemPrompt(DigestPersona.T1000, "RESUMO DA MANHÃ");
        assertThat(result)
                .contains("Você é T-1000")
                .contains("Use HTML")
                .contains("CONTEXTO DO PERÍODO")
                .contains("Este digest cobre o período diurno/noturno");
    }

    @Test
    void buildSystemPrompt_comPersonaBICENTENNIAL_deveIncluirPromptEContexto() {
        String result = factory.buildSystemPrompt(DigestPersona.BICENTENNIAL, "RESUMO DA MANHÃ");
        assertThat(result)
                .contains("Homem Bicentenário")
                .contains("Use HTML")
                .contains("CONTEXTO DO PERÍODO")
                .contains("Este digest cobre o período diurno/noturno");
    }

    @Test
    void buildSystemPrompt_comPersonaMATRIX_ARCHITECT_deveIncluirPromptEContexto() {
        String result =
                factory.buildSystemPrompt(DigestPersona.MATRIX_ARCHITECT, "RESUMO DA MANHÃ");
        assertThat(result)
                .contains("Arquiteto da Matrix")
                .contains("Use HTML")
                .contains("CONTEXTO DO PERÍODO")
                .contains("Este digest cobre o período diurno/noturno");
    }

    @Test
    void buildSystemPrompt_comPeriodLabelContendoMADRUGADA_deveUsarContextoMadrugada() {
        String result = factory.buildSystemPrompt(DigestPersona.T1000, "RESUMO DA MADRUGADA");
        assertThat(result)
                .contains("Este digest cobre madrugada e manhã")
                .contains("conversas atravessando a madrugada")
                .doesNotContain("Este digest cobre o período diurno/noturno");
    }

    @Test
    void buildSystemPrompt_comPeriodLabelNulo_deveUsarContextoPadrao() {
        String result = factory.buildSystemPrompt(DigestPersona.T1000, null);
        assertThat(result)
                .contains("Você é T-1000")
                .contains("CONTEXTO DO PERÍODO")
                .contains("Este digest cobre o período diurno/noturno");
    }

    // ===================== buildUserPrompt =====================

    @Test
    void buildUserPrompt_deveIncluirMensagensNoTemplate() {
        String messages = "Mensagem 1\nMensagem 2";
        String result = factory.buildUserPrompt(messages);

        assertThat(result)
                .contains("Analise TODAS as mensagens abaixo")
                .contains("O texto deve soar humano")
                .contains("<b>🎬 Resumo do Período</b>")
                .contains("<b>👥 Destaques do Grupo</b>")
                .contains("<b>🤖 Encerramento</b>")
                .contains("Mensagens:")
                .contains(messages);
    }

    @Test
    void buildUserPrompt_comMensagensVazias_deveIncluirTemplateSemMensagens() {
        String result = factory.buildUserPrompt("");
        assertThat(result)
                .contains("Analise TODAS as mensagens abaixo")
                .contains("Mensagens:")
                .doesNotContain("Mensagem 1"); // nenhuma mensagem extra
    }

    @Test
    void buildUserPrompt_comMensagensLongas_deveIncluir() {
        String longMessage = "a".repeat(10000);
        String result = factory.buildUserPrompt(longMessage);
        assertThat(result).contains(longMessage);
    }

    // ===================== buildTranscriptRefinementPrompt =====================

    @Test
    void buildTranscriptRefinementPrompt_deveRetornarPromptEsperado() {
        String result = factory.buildTranscriptRefinementPrompt();

        assertThat(result)
                .contains("Corrija:")
                .contains("pontuação")
                .contains("capitalização")
                .contains("vícios de fala")
                .contains("Preserve:")
                .contains("informalidade")
                .contains("gírias")
                .contains("intenção original")
                .contains("NÃO resuma")
                .contains("NÃO reescreva demais")
                .contains("Retorne apenas o texto limpo");
    }

    // ===================== buildPeriodContext (via reflexão – cobertura total)
    // =====================

    @Test
    void buildPeriodContext_comLabelMadrugada_deveRetornarContextoMadrugada() throws Exception {
        var method =
                DigestPromptFactory.class.getDeclaredMethod("buildPeriodContext", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(factory, "RESUMO DA MADRUGADA");
        assertThat(result)
                .contains("Este digest cobre madrugada e manhã")
                .contains("conversas atravessando a madrugada")
                .contains("pessoas acordando")
                .contains("humor mais contemplativo");
    }

    @Test
    void buildPeriodContext_comLabelNaoMadrugada_deveRetornarContextoPadrao() throws Exception {
        var method =
                DigestPromptFactory.class.getDeclaredMethod("buildPeriodContext", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(factory, "RESUMO DO DIA");
        assertThat(result)
                .contains("Este digest cobre o período diurno/noturno")
                .contains("desabafos")
                .contains("relatos emocionais")
                .contains("conflitos e tensões");
    }

    @Test
    void buildPeriodContext_comLabelNulo_deveRetornarContextoPadrao() throws Exception {
        var method =
                DigestPromptFactory.class.getDeclaredMethod("buildPeriodContext", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(factory, new Object[] {null});
        assertThat(result).contains("Este digest cobre o período diurno/noturno");
    }

    // ===================== Verificação de constantes (opcional, mas garante que não foram
    // alteradas)
    // =====================

    @Test
    void constantesDevemEstarPresentes() {
        // Apenas para garantir que as constantes não foram removidas acidentalmente
        assertThat(factory.buildSystemPrompt(DigestPersona.T1000, ""))
                .contains("T-1000")
                .contains("Use HTML");
        assertThat(factory.buildSystemPrompt(DigestPersona.BICENTENNIAL, ""))
                .contains("Homem Bicentenário");
        assertThat(factory.buildSystemPrompt(DigestPersona.MATRIX_ARCHITECT, ""))
                .contains("Arquiteto da Matrix");
    }
}
