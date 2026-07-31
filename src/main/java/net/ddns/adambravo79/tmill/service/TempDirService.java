package net.ddns.adambravo79.tmill.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TempDirService {

    private Path tempDir;

    @Value("${app.temp.dir:}")
    private String configuredTempDir;

    @PostConstruct
    public void init() {
        // 1. Tenta usar o diretório configurado
        if (configuredTempDir != null && !configuredTempDir.isBlank()) {
            Path configuredPath = Paths.get(configuredTempDir).toAbsolutePath().normalize();
            try {
                Files.createDirectories(configuredPath);
                this.tempDir = configuredPath;
                log.info("📁 Diretório temporário configurado: {}", this.tempDir);
                return;
            } catch (Exception e) {
                log.warn(
                        "⚠️ Não foi possível criar o diretório configurado '{}': {}",
                        configuredPath,
                        e.getMessage());
                log.info("🔄 Usando diretório temporário do sistema como fallback.");
            }
        }

        // 2. Fallback: diretório temporário do sistema
        String tmpDir = System.getProperty("java.io.tmpdir");
        if (tmpDir == null || tmpDir.isBlank()) {
            throw new IllegalStateException(
                    "Não foi possível determinar o diretório temporário do sistema.");
        }
        this.tempDir = Paths.get(tmpDir).resolve("t1000-temp");
        try {
            Files.createDirectories(this.tempDir);
            log.info("📁 Usando diretório temporário do sistema: {}", this.tempDir);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao criar diretório temporário em " + tmpDir, e);
        }
    }

    public Path getTempDir() {
        return tempDir;
    }

    public Path createTempFile(String prefix, String suffix) throws Exception {
        return Files.createTempFile(tempDir, prefix, suffix);
    }
}
