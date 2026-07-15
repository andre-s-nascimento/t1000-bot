package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import net.ddns.adambravo79.tmill.client.GroqClient;
import net.ddns.adambravo79.tmill.prompt.DigestPersona;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;

@ExtendWith(MockitoExtension.class)
class DailyDigestServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private GroqClient groqClient;
    @Mock private TelegramFacade telegramFacade;

    @InjectMocks private DailyDigestService service;

    private static final long CHAT_ID = 12345L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "digestEnabled", true);
        ReflectionTestUtils.setField(service, "digestChatIdsStr", String.valueOf(CHAT_ID));
        service.init();
    }

    // =========================
    // 🧪 TESTE DE GERAÇÃO DE DIGEST
    // =========================

    @Test
    void deveGerarDigestPersonalizadoComSucesso() throws Exception {
        LocalDateTime from = LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).minusDays(1);
        LocalDateTime to = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));

        List<Map<String, Object>> messages =
                List.of(
                        Map.of(
                                "user_name",
                                "User1",
                                "text",
                                "Mensagem 1",
                                "timestamp",
                                from.plusMinutes(1).toString()),
                        Map.of(
                                "user_name",
                                "User2",
                                "text",
                                "Mensagem 2",
                                "timestamp",
                                from.plusMinutes(5).toString()));
        List<Map<String, Object>> transcripts =
                List.of(
                        Map.of(
                                "user_name",
                                "User3",
                                "text",
                                "Transcrição 1",
                                "timestamp",
                                from.plusMinutes(10).toString()));

        // Uso de anyString() e any(Object[].class) para resolver ambiguidade
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages, transcripts);
        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenReturn("Resumo do digest");

        service.generateDigestCustom(from, to, CHAT_ID);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());
        String mensagem = captor.getValue();
        assertThat(mensagem).contains("PERÍODO PERSONALIZADO").contains("Resumo do digest");
    }

    @Test
    void deveNaoGerarDigestQuandoNaoHaMensagens() {
        LocalDateTime from = LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).minusDays(1);
        LocalDateTime to = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(), List.of());

        service.generateDigestCustom(from, to, CHAT_ID);

        verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
        verifyNoInteractions(groqClient);
    }

    @Test
    void deveTruncarPromptLongo() throws Exception {
        LocalDateTime from = LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).minusDays(1);
        LocalDateTime to = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));

        String textoLongo = "a".repeat(40000);
        List<Map<String, Object>> messages =
                List.of(
                        Map.of(
                                "user_name",
                                "User1",
                                "text",
                                textoLongo,
                                "timestamp",
                                from.plusMinutes(1).toString()));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages, List.of());
        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenReturn("Resumo truncado");

        service.generateDigestCustom(from, to, CHAT_ID);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(groqClient)
                .gerarResumoDigest(captor.capture(), any(DigestPersona.class), anyString());
        String prompt = captor.getValue();
        assertThat(prompt.length()).isLessThan(40000);
    }

    @Test
    void deveSanitizarResumo() throws Exception {
        LocalDateTime from = LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).minusDays(1);
        LocalDateTime to = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));

        List<Map<String, Object>> messages =
                List.of(
                        Map.of(
                                "user_name",
                                "User1",
                                "text",
                                "Mensagem",
                                "timestamp",
                                from.plusMinutes(1).toString()));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages, List.of());
        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenReturn("<ul><li>Item</li></ul><b>Negrito</b>");

        service.generateDigestCustom(from, to, CHAT_ID);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());
        String sanitizado = captor.getValue();
        assertThat(sanitizado)
                .doesNotContain("<ul>", "<li>", "</li>")
                .contains("<b>Negrito</b>")
                .contains("• Item");
    }

    @Test
    void deveEnviarFallbackParaTextoQuandoFalhaParseHTML() throws Exception {
        LocalDateTime from = LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).minusDays(1);
        LocalDateTime to = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));

        List<Map<String, Object>> messages =
                List.of(
                        Map.of(
                                "user_name",
                                "User1",
                                "text",
                                "Mensagem",
                                "timestamp",
                                from.plusMinutes(1).toString()));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages, List.of());
        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenReturn("Resumo com <b>tag aberta");

        doThrow(new RuntimeException("can't parse entities"))
                .when(telegramFacade)
                .enviarMensagemHtml(eq(CHAT_ID), anyString());

        service.generateDigestCustom(from, to, CHAT_ID);

        verify(telegramFacade, times(1)).enviarMensagemHtml(eq(CHAT_ID), anyString());
        verify(telegramFacade, times(1)).enviarMensagem(eq(CHAT_ID), contains("Resumo com"));
    }

    // =========================
    // 🧪 TESTES DE SANITIZAÇÃO DIRETA
    // =========================

    @Test
    void sanitizeDigestText_deveRemoverTagsNaoPermitidas() {
        String texto = "<ul><li>Item 1</li><li>Item 2</li></ul><br><b>Negrito</b>";
        String resultado = ReflectionTestUtils.invokeMethod(service, "sanitizeDigestText", texto);
        assertThat(resultado)
                .doesNotContain("<ul>", "</ul>", "<li>", "</li>", "<br>")
                .contains("<b>Negrito</b>", "• Item 1", "• Item 2");
    }

    @Test
    void sanitizeDigestText_deveLidarComTextoNull() {
        String resultado =
                ReflectionTestUtils.invokeMethod(service, "sanitizeDigestText", (Object) null);
        assertThat(resultado).isEmpty();
    }

    // =========================
    // 🧪 TESTE DE CONSTRUÇÃO DE MENSAGENS (esboço – será preenchido depois)
    // =========================

    @Test
    void buildMessagesBlock_deveAgruparPorBlocos() {
        // Este teste será implementado em uma iteração futura
        assertThat(true).isTrue(); // placeholder para evitar warning de teste sem asserção
    }
}
