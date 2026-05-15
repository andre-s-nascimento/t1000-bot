/* (c) 2026 | 27/04/2026 */
package net.ddns.adambravo79.tmill.config;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.dto.ErrorResponse;
import net.ddns.adambravo79.tmill.exception.AudioProcessingException;
import net.ddns.adambravo79.tmill.exception.MovieNotFoundException;
import net.ddns.adambravo79.tmill.exception.TelegramFileException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AudioProcessingException.class)
    public ResponseEntity<ErrorResponse> handleAudio(AudioProcessingException ex) {
        log.error(
                "❌ Erro no processamento de áudio tipo={} msg={}",
                ex.getClass().getSimpleName(),
                ex.getMessage(),
                ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        new ErrorResponse(
                                ex.getMessage(), "AudioProcessingException", Instant.now()));
    }

    @ExceptionHandler(TelegramFileException.class)
    public ResponseEntity<ErrorResponse> handleTelegramFile(TelegramFileException ex) {
        log.error(
                "❌ Erro ao manipular arquivo do Telegram tipo={} msg={}",
                ex.getClass().getSimpleName(),
                ex.getMessage(),
                ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ex.getMessage(), "TelegramFileException", Instant.now()));
    }

    @ExceptionHandler(MovieNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMovieNotFound(MovieNotFoundException ex) {
        log.warn(
                "⚠️ Filme não encontrado tipo={} msg={}",
                ex.getClass().getSimpleName(),
                ex.getMessage(),
                ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(ex.getMessage(), "MovieNotFoundException", Instant.now()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException ex) {
        log.error(
                "❌ Erro inesperado tipo={} msg={}",
                ex.getClass().getSimpleName(),
                ex.getMessage(),
                ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(ex.getMessage(), "RuntimeException", Instant.now()));
    }
}
