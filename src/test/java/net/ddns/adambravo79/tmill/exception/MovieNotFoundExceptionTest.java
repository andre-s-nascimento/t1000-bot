package net.ddns.adambravo79.tmill.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MovieNotFoundExceptionTest {

    private static final String MENSAGEM = "Filme não encontrado no TMDB";
    private static final String CONTEXTO = "query=batman";
    private static final Throwable CAUSA = new RuntimeException("Causa raiz");

    @Test
    void construtorComMensagem_deveCriarExcecao() {
        MovieNotFoundException ex = new MovieNotFoundException(MENSAGEM);

        assertThat(ex.getMessage()).isEqualTo(MENSAGEM);
        assertThat(ex.getContexto()).isNull();
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void construtorComMensagemECausa_deveCriarExcecao() {
        MovieNotFoundException ex = new MovieNotFoundException(MENSAGEM, CAUSA);

        assertThat(ex.getMessage()).isEqualTo(MENSAGEM);
        assertThat(ex.getContexto()).isNull();
        assertThat(ex.getCause()).isEqualTo(CAUSA);
    }

    @Test
    void construtorComMensagemContextoCausa_deveCriarExcecao() {
        MovieNotFoundException ex = new MovieNotFoundException(MENSAGEM, CONTEXTO, CAUSA);

        assertThat(ex.getMessage()).isEqualTo(MENSAGEM);
        assertThat(ex.getContexto()).isEqualTo(CONTEXTO);
        assertThat(ex.getCause()).isEqualTo(CAUSA);
    }

    @Test
    void getContexto_deveRetornarValor() {
        // Com contexto
        MovieNotFoundException ex = new MovieNotFoundException(MENSAGEM, CONTEXTO, CAUSA);
        assertThat(ex.getContexto()).isEqualTo(CONTEXTO);

        // Sem contexto
        MovieNotFoundException ex2 = new MovieNotFoundException(MENSAGEM);
        assertThat(ex2.getContexto()).isNull();
    }

    @Test
    void toString_deveConterMensagemEContexto() {
        // Com contexto
        MovieNotFoundException ex = new MovieNotFoundException(MENSAGEM, CONTEXTO, CAUSA);
        String str = ex.toString();

        assertThat(str).contains("MovieNotFoundException").contains(MENSAGEM).contains(CONTEXTO);

        // Sem contexto
        MovieNotFoundException ex2 = new MovieNotFoundException(MENSAGEM);
        String str2 = ex2.toString();
        assertThat(str2)
                .contains("MovieNotFoundException")
                .contains(MENSAGEM)
                .contains("contexto=null");
    }

    @Test
    void deveSerInstanciaDeRuntimeException() {
        MovieNotFoundException ex = new MovieNotFoundException(MENSAGEM);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void deveSerInstanciaDeMovieNotFoundException() {
        MovieNotFoundException ex = new MovieNotFoundException(MENSAGEM, CONTEXTO, CAUSA);
        assertThat(ex).isInstanceOf(MovieNotFoundException.class);
    }
}
