package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import net.ddns.adambravo79.tmill.exception.GroqRateLimitException;
import net.ddns.adambravo79.tmill.prompt.DigestPersona;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;

@ExtendWith(MockitoExtension.class)
class DailyDigestServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private GroqClient groqClient;
    @Mock private TelegramFacade telegramFacade;
    @Mock private MetricsService metricsService;

    @InjectMocks private DailyDigestService service;

    private static final long CHAT_ID = 12345L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "digestChatIds", new HashSet<>());
        ReflectionTestUtils.setField(service, "digestEnabled", true);
        ReflectionTestUtils.setField(service, "digestChatIdsStr", String.valueOf(CHAT_ID));
        service.init();
    }

    // ============================================================
    // INIT
    // ============================================================

    @Test
    @DisplayName("init: ignora IDs inválidos")
    void init_idsInvalidos() {
        ReflectionTestUtils.setField(service, "digestChatIds", new HashSet<>());
        ReflectionTestUtils.setField(service, "digestChatIdsStr", "abc,123,456");
        service.init();

        @SuppressWarnings("unchecked")
        Set<Long> ids = (Set<Long>) ReflectionTestUtils.getField(service, "digestChatIds");
        assertThat(ids).containsExactlyInAnyOrder(123L, 456L);
        verifyNoInteractions(metricsService);
    }

    @Test
    @DisplayName("init: chat-ids null/vazio não carrega")
    void init_chatIdsNulos() {
        ReflectionTestUtils.setField(service, "digestChatIds", new HashSet<>());
        ReflectionTestUtils.setField(service, "digestChatIdsStr", null);
        service.init();
        assertThat((Set<?>) ReflectionTestUtils.getField(service, "digestChatIds")).isEmpty();
        verifyNoInteractions(metricsService);
    }

    // ============================================================
    // VALIDAÇÕES DE generateDigestCustom
    // ============================================================

    @Test
    @DisplayName("generateDigestCustom: from null lança IllegalArgumentException")
    void fromNull_lancaExcecao() {
        assertThatThrownBy(() -> service.generateDigestCustom(null, LocalDateTime.now(), CHAT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Período não pode ser nulo");
        verifyNoInteractions(metricsService);
    }

    @Test
    @DisplayName("generateDigestCustom: to null lança IllegalArgumentException")
    void toNull_lancaExcecao() {
        assertThatThrownBy(() -> service.generateDigestCustom(LocalDateTime.now(), null, CHAT_ID))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(metricsService);
    }

    @Test
    @DisplayName("generateDigestCustom: from > to lança IllegalArgumentException")
    void fromAposTo_lancaExcecao() {
        LocalDateTime from = LocalDateTime.now().plusDays(1);
        LocalDateTime to = LocalDateTime.now();
        assertThatThrownBy(() -> service.generateDigestCustom(from, to, CHAT_ID))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(metricsService);
    }

    // ============================================================
    // SEM MENSAGENS — métrica `digest_sem_mensagens`
    // ============================================================

    @Test
    @DisplayName("Sem mensagens: registra error('digest_sem_mensagens')")
    void semMensagens_registraMetrica() {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();

        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of())
                .thenReturn(List.of());

        service.generateDigestCustom(from, to, CHAT_ID);

        verify(metricsService).error("digest_sem_mensagens");
        verify(metricsService, never()).success(anyString());
        verifyNoInteractions(groqClient);
        verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
    }

    // ============================================================
    // SUCESSO — métrica `digest_gerado_sucesso`
    // ============================================================

    @Test
    @DisplayName("Sucesso: registra success('digest_gerado_sucesso')")
    void sucesso_registraMetrica() throws Exception {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();

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
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages)
                .thenReturn(List.of());
        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenReturn("Resumo do digest");

        service.generateDigestCustom(from, to, CHAT_ID);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue())
                .contains("PERÍODO PERSONALIZADO")
                .contains("Resumo do digest");

        verify(metricsService).success("digest_gerado_sucesso");
        verify(metricsService, never()).error(anyString());
    }

    @Test
    @DisplayName("Sucesso com múltiplos chats: registra 1 success (não N)")
    void sucessoMultiplosChats_umSuccess() {
        ReflectionTestUtils.setField(service, "digestChatIds", new HashSet<>());
        ReflectionTestUtils.setField(service, "digestChatIdsStr", "123,456,789");
        service.init();

        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();

        List<Map<String, Object>> messages =
                List.of(
                        Map.of(
                                "user_name",
                                "U",
                                "text",
                                "M",
                                "timestamp",
                                from.plusMinutes(1).toString()));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages)
                .thenReturn(List.of());
        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenReturn("Resumo");

        service.generateDigestCustom(from, to, null);

        verify(telegramFacade, times(3)).enviarMensagemHtml(anyLong(), anyString());
        // A métrica é registrada UMA vez por execução do generateDigest (não por chat)
        verify(metricsService, times(1)).success("digest_gerado_sucesso");
    }

    // ============================================================
    // GROQ VAZIO — métrica `digest_groq_vazio`
    // ============================================================

    @Test
    @DisplayName("Groq devolve null: registra error('digest_groq_vazio')")
    void groqNull_registraMetrica() {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();

        List<Map<String, Object>> messages =
                List.of(
                        Map.of(
                                "user_name",
                                "U",
                                "text",
                                "M",
                                "timestamp",
                                from.plusMinutes(1).toString()));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages)
                .thenReturn(List.of());
        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenReturn(null);

        service.generateDigestCustom(from, to, CHAT_ID);

        verify(metricsService).error("digest_groq_vazio");
        verify(metricsService, never()).success(anyString());
        verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
    }

    @Test
    @DisplayName("Groq devolve string em branco: registra error('digest_groq_vazio')")
    void groqEmBranco_registraMetrica() {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();

        List<Map<String, Object>> messages =
                List.of(
                        Map.of(
                                "user_name",
                                "U",
                                "text",
                                "M",
                                "timestamp",
                                from.plusMinutes(1).toString()));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages)
                .thenReturn(List.of());
        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenReturn("   ");

        service.generateDigestCustom(from, to, CHAT_ID);

        verify(metricsService).error("digest_groq_vazio");
    }

    // ============================================================
    // ERROS — DataAccessException
    // ============================================================

    @Test
    @DisplayName("DataAccessException: registra error('digest_db_error')")
    void dataAccessError_registraMetrica() {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();

        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenThrow(
                        new org.springframework.dao.DataAccessResourceFailureException("DB down"));

        service.generateDigestCustom(from, to, CHAT_ID);

        verify(metricsService).error("digest_db_error");
        verify(metricsService, never()).success(anyString());
        verifyNoInteractions(groqClient, telegramFacade);
    }

    // ============================================================
    // ERROS — GroqRateLimitException
    // ============================================================

    @Test
    @DisplayName("GroqRateLimitException: registra error('digest_groq_rate_limit')")
    void rateLimit_registraMetrica() {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();

        List<Map<String, Object>> messages =
                List.of(
                        Map.of(
                                "user_name",
                                "U",
                                "text",
                                "M",
                                "timestamp",
                                from.plusMinutes(1).toString()));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages)
                .thenReturn(List.of());

        // 👇 Use GroqRateLimitException DIRETAMENTE.
        // O GroqClient já encapsula o TooManyRequests → GroqRateLimitException.
        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenThrow(new GroqRateLimitException("Rate limit", null));

        service.generateDigestCustom(from, to, CHAT_ID);

        verify(metricsService).error("digest_groq_rate_limit");
        verify(metricsService, never()).success(anyString());
    }

    // ============================================================
    // ERROS — HttpClientErrorException
    // ============================================================

    @Test
    @DisplayName("HttpClientErrorException genérico: registra error('digest_groq_http_error')")
    void httpError_registraMetrica() {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();

        List<Map<String, Object>> messages =
                List.of(
                        Map.of(
                                "user_name",
                                "U",
                                "text",
                                "M",
                                "timestamp",
                                from.plusMinutes(1).toString()));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages)
                .thenReturn(List.of());

        HttpClientErrorException httpError =
                HttpClientErrorException.create(
                        HttpStatusCode.valueOf(401),
                        "Unauthorized",
                        null,
                        null,
                        StandardCharsets.UTF_8);
        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenThrow(httpError);

        service.generateDigestCustom(from, to, CHAT_ID);

        verify(metricsService).error("digest_groq_http_error");
        verify(metricsService, never()).success(anyString());
    }

    // ============================================================
    // ERROS — DigestGenerationException (ResourceAccessException)
    // ============================================================

    @Test
    @DisplayName(
            "ResourceAccessException: gera DigestGenerationException e registra"
                    + " 'digest_groq_indisponivel'")
    void resourceAccess_registraMetrica() {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();

        List<Map<String, Object>> messages =
                List.of(
                        Map.of(
                                "user_name",
                                "U",
                                "text",
                                "M",
                                "timestamp",
                                from.plusMinutes(1).toString()));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages)
                .thenReturn(List.of());
        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenThrow(new ResourceAccessException("Timeout"));

        service.generateDigestCustom(from, to, CHAT_ID);

        verify(metricsService).error("digest_groq_indisponivel");
        verify(metricsService, never()).success(anyString());
    }

    // ============================================================
    // ERROS — DigestSendException
    // ============================================================

    @Test
    @DisplayName("Falha no envio: registra error('digest_envio_erro')")
    void envioErro_registraMetrica() {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();

        List<Map<String, Object>> messages =
                List.of(
                        Map.of(
                                "user_name",
                                "U",
                                "text",
                                "M",
                                "timestamp",
                                from.plusMinutes(1).toString()));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages)
                .thenReturn(List.of());
        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenReturn("Resumo");

        // enviarMensagemHtml falha com Forbidden → sendChunk lança DigestSendException
        doThrow(
                        HttpClientErrorException.create(
                                HttpStatusCode.valueOf(403),
                                "Forbidden",
                                null,
                                null,
                                StandardCharsets.UTF_8))
                .when(telegramFacade)
                .enviarMensagemHtml(eq(CHAT_ID), anyString());

        service.generateDigestCustom(from, to, CHAT_ID);

        verify(metricsService).error("digest_envio_erro");
        verify(metricsService, never()).success("digest_gerado_sucesso");
    }

    // ============================================================
    // ERROS — RuntimeException genérica
    // ============================================================

    @Test
    @DisplayName("RuntimeException inesperada: registra error('digest_erro_inesperado') e relança")
    void runtimeError_registraMetrica() {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();

        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("Boom inesperado"));

        assertThatThrownBy(() -> service.generateDigestCustom(from, to, CHAT_ID))
                .isInstanceOf(DigestGenerationException.class)
                .hasCauseInstanceOf(RuntimeException.class);

        verify(metricsService).error("digest_erro_inesperado");
        verify(metricsService, never()).success(anyString());
    }

    // ============================================================
    // MÉTRICAS — contagem em múltiplas chamadas
    // ============================================================

    @Test
    @DisplayName("3 sucessos seguidos: 3 métricas de sucesso")
    void tresSucessos() {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();

        List<Map<String, Object>> messages =
                List.of(
                        Map.of(
                                "user_name",
                                "U",
                                "text",
                                "M",
                                "timestamp",
                                from.plusMinutes(1).toString()));

        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages)
                .thenReturn(List.of())
                .thenReturn(messages)
                .thenReturn(List.of())
                .thenReturn(messages)
                .thenReturn(List.of());
        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenReturn("Resumo");

        service.generateDigestCustom(from, to, CHAT_ID);
        service.generateDigestCustom(from, to, CHAT_ID);
        service.generateDigestCustom(from, to, CHAT_ID);

        verify(metricsService, times(3)).success("digest_gerado_sucesso");
        verify(metricsService, never()).error(anyString());
    }

    @Test
    @DisplayName("Sucesso seguido de vazio: 1 success e 1 'digest_sem_mensagens'")
    void sucessoDepoisVazio() {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();

        List<Map<String, Object>> messages =
                List.of(
                        Map.of(
                                "user_name",
                                "U",
                                "text",
                                "M",
                                "timestamp",
                                from.plusMinutes(1).toString()));

        // 1ª chamada: sucesso
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages)
                .thenReturn(List.of())
                // 2ª chamada: vazio
                .thenReturn(List.of())
                .thenReturn(List.of());
        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenReturn("Resumo");

        service.generateDigestCustom(from, to, CHAT_ID);
        service.generateDigestCustom(from, to, CHAT_ID);

        verify(metricsService, times(1)).success("digest_gerado_sucesso");
        verify(metricsService, times(1)).error("digest_sem_mensagens");
    }

    // ============================================================
    // MÉTODOS AGENDADOS
    // ============================================================

    @Test
    @DisplayName("generateMorningDigest: desabilitado não faz nada")
    void morningDigest_desabilitado() {
        ReflectionTestUtils.setField(service, "digestEnabled", false);
        service.generateMorningDigest();
        verifyNoInteractions(jdbcTemplate, groqClient, telegramFacade, metricsService);
    }

    @Test
    @DisplayName("generateMorningDigest: sem chats não faz nada")
    void morningDigest_semChats() {
        ReflectionTestUtils.setField(service, "digestChatIds", new HashSet<>());
        service.generateMorningDigest();
        verifyNoInteractions(jdbcTemplate, groqClient, telegramFacade, metricsService);
    }

    @Test
    @DisplayName("generateEveningDigest: desabilitado não faz nada")
    void eveningDigest_desabilitado() {
        ReflectionTestUtils.setField(service, "digestEnabled", false);
        service.generateEveningDigest();
        verifyNoInteractions(jdbcTemplate, groqClient, telegramFacade, metricsService);
    }

    @Test
    @DisplayName("generateEveningDigest: sem chats não faz nada")
    void eveningDigest_semChats() {
        ReflectionTestUtils.setField(service, "digestChatIds", new HashSet<>());
        service.generateEveningDigest();
        verifyNoInteractions(jdbcTemplate, groqClient, telegramFacade, metricsService);
    }

    // ============================================================
    // MÉTRICAS — helpers internos (não registram)
    // ============================================================

    @Test
    @DisplayName("parseTimestampSafely: não interage com MetricsService")
    void parseTimestampSafely_naoInterageComMetrics() {
        java.lang.reflect.Method m;
        try {
            m = DailyDigestService.class.getDeclaredMethod("parseTimestampSafely", String.class);
            m.setAccessible(true);
            m.invoke(service, "2026-07-24T10:30:00");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        verifyNoInteractions(metricsService);
    }

    @Test
    @DisplayName("sanitizeDigestText: não interage com MetricsService")
    void sanitizeDigestText_naoInterageComMetrics() {
        java.lang.reflect.Method m;
        try {
            m = DailyDigestService.class.getDeclaredMethod("sanitizeDigestText", String.class);
            m.setAccessible(true);
            m.invoke(service, "<b>texto</b>");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        verifyNoInteractions(metricsService);
    }

    @Test
    @DisplayName("buildHeader: não interage com MetricsService")
    void buildHeader_naoInterageComMetrics() {
        java.lang.reflect.Method m;
        try {
            m =
                    DailyDigestService.class.getDeclaredMethod(
                            "buildHeader", String.class, LocalDateTime.class, LocalDateTime.class);
            m.setAccessible(true);
            m.invoke(
                    service,
                    "RESUMO",
                    LocalDateTime.of(2026, Month.JULY, 24, 8, 30),
                    LocalDateTime.of(2026, Month.JULY, 24, 20, 30));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        verifyNoInteractions(metricsService);
    }

    // ============================================================
    // INTEGRAÇÃO — fluxo completo com sucesso e fallback de HTML
    // ============================================================

    @Test
    @DisplayName("Fallback HTML → texto puro: registra success, não error")
    void fallbackHtmlParaTexto_registraSuccess() {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();

        List<Map<String, Object>> messages =
                List.of(
                        Map.of(
                                "user_name",
                                "U",
                                "text",
                                "M",
                                "timestamp",
                                from.plusMinutes(1).toString()));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages)
                .thenReturn(List.of());
        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenReturn("<b>Resumo</b> com tag inválida");

        // Primeira chamada (HTML) falha com BadRequest contendo "can't parse entities"
        HttpClientErrorException.BadRequest badRequest =
                mock(HttpClientErrorException.BadRequest.class);
        when(badRequest.getMessage()).thenReturn("can't parse entities");
        doThrow(badRequest).when(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), anyString());
        // Fallback (texto puro) NÃO lança → sucesso
        doNothing().when(telegramFacade).enviarMensagem(eq(CHAT_ID), anyString());

        service.generateDigestCustom(from, to, CHAT_ID);

        verify(telegramFacade).enviarMensagem(eq(CHAT_ID), anyString());
        verify(metricsService).success("digest_gerado_sucesso");
        verify(metricsService, never()).error(anyString());
    }

    @Test
    @DisplayName("Fallback HTML → texto puro: se fallback falhar, registra envio_erro")
    void fallbackHtmlFalha_registraEnvioErro() {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();

        List<Map<String, Object>> messages =
                List.of(
                        Map.of(
                                "user_name",
                                "U",
                                "text",
                                "M",
                                "timestamp",
                                from.plusMinutes(1).toString()));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages)
                .thenReturn(List.of());
        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenReturn("Resumo com <b>tag");

        HttpClientErrorException.BadRequest badRequest =
                mock(HttpClientErrorException.BadRequest.class);
        when(badRequest.getMessage()).thenReturn("can't parse entities");
        doThrow(badRequest).when(telegramFacade).enviarMensagemHtml(eq(CHAT_ID), anyString());
        doThrow(new RuntimeException("Fallback também falhou"))
                .when(telegramFacade)
                .enviarMensagem(eq(CHAT_ID), anyString());

        service.generateDigestCustom(from, to, CHAT_ID);

        verify(metricsService).error("digest_envio_erro");
        verify(metricsService, never()).success("digest_gerado_sucesso");
    }

    // ============================================================
    // TRUNCAMENTO — verifica que mensagens longas não quebram
    // ============================================================

    @Test
    @DisplayName("Prompt muito longo: trunca e ainda assim registra success")
    void promptLongo_truncaRegistraSuccess() {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();

        String textoLongo = "a".repeat(40000);
        List<Map<String, Object>> messages =
                List.of(
                        Map.of(
                                "user_name",
                                "U",
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
        assertThat(captor.getValue().length()).isLessThan(40000);

        verify(metricsService).success("digest_gerado_sucesso");
    }

    // ============================================================
    // CUSTOM DIGEST COM CHAT ESPECÍFICO
    // ============================================================

    @Test
    @DisplayName("Chat específico: só envia para ele e registra success")
    void chatEspecifico() {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();

        List<Map<String, Object>> messages =
                List.of(
                        Map.of(
                                "user_name",
                                "U",
                                "text",
                                "M",
                                "timestamp",
                                from.plusMinutes(1).toString()));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(messages)
                .thenReturn(List.of());
        when(groqClient.gerarResumoDigest(anyString(), any(DigestPersona.class), anyString()))
                .thenReturn("Resumo");

        service.generateDigestCustom(from, to, 999L);

        verify(telegramFacade).enviarMensagemHtml(eq(999L), anyString());
        verify(metricsService).success("digest_gerado_sucesso");
    }
}
