package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pengrad.telegrambot.request.RestrictChatMember;
import com.pengrad.telegrambot.response.BaseResponse;

import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;

@ExtendWith(MockitoExtension.class)
class TelegramModerationServiceTest {

    @Mock private TelegramFacade telegramFacade;
    @Mock private BaseResponse baseResponse;

    private TelegramModerationService service;

    private static final long CHAT_ID = -100L;
    private static final long USER_ID = 123L;

    @BeforeEach
    void setUp() {
        service = new TelegramModerationService(telegramFacade);
    }

    @Test
    @DisplayName("muteUser: deve zerar todas as permissões e não definir data (permanente)")
    void muteUser_permanente() {
        when(telegramFacade.executar(any(RestrictChatMember.class))).thenReturn(baseResponse);

        boolean sucesso = service.muteUser(CHAT_ID, USER_ID, 0);

        assertThat(sucesso).isTrue();

        ArgumentCaptor<RestrictChatMember> captor =
                ArgumentCaptor.forClass(RestrictChatMember.class);
        verify(telegramFacade).executar(captor.capture());

        Map<String, Object> params = captor.getValue().getParameters();

        assertThat(params)
                .containsEntry("chat_id", CHAT_ID)
                .containsEntry("user_id", USER_ID)
                // 🔧 FIX: O Pengrad omite a chave "until_date" quando o valor é 0 (permanente)
                .doesNotContainKey("until_date");

        assertThat(params.get("permissions").toString()).contains("can_send_messages=false");
    }

    @Test
    @DisplayName("muteUser: deve calcular o untilDate corretamente para 60 minutos")
    void muteUser_porTempo() {
        when(telegramFacade.executar(any(RestrictChatMember.class))).thenReturn(baseResponse);

        long agora = System.currentTimeMillis() / 1000;
        boolean sucesso = service.muteUser(CHAT_ID, USER_ID, 60);

        assertThat(sucesso).isTrue();

        ArgumentCaptor<RestrictChatMember> captor =
                ArgumentCaptor.forClass(RestrictChatMember.class);
        verify(telegramFacade).executar(captor.capture());

        int untilDate = (int) captor.getValue().getParameters().get("until_date");

        // Como 60 minutos = 3600 segundos, o untilDate deve ser aproximadamente (agora + 3600)
        // Adicionamos uma margem de 5 segundos para execução do teste
        assertThat(untilDate).isBetween((int) agora + 3595, (int) agora + 3605);
    }

    @Test
    @DisplayName("muteUser: deve retornar false se o Telegram lançar exceção")
    void muteUser_erro() {
        when(telegramFacade.executar(any(RestrictChatMember.class)))
                .thenThrow(new RuntimeException("Bad Request: PARTICIPANT_ID_INVALID"));

        boolean sucesso = service.muteUser(CHAT_ID, USER_ID, 0);

        assertThat(sucesso).isFalse();
    }

    @Test
    @DisplayName("unmuteUser: deve restaurar as permissões de um usuário comum")
    void unmuteUser() {
        when(telegramFacade.executar(any(RestrictChatMember.class))).thenReturn(baseResponse);

        boolean sucesso = service.unmuteUser(CHAT_ID, USER_ID);

        assertThat(sucesso).isTrue();

        ArgumentCaptor<RestrictChatMember> captor =
                ArgumentCaptor.forClass(RestrictChatMember.class);
        verify(telegramFacade).executar(captor.capture());

        Map<String, Object> params = captor.getValue().getParameters();

        // Permissões de um usuário comum (todas true)
        assertThat(params.get("permissions").toString()).contains("can_send_messages=true");
    }

    @Test
    @DisplayName("unmuteUser: deve retornar false se o Telegram lançar exceção")
    void unmuteUser_erro() {
        when(telegramFacade.executar(any(RestrictChatMember.class)))
                .thenThrow(new RuntimeException("Falha no Telegram"));

        boolean sucesso = service.unmuteUser(CHAT_ID, USER_ID);

        assertThat(sucesso).isFalse();
    }

    @Test
    @DisplayName(
            "muteUser: deve definir untilDate como 0 se duration for -1 (permanente explícito)")
    void muteUser_permanenteExplicito() {
        when(telegramFacade.executar(any(RestrictChatMember.class))).thenReturn(baseResponse);

        boolean sucesso = service.muteUser(CHAT_ID, USER_ID, -1);

        assertThat(sucesso).isTrue();

        ArgumentCaptor<RestrictChatMember> captor =
                ArgumentCaptor.forClass(RestrictChatMember.class);
        verify(telegramFacade).executar(captor.capture());

        // 🔧 FIX: Como o código chama request.untilDate(0), a chave existe e o valor é 0
        assertThat(captor.getValue().getParameters()).containsEntry("until_date", 0);
    }
}
