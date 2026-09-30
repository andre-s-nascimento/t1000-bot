/* (c) 2026 | 30/09/2026 */
package net.ddns.adambravo79.tmill.prompt;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.ddns.adambravo79.tmill.service.config.JsonConfigLoader;
import net.ddns.adambravo79.tmill.service.prompt.PromptRegistryService;
import tools.jackson.databind.ObjectMapper;

class PodcastPromptParityTest {

    private PromptRegistryService promptRegistry;

    @BeforeEach
    void setUp() {
        JsonConfigLoader jsonConfigLoader = new JsonConfigLoader(new ObjectMapper());

        promptRegistry = new PromptRegistryService(jsonConfigLoader, null, null, null);
    }

    @Test
    @DisplayName("Paridade: Podcast System Prompt e Encerramento configurável")
    void testPodcastSystemPromptParity() {
        String podcastSystem = promptRegistry.getPodcastSystemPrompt();

        assertNotNull(podcastSystem);
        assertTrue(podcastSystem.contains("Silas Cast"));
        assertTrue(
                podcastSystem.contains(
                        "E caso eu não veja vocês, bom dia, boa tarde e boa noite!"));
    }
}
