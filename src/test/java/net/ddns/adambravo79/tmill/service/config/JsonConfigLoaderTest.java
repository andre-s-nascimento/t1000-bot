/* (c) 2026 | 30/09/2026 */
package net.ddns.adambravo79.tmill.service.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import net.ddns.adambravo79.tmill.exception.ConfigLoadException;
import tools.jackson.databind.ObjectMapper;

class JsonConfigLoaderTest {

    private JsonConfigLoader jsonConfigLoader;
    private ObjectMapper objectMapper;

    // DTO fictício para validação do mapeamento
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TestConfigRecord(String name, int version) {}

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        jsonConfigLoader = new JsonConfigLoader(objectMapper);
    }

    @Nested
    @DisplayName("Cenários de Fallback e Prioridade")
    class FallbackAndPriorityTests {

        @Test
        @DisplayName("Deve carregar da propriedade de override quando informada e existente")
        void shouldLoadFromOverridePathWhenProvided(@TempDir Path tempDir) throws IOException {
            // Arrange
            Path overrideFile = tempDir.resolve("override-config.json");
            String jsonContent =
                    """
                    {
                        "name": "override-source",
                        "version": 1
                    }
                    """;
            Files.writeString(overrideFile, jsonContent);

            // Act
            Optional<TestConfigRecord> result =
                    jsonConfigLoader.loadConfig(
                            "unused-file.json",
                            TestConfigRecord.class,
                            overrideFile.toAbsolutePath().toString());

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().name()).isEqualTo("override-source");
            assertThat(result.get().version()).isEqualTo(1);
        }

        @Test
        @DisplayName(
                "Deve carregar do classpath quando o arquivo não existir nos diretórios externos")
        void shouldFallbackToClasspathWhenExternalFilesDoNotExist() {
            // Act
            Optional<TestConfigRecord> result =
                    jsonConfigLoader.loadConfig(
                            "non-existent-config-file.json", TestConfigRecord.class, null);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName(
                "Deve ignorar o overridePath se for nulo, em branco ou se o arquivo não existir")
        void shouldIgnoreInvalidOverridePath(@TempDir Path tempDir) {
            // Arrange
            String nonExistentOverride =
                    tempDir.resolve("ghost-file.json").toAbsolutePath().toString();

            // Act
            Optional<TestConfigRecord> result =
                    jsonConfigLoader.loadConfig(
                            "non-existent-file.json", TestConfigRecord.class, nonExistentOverride);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Cenários de Tratamento de Erro e Mapeamento")
    class ErrorAndMappingTests {

        @Test
        @DisplayName(
                "Deve lançar ConfigLoadException quando o arquivo contiver JSON sintaticamente"
                        + " inválido")
        void shouldThrowConfigLoadExceptionWhenJsonIsMalformed(@TempDir Path tempDir)
                throws IOException {
            // Arrange
            Path invalidJsonFile = tempDir.resolve("invalid-config.json");
            String malformedJson = "{ name: 'broken-json', version: }"; // JSON quebrado
            Files.writeString(invalidJsonFile, malformedJson);

            String overridePath = invalidJsonFile.toAbsolutePath().toString();

            // Act & Assert
            assertThatThrownBy(
                            () ->
                                    jsonConfigLoader.loadConfig(
                                            "any-file.json", TestConfigRecord.class, overridePath))
                    .isInstanceOf(ConfigLoadException.class)
                    .hasMessageContaining("JSON malformado ou incompatível com o modelo")
                    .hasFieldOrPropertyWithValue("resourcePath", overridePath);
        }

        @Test
        @DisplayName(
                "Deve retornar Optional.empty() quando o arquivo não for encontrado em nenhuma das"
                        + " 4 fontes")
        void shouldReturnEmptyOptionalWhenConfigNotFoundAnywhere() {
            // Act
            Optional<TestConfigRecord> result =
                    jsonConfigLoader.loadConfig(
                            "file-that-does-not-exist-anywhere-12345.json",
                            TestConfigRecord.class,
                            null);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName(
                "Deve mapear corretamente para um Record/DTO anotado com propriedades"
                        + " desconhecidas")
        void shouldCorrectlyMapToAnnotatedRecordIgnoringUnknownProperties(@TempDir Path tempDir)
                throws IOException {
            // Arrange
            Path configFile = tempDir.resolve("extra-fields-config.json");
            String jsonWithExtraFields =
                    """
                    {
                        "name": "mapped-record",
                        "version": 42,
                        "extraField": "should be ignored",
                        "anotherUnmappedKey": 999
                    }
                    """;
            Files.writeString(configFile, jsonWithExtraFields);

            // Act
            Optional<TestConfigRecord> result =
                    jsonConfigLoader.loadConfig(
                            "dummy.json",
                            TestConfigRecord.class,
                            configFile.toAbsolutePath().toString());

            // Assert
            assertThat(result).isPresent();
            TestConfigRecord config = result.get();
            assertThat(config.name()).isEqualTo("mapped-record");
            assertThat(config.version()).isEqualTo(42);
        }
    }
}
