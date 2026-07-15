package net.ddns.adambravo79.tmill.telegram.util;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.User;

@ExtendWith(MockitoExtension.class)
class TelegramUtilsTest {

    private TelegramUtils utils;

    @Mock private User user;
    @Mock private Message message;
    @Mock private Chat chat;

    @BeforeEach
    void setUp() {
        utils = new TelegramUtils();
    }

    // =========================
    // 🧪 buildFullName
    // =========================

    @Test
    void buildFullName_deveRetornarVazioQuandoUserNull() {
        assertThat(utils.buildFullName(null)).isEmpty();
    }

    @Test
    void buildFullName_deveRetornarApenasPrimeiroNomeQuandoSemSobrenome() {
        when(user.firstName()).thenReturn("João");
        when(user.lastName()).thenReturn(null);
        assertThat(utils.buildFullName(user)).isEqualTo("João");
    }

    @Test
    void buildFullName_deveRetornarPrimeiroESobrenome() {
        when(user.firstName()).thenReturn("João");
        when(user.lastName()).thenReturn("Silva");
        assertThat(utils.buildFullName(user)).isEqualTo("João Silva");
    }

    @Test
    void buildFullName_deveIgnorarSobrenomeVazio() {
        when(user.firstName()).thenReturn("João");
        when(user.lastName()).thenReturn("");
        assertThat(utils.buildFullName(user)).isEqualTo("João");
    }

    // =========================
    // 🧪 escapeHtml
    // =========================

    @Test
    void escapeHtml_deveRetornarVazioQuandoNull() {
        assertThat(utils.escapeHtml(null)).isEmpty();
    }

    @Test
    void escapeHtml_deveEscaparCaracteresEspeciais() {
        String input = "& < > \" '";
        String expected = "&amp; &lt; &gt; &quot; &#39;";
        assertThat(utils.escapeHtml(input)).isEqualTo(expected);
    }

    @Test
    void escapeHtml_deveRetornarTextoNormalSemCaracteres() {
        String input = "Olá, tudo bem?";
        assertThat(utils.escapeHtml(input)).isEqualTo(input);
    }

    // =========================
    // 🧪 buildUserMention
    // =========================

    @Test
    void buildUserMention_deveRetornarUsuarioStringQuandoNull() {
        assertThat(utils.buildUserMention(null)).isEqualTo("Usuário");
    }

    @Test
    void buildUserMention_deveRetornarUsernameSeExistir() {
        when(user.username()).thenReturn("joao123");
        when(user.firstName()).thenReturn("João");
        when(user.lastName()).thenReturn(null);
        assertThat(utils.buildUserMention(user)).isEqualTo("@joao123");
    }

    @Test
    void buildUserMention_deveRetornarNomeEscapadoQuandoSemUsername() {
        when(user.username()).thenReturn(null);
        when(user.firstName()).thenReturn("João");
        when(user.lastName()).thenReturn("Silva");
        assertThat(utils.buildUserMention(user)).isEqualTo("João Silva");
    }

    @Test
    void buildUserMention_deveEscaparCaracteresEspeciaisNoNome() {
        when(user.username()).thenReturn(null);
        when(user.firstName()).thenReturn("João & Maria");
        when(user.lastName()).thenReturn(null);
        assertThat(utils.buildUserMention(user)).isEqualTo("João &amp; Maria");
    }

    // =========================
    // 🧪 splitMessage
    // =========================

    @Test
    void splitMessage_deveRetornarListaVaziaParaNull() {
        assertThat(utils.splitMessage(null)).isEmpty();
    }

    @Test
    void splitMessage_deveRetornarListaVaziaParaVazio() {
        assertThat(utils.splitMessage("")).isEmpty();
        assertThat(utils.splitMessage(" ")).isEmpty();
    }

    @Test
    void splitMessage_deveDividirTextoQuandoUltrapassaLimite() {
        String texto = "a".repeat(5000);
        List<String> partes = utils.splitMessage(texto, 4000);
        assertThat(partes).hasSize(2);
        assertThat(partes.get(0)).hasSize(4000);
        assertThat(partes.get(1)).hasSize(1000);
    }

    @Test
    void splitMessage_deveRespeitarQuebraDeLinha() {
        // Texto com duas linhas, total ~13 caracteres
        String texto = "linha1\nlinha2";
        // Limite 10: deve quebrar na quebra de linha, pois está dentro do limite
        List<String> partes = utils.splitMessage(texto, 10);

        assertThat(partes).hasSize(2);
        assertThat(partes.get(0)).isEqualTo("linha1");
        assertThat(partes.get(1)).isEqualTo("linha2");
    }

    @Test
    void splitMessage_semParametro_deveUsarLimitePadrao() {
        String texto = "a".repeat(5000);
        List<String> partes = utils.splitMessage(texto);
        assertThat(partes).hasSize(2);
        assertThat(partes.get(0)).hasSize(TelegramUtils.TELEGRAM_LIMIT);
    }

    // =========================
    // 🧪 getChatName
    // =========================

    @Test
    void getChatName_deveRetornarPrivadoParaChatPrivado() {
        when(message.chat()).thenReturn(chat);
        when(chat.type()).thenReturn(com.pengrad.telegrambot.model.Chat.Type.Private);
        assertThat(utils.getChatName(message)).isEqualTo("privado");
    }

    @Test
    void getChatName_deveRetornarTituloParaGrupoComTitulo() {
        when(message.chat()).thenReturn(chat);
        when(chat.type()).thenReturn(com.pengrad.telegrambot.model.Chat.Type.supergroup);
        when(chat.title()).thenReturn("Meu Grupo");
        assertThat(utils.getChatName(message)).isEqualTo("Meu Grupo");
    }

    @Test
    void getChatName_deveRetornarGrupoParaGrupoSemTitulo() {
        when(message.chat()).thenReturn(chat);
        when(chat.type()).thenReturn(com.pengrad.telegrambot.model.Chat.Type.group);
        when(chat.title()).thenReturn(null);
        assertThat(utils.getChatName(message)).isEqualTo("grupo");
    }
}
