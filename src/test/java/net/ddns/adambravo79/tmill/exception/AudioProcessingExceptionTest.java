package net.ddns.adambravo79.tmill.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AudioProcessingExceptionTest {

    private static final String MENSAGEM = "Erro no processamento de áudio";
    private static final String CONTEXTO = "fileId=123, chatId=456";
    private static final Throwable CAUSA = new RuntimeException("Causa raiz");

    @Test
    void construtorComMensagem_deveCriarExcecao() {
        AudioProcessingException ex = new AudioProcessingException(MENSAGEM);

        assertThat(ex.getMessage()).isEqualTo(MENSAGEM);
        assertThat(ex.getContexto()).isNull();
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void construtorComMensagemECausa_deveCriarExcecao() {
        AudioProcessingException ex = new AudioProcessingException(MENSAGEM, CAUSA);

        assertThat(ex.getMessage()).isEqualTo(MENSAGEM);
        assertThat(ex.getContexto()).isNull();
        assertThat(ex.getCause()).isEqualTo(CAUSA);
    }

    @Test
    void construtorComMensagemContextoCausa_deveCriarExcecao() {
        AudioProcessingException ex = new AudioProcessingException(MENSAGEM, CONTEXTO, CAUSA);

        assertThat(ex.getMessage()).isEqualTo(MENSAGEM);
        assertThat(ex.getContexto()).isEqualTo(CONTEXTO);
        assertThat(ex.getCause()).isEqualTo(CAUSA);
    }

    @Test
    void getContexto_deveRetornarValor() {
        AudioProcessingException ex = new AudioProcessingException(MENSAGEM, CONTEXTO, CAUSA);
        assertThat(ex.getContexto()).isEqualTo(CONTEXTO);

        // Sem contexto
        AudioProcessingException ex2 = new AudioProcessingException(MENSAGEM);
        assertThat(ex2.getContexto()).isNull();
    }

    @Test
    void toString_deveConterMensagemEContexto() {
        AudioProcessingException ex = new AudioProcessingException(MENSAGEM, CONTEXTO, CAUSA);
        String str = ex.toString();

        assertThat(str).contains("AudioProcessingException").contains(MENSAGEM).contains(CONTEXTO);

        // Sem contexto
        AudioProcessingException ex2 = new AudioProcessingException(MENSAGEM);
        String str2 = ex2.toString();
        assertThat(str2)
                .contains("AudioProcessingException")
                .contains(MENSAGEM)
                .contains("contexto=null");
    }
}
