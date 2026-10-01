/* (c) 2026 | 01/10/2026 */
package net.ddns.adambravo79.tmill.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.ddns.adambravo79.tmill.service.config.JsonConfigLoader;
import net.ddns.adambravo79.tmill.service.digest.DigestPromptFactory;
import net.ddns.adambravo79.tmill.service.prompt.PromptRegistryService;
import tools.jackson.databind.ObjectMapper;

class DigestPromptParityTest {

    private PromptRegistryService promptRegistry;
    private DigestPromptFactory promptFactory;

    @BeforeEach
    void setUp() {
        JsonConfigLoader jsonConfigLoader = new JsonConfigLoader(new ObjectMapper());
        promptRegistry = new PromptRegistryService(jsonConfigLoader, null, null, null);
        promptFactory = new DigestPromptFactory(promptRegistry);
    }

    @Test
    @DisplayName("Paridade: System Prompt T1000 com contexto MADRUGADA")
    void testT1000MadrugadaParity() {
        String hardcoded = promptFactory.buildSystemPrompt(DigestPersona.T1000, "MADRUGADA");
        String external = promptRegistry.getDigestSystemPrompt("T1000", "MADRUGADA");

        assertNotNull(external);
        assertEquals(normalizeText(hardcoded), normalizeText(external));
    }

    @Test
    @DisplayName("Paridade: System Prompt BICENTENNIAL com contexto DIA")
    void testBicentennialDiaParity() {
        String hardcoded = promptFactory.buildSystemPrompt(DigestPersona.BICENTENNIAL, "DIA");
        String external = promptRegistry.getDigestSystemPrompt("BICENTENNIAL", "DIA");

        assertNotNull(external);
        assertEquals(normalizeText(hardcoded), normalizeText(external));
    }

    @Test
    @DisplayName("Paridade: System Prompt MATRIX_ARCHITECT com contexto padrão")
    void testArchitectDefaultParity() {
        String hardcoded = promptFactory.buildSystemPrompt(DigestPersona.MATRIX_ARCHITECT, null);
        String external = promptRegistry.getDigestSystemPrompt("MATRIX_ARCHITECT", null);

        assertNotNull(external);
        assertEquals(normalizeText(hardcoded), normalizeText(external));
    }

    @Test
    @DisplayName("Paridade: User Prompt Template")
    void testUserPromptParity() {
        String sampleMessages = "Mensagem 1\nMensagem 2";
        String hardcoded = promptFactory.buildUserPrompt(sampleMessages);
        String external = promptRegistry.getDigestUserPrompt(sampleMessages);

        assertNotNull(external);
        assertEquals(normalizeText(hardcoded), normalizeText(external));
    }

    /**
     * Normaliza quebras de linha e remove espaços extras/indentações em cada linha.
     */
    @SuppressWarnings("null")
    private String normalizeText(String input) {
        if (input == null) return "";
        return Arrays.stream(input.replaceAll("\r\n", "\n").split("\n"))
                .map(String::trim)
                .collect(Collectors.joining("\n"))
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }
}
