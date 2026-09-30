package net.ddns.adambravo79.tmill.config;

import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Configuração de caches Caffeine da aplicação.
 *
 * <p>Centralizar aqui permite:
 *
 * <ul>
 *   <li>Trocar a política de expiração/tamanho sem tocar no código de negócio.
 *   <li>Mockar o cache nos testes via {@code @Mock}, já que ele passa a ser injetado por
 *       construtor.
 *   <li>Reutilizar o mesmo cache em vários serviços, se necessário.
 * </ul>
 *
 * <p>O cache de provedores de streaming é usado pelo {@link
 * net.ddns.adambravo79.tmill.service.DailyReleasesService} para evitar chamadas repetidas ao
 * Watchmode dentro da mesma execução (a cada 6 horas).
 */
@Configuration
public class CaffeineConfig {

    /**
     * Cache de provedores de streaming por {@code (tipo, tmdbId)} → string de provedores.
     *
     * <p>TTL de 24h e máximo de 500 entradas — mais que suficiente para o volume de lançamentos
     * diário e evita pressão de memória.
     */
    @Bean
    public Cache<String, String> providerCache() {
        return Caffeine.newBuilder().expireAfterWrite(24, TimeUnit.HOURS).maximumSize(500).build();
    }
}
