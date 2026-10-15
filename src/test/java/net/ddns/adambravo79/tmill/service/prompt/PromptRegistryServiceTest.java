/* (c) 2026 | 30/09/2026 */
package net.ddns.adambravo79.tmill.service.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.ddns.adambravo79.tmill.service.config.JsonConfigLoader;

class PromptRegistryServiceTest {

    private JsonConfigLoader loader;
    private PromptRegistryService service;

    @BeforeEach
    void setUp() {
        loader = mock(JsonConfigLoader.class);

        doReturn(Optional.of(basePrompts()))
                .when(loader)
                .loadConfig(eq("prompts/digest-personas.json"), eq(Map.class), any());

        Map<String, Object> podcastSystem = new HashMap<>();
        podcastSystem.put("systemPrompt", "System podcast");

        doReturn(Optional.of(podcastSystem))
                .when(loader)
                .loadConfig(eq("prompts/podcast-system.json"), eq(Map.class), any());

        Map<String, Object> podcastConfig = new HashMap<>();
        Map<String, Object> podcastRules = new HashMap<>();
        podcastRules.put("closingLine", "Encerramento configurado");
        podcastConfig.put("rules", podcastRules);

        doReturn(Optional.of(podcastConfig))
                .when(loader)
                .loadConfig(eq("prompts/podcast-config.json"), eq(Map.class), any());

        service = new PromptRegistryService(loader, null, null, null);
    }

    @Test
    @DisplayName("Deve carregar, expor e recarregar todos os prompts")
    void shouldLoadExposeAndReloadPrompts() {
        assertThat(service.getAllPrompts())
                .containsKeys("personas", "periodContexts", "userPromptTemplate");

        service.reload();

        verify(loader, times(2))
                .loadConfig(
                        "prompts/digest-personas.json",
                        Map.class,
                        "config/prompts/digest-personas.json");
    }

    @Test
    @DisplayName("loadPrompts deve limpar o cache quando a fonte não retornar configuração")
    void shouldClearCacheWhenConfigIsMissing() {
        doReturn(Optional.empty())
                .when(loader)
                .loadConfig(eq("prompts/digest-personas.json"), eq(Map.class), any());

        service.reload();

        assertThat(service.getAllPrompts()).isEmpty();
    }

