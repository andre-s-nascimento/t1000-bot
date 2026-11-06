/* (c) 2026 | 06/11/2026 */
package net.ddns.adambravo79.tmill.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import net.ddns.adambravo79.tmill.model.Reminder;
import net.ddns.adambravo79.tmill.service.ReminderDispatcher;
import net.ddns.adambravo79.tmill.service.ReminderScheduler;
import net.ddns.adambravo79.tmill.service.ReminderService;
import tools.jackson.databind.ObjectMapper;

/**
 * Testes do {@link ReminderController} via MockMvc standalone.
 *
 * <p><b>Regra de {@code eq()}:</b> só é usado quando <b>algum</b> argumento do {@code verify} é
 * matcher ({@code any()}, {@code anyLong()}, {@code contains()} etc.). Quando todos os argumentos
 * são literais, passa-se direto (Sonar {@code java:S6068}).
 *
 * <p><b>Métodos {@code void}:</b> {@code reminderService.reload()} é {@code void} — o mock já é
 * no-op por padrão, então <b>não</b> se usa {@code doNothing().when(...)}.
 *
 * <p><b>Colisão de nome:</b> o método de teste do nested {@link Diagnostico} <b>não</b> pode se
 * chamar {@code status()}, porque {@code MockMvcResultMatchers.status()} é um import estático e
 * o Java dá precedência a métodos locais. Por isso o nome é {@code diagnostico()}.
 *
 * <p>Cobre os 9 endpoints do controller:
 *
 * <ul>
 *   <li>{@code GET    /admin/reminders}              — lista
 *   <li>{@code GET    /admin/reminders/{id}}         — busca
 *   <li>{@code POST   /admin/reminders}              — cria
 *   <li>{@code PUT    /admin/reminders/{id}}         — atualiza
 *   <li>{@code POST   /admin/reminders/{id}/toggle}  — alterna enabled
 *   <li>{@code DELETE /admin/reminders/{id}}         — remove
 *   <li>{@code POST   /admin/reminders/{id}/test}    — teste manual
 *   <li>{@code POST   /admin/reminders/reload}       — recarrega do disco
 *   <li>{@code GET    /admin/reminders/status}       — diagnóstico
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ReminderControllerTest {

    private static final String BASE_URL = "/admin/reminders";
    private static final String REMINDER_ID = "quatro-horas-quarta";
    private static final String CRON_VALIDO = "0 0 16 * * WED";
    private static final String TZ = "America/Sao_Paulo";
    private static final String MESSAGE = "<b>Lembrete!</b>";
    private static final long CHAT_ID = -100123L;
    private static final String NAO_ENCONTRADO = "não encontrado";

    @Mock private ReminderService reminderService;
    @Mock private ReminderScheduler reminderScheduler;
    @Mock private ReminderDispatcher reminderDispatcher;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        ReminderController controller =
                new ReminderController(reminderService, reminderScheduler, reminderDispatcher);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        lenient().when(reminderService.getAll()).thenReturn(List.of());
        lenient().when(reminderService.getEnabled()).thenReturn(List.of());
        lenient().when(reminderService.count()).thenReturn(0);
        lenient().when(reminderScheduler.scheduledCount()).thenReturn(0);
        lenient().when(reminderScheduler.scheduledIds()).thenReturn(Set.of());
    }

    // =========================================================================
    // FIXTURES
    // =========================================================================

    private Reminder reminder() {
        return new Reminder(
                REMINDER_ID,
                "Quatro Horas de Quarta",
                true,
                CRON_VALIDO,
                TZ,
                MESSAGE,
                null,
                List.of(String.valueOf(CHAT_ID)),
                List.of("cinema"));
    }

    private Reminder reminder(String id, boolean enabled, String cron) {
        return new Reminder(
                id,
                id,
                enabled,
                cron,
                TZ,
                "<b>Msg " + id + "</b>",
                null,
                List.of(String.valueOf(CHAT_ID)),
                List.of());
    }

    private String json(Reminder r) {
        return objectMapper.writeValueAsString(r);
    }

    // =========================================================================
    // GET /admin/reminders
    // =========================================================================

    @Nested
    @DisplayName("GET /admin/reminders")
    class Listar {

        @Test
        @DisplayName("lista vazia: 200 + array vazio")
        void listaVazia() throws Exception {
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("2 reminders: 200 + array com 2")
        void doisReminders() throws Exception {
            when(reminderService.getAll())
                    .thenReturn(
                            List.of(
                                    reminder("a", true, CRON_VALIDO),
                                    reminder("b", false, CRON_VALIDO)));

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value("a"))
                    .andExpect(jsonPath("$[1].id").value("b"));
        }
    }

    @Nested
    @DisplayName("GET /admin/reminders/{id}")
    class BuscarPorId {

        @Test
        @DisplayName("existente: 200 + JSON do reminder")
        void existente() throws Exception {
            when(reminderService.getById(REMINDER_ID)).thenReturn(Optional.of(reminder()));

            mockMvc.perform(get(BASE_URL + "/" + REMINDER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(REMINDER_ID))
                    .andExpect(jsonPath("$.cron").value(CRON_VALIDO));
        }

        @Test
        @DisplayName("inexistente: 404 + erro")
        void inexistente() throws Exception {
            when(reminderService.getById(REMINDER_ID)).thenReturn(Optional.empty());

            mockMvc.perform(get(BASE_URL + "/" + REMINDER_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.erro").value(containsString(NAO_ENCONTRADO)));
        }
    }

    // =========================================================================
    // POST /admin/reminders
    // =========================================================================

    @Nested
    @DisplayName("POST /admin/reminders")
    class Criar {

        @Test
        @DisplayName("body válido: 201 + JSON criado + chama service")
        void valido() throws Exception {
            Reminder novo = reminder("novo", true, CRON_VALIDO);
            when(reminderService.create(any(Reminder.class))).thenReturn(novo);

            mockMvc.perform(
                            post(BASE_URL)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json(novo)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value("novo"));

            verify(reminderService).create(any(Reminder.class));
        }

        @Test
        @DisplayName("id duplicado: 409 + erro")
        void idDuplicado() throws Exception {
            Reminder novo = reminder("novo", true, CRON_VALIDO);
            when(reminderService.create(any(Reminder.class)))
                    .thenThrow(new IllegalArgumentException("Reminder com id 'novo' já existe"));

            mockMvc.perform(
                            post(BASE_URL)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json(novo)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.erro").value(containsString("já existe")));
        }

        @Test
        @DisplayName("cron inválido: 400 + erro, NÃO chama service")
        void cronInvalido() throws Exception {
            Reminder invalido = reminder("x", true, "cron-invalido");

            mockMvc.perform(
                            post(BASE_URL)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json(invalido)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erro").value(containsString("cron inválida")));

            verifyNoInteractions(reminderService);
        }

        @Test
        @DisplayName("erro inesperado do service: 500")
        void erroInesperado() throws Exception {
            Reminder novo = reminder("novo", true, CRON_VALIDO);
            when(reminderService.create(any(Reminder.class)))
                    .thenThrow(new RuntimeException("boom"));

            mockMvc.perform(
                            post(BASE_URL)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json(novo)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(
                            jsonPath("$.erro")
                                    .value("Erro interno. Verifique os logs do servidor."));
        }
    }

    @Nested
    @DisplayName("PUT /admin/reminders/{id}")
    class Atualizar {

        @Test
        @DisplayName("body válido + id bate: 200 + JSON atualizado")
        void valido() throws Exception {
            Reminder atualizado = reminder(REMINDER_ID, false, CRON_VALIDO);
            when(reminderService.update(eq(REMINDER_ID), any(Reminder.class)))
                    .thenReturn(atualizado);

            mockMvc.perform(
                            put(BASE_URL + "/" + REMINDER_ID)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json(atualizado)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(REMINDER_ID))
                    .andExpect(jsonPath("$.enabled").value(false));

            verify(reminderService).update(eq(REMINDER_ID), any(Reminder.class));
        }

        @Test
        @DisplayName("id do body diverge do id do path: 400")
        void idDivergente() throws Exception {
            Reminder diferente = reminder("outro-id", true, CRON_VALIDO);

            mockMvc.perform(
                            put(BASE_URL + "/" + REMINDER_ID)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json(diferente)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erro").value(containsString("diverge")));

            verifyNoInteractions(reminderService);
        }

        @Test
        @DisplayName("id não existe: 404")
        void naoExiste() throws Exception {
            Reminder atualizado = reminder(REMINDER_ID, true, CRON_VALIDO);
            when(reminderService.update(eq(REMINDER_ID), any(Reminder.class)))
                    .thenThrow(
                            new IllegalArgumentException(
                                    "Reminder '" + REMINDER_ID + "' não encontrado"));

            mockMvc.perform(
                            put(BASE_URL + "/" + REMINDER_ID)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json(atualizado)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.erro").value(containsString(NAO_ENCONTRADO)));
        }

        @Test
        @DisplayName("cron inválido: 400, NÃO chama service")
        void cronInvalido() throws Exception {
            Reminder invalido = reminder(REMINDER_ID, true, "nao-e-cron");

            mockMvc.perform(
                            put(BASE_URL + "/" + REMINDER_ID)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json(invalido)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erro").value(containsString("cron inválida")));

            verifyNoInteractions(reminderService);
        }
    }

    // =========================================================================
    // POST /admin/reminders/{id}/toggle
    // =========================================================================

    @Nested
    @DisplayName("POST /admin/reminders/{id}/toggle")
    class Toggle {

        @Test
        @DisplayName("com 'enabled' explícito: passa o valor ao service")
        void enabledExplicito() throws Exception {
            Reminder atualizado = reminder(REMINDER_ID, false, CRON_VALIDO);
            when(reminderService.getById(REMINDER_ID)).thenReturn(Optional.of(reminder()));
            when(reminderService.toggle(REMINDER_ID, false)).thenReturn(atualizado);

            mockMvc.perform(
                            post(BASE_URL + "/" + REMINDER_ID + "/toggle")
                                    .param("enabled", "false"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(false));

            verify(reminderService).toggle(REMINDER_ID, false);
        }

        @Test
        @DisplayName("sem 'enabled': inverte o valor atual")
        void semEnabledInverte() throws Exception {
            Reminder original = reminder(REMINDER_ID, true, CRON_VALIDO);
            Reminder atualizado = reminder(REMINDER_ID, false, CRON_VALIDO);
            when(reminderService.getById(REMINDER_ID)).thenReturn(Optional.of(original));
            when(reminderService.toggle(REMINDER_ID, false)).thenReturn(atualizado);

            mockMvc.perform(post(BASE_URL + "/" + REMINDER_ID + "/toggle"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(false));

            verify(reminderService).toggle(REMINDER_ID, false);
        }

        @Test
        @DisplayName("id inexistente: 404")
        void idInexistente() throws Exception {
            when(reminderService.getById(REMINDER_ID)).thenReturn(Optional.empty());

            mockMvc.perform(post(BASE_URL + "/" + REMINDER_ID + "/toggle"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.erro").value(containsString(NAO_ENCONTRADO)));
        }
    }

    @Nested
    @DisplayName("DELETE /admin/reminders/{id}")
    class Deletar {

        @Test
        @DisplayName("existente: 204 No Content")
        void existente() throws Exception {
            when(reminderService.delete(REMINDER_ID)).thenReturn(true);

            mockMvc.perform(delete(BASE_URL + "/" + REMINDER_ID)).andExpect(status().isNoContent());

            verify(reminderService).delete(REMINDER_ID);
        }

        @Test
        @DisplayName("inexistente: 404")
        void inexistente() throws Exception {
            when(reminderService.delete(REMINDER_ID)).thenReturn(false);

            mockMvc.perform(delete(BASE_URL + "/" + REMINDER_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.erro").value(containsString(NAO_ENCONTRADO)));
        }

        @Test
        @DisplayName("erro inesperado: 500")
        void erroInesperado() throws Exception {
            when(reminderService.delete(REMINDER_ID)).thenThrow(new RuntimeException("boom"));

            mockMvc.perform(delete(BASE_URL + "/" + REMINDER_ID))
                    .andExpect(status().isInternalServerError());
        }
    }

    // =========================================================================
    // POST /admin/reminders/{id}/test
    // =========================================================================

    @Nested
    @DisplayName("POST /admin/reminders/{id}/test")
    class Teste {

        @Test
        @DisplayName("sucesso: 200 + mensagem")
        void sucesso() throws Exception {
            when(reminderDispatcher.dispatchToChat(REMINDER_ID, CHAT_ID)).thenReturn(true);

            mockMvc.perform(
                            post(BASE_URL + "/" + REMINDER_ID + "/test")
                                    .param("chatId", String.valueOf(CHAT_ID)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mensagem").value(containsString("enviado")));

            verify(reminderDispatcher).dispatchToChat(REMINDER_ID, CHAT_ID);
        }

        @Test
        @DisplayName("falha: 500 + erro")
        void falha() throws Exception {
            when(reminderDispatcher.dispatchToChat(REMINDER_ID, CHAT_ID)).thenReturn(false);

            mockMvc.perform(
                            post(BASE_URL + "/" + REMINDER_ID + "/test")
                                    .param("chatId", String.valueOf(CHAT_ID)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.erro").value(containsString("Falha ao enviar")));
        }

        @Test
        @DisplayName("reminder inexistente: dispatchToChat retorna false, 500")
        void reminderInexistente() throws Exception {
            when(reminderDispatcher.dispatchToChat(REMINDER_ID, CHAT_ID)).thenReturn(false);

            mockMvc.perform(
                            post(BASE_URL + "/" + REMINDER_ID + "/test")
                                    .param("chatId", String.valueOf(CHAT_ID)))
                    .andExpect(status().isInternalServerError());
        }
    }

    // =========================================================================
    // POST /admin/reminders/reload
    // =========================================================================

    @Nested
    @DisplayName("POST /admin/reminders/reload")
    class Reload {

        @Test
        @DisplayName("sucesso: 200 + contadores")
        void sucesso() throws Exception {
            // ReminderService.reload() é void — o mock já é no-op.
            when(reminderService.count()).thenReturn(3);
            when(reminderScheduler.scheduledCount()).thenReturn(2);

            mockMvc.perform(post(BASE_URL + "/reload"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mensagem").value(containsString("3")))
                    .andExpect(jsonPath("$.scheduled").value(2));

            verify(reminderService).reload();
        }

        @Test
        @DisplayName("erro inesperado: 500")
        void erro() throws Exception {
            doThrow(new RuntimeException("boom")).when(reminderService).reload();

            mockMvc.perform(post(BASE_URL + "/reload")).andExpect(status().isInternalServerError());
        }
    }

    // =========================================================================
    // GET /admin/reminders/status
    // =========================================================================

    @Nested
    @DisplayName("GET /admin/reminders/status")
    class Diagnostico {

        @Test
        @DisplayName("retorna contadores e IDs agendados")
        void diagnostico() throws Exception {
            when(reminderService.count()).thenReturn(5);
            when(reminderService.getEnabled()).thenReturn(List.of(reminder()));
            when(reminderScheduler.scheduledCount()).thenReturn(1);
            when(reminderScheduler.scheduledIds()).thenReturn(Set.of(REMINDER_ID));

            mockMvc.perform(get(BASE_URL + "/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(5))
                    .andExpect(jsonPath("$.enabled").value(1))
                    .andExpect(jsonPath("$.scheduled").value(1))
                    .andExpect(jsonPath("$.scheduledIds").isArray())
                    .andExpect(jsonPath("$.scheduledIds[0]").value(REMINDER_ID));
        }
    }

    // =========================================================================
    // VALIDAÇÕES GERAIS
    // =========================================================================

    @Nested
    @DisplayName("Validações gerais")
    class Validacoes {

        @Test
        @DisplayName("POST /admin/reminders com body vazio: 400")
        void bodyVazio() throws Exception {
            mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(""))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("PUT com id do path e body id iguais: aceito")
        void putIdsIguais() throws Exception {
            Reminder r = reminder(REMINDER_ID, true, CRON_VALIDO);
            when(reminderService.update(eq(REMINDER_ID), any(Reminder.class))).thenReturn(r);

            mockMvc.perform(
                            put(BASE_URL + "/" + REMINDER_ID)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json(r)))
                    .andExpect(status().isOk());
        }
    }
}
