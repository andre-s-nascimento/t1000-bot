package net.ddns.adambravo79.tmill.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import net.ddns.adambravo79.tmill.document.BotConversationDocument;

@Repository
public interface BotConversationRepository
        extends MongoRepository<BotConversationDocument, String> {

    // Busca o histórico de conversas de um chat específico ordenado por relevância/data
    List<BotConversationDocument> findByChatId(long chatId);
}
