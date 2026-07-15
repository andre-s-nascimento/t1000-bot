package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class IdeasLoggerTest {

    @TempDir Path tempDir;

    @Test
    void deveSalvarIdeiaEmArquivoDiario() throws Exception {
        // Arrange
        IdeasLogger logger = new IdeasLogger();
        ReflectionTestUtils.setField(logger, "logDirectory", tempDir.toString());

        long userId = 123L;
        String userName = "Fulano";
        long chatId = 456L;
        String idea = "Minha ideia";
        String groupName = "Grupo Teste";

        // Act
        logger.saveIdea(userId, userName, chatId, idea, groupName);

        // Assert
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Path logFile = tempDir.resolve("ideas_" + today + ".txt");
        assertThat(logFile).exists();

        String content = Files.readString(logFile);
        assertThat(content)
                .contains("userId=123")
                .contains("name=Fulano")
                .contains("chatId=456")
                .contains("chatName=Grupo Teste")
                .contains("idea=Minha ideia");
    }

    @Test
    void deveCriarDiretorioSeNaoExistir() throws Exception {
        // Arrange
        Path deeperDir = tempDir.resolve("subdir").resolve("logs");
        IdeasLogger logger = new IdeasLogger();
        ReflectionTestUtils.setField(logger, "logDirectory", deeperDir.toString());

        // Act
        logger.saveIdea(1L, "Teste", 2L, "Ideia", "Grupo");

        // Assert
        assertThat(deeperDir).exists();
        assertThat(Files.isDirectory(deeperDir)).isTrue();
    }

    @Test
    void deveLidarComNomeVazio() throws Exception {
        // Arrange
        IdeasLogger logger = new IdeasLogger();
        ReflectionTestUtils.setField(logger, "logDirectory", tempDir.toString());

        // Act
        logger.saveIdea(1L, "", 2L, "Ideia", "");

        // Assert
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Path logFile = tempDir.resolve("ideas_" + today + ".txt");
        assertThat(logFile).exists();
        String content = Files.readString(logFile);
        assertThat(content).contains("name=");
    }

    @Test
    void deveLidarComIdeiaVazia() throws Exception {
        // Arrange
        IdeasLogger logger = new IdeasLogger();
        ReflectionTestUtils.setField(logger, "logDirectory", tempDir.toString());

        // Act
        logger.saveIdea(1L, "User", 2L, "", "Group");

        // Assert
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Path logFile = tempDir.resolve("ideas_" + today + ".txt");
        assertThat(logFile).exists();
        String content = Files.readString(logFile);
        assertThat(content).contains("idea=");
    }
}
