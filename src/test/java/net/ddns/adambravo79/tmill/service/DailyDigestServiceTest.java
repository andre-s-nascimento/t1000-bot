package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import net.ddns.adambravo79.tmill.client.GroqClient;
import net.ddns.adambravo79.tmill.exception.DigestGenerationException;
import net.ddns.adambravo79.tmill.exception.DigestSendException;
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
        // Limpa o Set antes de cada teste para evitar acúmulo
        ReflectionTestUtils.setField(service, "digestChatIds", new HashSet<>());
        ReflectionTestUtils.setField(service, "digestEnabled", true);
        ReflectionTestUtils.setField(service, "digestChatIdsStr", String.valueOf(CHAT_ID));
        service.init();
    }

    // =========================
    // TESTES DE GERAÇÃO DE DIGEST
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

        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages)
                .thenReturn(transcripts);
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
                .thenReturn(List.of())
                .thenReturn(List.of());

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
                .thenReturn(messages)
                .thenReturn(List.of());
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
                .thenReturn(messages)
                .thenReturn(List.of());
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
                .thenReturn(messages)
                .thenReturn(List.of());

        // Stub do groqClient ANTES de qualquer chamada
        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenReturn("Resumo com <b>tag aberta");

        HttpClientErrorException.BadRequest ex =
                HttpClientErrorException.create(
                        HttpStatusCode.valueOf(400),
                        "can't parse entities",
                        null,
                        null,
                        StandardCharsets.UTF_8);

        doThrow(ex).when(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), anyString());

        service.generateDigestCustom(from, to, CHAT_ID);

        verify(telegramFacade, times(1)).enviarMensagemHtml(eq(CHAT_ID), anyString());
        verify(telegramFacade, times(1)).enviarMensagem(eq(CHAT_ID), contains("Resumo com"));
    }

    // =========================
    // TESTES DE SANITIZAÇÃO (via comportamento público)
    // =========================

    @Test
    void deveSanitizarTagsNaoPermitidasNoDigest() throws Exception {
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
                .thenReturn(messages)
                .thenReturn(List.of());
        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenReturn("<ul><li>Item 1</li><li>Item 2</li></ul><br><b>Negrito</b>");

        service.generateDigestCustom(from, to, CHAT_ID);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());
        String resultado = captor.getValue();
        assertThat(resultado)
                .doesNotContain("<ul>", "</ul>", "<li>", "</li>", "<br>")
                .contains("<b>Negrito</b>", "• Item 1", "• Item 2");
    }

    @Test
    void deveLidarComResumoNuloDoGroq() throws Exception {
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
                .thenReturn(messages)
                .thenReturn(List.of());
        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenReturn(null);

        service.generateDigestCustom(from, to, CHAT_ID);

        verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
    }

    // ===================== TESTES ADICIONAIS =====================

    // 1. init()
    @Test
    void init_deveIgnorarIdsInvalidos() {
        ReflectionTestUtils.setField(service, "digestChatIds", new HashSet<>());
        ReflectionTestUtils.setField(service, "digestChatIdsStr", "abc,123,456");
        service.init();
        @SuppressWarnings("unchecked")
        Set<Long> ids = (Set<Long>) ReflectionTestUtils.getField(service, "digestChatIds");
        assertThat(ids).containsExactlyInAnyOrder(123L, 456L);
    }

    @Test
    void init_quandoChatIdsNulo_naoFazNada() {
        ReflectionTestUtils.setField(service, "digestChatIds", new HashSet<>());
        ReflectionTestUtils.setField(service, "digestChatIdsStr", null);
        service.init();
        @SuppressWarnings("unchecked")
        Set<Long> ids = (Set<Long>) ReflectionTestUtils.getField(service, "digestChatIds");
        assertThat(ids).isEmpty();
    }

    @Test
    void init_quandoChatIdsVazio_naoFazNada() {
        ReflectionTestUtils.setField(service, "digestChatIds", new HashSet<>());
        ReflectionTestUtils.setField(service, "digestChatIdsStr", "");
        service.init();
        @SuppressWarnings("unchecked")
        Set<Long> ids = (Set<Long>) ReflectionTestUtils.getField(service, "digestChatIds");
        assertThat(ids).isEmpty();
    }

    // 2. generateDigestCustom - validações
    @Test
    void generateDigestCustom_deveLancarExcecaoQuandoFromNulo() {
        LocalDateTime to = LocalDateTime.now();
        assertThatThrownBy(() -> service.generateDigestCustom(null, to, CHAT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Período não pode ser nulo");
    }

    @Test
    void generateDigestCustom_deveLancarExcecaoQuandoToNulo() {
        LocalDateTime from = LocalDateTime.now();
        assertThatThrownBy(() -> service.generateDigestCustom(from, null, CHAT_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generateDigestCustom_deveLancarExcecaoQuandoFromAposTo() {
        LocalDateTime from = LocalDateTime.now().plusDays(1);
        LocalDateTime to = LocalDateTime.now();
        assertThatThrownBy(() -> service.generateDigestCustom(from, to, CHAT_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 3. Exceções no generateDigest
    @Test
    void generateDigestCustom_deveLogarERelancarRuntimeException() {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("Erro inesperado"));

        assertThatThrownBy(() -> service.generateDigestCustom(from, to, CHAT_ID))
                .isInstanceOf(DigestGenerationException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void generateDigestCustom_deveCapturarDataAccessException() {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenThrow(new org.springframework.dao.DataAccessException("DB error") {});

        service.generateDigestCustom(from, to, CHAT_ID);
        verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
    }

    @Test
    void generateDigestCustom_deveCapturarHttpClientErrorExceptionDoGroq() throws Exception {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();

        List<Map<String, Object>> messages =
                List.of(
                        Map.of(
                                "user_name",
                                "User",
                                "text",
                                "msg",
                                "timestamp",
                                from.plusMinutes(1).toString()));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages)
                .thenReturn(List.of());

        HttpClientErrorException ex =
                HttpClientErrorException.create(
                        HttpStatusCode.valueOf(429),
                        "Rate limit",
                        null,
                        null,
                        StandardCharsets.UTF_8);
        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenThrow(ex);

        service.generateDigestCustom(from, to, CHAT_ID);
        verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
    }

    // 4. Métodos agendados
    @Test
    void generateMorningDigest_quandoHabilitado_deveEnviarDigest() throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        LocalDateTime from = now.minusDays(1).withHour(20).withMinute(30).withSecond(0);
        LocalDateTime to = now.withHour(8).withMinute(30).withSecond(0);

        List<Map<String, Object>> messages =
                List.of(
                        Map.of(
                                "user_name",
                                "User",
                                "text",
                                "Mensagem",
                                "timestamp",
                                from.plusMinutes(1).toString()));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages)
                .thenReturn(List.of());
        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenReturn("Resumo da manhã");

        service.generateMorningDigest();

        verify(telegramFacade, times(1))
                .enviarMensagemHtml(eq(CHAT_ID), contains("RESUMO DA MADRUGADA/MANHÃ"));
        verify(telegramFacade, times(1))
                .enviarMensagemHtml(eq(CHAT_ID), contains("Resumo da manhã"));
    }

    @Test
    void generateMorningDigest_quandoDesabilitado_naoFazNada() {
        ReflectionTestUtils.setField(service, "digestEnabled", false);
        service.generateMorningDigest();
        verifyNoInteractions(groqClient, jdbcTemplate);
    }

    @Test
    void generateEveningDigest_quandoHabilitado_deveEnviarDigest() throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        LocalDateTime from = now.withHour(8).withMinute(30).withSecond(0);
        LocalDateTime to = now.withHour(20).withMinute(30).withSecond(0);

        List<Map<String, Object>> messages =
                List.of(
                        Map.of(
                                "user_name",
                                "User",
                                "text",
                                "Mensagem",
                                "timestamp",
                                from.plusMinutes(1).toString()));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages)
                .thenReturn(List.of());
        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenReturn("Resumo da noite");

        service.generateEveningDigest();

        verify(telegramFacade, times(1)).enviarMensagemHtml(eq(CHAT_ID), contains("RESUMO DO DIA"));
        verify(telegramFacade, times(1))
                .enviarMensagemHtml(eq(CHAT_ID), contains("Resumo da noite"));
    }

    @Test
    void generateEveningDigest_quandoDesabilitado_naoFazNada() {
        ReflectionTestUtils.setField(service, "digestEnabled", false);
        service.generateEveningDigest();
        verifyNoInteractions(groqClient, jdbcTemplate);
    }

    @Test
    void generateMorningDigest_quandoSemChats_naoFazNada() {
        ReflectionTestUtils.setField(service, "digestChatIds", new HashSet<>());
        ReflectionTestUtils.setField(service, "digestChatIdsStr", "");
        service.init();
        service.generateMorningDigest();
        verifyNoInteractions(groqClient, jdbcTemplate);
    }

    @Test
    void generateEveningDigest_quandoSemChats_naoFazNada() {
        ReflectionTestUtils.setField(service, "digestChatIds", new HashSet<>());
        ReflectionTestUtils.setField(service, "digestChatIdsStr", "");
        service.init();
        service.generateEveningDigest();
        verifyNoInteractions(groqClient, jdbcTemplate);
    }

    // 5. buildMessagesBlock - separador por diferença ≥ 20min
    @Test
    void buildMessagesBlock_deveInserirSeparadorQuandoDiferencaMaiorQue20Minutos()
            throws Exception {
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
                                from.plusMinutes(30).toString()));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages)
                .thenReturn(List.of());

        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenReturn("Resumo");

        service.generateDigestCustom(from, to, CHAT_ID);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(groqClient)
                .gerarResumoDigest(promptCaptor.capture(), any(DigestPersona.class), anyString());
        assertThat(promptCaptor.getValue()).contains("NOVO BLOCO DE CONVERSA");
    }

    // 6. sanitizeDigestText - tags adicionais
    @Test
    void sanitizeDigestText_deveManterTagsPermitidas() throws Exception {
        LocalDateTime from = LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).minusDays(1);
        LocalDateTime to = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));

        List<Map<String, Object>> messages =
                List.of(
                        Map.of(
                                "user_name",
                                "User",
                                "text",
                                "msg",
                                "timestamp",
                                from.plusMinutes(1).toString()));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages)
                .thenReturn(List.of());

        String resumoComTags =
                "<i>Itálico</i> <u>sublinhado</u> <s>tachado</s> <code>código</code> <pre>pré</pre>"
                        + " <a>link</a>";
        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenReturn(resumoComTags);

        service.generateDigestCustom(from, to, CHAT_ID);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());
        String sanitizado = captor.getValue();
        assertThat(sanitizado)
                .contains(
                        "<i>Itálico</i>",
                        "<u>sublinhado</u>",
                        "<s>tachado</s>",
                        "<code>código</code>",
                        "<pre>pré</pre>",
                        "<a>link</a>");
    }

    // 7. sendChunk - outros erros
    @Test
    void sendChunk_deveLancarDigestSendException_quandoHttpClientErrorExceptionNaoBadRequest()
            throws Exception {
        LocalDateTime from = LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).minusDays(1);
        LocalDateTime to = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));

        List<Map<String, Object>> messages =
                List.of(
                        Map.of(
                                "user_name",
                                "User",
                                "text",
                                "msg",
                                "timestamp",
                                from.plusMinutes(1).toString()));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages)
                .thenReturn(List.of());

        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenReturn("Resumo");

        HttpClientErrorException ex =
                HttpClientErrorException.create(
                        HttpStatusCode.valueOf(403),
                        "Forbidden",
                        null,
                        null,
                        StandardCharsets.UTF_8);
        doThrow(ex).when(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), anyString());

        // Agora o erro é capturado e relançado como DigestSendException
        assertThatThrownBy(() -> service.generateDigestCustom(from, to, CHAT_ID))
                .isInstanceOf(DigestSendException.class)
                .hasMessageContaining("Erro HTTP 403");
    }

    @Test
    void sendChunk_deveLancarDigestSendException_quandoResourceAccessException() throws Exception {
        LocalDateTime from = LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).minusDays(1);
        LocalDateTime to = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));

        List<Map<String, Object>> messages =
                List.of(
                        Map.of(
                                "user_name",
                                "User",
                                "text",
                                "msg",
                                "timestamp",
                                from.plusMinutes(1).toString()));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages)
                .thenReturn(List.of());

        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenReturn("Resumo");

        doThrow(new ResourceAccessException("Conectividade"))
                .when(telegramFacade)
                .enviarMensagemHtml(eq(CHAT_ID), anyString());

        assertThatThrownBy(() -> service.generateDigestCustom(from, to, CHAT_ID))
                .isInstanceOf(DigestSendException.class)
                .hasMessageContaining("Falha de conectividade");
    }

    // 8. isHtmlParseError (teste direto via reflexão)
    @Test
    void isHtmlParseError_deveRetornarTrueQuandoMensagemContemParseEntities() throws Exception {
        java.lang.reflect.Method method =
                DailyDigestService.class.getDeclaredMethod(
                        "isHtmlParseError", HttpClientErrorException.BadRequest.class);
        method.setAccessible(true);

        HttpClientErrorException.BadRequest ex = mock(HttpClientErrorException.BadRequest.class);
        when(ex.getMessage()).thenReturn("can't parse entities");
        boolean result = (boolean) method.invoke(service, ex);
        assertThat(result).isTrue();

        HttpClientErrorException.BadRequest ex2 = mock(HttpClientErrorException.BadRequest.class);
        when(ex2.getMessage()).thenReturn("Bad Request");
        result = (boolean) method.invoke(service, ex2);
        assertThat(result).isFalse();
    }

    // 9. parseTimestampSafely
    @Test
    void parseTimestampSafely_deveParsearComT() throws Exception {
        java.lang.reflect.Method method =
                DailyDigestService.class.getDeclaredMethod("parseTimestampSafely", String.class);
        method.setAccessible(true);

        LocalDateTime result = (LocalDateTime) method.invoke(service, "2026-07-24T10:30:00");
        assertThat(result).isNotNull();
    }

    @Test
    void parseTimestampSafely_deveParsearComEspaco() throws Exception {
        java.lang.reflect.Method method =
                DailyDigestService.class.getDeclaredMethod("parseTimestampSafely", String.class);
        method.setAccessible(true);

        LocalDateTime result = (LocalDateTime) method.invoke(service, "2026-07-24 10:30:00");
        assertThat(result).isNotNull();
    }

    @Test
    void parseTimestampSafely_deveRetornarNullQuandoInvalido() throws Exception {
        java.lang.reflect.Method method =
                DailyDigestService.class.getDeclaredMethod("parseTimestampSafely", String.class);
        method.setAccessible(true);

        LocalDateTime result = (LocalDateTime) method.invoke(service, "invalid");
        assertThat(result).isNull();
    }

    @Test
    void parseTimestampSafely_deveRetornarNullQuandoNull() throws Exception {
        java.lang.reflect.Method method =
                DailyDigestService.class.getDeclaredMethod("parseTimestampSafely", String.class);
        method.setAccessible(true);

        // Passar explicitamente um array com null para evitar erro de argumentos
        LocalDateTime result = (LocalDateTime) method.invoke(service, new Object[] {null});
        assertThat(result).isNull();
    }

    // 10. truncateIfNeeded - dentro do limite
    @Test
    void truncateIfNeeded_naoTruncarQuandoDentroDoLimite() throws Exception {
        java.lang.reflect.Method method =
                DailyDigestService.class.getDeclaredMethod("truncateIfNeeded", String.class);
        method.setAccessible(true);

        String texto = "a".repeat(1000);
        String result = (String) method.invoke(service, texto);
        assertThat(result).isEqualTo(texto);
    }

    // 11. safeSubstring - bordas
    @Test
    void safeSubstring_deveLidarComBordas() throws Exception {
        java.lang.reflect.Method method =
                DailyDigestService.class.getDeclaredMethod(
                        "safeSubstring", String.class, int.class, int.class);
        method.setAccessible(true);

        String text = "abcdef";
        String result = (String) method.invoke(service, text, -5, 3);
        assertThat(result).isEqualTo("abc");
        result = (String) method.invoke(service, text, 2, 20);
        assertThat(result).isEqualTo("cdef");
        result = (String) method.invoke(service, text, 4, 2);
        assertThat(result).isEmpty();
    }

    // 12. buildHeader
    @Test
    void buildHeader_deveFormatarCorretamente() throws Exception {
        java.lang.reflect.Method method =
                DailyDigestService.class.getDeclaredMethod(
                        "buildHeader", String.class, LocalDateTime.class, LocalDateTime.class);
        method.setAccessible(true);

        LocalDateTime from = LocalDateTime.of(2026, Month.JULY, 24, 8, 30);
        LocalDateTime to = LocalDateTime.of(2026, Month.JULY, 24, 20, 30);
        String header = (String) method.invoke(service, "PERÍODO TESTE", from, to);
        assertThat(header)
                .contains("PERÍODO TESTE")
                .contains("24/07/2026 08:30")
                .contains("24/07/2026 20:30");
    }

    // 13. generateSummary - exceções específicas
    @Test
    void generateDigestCustom_deveCapturarTooManyRequests() throws Exception {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();

        List<Map<String, Object>> messages =
                List.of(
                        Map.of(
                                "user_name",
                                "User",
                                "text",
                                "msg",
                                "timestamp",
                                from.plusMinutes(1).toString()));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages)
                .thenReturn(List.of());

        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenThrow(HttpClientErrorException.TooManyRequests.class);

        service.generateDigestCustom(from, to, CHAT_ID);
        verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
    }

    @Test
    void generateDigestCustom_deveCapturarResourceAccessException() throws Exception {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();

        List<Map<String, Object>> messages =
                List.of(
                        Map.of(
                                "user_name",
                                "User",
                                "text",
                                "msg",
                                "timestamp",
                                from.plusMinutes(1).toString()));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages)
                .thenReturn(List.of());

        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenThrow(new ResourceAccessException("Conectividade"));

        service.generateDigestCustom(from, to, CHAT_ID);
        verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
    }

    // 14. sendDigestToChat - erro inesperado
    @Test
    void sendDigestToChat_deveLancarDigestSendException_quandoErroInesperado() throws Exception {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();

        List<Map<String, Object>> messages =
                List.of(
                        Map.of(
                                "user_name",
                                "User",
                                "text",
                                "msg",
                                "timestamp",
                                from.plusMinutes(1).toString()));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages)
                .thenReturn(List.of());

        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenReturn("Resumo");

        doThrow(new RuntimeException("Erro inesperado no Telegram"))
                .when(telegramFacade)
                .enviarMensagemHtml(eq(CHAT_ID), anyString());

        assertThatThrownBy(() -> service.generateDigestCustom(from, to, CHAT_ID))
                .isInstanceOf(DigestSendException.class)
                .hasMessageContaining("Erro inesperado no envio do digest");
    }

    // 15. sanitizeDigestText - tags <a> vazias
    @Test
    void sanitizeDigestText_deveRemoverTagsA_Vazias() throws Exception {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();

        List<Map<String, Object>> messages =
                List.of(
                        Map.of(
                                "user_name",
                                "User",
                                "text",
                                "msg",
                                "timestamp",
                                from.plusMinutes(1).toString()));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages)
                .thenReturn(List.of());

        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenReturn("Texto <a> </a> com link vazio");

        service.generateDigestCustom(from, to, CHAT_ID);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());
        String sanitizado = captor.getValue();
        // A sanitização atual remove a tag, mas mantém os espaços
        assertThat(sanitizado).contains("Texto  com link vazio");
    }
}
