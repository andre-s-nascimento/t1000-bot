/* (c) 2026 | 04/11/2026 */
package net.ddns.adambravo79.tmill.telegram.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;

import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.service.WorldCupSchedulerService;
import net.ddns.adambravo79.tmill.telegram.core.GroupAuthorizationService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;
import net.ddns.adambravo79.tmill.telegram.util.TelegramUtils;

/**
 * Testes do {@link WorldCupHandler}.
 *
 * <p>Cobre:
 *
 * <ul>
 *   <li>{@code t1000 jogos [data]} e {@code t1000 copa [data]}
 *   <li>{@code t1000 resultados [data]}
 *   <li>Feature flag {@code worldcup.enabled} (on/off)
 *   <li>Parse de datas (hoje, ontem, dd/MM, ISO, com ruído)
 *   <li>Métricas: {@code comando_copa}, {@code comando_resultados}
 *   <li>Autorização
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorldCupHandlerTest {

    @Mock private TelegramFacade telegramFacade;
    @Mock private GroupAuthorizationService authService;
    @Mock private TelegramUtils utils;
    @Mock private WorldCupSchedulerService worldCupSchedulerService;
    @Mock private MetricsService metricsService;

    private WorldCupHandler handler;

    private static final long CHAT_ID = 12345L;
    private static final long USER_ID = 999L;
    private static final String USER_NAME = "Testador Silva";
    private static final ZoneId BRAZIL_ZONE = ZoneId.of(BotMessages.BRAZIL_ZONE);

    @BeforeEach
    void setUp() {
        handler =
                new WorldCupHandler(
                        telegramFacade,
                        authService,
                        utils,
                        worldCupSchedulerService,
                        metricsService);

        // Default: flag habilitada
        ReflectionTestUtils.setField(handler, "worldcupEnabled", true);

        lenient().when(authService.isAuthorized(any(Update.class))).thenReturn(true);
        lenient().when(utils.buildFullName(any(User.class))).thenReturn(USER_NAME);
    }

    // =========================================================================
    // FIXTURES
    // =========================================================================

    private Update mockUpdate(String texto) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.text()).thenReturn(texto);
        when(message.chat()).thenReturn(chat);
        when(message.from()).thenReturn(user);
        when(chat.id()).thenReturn(CHAT_ID);
        when(user.id()).thenReturn(USER_ID);
        when(user.firstName()).thenReturn("Testador");
        when(user.lastName()).thenReturn("Silva");

        return update;
    }

    // =========================================================================
    // getPrefixo
    // =========================================================================

    @Test
    @DisplayName("getPrefixo retorna 't1000 ' (prefixo raiz)")
    void getPrefixo() {
        assertThat(handler.getPrefixo()).isEqualTo("t1000 ");
    }

    // =========================================================================
    // t1000 jogos / copa
    // =========================================================================

    @Nested
    @DisplayName("t1000 jogos / copa")
    class Jogos {

        @ParameterizedTest(name = "[{index}] ''{0}''")
        @ValueSource(
                strings = {
                    "t1000 jogos",
                    "t1000 jogos de hoje",
                    "t1000 copa",
                    "t1000 copa hoje",
                    "T1000 JOGOS",
                    "t1000  jogos  de  hoje"
                })
        @DisplayName("Variações de prefixo chamam sendMatchesToChat com a data correta")
        void variacoesDePrefixo(String input) {
            Update update = mockUpdate(input);

            handler.processarComando(update);

            verify(worldCupSchedulerService).sendMatchesToChat(eq(CHAT_ID), any(LocalDate.class));
            verify(metricsService).success("comando_copa");
        }

        @Test
        @DisplayName("Sem data: usa hoje")
        void semDataUsaHoje() {
            Update update = mockUpdate("t1000 jogos");

            handler.processarComando(update);

            ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);
            verify(worldCupSchedulerService).sendMatchesToChat(eq(CHAT_ID), captor.capture());

            assertThat(captor.getValue()).isEqualTo(LocalDate.now(BRAZIL_ZONE));
        }

        @Test
        @DisplayName("Com 'hoje': usa hoje")
        void comHoje() {
            Update update = mockUpdate("t1000 jogos hoje");

            handler.processarComando(update);

            ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);
            verify(worldCupSchedulerService).sendMatchesToChat(eq(CHAT_ID), captor.capture());

            assertThat(captor.getValue()).isEqualTo(LocalDate.now(BRAZIL_ZONE));
        }

        @Test
        @DisplayName("Com 'ontem': usa ontem")
        void comOntem() {
            Update update = mockUpdate("t1000 jogos ontem");

            handler.processarComando(update);

            ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);
            verify(worldCupSchedulerService).sendMatchesToChat(eq(CHAT_ID), captor.capture());

            assertThat(captor.getValue()).isEqualTo(LocalDate.now(BRAZIL_ZONE).minusDays(1));
        }

        @ParameterizedTest(name = "[{index}] ''{0}'' → {1}")
        @CsvSource({
            "'t1000 jogos 20/06', 2026-06-20",
            "'t1000 jogos 20-06', 2026-06-20",
            "'t1000 jogos 2026-06-20', 2026-06-20",
            "'t1000 copa 25/12/2025', 2025-12-25"
        })
        @DisplayName("Com data numérica: parseia corretamente")
        void comDataNumerica(String input, LocalDate esperado) {
            Update update = mockUpdate(input);

            handler.processarComando(update);

            verify(worldCupSchedulerService).sendMatchesToChat(CHAT_ID, esperado);
            verify(metricsService).success("comando_copa");
        }

        @Test
        @DisplayName("Data inválida em jogos: envia DATA_INVALIDA, não chama o scheduler")
        void dataInvalidaEmJogos() {
            Update update = mockUpdate("t1000 jogos data-muito-invalida-xyz");

            handler.processarComando(update);

            verify(telegramFacade).enviarMensagem(CHAT_ID, BotMessages.DATA_INVALIDA);
            verifyNoInteractions(worldCupSchedulerService);
            verify(metricsService).success("comando_copa");
        }

        @Test
        @DisplayName("Flag worldcup desabilitada: envia WORLD_CUP_FINISHED")
        void flagDesabilitada() {
            ReflectionTestUtils.setField(handler, "worldcupEnabled", false);
            Update update = mockUpdate("t1000 jogos");

            handler.processarComando(update);

            verify(telegramFacade).enviarMensagem(CHAT_ID, BotMessages.WORLD_CUP_FINISHED);
            verifyNoInteractions(worldCupSchedulerService);
            verify(metricsService).success("comando_copa");
        }
    }

    // =========================================================================
    // t1000 resultados
    // =========================================================================

    @Nested
    @DisplayName("t1000 resultados")
    class Resultados {

        @ParameterizedTest(name = "[{index}] ''{0}''")
        @ValueSource(
                strings = {
                    "t1000 resultados",
                    "t1000 resultados hoje",
                    "T1000 RESULTADOS",
                    "t1000  resultados  de  hoje"
                })
        @DisplayName("Variações de prefixo chamam sendResultsToChat")
        void variacoesDePrefixo(String input) {
            Update update = mockUpdate(input);

            handler.processarComando(update);

            verify(worldCupSchedulerService).sendResultsToChat(eq(CHAT_ID), any(LocalDate.class));
            verify(metricsService).success("comando_resultados");
        }

        @Test
        @DisplayName("Sem data: usa hoje")
        void semDataUsaHoje() {
            Update update = mockUpdate("t1000 resultados");

            handler.processarComando(update);

            ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);
            verify(worldCupSchedulerService).sendResultsToChat(eq(CHAT_ID), captor.capture());

            assertThat(captor.getValue()).isEqualTo(LocalDate.now(BRAZIL_ZONE));
        }

        @Test
        @DisplayName("Com 'ontem': usa ontem")
        void comOntem() {
            Update update = mockUpdate("t1000 resultados ontem");

            handler.processarComando(update);

            ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);
            verify(worldCupSchedulerService).sendResultsToChat(eq(CHAT_ID), captor.capture());

            assertThat(captor.getValue()).isEqualTo(LocalDate.now(BRAZIL_ZONE).minusDays(1));
        }

        @ParameterizedTest(name = "[{index}] ''{0}'' → {1}")
        @CsvSource({
            "'t1000 resultados 20/06', 2026-06-20",
            "'t1000 resultados 2026-06-20', 2026-06-20",
            "'t1000 resultados 25/12/2025', 2025-12-25"
        })
        @DisplayName("Com data numérica: parseia corretamente")
        void comDataNumerica(String input, LocalDate esperado) {
            Update update = mockUpdate(input);

            handler.processarComando(update);

            verify(worldCupSchedulerService).sendResultsToChat(CHAT_ID, esperado);
            verify(metricsService).success("comando_resultados");
        }

        @Test
        @DisplayName("Data inválida (texto puro): envia DATA_INVALIDA")
        void dataInvalida() {
            Update update = mockUpdate("t1000 resultados data-muito-invalida-xyz");

            handler.processarComando(update);

            verify(telegramFacade).enviarMensagem(CHAT_ID, BotMessages.DATA_INVALIDA);
            verifyNoInteractions(worldCupSchedulerService);
            verify(metricsService).success("comando_resultados");
        }

        @Test
        @DisplayName("Flag worldcup desabilitada: envia WORLD_CUP_FINISHED")
        void flagDesabilitada() {
            ReflectionTestUtils.setField(handler, "worldcupEnabled", false);
            Update update = mockUpdate("t1000 resultados");

            handler.processarComando(update);

            verify(telegramFacade).enviarMensagem(CHAT_ID, BotMessages.WORLD_CUP_FINISHED);
            verifyNoInteractions(worldCupSchedulerService);
            verify(metricsService).success("comando_resultados");
        }
    }

    // =========================================================================
    // COMANDO DESCONHECIDO
    // =========================================================================

    @Nested
    @DisplayName("Comando desconhecido")
    class ComandoDesconhecido {

        @Test
        @DisplayName(
                "texto com 't1000 ' mas sem 'jogos'/'copa'/'resultados': envia"
                        + " COMANDO_NAO_RECONHECIDO")
        void comandoDesconhecido() {
            Update update = mockUpdate("t1000 banana");

            handler.processarComando(update);

            verify(telegramFacade).enviarMensagem(CHAT_ID, BotMessages.COMANDO_NAO_RECONHECIDO);
            verifyNoInteractions(worldCupSchedulerService);
        }
    }

    // =========================================================================
    // MÉTRICAS
    // =========================================================================

    @Nested
    @DisplayName("Métricas")
    class Metricas {

        @Test
        @DisplayName("3 jogos: 3 successes ('comando_copa')")
        void tresJogos() {
            handler.processarComando(mockUpdate("t1000 jogos"));
            handler.processarComando(mockUpdate("t1000 jogos"));
            handler.processarComando(mockUpdate("t1000 jogos"));

            verify(metricsService, times(3)).success("comando_copa");
        }

        @Test
        @DisplayName("3 resultados: 3 successes ('comando_resultados')")
        void tresResultados() {
            handler.processarComando(mockUpdate("t1000 resultados"));
            handler.processarComando(mockUpdate("t1000 resultados"));
            handler.processarComando(mockUpdate("t1000 resultados"));

            verify(metricsService, times(3)).success("comando_resultados");
        }

        @Test
        @DisplayName("jogos + resultados: cada um na sua métrica")
        void jogosEResultadosSeparados() {
            handler.processarComando(mockUpdate("t1000 jogos"));
            handler.processarComando(mockUpdate("t1000 resultados"));

            verify(metricsService).success("comando_copa");
            verify(metricsService).success("comando_resultados");
            verify(metricsService, never()).error(anyString());
        }
    }

    // =========================================================================
    // AUTORIZAÇÃO
    // =========================================================================

    @Nested
    @DisplayName("Autorização")
    class Autorizacao {

        @Test
        @DisplayName("não autorizado: não processa nada")
        void naoAutorizado() {
            when(authService.isAuthorized(any(Update.class))).thenReturn(false);
            Update update = mockUpdate("t1000 jogos");

            handler.processarComando(update);

            verifyNoInteractions(worldCupSchedulerService);
            verify(telegramFacade, never()).enviarMensagem(anyLong(), anyString());
            verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
        }
    }
}
