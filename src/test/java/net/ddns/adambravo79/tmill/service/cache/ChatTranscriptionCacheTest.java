package net.ddns.adambravo79.tmill.service.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatTranscriptionCacheTest {

    private ChatTranscriptionCache cache;

    @BeforeEach
    void setUp() {
        cache = new ChatTranscriptionCache();
    }

    @Test
    void salvar_deveArmazenarTextoParaChatId() {
        long chatId = 123L;
        String texto = "Transcrição refinada";

        cache.salvar(chatId, texto);

        assertThat(cache.recuperar(chatId)).isEqualTo(texto);
        assertThat(cache.existe(chatId)).isTrue();
    }

    @Test
    void salvar_deveSobrescreverTextoExistente() {
        long chatId = 123L;
        cache.salvar(chatId, "primeiro");
        cache.salvar(chatId, "segundo");

        assertThat(cache.recuperar(chatId)).isEqualTo("segundo");
    }

    @Test
    void salvar_deveLancarExcecaoQuandoTextoNull() {
        long chatId = 123L;
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> cache.salvar(chatId, null));
    }

    @Test
    void recuperar_deveRetornarNullQuandoChatIdNaoExiste() {
        long chatId = 999L;
        assertThat(cache.recuperar(chatId)).isNull();
        assertThat(cache.existe(chatId)).isFalse();
    }

    @Test
    void remover_deveRemoverEntrada() {
        long chatId = 123L;
        cache.salvar(chatId, "texto");

        cache.remover(chatId);

        assertThat(cache.recuperar(chatId)).isNull();
        assertThat(cache.existe(chatId)).isFalse();
    }

    @Test
    void remover_deveNaoFalharQuandoChatIdNaoExiste() {
        long chatId = 999L;
        cache.remover(chatId); // não deve lançar exceção
        assertThat(cache.existe(chatId)).isFalse();
    }

    @Test
    void existe_deveRetornarFalseParaChatIdInexistente() {
        assertThat(cache.existe(456L)).isFalse();
    }

    @Test
    void existe_deveRetornarTrueParaChatIdExistente() {
        long chatId = 123L;
        cache.salvar(chatId, "texto");
        assertThat(cache.existe(chatId)).isTrue();
    }
}
