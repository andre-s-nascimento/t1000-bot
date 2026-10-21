/* (c) 2026 | 21/10/2026 */
package net.ddns.adambravo79.tmill.model;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Regra de auto-response já processada e pronta para uso em runtime.
 *
 * <p>Mantém duas visões das animações, resolvidas pelo {@link
 * net.ddns.adambravo79.tmill.service.AutoResponseService} no carregamento:
 *
 * <ul>
 *   <li>{@link #responsePairs()} — pares (texto, animação). Quando não vazia, tem precedência: o
 *       sorteio escolhe um par inteiro, e {@code response}/{@code animacoes} são ignorados.
 *   <li>{@link #animacoes()} — lista de animações avulsas, todas compartilhando o mesmo texto
 *       {@link #response()}. Usada quando {@code responsePairs} está vazia (formato
 *       {@code animations} ou {@code animation} legado).
 * </ul>
 *
 * <p>A precedência entre os dois campos é aplicada no momento do disparo ({@link
 * net.ddns.adambravo79.tmill.service.AutoResponseService#getResponseRule}), não aqui.
 *
 * <p>O record é imutável. As listas são construídas com {@code List.copyOf} pelo serviço antes de
 * serem passadas, garantindo que não há mutação compartilhada entre threads.
 */
public record AutoResponseRule(
        String response,
        String animation,
        List<String> animacoes,
        List<ResponseAnimationPair> responsePairs,
        LocalTime startTime,
        LocalTime endTime,
        Map<String, AutoResponseOverride> userOverrides) {}
