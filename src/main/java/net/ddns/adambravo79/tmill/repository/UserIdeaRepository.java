package net.ddns.adambravo79.tmill.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import net.ddns.adambravo79.tmill.document.UserIdeaDocument;

@Repository
public interface UserIdeaRepository extends MongoRepository<UserIdeaDocument, String> {

    // Busca ideias cadastradas por um usuário específico
    List<UserIdeaDocument> findByUserId(long userId);
}
