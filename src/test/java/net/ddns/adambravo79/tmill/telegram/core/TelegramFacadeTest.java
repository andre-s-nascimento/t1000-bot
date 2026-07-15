package net.ddns.adambravo79.tmill.telegram.core;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.pengrad.telegrambot.model.File;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendPhoto;
import com.pengrad.telegrambot.response.GetFileResponse;

import io.ksilisk.telegrambot.core.executor.TelegramBotExecutor;

@ExtendWith(MockitoExtension.class)
class TelegramFacadeTest {

    @Mock private TelegramBotExecutor executor;
    @Mock private TelegramSafeExecutor safeExecutor;

    @InjectMocks private TelegramFacade facade;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(facade, "botToken", "token123");
    }

    @Test
    void deveEnviarMensagemHtml() throws Exception {
        doAnswer(
                        inv -> {
                            TelegramSafeExecutor.ThrowingRunnable action = inv.getArgument(2);
                            action.run();
                            return null;
                        })
                .when(safeExecutor)
                .run(anyLong(), any(), any());

        facade.enviarMensagemHtml(123L, "texto <b>HTML</b>");

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(executor).execute(captor.capture());
        SendMessage msg = captor.getValue();
        assertThat(msg.getParameters().get("chat_id")).isEqualTo(123L);
        assertThat(msg.getParameters().get("text")).isEqualTo("texto <b>HTML</b>");
        assertThat(msg.getParameters().get("parse_mode"))
                .isEqualTo(ParseMode.HTML); // antes era .name()
    }

    @Test
    void deveEnviarFotoHtml() throws Exception {
        doAnswer(
                        inv -> {
                            TelegramSafeExecutor.ThrowingRunnable action = inv.getArgument(2);
                            action.run();
                            return null;
                        })
                .when(safeExecutor)
                .run(anyLong(), any(), any());

        facade.enviarFotoHtml(123L, "http://foto.jpg", "legenda");

        ArgumentCaptor<SendPhoto> captor = ArgumentCaptor.forClass(SendPhoto.class);
        verify(executor).execute(captor.capture());
        SendPhoto photo = captor.getValue();
        assertThat(photo.getParameters().get("chat_id")).isEqualTo(123L);
        assertThat(photo.getParameters().get("photo")).isEqualTo("http://foto.jpg");
        assertThat(photo.getParameters().get("caption")).isEqualTo("legenda");
        assertThat(photo.getParameters().get("parse_mode"))
                .isEqualTo(ParseMode.HTML); // antes era .name()
    }

    @Test
    void deveGetFile() {
        GetFileResponse response = mock(GetFileResponse.class);
        when(response.isOk()).thenReturn(true);
        File file = mock(File.class);
        when(response.file()).thenReturn(file);
        when(executor.execute(any())).thenReturn(response);

        File result = facade.getFile("fileId");
        assertThat(result).isSameAs(file);
    }

    @Test
    void deveGetFileComErro() {
        GetFileResponse response = mock(GetFileResponse.class);
        when(response.isOk()).thenReturn(false);
        when(response.description()).thenReturn("Erro");
        when(executor.execute(any())).thenReturn(response);

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> facade.getFile("fileId"))
                .withMessageContaining("Falha ao obter arquivo");
    }
}
