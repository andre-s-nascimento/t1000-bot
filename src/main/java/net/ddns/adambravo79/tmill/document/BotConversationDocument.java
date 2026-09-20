package net.ddns.adambravo79.tmill.document;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "bot_conversations")
public record BotConversationDocument(
        @Id String id,
        long chatId,
        long userId,
        String userName,
        String messageType, // Ex: "TEXT", "AUDIO", "COMMAND"
        String content, // Texto da mensagem ou ID do arquivo bruto
        String botResponse, // Resposta enviada pelo T1000
        Instant timestamp) {}
