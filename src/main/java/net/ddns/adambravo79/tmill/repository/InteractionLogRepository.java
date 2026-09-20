package net.ddns.adambravo79.tmill.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import net.ddns.adambravo79.tmill.document.InteractionLogDocument;

@Repository
public interface InteractionLogRepository extends MongoRepository<InteractionLogDocument, String> {

    // Método derivado automático para buscar logs de um chat específico
    List<InteractionLogDocument> findByChatId(long chatId);
}
