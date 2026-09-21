package net.ddns.adambravo79.tmill.document;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "user_ideas")
public record UserIdeaDocument(
        @Id String id,
        long userId,
        String originalText,
        List<String> tags, // Ex: ["arquitetura", "java", "kafka"]
        String category, // Ex: "BACKLOG_TECNICO"
        Instant createdAt) {}
