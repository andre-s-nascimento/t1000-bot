/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import lombok.SneakyThrows;

class UserInteractionLoggerTest {

    @TempDir Path tempDir;

    @Test
    void deveEscreverLinhaNoArquivoDiario() throws IOException {
        // Arrange
        UserInteractionLogger logger = new UserInteractionLogger();
        ReflectionTestUtils.setField(logger, "logDirectory", tempDir.toString());

        long userId = 123L;
        String userName = "Fulano Teste";
        String action = "message:text";

        // Act
        logger.logUser(userId, userName, action);

        // Assert
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Path logFile = tempDir.resolve("users_" + today + ".txt");
        assertThat(Files.exists(logFile)).isTrue();

        String content = Files.readString(logFile);
        assertThat(content)
                .contains("userId=123")
                .contains("name=Fulano Teste")
                .contains("action=message:text");
    }

    @Test
    @SneakyThrows
    void deveCriarDiretorioSeNaoExistir() {
        // Arrange
        UserInteractionLogger logger = new UserInteractionLogger();
        Path deeperDir = tempDir.resolve("subdir").resolve("logs");
        ReflectionTestUtils.setField(logger, "logDirectory", deeperDir.toString());

        // Act
        logger.logUser(1L, "Teste", "callback:x");

        // Assert
        assertThat(Files.exists(deeperDir)).isTrue();
        assertThat(Files.isDirectory(deeperDir)).isTrue();
    }
}
