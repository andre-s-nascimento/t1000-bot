/* (c) 2026 | 27/04/2026 */
package net.ddns.adambravo79.tmill.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import net.ddns.adambravo79.tmill.dto.ErrorResponse;
import net.ddns.adambravo79.tmill.exception.AudioProcessingException;
import net.ddns.adambravo79.tmill.exception.MovieNotFoundException;
import net.ddns.adambravo79.tmill.exception.TelegramFileException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void deveTratarAudioProcessingException() {
        ResponseEntity<ErrorResponse> response =
                handler.handleAudio(new AudioProcessingException("Falha no áudio"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.erro()).isEqualTo("Falha no áudio");
        assertThat(body.tipo()).isEqualTo("AudioProcessingException");
        assertThat(body.timestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void deveTratarTelegramFileException() {
        ResponseEntity<ErrorResponse> response =
                handler.handleTelegramFile(new TelegramFileException("Erro no arquivo", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.erro()).isEqualTo("Erro no arquivo");
        assertThat(body.tipo()).isEqualTo("TelegramFileException");
        assertThat(body.timestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void deveTratarMovieNotFoundException() {
        ResponseEntity<ErrorResponse> response =
                handler.handleMovieNotFound(new MovieNotFoundException("Filme não encontrado"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.erro()).isEqualTo("Filme não encontrado");
        assertThat(body.tipo()).isEqualTo("MovieNotFoundException");
        assertThat(body.timestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void deveTratarRuntimeException() {
        ResponseEntity<ErrorResponse> response =
                handler.handleRuntime(new RuntimeException("Erro genérico"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.erro()).isEqualTo("Erro genérico");
        assertThat(body.tipo()).isEqualTo("RuntimeException");
        assertThat(body.timestamp()).isBeforeOrEqualTo(Instant.now());
    }
}
