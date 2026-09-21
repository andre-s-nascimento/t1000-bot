package net.ddns.adambravo79.tmill.document;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "interaction_logs")
public record InteractionLogDocument(
        @Id String id,
        long chatId,
        long userId,
        String userName,
        String actionType, // Ex: "AUDIO_TRANSCRIPTION", "AI_PROMPT", "MOVIE_SEARCH"
        String content, // O texto enviado ou processado
        Instant timestamp) {}
