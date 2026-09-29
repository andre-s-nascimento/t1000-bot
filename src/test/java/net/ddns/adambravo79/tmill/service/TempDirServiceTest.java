/* (c) 2026 | 28/09/2026 */
package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class TempDirServiceTest {

    @TempDir Path tempDir;

    @Test
    @DisplayName("init com diretório configurado válido usa o diretório")
    void init_withConfiguredDir_usesIt() {
        Path custom = tempDir.resolve("custom-temp");
        TempDirService svc = new TempDirService();
        ReflectionTestUtils.setField(svc, "configuredTempDir", custom.toString());

        svc.init();

        assertThat(svc.getTempDir()).isEqualTo(custom.toAbsolutePath().normalize());
        assertThat(Files.exists(custom)).isTrue();
    }

    @Test
    @DisplayName("init sem diretório configurado usa fallback do sistema")
    void init_withoutConfiguredDir_usesSystemTemp() {
        TempDirService svc = new TempDirService();
        ReflectionTestUtils.setField(svc, "configuredTempDir", "");

        svc.init();

        Path tempDir = svc.getTempDir();
        assertThat(tempDir).isNotNull();
        assertThat(tempDir.toString()).contains("t1000-temp");
        assertThat(Files.exists(tempDir)).isTrue();
    }

    @Test
    @DisplayName("init com diretório configurado inválido usa fallback")
    void init_withInvalidConfiguredDir_usesFallback() {
        TempDirService svc = new TempDirService();
        // Cria um arquivo (não diretório) no lugar do diretório → createDirectories falha
        Path blocker = tempDir.resolve("blocker");
        assertThatCode(
                        () -> {
                            Files.createFile(blocker);
                        })
                .doesNotThrowAnyException();

        ReflectionTestUtils.setField(svc, "configuredTempDir", blocker.toString());
        svc.init();

        assertThat(svc.getTempDir().toString()).contains("t1000-temp");
    }

    @Test
    @DisplayName("init com null usa fallback do sistema")
    void init_withNullConfiguredDir_usesFallback() {
        TempDirService svc = new TempDirService();
        ReflectionTestUtils.setField(svc, "configuredTempDir", null);

        svc.init();

        assertThat(svc.getTempDir().toString()).contains("t1000-temp");
    }

    @Test
    @DisplayName("createTempFile cria arquivo no diretório configurado")
    void createTempFile_createsFileInConfiguredDir() throws Exception {
        Path custom = tempDir.resolve("custom");
        TempDirService svc = new TempDirService();
        ReflectionTestUtils.setField(svc, "configuredTempDir", custom.toString());
        svc.init();

        Path file = svc.createTempFile("test-", ".txt");

        assertThat(file).exists();
        assertThat(file.getParent()).isEqualTo(custom.toAbsolutePath().normalize());
        assertThat(file.getFileName().toString()).startsWith("test-").endsWith(".txt");
    }
}
