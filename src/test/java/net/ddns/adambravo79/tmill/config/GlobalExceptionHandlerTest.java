/* (c) 2026 | 27/04/2026 */
package net.ddns.adambravo79.tmill.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import net.ddns.adambravo79.tmill.dto.ErrorResponse;
import net.ddns.adambravo79.tmill.exception.AudioProcessingException;
import net.ddns.adambravo79.tmill.exception.MovieNotFoundException;
import net.ddns.adambravo79.tmill.telegram.exception.TelegramFileException;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    // =========================
    // TESTES PARA AudioProcessingException
    // =========================

    @Test
    void handleAudio_deveRetornarInternalServerError() {
        String errorMsg = "Falha ao processar áudio";
        AudioProcessingException ex = new AudioProcessingException(errorMsg);

        ResponseEntity<ErrorResponse> response = handler.handleAudio(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().erro()).isEqualTo(errorMsg);
        assertThat(response.getBody().tipo()).isEqualTo("AudioProcessingException");
        assertThat(response.getBody().timestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void handleAudio_naoLancarExcecao() {
        AudioProcessingException ex = new AudioProcessingException("teste");
        // Apenas verifica que o método não lança exceção (cobre o log)
        assertThatCode(() -> handler.handleAudio(ex)).doesNotThrowAnyException();
    }

    // =========================
    // TESTES PARA TelegramFileException
    // =========================

    @Test
    void handleTelegramFile_deveRetornarBadRequest() {
        String errorMsg = "Arquivo não encontrado";
        TelegramFileException ex = new TelegramFileException(errorMsg, null);

        ResponseEntity<ErrorResponse> response = handler.handleTelegramFile(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().erro()).isEqualTo(errorMsg);
        assertThat(response.getBody().tipo()).isEqualTo("TelegramFileException");
        assertThat(response.getBody().timestamp()).isBeforeOrEqualTo(Instant.now());
    }

    // =========================
    // TESTES PARA MovieNotFoundException
    // =========================

    @Test
    void handleMovieNotFound_deveRetornarNotFound() {
        String errorMsg = "Filme com ID 123 não encontrado";
        MovieNotFoundException ex = new MovieNotFoundException(errorMsg);

        ResponseEntity<ErrorResponse> response = handler.handleMovieNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().erro()).isEqualTo(errorMsg);
        assertThat(response.getBody().tipo()).isEqualTo("MovieNotFoundException");
        assertThat(response.getBody().timestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void handleMovieNotFound_naoLancarExcecao() {
        MovieNotFoundException ex = new MovieNotFoundException("teste");
        assertThatCode(() -> handler.handleMovieNotFound(ex)).doesNotThrowAnyException();
    }

    // =========================
    // TESTES PARA RuntimeException (fallback)
    // =========================

    @Test
    void handleRuntime_deveRetornarInternalServerError() {
        String errorMsg = "Erro inesperado no sistema";
        RuntimeException ex = new RuntimeException(errorMsg);

        ResponseEntity<ErrorResponse> response = handler.handleRuntime(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().erro()).isEqualTo(errorMsg);
        assertThat(response.getBody().tipo()).isEqualTo("RuntimeException");
        assertThat(response.getBody().timestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void handleRuntime_naoLancarExcecao() {
        RuntimeException ex = new RuntimeException("teste");
        assertThatCode(() -> handler.handleRuntime(ex)).doesNotThrowAnyException();
    }
}
