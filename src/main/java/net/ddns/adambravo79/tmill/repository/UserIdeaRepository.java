package net.ddns.adambravo79.tmill.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import net.ddns.adambravo79.tmill.document.UserIdeaDocument;

public interface UserIdeaRepository extends MongoRepository<UserIdeaDocument, String> {

    // Busca ideias cadastradas por um usuário específico
    List<UserIdeaDocument> findByUserId(long userId);
}
