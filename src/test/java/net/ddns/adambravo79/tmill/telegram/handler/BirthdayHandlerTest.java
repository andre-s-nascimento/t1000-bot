/* (c) 2026 | 04/11/2026 */
package net.ddns.adambravo79.tmill.telegram.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;

import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.service.BirthdayService;
import net.ddns.adambravo79.tmill.telegram.core.GroupAuthorizationService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;
import net.ddns.adambravo79.tmill.telegram.util.TelegramUtils;

/**
 * Testes do {@link BirthdayHandler}.
 *
 * <p>Cobre:
 *
 * <ul>
 *   <li>Reconhecimento das 6 variantes de prefixo (registrar/anotar, com/sem acento)
 *   <li>Extração da data via regex (com/sem espaços, com data colada)
 *   <li>Delegação ao {@link BirthdayService#registrar}
 *   <li>Envio da resposta do service ao usuário
 *   <li>Tratamento de exceção do service (não relança, envia erro genérico)
 *   <li>Métricas: success em fluxo normal, error em exceção
 *   <li>Autorização
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BirthdayHandlerTest {

    @Mock private TelegramFacade telegramFacade;
    @Mock private GroupAuthorizationService authService;
    @Mock private TelegramUtils utils;
    @Mock private BirthdayService birthdayService;
    @Mock private MetricsService metricsService;

    private BirthdayHandler handler;

    private static final long CHAT_ID = 12345L;
    private static final long USER_ID = 999L;
    private static final String USER_NAME = "Testador Silva";
    private static final String RESPOSTA_REGISTRADO = "✅ Aniversário registrado para 05/10";

    @BeforeEach
    void setUp() {
        handler =
                new BirthdayHandler(
                        telegramFacade, authService, utils, birthdayService, metricsService);

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
    @DisplayName("getPrefixo retorna 't1000 registrar aniversario'")
    void getPrefixo() {
        assertThat(handler.getPrefixo()).isEqualTo("t1000 registrar aniversario");
    }

    // =========================================================================
    // VARIANTES DE PREFIXO
    // =========================================================================

    @Nested
    @DisplayName("Variantes de prefixo")
    class VariantesPrefixo {

        @ParameterizedTest(name = "[{index}] ''{0}'' → data ''{1}''")
        @DisplayName("Todas as variantes de prefixo extraem a data corretamente")
        @CsvSource({
            // Registrar com acento
            "'t1000 registrar aniversário 05/10', '05/10'",
            // Registrar sem acento
            "'t1000 registrar aniversario 05/10', '05/10'",
            // Anotar com acento
            "'t1000 anotar aniversário 05/10', '05/10'",
            // Anotar sem acento
            "'t1000 anotar aniversario 05/10', '05/10'",
            // Direto (sem verbo)
            "'t1000 aniversário 05/10', '05/10'",
            "'t1000 aniversario 05/10', '05/10'",
            // Case-insensitive
            "'T1000 REGISTRAR ANIVERSÁRIO 05/10', '05/10'",
            // Com data sem zero à esquerda
            "'t1000 registrar aniversário 5/10', '5/10'",
            // Com hífen
            "'t1000 registrar aniversário 05-10', '05-10'",
            // Com ano explícito
            "'t1000 registrar aniversário 05/10/1990', '05/10/1990'",
            // Com espaços extras
            "'t1000   registrar   aniversário   05/10', '05/10'"
        })
        void variantesDePrefixo(String input, String dataEsperada) {
            Update update = mockUpdate(input);

            handler.processarComando(update);

            // O service recebe a data extraída
            verify(birthdayService).registrar(USER_ID, USER_NAME, dataEsperada);
        }
    }

    // =========================================================================
    // FLUXO FELIZ
    // =========================================================================

    @Nested
    @DisplayName("Fluxo feliz")
    class FluxoFeliz {

        @Test
        @DisplayName("service retorna sucesso: envia resposta, registra success")
        void sucesso() {
            when(birthdayService.registrar(USER_ID, USER_NAME, "05/10"))
                    .thenReturn(RESPOSTA_REGISTRADO);
            Update update = mockUpdate("t1000 registrar aniversário 05/10");

            handler.processarComando(update);

            verify(telegramFacade).enviarMensagemHtml(CHAT_ID, RESPOSTA_REGISTRADO);
            verify(metricsService).success("comando_aniversario");
            verify(metricsService, never()).error(anyString());
        }

        @Test
        @DisplayName("service retorna erro de validação: envia ao usuário, mas registra success")
        void serviceRetornaErroDeValidacao() {
            when(birthdayService.registrar(USER_ID, USER_NAME, "31/02"))
                    .thenReturn(BotMessages.ANIVERSARIO_DATA_INVALIDA);
            Update update = mockUpdate("t1000 registrar aniversário 31/02");

            handler.processarComando(update);

            verify(telegramFacade)
                    .enviarMensagemHtml(CHAT_ID, BotMessages.ANIVERSARIO_DATA_INVALIDA);
            // success porque o comando foi processado (o service retornou erro de validação)
            verify(metricsService).success("comando_aniversario");
        }

        @Test
        @DisplayName("3 aniversários: 3 successes")
        void tresAniversarios() {
            when(birthdayService.registrar(anyLong(), anyString(), anyString()))
                    .thenReturn(RESPOSTA_REGISTRADO);

            handler.processarComando(mockUpdate("t1000 registrar aniversário 01/01"));
            handler.processarComando(mockUpdate("t1000 registrar aniversário 02/02"));
            handler.processarComando(mockUpdate("t1000 registrar aniversário 03/03"));

            verify(birthdayService, times(3)).registrar(anyLong(), anyString(), anyString());
            verify(metricsService, times(3)).success("comando_aniversario");
        }
    }

    // =========================================================================
    // SEM DATA
    // =========================================================================

    @Nested
    @DisplayName("Comando sem data")
    class SemData {

        @ParameterizedTest(name = "[{index}] ''{0}''")
        @ValueSource(
                strings = {
                    "t1000 registrar aniversário",
                    "t1000 anotar aniversário",
                    "t1000 aniversário",
                    "t1000 registrar aniversário   ",
                    "t1000 registrar aniversário "
                })
        @DisplayName("Comando sem data: pede ao usuário para informar")
        void semData(String input) {
            Update update = mockUpdate(input);

            handler.processarComando(update);

            verify(telegramFacade).enviarMensagemHtml(CHAT_ID, BotMessages.ANIVERSARIO_PEDE_DATA);
            verify(birthdayService, never()).registrar(anyLong(), anyString(), anyString());
            verify(metricsService).success("comando_aniversario");
        }
    }

    // =========================================================================
    // EXCEÇÃO NO SERVICE
    // =========================================================================

    @Nested
    @DisplayName("Exceção no service")
    class ExcecaoNoService {

        @Test
        @DisplayName("service lança exceção: envia erro genérico, registra error")
        void serviceLancaExcecao() {
            when(birthdayService.registrar(anyLong(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("DB down"));
            Update update = mockUpdate("t1000 registrar aniversário 05/10");

            assertThatCode(() -> handler.processarComando(update)).doesNotThrowAnyException();

            verify(telegramFacade).enviarMensagem(CHAT_ID, BotMessages.ERRO_GENERICO);
            verify(metricsService).error("comando_aniversario");
            verify(metricsService, never()).success("comando_aniversario");
        }

        @Test
        @DisplayName("service lança exceção: NÃO chama executarComProtecao (não duplica erro)")
        void naoDuplicaMensagemDeErro() {
            when(birthdayService.registrar(anyLong(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("DB down"));
            Update update = mockUpdate("t1000 registrar aniversário 05/10");

            handler.processarComando(update);

            // Apenas uma mensagem de erro é enviada (a do próprio handler)
            verify(telegramFacade, times(1)).enviarMensagem(CHAT_ID, BotMessages.ERRO_GENERICO);
        }

        @Test
        @DisplayName("exceção no service não propaga")
        void naoPropaga() {
            when(birthdayService.registrar(anyLong(), anyString(), anyString()))
                    .thenThrow(new IllegalStateException("boom"));
            Update update = mockUpdate("t1000 registrar aniversário 05/10");

            assertThatCode(() -> handler.processarComando(update)).doesNotThrowAnyException();
        }
    }

    // =========================================================================
    // MÉTRICAS
    // =========================================================================

    @Nested
    @DisplayName("Métricas")
    class Metricas {

        @Test
        @DisplayName("sucesso: success('comando_aniversario')")
        void sucessoRegistraSuccess() {
            when(birthdayService.registrar(anyLong(), anyString(), anyString()))
                    .thenReturn(RESPOSTA_REGISTRADO);
            Update update = mockUpdate("t1000 registrar aniversário 05/10");

            handler.processarComando(update);

            verify(metricsService).success("comando_aniversario");
            verify(metricsService, never()).error(anyString());
        }

        @Test
        @DisplayName("exceção: error('comando_aniversario')")
        void excecaoRegistraError() {
            when(birthdayService.registrar(anyLong(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("boom"));
            Update update = mockUpdate("t1000 registrar aniversário 05/10");

            handler.processarComando(update);

            verify(metricsService).error("comando_aniversario");
            verify(metricsService, never()).success("comando_aniversario");
        }

        @Test
        @DisplayName("sem data: success (registra tentativa)")
        void semDataRegistraSuccess() {
            Update update = mockUpdate("t1000 aniversário");

            handler.processarComando(update);

            verify(metricsService).success("comando_aniversario");
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
            Update update = mockUpdate("t1000 registrar aniversário 05/10");

            handler.processarComando(update);

            verifyNoInteractions(birthdayService);
            verify(telegramFacade, never()).enviarMensagemHtml(anyLong(), anyString());
            verify(telegramFacade, never()).enviarMensagem(anyLong(), anyString());
        }
    }
}
