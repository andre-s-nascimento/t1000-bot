/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class IdeasLogger {

    @Value("${ideas.log.directory:logs}")
    private String logDirectory;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public void saveIdea(long userId, String userName, long chatId, String idea, String groupName) {
        try {
            String today = LocalDate.now().format(DATE_FORMATTER);
            Path logFile = Paths.get(logDirectory, "ideas_" + today + ".txt");
            Files.createDirectories(logFile.getParent());

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            String line =
                    String.format(
                            "%s | userId=%d | name=%s | chatId=%d | chatName=%s | idea=%s%n",
                            timestamp, userId, userName, chatId, groupName, idea);

            Files.writeString(logFile, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.info("💡 Ideia salva: userId={}, chatId={}", userId, chatId);
        } catch (IOException e) {
            log.error("Falha ao salvar ideia", e);
        }
    }
}
