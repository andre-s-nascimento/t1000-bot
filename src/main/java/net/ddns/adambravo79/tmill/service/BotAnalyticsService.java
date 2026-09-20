package net.ddns.adambravo79.tmill.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.document.BotConversationDocument;
import net.ddns.adambravo79.tmill.document.InteractionLogDocument;
import net.ddns.adambravo79.tmill.document.UserIdeaDocument;
import net.ddns.adambravo79.tmill.repository.BotConversationRepository;
import net.ddns.adambravo79.tmill.repository.InteractionLogRepository;
import net.ddns.adambravo79.tmill.repository.UserIdeaRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class BotAnalyticsService {

    private final BotConversationRepository conversationRepository;
    private final InteractionLogRepository interactionLogRepository;
    private final UserIdeaRepository userIdeaRepository;

    public void registrarConversa(
            long chatId,
            long userId,
            String userName,
            String messageType,
            String content,
            String botResponse) {
        BotConversationDocument doc =
                new BotConversationDocument(
                        null,
                        chatId,
                        userId,
                        userName,
                        messageType,
                        content,
                        botResponse,
                        Instant.now());
        conversationRepository.save(doc);
        log.debug("💾 Conversa salva no MongoDB para chatId={}", chatId);
    }

    public void registrarLogInteracao(
            long chatId, long userId, String userName, String actionType, String content) {
        InteractionLogDocument doc =
                new InteractionLogDocument(
                        null, chatId, userId, userName, actionType, content, Instant.now());
        interactionLogRepository.save(doc);
    }

    public void salvarIdeia(long userId, String originalText, List<String> tags, String category) {
        UserIdeaDocument idea =
                new UserIdeaDocument(null, userId, originalText, tags, category, Instant.now());
        userIdeaRepository.save(idea);
        log.info("💡 Nova ideia salva no MongoDB para userId={}", userId);
    }
}
