/* (c) 2026 | 21/10/2026 */
package net.ddns.adambravo79.tmill.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Par (texto de resposta, URL de animação) para regras de auto-response.
 *
 * <p>Permite que uma regra tenha múltiplas respostas, cada uma com sua própria animação. No
 * momento do disparo, o {@link net.ddns.adambravo79.tmill.service.AutoResponseService} sorteia um
 * par inteiro (não mistura texto de um com animação de outro).
 *
 * <p>Ambos os campos podem ser {@code null}:
 *
 * <ul>
 *   <li>{@code text} nulo → a regra cai no {@code response} padrão (se existir)
 *   <li>{@code animation} nulo → envia apenas texto (sem mídia)
 * </ul>
 *
 * <p>O record é imutável e seus campos são {@link String} (também imutáveis), então não há risco de
 * mutação compartilhada entre threads.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResponseAnimationPair(String text, String animation) {}
