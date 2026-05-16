/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.telegram.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável por registrar métricas de sucesso e erro em operações do bot.
 *
 * <p>Características: - Armazena contadores em memória usando {@link ConcurrentHashMap} e {@link
 * AtomicLong}. - Permite incrementos concorrentes de forma segura. - Fornece métodos para recuperar
 * métricas por chave.
 *
 * <p>Usado para monitoramento interno e diagnóstico de falhas.
 */
@Slf4j
@Service
public class MetricsService {

    private final ConcurrentHashMap<String, AtomicLong> success = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> error = new ConcurrentHashMap<>();

    /**
     * Incrementa o contador de sucesso para uma chave específica.
     *
     * @param key identificador da operação ou contexto.
     */
    public void success(String key) {
        success.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
    }

    /**
     * Incrementa o contador de erro para uma chave específica.
     *
     * @param key identificador da operação ou contexto.
     */
    public void error(String key) {
        error.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
    }

    /**
     * Recupera o número de sucessos registrados para uma chave.
     *
     * @param key identificador da operação ou contexto.
     * @return número de sucessos registrados.
     */
    public long getSuccess(String key) {
        return success.getOrDefault(key, new AtomicLong(0)).get();
    }

    /**
     * Recupera o número de erros registrados para uma chave.
     *
     * @param key identificador da operação ou contexto.
     * @return número de erros registrados.
     */
    public long getError(String key) {
        return error.getOrDefault(key, new AtomicLong(0)).get();
    }
}
