// AutoResponseRuleEntry.java 21/10/2026
package net.ddns.adambravo79.tmill.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Entrada de uma regra de auto-response, desserializada de {@code auto-responses.json}.
 *
 * <p>Suporta três formatos de animação, com precedência decrescente:
 *
 * <ol>
 *   <li>{@code responses} — lista de pares (texto, animação). O sorteio escolhe um par inteiro.
 *   <li>{@code animations} — lista de animações (texto fixo em {@code response}).
 *   <li>{@code animation} — animação única (texto fixo em {@code response}). Formato legado.
 * </ol>
 *
 * <p>Essa precedência é aplicada pelo {@link
 * net.ddns.adambravo79.tmill.service.AutoResponseService} no carregamento, não aqui.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AutoResponseRuleEntry(
        List<String> triggers,
        String response,
        String animation,
        List<String> animations,
        List<ResponseAnimationPair> responses,
        Map<String, String> timeRange,
        Map<String, UserOverride> userOverrides,
        Map<String, String> userResponse,
        Map<String, String> userAnimation) {}
