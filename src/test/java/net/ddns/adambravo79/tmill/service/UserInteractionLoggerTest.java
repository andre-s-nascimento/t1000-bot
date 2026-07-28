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

    @Test
    void deveCapturarIOExceptionQuandoFalhaAoCriarDiretorio() throws IOException {
        // Arrange: cria um arquivo no lugar do diretório pai para forçar IOException no
        // createDirectories
        UserInteractionLogger logger = new UserInteractionLogger();
        Path dirPath = tempDir.resolve("logs"); // caminho que será usado como diretório de logs
        // Cria um arquivo com o mesmo nome, para que createDirectories lance IOException
        Files.createFile(dirPath);
        ReflectionTestUtils.setField(logger, "logDirectory", dirPath.toString());

        // Act: tenta escrever o log – deve capturar a exceção e não propagar
        logger.logUser(123L, "Teste", "forçar-erro");

        // Assert: o arquivo de log não deveria ter sido criado
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Path logFile = dirPath.resolve("users_" + today + ".txt");
        assertThat(Files.exists(logFile)).isFalse();

        // Também podemos verificar que o arquivo original (dirPath) permanece como arquivo
        assertThat(Files.isRegularFile(dirPath)).isTrue();
    }
}
