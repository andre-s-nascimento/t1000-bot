/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.cache;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Cache em memória que guarda a última transcrição refinada por chatId.
 *
 * <p>Características: - Não persiste em disco (estado se perde se o bot reiniciar). - Não possui
 * TTL ou políticas complexas de expiração. - Simples e intencional para atender ao caso de uso do
 * bot.
 *
 * <p>Usado pelo {@link net.ddns.adambravo79.tmill.controller.TelegramController} para recuperar
 * transcrições quando o usuário interage com botões de ação.
 */
@Slf4j
@Component
public class TranscricaoCache {

    // Mapeamento: chatId → texto refinado da última transcrição
    private final ConcurrentHashMap<Long, String> cache = new ConcurrentHashMap<>();

    /**
     * Salva a transcrição refinada associada a um chatId.
     *
     * @param chatId identificador único do chat.
     * @param textoRefinado texto refinado da transcrição.
     */
    public void salvar(long chatId, String textoRefinado) {
        cache.put(chatId, textoRefinado);
        log.debug("Cache: Transcrição salva para chatId={}", chatId);
    }

    /**
     * Recupera a transcrição refinada associada a um chatId.
     *
     * @param chatId identificador único do chat.
     * @return texto refinado ou {@code null} se não existir.
     */
    public String recuperar(long chatId) {
        return cache.get(chatId);
    }

    /**
     * Remove a transcrição associada a um chatId.
     *
     * @param chatId identificador único do chat.
     */
    public void remover(long chatId) {
        cache.remove(chatId);
        log.debug("Cache: Transcrição removida para chatId={}", chatId);
    }

    /**
     * Verifica se existe uma transcrição associada a um chatId.
     *
     * @param chatId identificador único do chat.
     * @return {@code true} se existir, {@code false} caso contrário.
     */
    public boolean existe(long chatId) {
        return cache.containsKey(chatId);
    }
}
