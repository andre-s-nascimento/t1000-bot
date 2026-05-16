/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO que representa a resposta de transcrição gerada pelo Whisper.
 *
 * <p>Campos: - text: texto transcrito a partir do áudio processado.
 *
 * <p>Usado pelo {@link net.ddns.adambravo79.tmill.service.AudioService} para transportar o
 * resultado da transcrição e disponibilizá-lo ao {@link
 * net.ddns.adambravo79.tmill.controller.TelegramController}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TranscriptionResponse(String text) {}
