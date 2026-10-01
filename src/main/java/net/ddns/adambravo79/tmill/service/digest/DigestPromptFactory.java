/* (c) 2026 | 01/10/2026 */
package net.ddns.adambravo79.tmill.service.digest;

import org.springframework.stereotype.Component;

import net.ddns.adambravo79.tmill.prompt.DigestPersona;
import net.ddns.adambravo79.tmill.service.prompt.PromptRegistryService;

@Component
public class DigestPromptFactory {

    private final PromptRegistryService promptRegistryService;

    public DigestPromptFactory(PromptRegistryService promptRegistryService) {
        this.promptRegistryService = promptRegistryService;
    }

    /**
     * Obtém o System Prompt configurado para uma instância de DigestPersona e o período indicados.
     */
    public String buildSystemPrompt(DigestPersona persona, String periodLabel) {
        String personaName = (persona != null) ? persona.getId() : "ANALISTA";
        return buildSystemPrompt(personaName, periodLabel);
    }

    /**
     * Obtém o System Prompt configurado para o nome/ID da persona e o período indicados.
     */
    public String buildSystemPrompt(String personaName, String periodLabel) {
        if (personaName == null || personaName.isBlank()) {
            return promptRegistryService.getDigestSystemPrompt("ANALISTA", periodLabel);
        }
        return promptRegistryService.getDigestSystemPrompt(personaName, periodLabel);
    }

    /**
     * Obtém o User Prompt formatado aplicando o conteúdo das mensagens ao template configurado.
     */
    public String buildUserPrompt(String newsContent) {
        String content = (newsContent != null) ? newsContent : "";
        return promptRegistryService.getDigestUserPrompt(content);
    }
}