    @Test
    @DisplayName("getAllPrompts deve retornar mapa somente para leitura")
    void shouldReturnUnmodifiablePromptMap() {
        Map<String, Object> prompts = service.getAllPrompts();

        assertThatThrownBy(() -> prompts.put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("getPrompt deve retornar valor quando a chave e o tipo forem compatíveis")
    void shouldGetPromptWhenKeyAndTypeMatch() {
        assertThat(service.getPrompt("userPromptTemplate", String.class)).contains("Mensagens: %s");
    }

    @Test
    @DisplayName("getPrompt deve retornar vazio para chave inexistente")
    void shouldReturnEmptyForMissingPrompt() {
        assertThat(service.getPrompt("does-not-exist", String.class)).isEmpty();
    }

    @Test
    @DisplayName("getPrompt deve retornar vazio quando o tipo solicitado não for compatível")
    void shouldReturnEmptyForWrongPromptType() {
        assertThat(service.getPrompt("userPromptTemplate", Integer.class)).isEmpty();
    }

    @Test
    @DisplayName("Digest deve combinar persona com contexto da madrugada")
    void shouldBuildDigestPromptWithMadrugadaContext() {
        String result = service.getDigestSystemPrompt("T1000", "MADRUGADA");

        assertThat(result).isEqualTo("Persona T1000\n\n\nContexto madrugada");
    }

    @Test
    @DisplayName("Digest deve usar contexto DEFAULT para período que não seja madrugada")
    void shouldBuildDigestPromptWithDefaultContext() {
        String result = service.getDigestSystemPrompt("T1000", "MANHÃ");

        assertThat(result).isEqualTo("Persona T1000\n\n\nContexto padrão");
    }

    @Test
    @DisplayName("Digest deve retornar apenas a persona quando o contexto estiver em branco")
    void shouldReturnOnlyPersonaWhenContextIsBlank() {
        Map<String, Object> prompts = basePrompts();

        @SuppressWarnings("unchecked")
        Map<String, Object> contexts = (Map<String, Object>) prompts.get("periodContexts");

        contexts.put("DEFAULT", "");

        stubPrompts(prompts);

        assertThat(service.getDigestSystemPrompt("T1000", "MANHÃ")).isEqualTo("Persona T1000");
    }

    @Test
    @DisplayName("Digest deve funcionar sem o mapa de personas")
    void shouldHandleMissingPersonasMap() {
        Map<String, Object> prompts = basePrompts();
        prompts.remove("personas");

        stubPrompts(prompts);

        assertThat(service.getDigestSystemPrompt("T1000", "MANHÃ"))
                .isEqualTo("\n\n\nContexto padrão");
    }

    @Test
    @DisplayName("Digest deve retornar contexto quando a persona não existir")
    void shouldReturnContextWhenPersonaDoesNotExist() {
        String result = service.getDigestSystemPrompt("UNKNOWN", "MANHÃ");

        assertThat(result).isEqualTo("\n\n\nContexto padrão");
    }

    @Test
    @DisplayName("Digest user prompt deve substituir o placeholder do template")
    void shouldBuildDigestUserPrompt() {
        assertThat(service.getDigestUserPrompt("A\nB")).isEqualTo("Mensagens: A\nB");
    }

    @Test
    @DisplayName("Digest user prompt deve retornar as mensagens quando não houver template")
    void shouldReturnMessagesWhenUserPromptTemplateIsMissing() {
        Map<String, Object> prompts = basePrompts();
        prompts.remove("userPromptTemplate");

        stubPrompts(prompts);

        assertThat(service.getDigestUserPrompt("A\nB")).isEqualTo("A\nB");
    }

    @Test
    @DisplayName("Deve retornar apenas a persona quando periodContexts não estiver carregado")
    void shouldReturnOnlyPersonaWhenPeriodContextsAreMissing() {
        Map<String, Object> prompts = basePrompts();
        prompts.remove("periodContexts");

        stubPrompts(prompts);

        assertThat(service.getDigestSystemPrompt("T1000", "MADRUGADA")).isEqualTo("Persona T1000");
    }

    @Test
    @DisplayName("Podcast deve combinar system prompt e closing line configurável")
    void shouldBuildPodcastPromptWithConfiguredClosingLine() {
        assertThat(service.getPodcastSystemPrompt())
                .isEqualTo("System podcast\n" + "- Encerre com: \"Encerramento configurado\"");
    }

    @Test
    @DisplayName("Podcast deve usar valores padrão quando as configurações não existirem")
    void shouldUsePodcastDefaultsWhenConfigsAreMissing() {
        doReturn(Optional.empty())
                .when(loader)
                .loadConfig(eq("prompts/podcast-system.json"), eq(Map.class), any());

        doReturn(Optional.empty())
                .when(loader)
                .loadConfig(eq("prompts/podcast-config.json"), eq(Map.class), any());

        assertThat(service.getPodcastSystemPrompt())
                .isEqualTo(
                        "\n"
                                + "- Encerre com: "
                                + "\"E caso eu não veja vocês, bom dia, boa tarde e boa noite!\"");
    }

    @Test
    @DisplayName("Podcast deve aceitar rules nulo e manter o encerramento padrão")
    void shouldUseDefaultClosingLineWhenRulesAreNull() {
        Map<String, Object> config = new HashMap<>();
        config.put("rules", null);

        doReturn(Optional.of(config))
                .when(loader)
                .loadConfig(eq("prompts/podcast-config.json"), eq(Map.class), any());

        assertThat(service.getPodcastSystemPrompt())
                .contains("E caso eu não veja vocês, bom dia, boa tarde e boa noite!");
    }

    @Test
    @DisplayName("Podcast deve aceitar closingLine ausente e manter o encerramento padrão")
    void shouldUseDefaultClosingLineWhenClosingLineIsMissing() {
        Map<String, Object> config = new HashMap<>();
        config.put("rules", new HashMap<String, Object>());

        doReturn(Optional.of(config))
                .when(loader)
                .loadConfig(eq("prompts/podcast-config.json"), eq(Map.class), any());

        assertThat(service.getPodcastSystemPrompt())
                .contains("E caso eu não veja vocês, bom dia, boa tarde e boa noite!");
    }

    private void stubPrompts(Map<String, Object> prompts) {
        doReturn(Optional.of(prompts))
                .when(loader)
                .loadConfig(eq("prompts/digest-personas.json"), eq(Map.class), any());

        service.reload();
    }

    // =========================================================================
    // getActivePersonaName
    // =========================================================================

    @Test
    @DisplayName("getActivePersonaName deve retornar a persona configurada no JSON quando válida")
    void shouldReturnActivePersonaWhenConfiguredAndValid() {
        Map<String, Object> prompts = basePrompts();
        prompts.put("activePersona", "T1000");
        stubPrompts(prompts);

        assertThat(service.getActivePersonaName()).isEqualTo("T1000");
    }

    @Test
    @DisplayName(
            "getActivePersonaName deve retornar o fallback 'T1000' quando a chave estiver"
                    + " ausente")
    void shouldReturnFallbackT1000WhenActivePersonaIsMissing() {
        Map<String, Object> prompts = basePrompts();
        prompts.remove("activePersona");
        stubPrompts(prompts);

        assertThat(service.getActivePersonaName()).isEqualTo("T1000");
    }

    @Test
    @DisplayName(
            "getActivePersonaName deve retornar o fallback 'T1000' quando a persona estiver em"
                    + " branco ou não for String")
    void shouldReturnFallbackT1000WhenActivePersonaIsInvalidOrBlank() {
        // Cenário 1: String em branco
        Map<String, Object> prompts = basePrompts();
        prompts.put("activePersona", "   ");
        stubPrompts(prompts);

        assertThat(service.getActivePersonaName()).isEqualTo("T1000");

        // Cenário 2: Tipo incompatível (ex: Integer)
        prompts.put("activePersona", 12345);
        stubPrompts(prompts);

        assertThat(service.getActivePersonaName()).isEqualTo("T1000");
    }

    // =========================================================================
    // getPodcastUserPrompt
    // =========================================================================

    @Test
    @DisplayName("Podcast user prompt deve combinar o template configurado com as mensagens")
    void shouldBuildPodcastUserPromptWithConfiguredTemplate() {
        Map<String, Object> podcastSystem = new HashMap<>();
        podcastSystem.put("userPrompt", "Prefácio customizado:\n\n");

        doReturn(Optional.of(podcastSystem))
                .when(loader)
                .loadConfig(eq("prompts/podcast-system.json"), eq(Map.class), any());

        String result = service.getPodcastUserPrompt("Áudio 1\nÁudio 2");

        assertThat(result).isEqualTo("Prefácio customizado:\n\nÁudio 1\nÁudio 2");
    }

    @Test
    @DisplayName(
            "Podcast user prompt deve usar o fallback padrão se a chave userPrompt não existir")
    void shouldBuildPodcastUserPromptWithFallbackWhenConfigIsMissing() {
        doReturn(Optional.empty())
                .when(loader)
                .loadConfig(eq("prompts/podcast-system.json"), eq(Map.class), any());

        String result = service.getPodcastUserPrompt("Mensagem Teste");

        assertThat(result)
                .isEqualTo("Aqui estão as mensagens da semana passada:\n\nMensagem Teste");
    }

    private static Map<String, Object> basePrompts() {
        Map<String, Object> persona = new HashMap<>();
        persona.put("systemPrompt", "Persona T1000");

        Map<String, Object> personas = new HashMap<>();
        personas.put("T1000", persona);

        Map<String, Object> contexts = new HashMap<>();
        contexts.put("MADRUGADA", "Contexto madrugada");
        contexts.put("DEFAULT", "Contexto padrão");

        Map<String, Object> prompts = new HashMap<>();
        prompts.put("personas", personas);
        prompts.put("periodContexts", contexts);
        prompts.put("userPromptTemplate", "Mensagens: %s");

        return prompts;
    }

    @Test
    @DisplayName("getPrompt deve lidar com tipos e chaves inválidas corretamente")
    void shouldHandleGetPromptEdgeCases() {
        // Chave inexistente
        assertThat(service.getPrompt("inexistente", String.class)).isEmpty();

        // Tipo incorreto
        assertThat(service.getPrompt("userPromptTemplate", Integer.class)).isEmpty();
    }
}
