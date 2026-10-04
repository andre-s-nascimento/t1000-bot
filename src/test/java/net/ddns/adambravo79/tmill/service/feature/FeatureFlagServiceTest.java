/* (c) 2026 | 28/09/2026 */
package net.ddns.adambravo79.tmill.service.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.ObjectMapper;

/**
 * Testes do {@link FeatureFlagService}.
 *
 * <p>Cobre:
 *
 * <ul>
 *   <li>Registro de flags (editáveis e read-only)
 *   <li>Idempotência do registro
 *   <li>Leitura (isEnabled, list, listAsMap, get)
 *   <li>Toggle (sucesso, read-only, desconhecida, no-op)
 *   <li>Persistência em disco (enabled/disabled)
 *   <li>Boot com JSON existente, JSON corrompido, diretório inexistente
 *   <li>Escrita atômica (arquivo .tmp)
 * </ul>
 */
class FeatureFlagServiceTest {

    @TempDir Path tempDir;

    private ObjectMapper objectMapper;
    private Path persistPath;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        persistPath = tempDir.resolve("feature-flags.json");
    }

    private FeatureFlagService newService(boolean persistEnabled) {
        return new FeatureFlagService(objectMapper, persistEnabled, persistPath.toString());
    }

    // =========================================================================
    // REGISTRO
    // =========================================================================

    @Nested
    @DisplayName("register / registerReadOnly")
    class Registro {

        @Test
        @DisplayName("register adiciona flag editável com valor default")
        void register_addFlag() {
            FeatureFlagService svc = newService(false);

            svc.register("a.enabled", true, "Flag A");

            assertThat(svc.isEnabled("a.enabled")).isTrue();
            assertThat(svc.get("a.enabled")).isPresent();
            assertThat(svc.get("a.enabled").get().readOnly()).isFalse();
            assertThat(svc.get("a.enabled").get().description()).isEqualTo("Flag A");
        }

        @Test
        @DisplayName("registerReadOnly marca flag como read-only")
        void registerReadOnly_addFlag() {
            FeatureFlagService svc = newService(false);

            svc.registerReadOnly("ro.enabled", false, "Read-only");

            assertThat(svc.get("ro.enabled")).isPresent();
            assertThat(svc.get("ro.enabled").get().readOnly()).isTrue();
        }

        @Test
        @DisplayName("register é idempotente e preserva valor existente")
        void register_idempotent_preservesValue() {
            FeatureFlagService svc = newService(false);
            svc.register("a.enabled", true, "Descrição original");
            svc.toggle("a.enabled", false);

            // Registra de novo — não deve sobrescrever o valor
            svc.register("a.enabled", true, "Nova descrição");

            assertThat(svc.isEnabled("a.enabled")).isFalse();
            assertThat(svc.get("a.enabled").get().description()).isEqualTo("Nova descrição");
        }

        @Test
        @DisplayName("registro de múltiplas flags mantém todas")
        void register_multipleFlags() {
            FeatureFlagService svc = newService(false);
            svc.register("a", true, "A");
            svc.register("b", false, "B");
            svc.registerReadOnly("c", true, "C");

            assertThat(svc.list()).hasSize(3);
        }
    }

    // =========================================================================
    // LEITURA
    // =========================================================================

    @Nested
    @DisplayName("Leitura")
    class Leitura {

        @Test
        @DisplayName("isEnabled retorna false para flag desconhecida")
        void isEnabled_unknown_returnsFalse() {
            FeatureFlagService svc = newService(false);
            assertThat(svc.isEnabled("nao.existe")).isFalse();
        }

        @Test
        @DisplayName("list retorna flags ordenadas por chave")
        void list_sortedByKey() {
            FeatureFlagService svc = newService(false);
            svc.register("z", true, "");
            svc.register("a", true, "");
            svc.register("m", true, "");

            // 🔧 FIX: Expressão lambda explícita evita o aviso de Null Type Safety do JDT
            List<String> keys = svc.list().stream().map(state -> state.key()).toList();
            assertThat(keys).containsExactly("a", "m", "z");
        }

        @Test
        @DisplayName("listAsMap inclui enabled, description e readOnly")
        void listAsMap_includesAllFields() {
            FeatureFlagService svc = newService(false);
            svc.register("a", true, "Desc");
            svc.registerReadOnly("b", false, "RO");

            Map<String, Map<String, Object>> map = svc.listAsMap();

            assertThat(map).containsKeys("a", "b");
            assertThat(map.get("a"))
                    .containsEntry("enabled", true)
                    .containsEntry("description", "Desc")
                    .containsEntry("readOnly", false);
            assertThat(map.get("b"))
                    .containsEntry("enabled", false)
                    .containsEntry("readOnly", true);
        }

        @Test
        @DisplayName("get retorna Optional.empty para chave inexistente")
        void get_unknown_returnsEmpty() {
            FeatureFlagService svc = newService(false);
            assertThat(svc.get("nao.existe")).isEmpty();
        }
    }

    // =========================================================================
    // TOGGLE
    // =========================================================================

    @Nested
    @DisplayName("toggle")
    class Toggle {

        @Test
        @DisplayName("toggle alterna valor de flag editável")
        void toggle_editableFlag() {
            FeatureFlagService svc = newService(false);
            svc.register("a", false, "");

            svc.toggle("a", true);

            assertThat(svc.isEnabled("a")).isTrue();
        }

        @Test
        @DisplayName("toggle em flag read-only lança IllegalStateException")
        void toggle_readOnly_throws() {
            FeatureFlagService svc = newService(false);
            svc.registerReadOnly("ro", false, "");

            assertThatThrownBy(() -> svc.toggle("ro", true))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("read-only");
        }

        @Test
        @DisplayName("toggle em flag desconhecida lança IllegalArgumentException")
        void toggle_unknown_throws() {
            FeatureFlagService svc = newService(false);

            assertThatThrownBy(() -> svc.toggle("nao.existe", true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Flag desconhecida");
        }

        @Test
        @DisplayName("toggle no mesmo valor é no-op")
        void toggle_sameValue_noOp() {
            FeatureFlagService svc = newService(false);
            svc.register("a", true, "");

            // Não deve lançar, não deve alterar
            assertThatCode(() -> svc.toggle("a", true)).doesNotThrowAnyException();
            assertThat(svc.isEnabled("a")).isTrue();
        }
    }

    // =========================================================================
    // PERSISTÊNCIA
    // =========================================================================

    @Nested
    @DisplayName("Persistência em disco")
    class Persistencia {

        @Test
        @DisplayName("com persistência ligada, toggle grava arquivo")
        void persistEnabled_toggle_writesFile() throws IOException {
            FeatureFlagService svc = newService(true);
            svc.register("a", false, "Desc A");

            svc.toggle("a", true);

            assertThat(Files.exists(persistPath)).isTrue();
            String json = Files.readString(persistPath);
            assertThat(json).contains("\"a\"").contains("\"enabled\" : true");
        }

        @Test
        @DisplayName("com persistência desligada, toggle NÃO grava arquivo")
        void persistDisabled_toggle_doesNotWriteFile() { // 🔧 FIX: Removido throws IOException
            // desnecessário
            FeatureFlagService svc = newService(false);
            svc.register("a", false, "");

            svc.toggle("a", true);

            assertThat(Files.exists(persistPath)).isFalse();
        }

        @Test
        @DisplayName("escrita atômica não deixa arquivo .tmp para trás")
        void persistEnabled_atomicWrite_noTmpLeftBehind() {
            FeatureFlagService svc = newService(true);
            svc.register("a", false, "");
            svc.toggle("a", true);

            Path tmp = persistPath.resolveSibling(persistPath.getFileName() + ".tmp");
            assertThat(Files.exists(tmp)).isFalse();
        }

        @Test
        @DisplayName("boot com JSON existente sobrescreve defaults")
        void loadFromDisk_overridesDefaults() throws IOException {
            // 1. Prepara o arquivo com valor true
            Files.createDirectories(persistPath.getParent());
            Files.writeString(
                    persistPath,
                    """
                    {
                      "a.enabled" : {
                        "key" : "a.enabled",
                        "enabled" : true,
                        "description" : "Vindo do disco",
                        "readOnly" : false
                      }
                    }
                    """);

            // 2. Cria service com persistência e registra default false
            FeatureFlagService svc = newService(true);
            svc.register("a.enabled", false, "Default");

            // 3. Carrega do disco
            svc.init();

            // 4. O valor do disco deve vencer
            assertThat(svc.isEnabled("a.enabled")).isTrue();
            assertThat(svc.get("a.enabled").get().description()).isEqualTo("Vindo do disco");
        }

        @Test
        @DisplayName("boot com JSON corrompido usa defaults e não quebra")
        void loadFromDisk_corrupted_usesDefaults() throws IOException {
            Files.createDirectories(persistPath.getParent());
            Files.writeString(persistPath, "{ isso não é json válido }");

            FeatureFlagService svc = newService(true);
            svc.register("a", false, "");
            svc.init();

            // Default preservado
            assertThat(svc.isEnabled("a")).isFalse();
        }

        @Test
        @DisplayName("boot sem arquivo usa defaults")
        void loadFromDisk_noFile_usesDefaults() {
            FeatureFlagService svc = newService(true);
            svc.register("a", true, "");
            svc.init();

            assertThat(svc.isEnabled("a")).isTrue();
        }

        @Test
        @DisplayName("flag read-only não é sobrescrita por valor do disco")
        void loadFromDisk_readOnlyIgnored() throws IOException {
            Files.createDirectories(persistPath.getParent());
            Files.writeString(
                    persistPath,
                    """
                    {
                      "ro" : {
                        "key" : "ro",
                        "enabled" : true,
                        "description" : "Do disco",
                        "readOnly" : false
                      }
                    }
                    """);

            FeatureFlagService svc = newService(true);
            svc.registerReadOnly("ro", false, "Read-only");
            svc.init();

            // Valor default (false) preservado, mesmo com disco dizendo true
            assertThat(svc.isEnabled("ro")).isFalse();
        }
    }
}
