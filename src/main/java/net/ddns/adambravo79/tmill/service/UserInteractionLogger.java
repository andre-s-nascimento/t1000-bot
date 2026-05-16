/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class UserInteractionLogger {

    @Value("${user.log.directory:logs}")
    private String logDirectory;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public void logUser(long userId, String userName, String action) {
        try {
            String today = LocalDate.now().format(DATE_FORMATTER);
            Path logFile = Paths.get(logDirectory, "users_" + today + ".txt");
            Files.createDirectories(logFile.getParent());
            String line =
                    String.format(
                            "%s | userId=%d | name=%s | action=%s%n",
                            java.time.LocalDateTime.now(), userId, userName, action);
            Files.writeString(logFile, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Falha ao escrever log de usuário", e);
        }
    }
}
