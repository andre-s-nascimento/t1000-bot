/* (c) 2026 | 30/09/2026 */
package net.ddns.adambravo79.tmill.service.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import net.ddns.adambravo79.tmill.exception.ConfigLoadException;
import tools.jackson.databind.ObjectMapper;

class JsonConfigLoaderTest {

    private JsonConfigLoader jsonConfigLoader;
    private Path localConfigFile;
    private Path appConfigFile;

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TestConfigRecord(String name, int version) {}

    @BeforeEach
    void setUp() {
        jsonConfigLoader = new JsonConfigLoader(new ObjectMapper());
    }

    @AfterEach
    void cleanupConfigFiles() {
        if (localConfigFile != null) {
            try {
                Files.deleteIfExists(localConfigFile);
            } catch (IOException ignored) {
            }
        }

        if (appConfigFile != null) {
            try {
                Files.deleteIfExists(appConfigFile);

                Path appConfigDir = appConfigFile.getParent();
                if (appConfigDir != null) {
                    Files.deleteIfExists(appConfigDir);
                    if (appConfigDir.getParent() != null) {
                        Files.deleteIfExists(appConfigDir.getParent());
                    }
                }
            } catch (IOException | SecurityException ignored) {
                // Silencia exceções de permissão para diretórios do sistema (/app)
            }
        }
    }

    @Test
    @DisplayName("Override existente deve ter prioridade sobre as demais fontes")
    void shouldLoadFromOverridePathWhenProvided(@TempDir Path tempDir) throws IOException {

        Path overrideFile = tempDir.resolve("override-config.json");

        Files.writeString(overrideFile, "{\"name\":\"override-source\",\"version\":1}");

        Optional<TestConfigRecord> result =
                jsonConfigLoader.loadConfig(
                        uniqueFileName("unused"),
                        TestConfigRecord.class,
                        overrideFile.toAbsolutePath().toString());

        assertThat(result).contains(new TestConfigRecord("override-source", 1));
    }

    @Test
    @DisplayName("Deve carregar a configuração do diretório local ./config")
    void shouldLoadFromLocalConfigDirectory() throws IOException {

        String fileName = uniqueFileName("local");

        Path configDir = Path.of("config");
        Files.createDirectories(configDir);

        localConfigFile = configDir.resolve(fileName);

        Files.writeString(localConfigFile, "{\"name\":\"local-source\",\"version\":2}");

        Optional<TestConfigRecord> result =
                jsonConfigLoader.loadConfig(fileName, TestConfigRecord.class, null);

        assertThat(result).contains(new TestConfigRecord("local-source", 2));
    }

    @Test
    @DisplayName("Deve carregar a configuração do diretório /app/config quando existir")
    void shouldLoadFromAppConfigDirectory() throws IOException {
        String fileName = uniqueFileName("app");
        Path configDir = Path.of("/app/config");

        try {
            Files.createDirectories(configDir);
        } catch (IOException | SecurityException ex) {
            // Aborta o teste de forma limpa (Disabled/Skipped) se não houver permissão no SO
            Assumptions.assumeTrue(false, "Sem permissão para criar /app/config no ambiente local");
        }

        appConfigFile = configDir.resolve(fileName);

        try {
            Files.writeString(appConfigFile, "{\"name\":\"app-source\",\"version\":3}");
        } catch (IOException | SecurityException ex) {
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    false, "Sem permissão de escrita em /app/config");
        }

        Optional<TestConfigRecord> result =
                jsonConfigLoader.loadConfig(fileName, TestConfigRecord.class, null);

        assertThat(result).contains(new TestConfigRecord("app-source", 3));
    }

    @Test
    @DisplayName("Deve carregar um JSON válido do classpath")
    void shouldLoadFromClasspath() {

        Optional<TestConfigRecord> result =
                jsonConfigLoader.loadConfig(
                        "config/test-config.json", TestConfigRecord.class, null);

        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("classpath-source");
        assertThat(result.get().version()).isEqualTo(4);
    }

    @Test
    @DisplayName("Deve encapsular erro de parse do classpath em ConfigLoadException")
    void shouldThrowConfigLoadExceptionForInvalidClasspathMapping() {

        assertThatThrownBy(
                        () ->
                                jsonConfigLoader.loadConfig(
                                        "prompts/digest-personas.json", Integer.class, null))
                .isInstanceOf(ConfigLoadException.class)
                .hasMessageContaining("Erro ao ler JSON do classpath")
                .hasFieldOrPropertyWithValue("resourcePath", "/prompts/digest-personas.json");
    }

    @Test
    @DisplayName("Override inválido deve ser ignorado e permitir fallback")
    void shouldIgnoreInvalidOverridePath(@TempDir Path tempDir) {

        String nonExistentOverride = tempDir.resolve("ghost-file.json").toAbsolutePath().toString();

        Optional<TestConfigRecord> result =
                jsonConfigLoader.loadConfig(
                        uniqueFileName("missing"), TestConfigRecord.class, nonExistentOverride);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("JSON inválido no override deve lançar ConfigLoadException")
    void shouldThrowConfigLoadExceptionForMalformedOverride(@TempDir Path tempDir)
            throws IOException {

        Path invalidJsonFile = tempDir.resolve("invalid-config.json");

        Files.writeString(invalidJsonFile, "{ name: 'broken-json', version: }");

        assertThatThrownBy(
                        () ->
                                jsonConfigLoader.loadConfig(
                                        "any-file.json",
                                        TestConfigRecord.class,
                                        invalidJsonFile.toAbsolutePath().toString()))
                .isInstanceOf(ConfigLoadException.class)
                .hasMessageContaining("JSON malformado ou incompatível com o modelo")
                .hasFieldOrPropertyWithValue(
                        "resourcePath", invalidJsonFile.toAbsolutePath().toString());
    }

    @Test
    @DisplayName("Deve mapear propriedades conhecidas e ignorar propriedades extras")
    void shouldCorrectlyMapToAnnotatedRecord(@TempDir Path tempDir) throws IOException {

        Path configFile = tempDir.resolve("extra-fields-config.json");

        Files.writeString(
                configFile,
                """
                {
                    "name": "mapped-record",
                    "version": 42,
                    "extraField": "ignored",
                    "anotherUnmappedKey": 999
                }
                """);

        Optional<TestConfigRecord> result =
                jsonConfigLoader.loadConfig(
                        "dummy.json",
                        TestConfigRecord.class,
                        configFile.toAbsolutePath().toString());

        assertThat(result).contains(new TestConfigRecord("mapped-record", 42));
    }

    private static String uniqueFileName(String prefix) {
        return "t1000-" + prefix + "-" + UUID.randomUUID() + ".json";
    }
}
