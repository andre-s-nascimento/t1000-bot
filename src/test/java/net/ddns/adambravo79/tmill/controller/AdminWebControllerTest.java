package net.ddns.adambravo79.tmill.controller;

import static org.hamcrest.CoreMatchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import net.ddns.adambravo79.tmill.client.AzureTtsClient;
import net.ddns.adambravo79.tmill.service.TempDirService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;

@ExtendWith(MockitoExtension.class)
class AdminWebControllerTest {

    private MockMvc mockMvc;

    @Mock private AzureTtsClient azureTtsClient;

    @Mock private TelegramFacade telegramFacade;

    @Mock private TempDirService tempDirService;

    @Mock private Path tempFile;

    @Mock private Path finalFile;

    @InjectMocks private AdminWebController adminWebController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminWebController).build();
    }

    @Test
    void falaT1000Tts_withValidText_shouldReturnSuccess() throws Exception {
        // Arrange
        String text = "Test TTS";
        long chatId = 123L;
        byte[] audioData = new byte[] {1, 2, 3};

        // Mock do Path para arquivo temporário
        Path tempPath = Paths.get("/tmp/tts_audio.mp3");
        Path finalPath = Paths.get("/tmp/Cronicas-do-T1000-Audio-123.mp3");

        when(azureTtsClient.synthesizeFullText(text)).thenReturn(audioData);
        when(tempDirService.createTempFile(anyString(), anyString())).thenReturn(tempPath);

        // Mock dos métodos estáticos do Files
        try (var mockedFiles = mockStatic(Files.class)) {
            mockedFiles
                    .when(() -> Files.write(any(Path.class), any(byte[].class)))
                    .thenReturn(tempPath);
            mockedFiles
                    .when(() -> Files.move(any(Path.class), any(Path.class)))
                    .thenReturn(finalPath);
            mockedFiles.when(() -> Files.deleteIfExists(any(Path.class))).thenReturn(true);
            mockedFiles.when(() -> Files.exists(any(Path.class))).thenReturn(true);

            // Act & Assert
            mockMvc.perform(
                            post("/admin-web/fala-t1000-tts")
                                    .param("message", text)
                                    .param("chatId", String.valueOf(chatId))
                                    .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isOk())
                    .andExpect(
                            content()
                                    .string(
                                            containsString(
                                                    "Áudio enviado com sucesso para o chat "
                                                            + chatId)));

            // Verifica
            verify(azureTtsClient).synthesizeFullText(text);
            verify(telegramFacade)
                    .enviarMidia(eq(chatId), anyString(), eq("🔊 Áudios para a futura Skynet"));
        }
    }

    @Test
    void falaT1000Tts_withEmptyMessage_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(
                        post("/admin-web/fala-t1000-tts")
                                .param("message", "")
                                .param("chatId", "123"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Parâmetro 'message' é obrigatório")));
    }

    @Test
    void falaT1000Tts_withNullMessage_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/admin-web/fala-t1000-tts").param("chatId", "123"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Parâmetro 'message' é obrigatório")));
    }

    @Test
    void falaT1000Tts_withoutChatId_shouldUseDefaultChatId() throws Exception {
        // Arrange
        String text = "Test TTS";
        byte[] audioData = new byte[] {1, 2, 3};
        Path tempPath = Paths.get("/tmp/tts_audio.mp3");
        long defaultChatId = 0L; // Será resolvido pelo ownerId ou digest chat

        // Configura mocks
        when(azureTtsClient.synthesizeFullText(text)).thenReturn(audioData);
        when(tempDirService.createTempFile(anyString(), anyString())).thenReturn(tempPath);

        try (var mockedFiles = mockStatic(Files.class)) {
            mockedFiles
                    .when(() -> Files.write(any(Path.class), any(byte[].class)))
                    .thenReturn(tempPath);
            mockedFiles
                    .when(() -> Files.move(any(Path.class), any(Path.class)))
                    .thenReturn(tempPath);
            mockedFiles.when(() -> Files.deleteIfExists(any(Path.class))).thenReturn(true);
            mockedFiles.when(() -> Files.exists(any(Path.class))).thenReturn(true);

            mockMvc.perform(post("/admin-web/fala-t1000-tts").param("message", text))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Áudio enviado com sucesso")));
        }
    }

    @Test
    void falaT1000Tts_whenTtsFails_shouldReturnError() throws Exception {
        // Arrange
        String text = "Test TTS";
        when(azureTtsClient.synthesizeFullText(text)).thenReturn(new byte[0]);

        // Act & Assert
        mockMvc.perform(
                        post("/admin-web/fala-t1000-tts")
                                .param("message", text)
                                .param("chatId", "123"))
                .andExpect(status().is5xxServerError())
                .andExpect(content().string(containsString("Falha na síntese")));
    }

    @Test
    void falaT1000Tts_whenTtsThrowsException_shouldReturnError() throws Exception {
        // Arrange
        String text = "Test TTS";
        when(azureTtsClient.synthesizeFullText(text))
                .thenThrow(new RuntimeException("TTS service unavailable"));

        // Act & Assert
        mockMvc.perform(
                        post("/admin-web/fala-t1000-tts")
                                .param("message", text)
                                .param("chatId", "123"))
                .andExpect(status().is5xxServerError())
                .andExpect(content().string(containsString("Erro ao salvar áudio")));
    }
}
