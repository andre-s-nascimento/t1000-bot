package net.ddns.adambravo79.tmill.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import net.ddns.adambravo79.tmill.document.BotConversationDocument;
import net.ddns.adambravo79.tmill.document.InteractionLogDocument;
import net.ddns.adambravo79.tmill.document.UserIdeaDocument;
import net.ddns.adambravo79.tmill.repository.BotConversationRepository;
import net.ddns.adambravo79.tmill.repository.InteractionLogRepository;
import net.ddns.adambravo79.tmill.repository.UserIdeaRepository;

@ExtendWith(MockitoExtension.class)
class BotAnalyticsServiceTest {

    @Mock private BotConversationRepository conversationRepository;

    @Mock private InteractionLogRepository interactionLogRepository;

    @Mock private UserIdeaRepository userIdeaRepository;

    @InjectMocks private BotAnalyticsService botAnalyticsService;

    @Test
    @DisplayName("Deve salvar conversa no MongoDB com sucesso")
    void deveSalvarConversaComSucesso() {
        botAnalyticsService.registrarConversa(
                12345L, 6789L, "André Teste", "TEXT", "Olá T1000", "Olá, como posso ajudar?");

        verify(conversationRepository, times(1)).save(any(BotConversationDocument.class));
    }

    @Test
    @DisplayName("Deve salvar ideia do usuário no MongoDB com sucesso")
    void deveSalvarIdeiaComSucesso() {
        botAnalyticsService.salvarIdeia(
                6789L, "Criar testes automatizados", List.of("java", "test"), "BACKLOG");

        verify(userIdeaRepository, times(1)).save(any(UserIdeaDocument.class));
    }

    @Test
    @DisplayName("Deve registrar log de interação no MongoDB com sucesso")
    void deveRegistrarLogInteracaoComSucesso() {
        botAnalyticsService.registrarLogInteracao(
                12345L, 6789L, "André Teste", "TEST_ACTION", "Executando teste unitário");

        verify(interactionLogRepository, times(1)).save(any(InteractionLogDocument.class));
    }
}
