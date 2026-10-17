package net.ddns.adambravo79.tmill.config;

import java.util.concurrent.TimeUnit;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Configuração de caches Caffeine da aplicação.
 *
 * <p>Habilita {@code @EnableCaching} e cria:
 *
 * <ul>
 *   <li>{@link #cacheManager()} — gerenciador de caches do Spring (usado pelo
 *       {@code MovieService} para "movieDetails" e pelo {@code WatchmodeClient}
 *       para "watchmode_providers").
 *   <li>{@link #providerCache()} — cache manual chave-valor (usado pelo
 *       {@code DailyReleasesService}).
 * </ul>
 */
@Configuration
@EnableCaching
public class CaffeineConfig {

    /** Nome do cache de detalhes de filme. */
    public static final String MOVIE_DETAILS_CACHE = "movieDetails";

    /**
     * 🔧 FIX (Correção 3): nome do cache usado pelo {@code WatchmodeClient.@Cacheable}.
     *
     * <p>Antes, o {@code CaffeineCacheManager} só registrava {@code movieDetails}, e a chamada
     * {@code @Cacheable(value="watchmode_providers")} lançava
     * {@code IllegalArgumentException: Cannot find cache named 'watchmode_providers'} a cada
     * execução do {@code DailyReleasesService}, quebrando a feature de lançamentos.
     */
    public static final String WATCHMODE_PROVIDERS_CACHE = "watchmode_providers";

    /**
     * Gerenciador de caches do Spring baseado em Caffeine.
     *
     * <p>Registra os caches conhecidos com limite e TTL. O {@code recordStats()} é essencial para
     * que o Micrometer/Prometheus consiga expor métricas de hit/miss/eviction do cache.
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager =
                new CaffeineCacheManager(MOVIE_DETAILS_CACHE, WATCHMODE_PROVIDERS_CACHE);
        manager.setCaffeine(
                Caffeine.newBuilder()
                        .recordStats()
                        .maximumSize(1000)
                        .expireAfterWrite(1, TimeUnit.HOURS));
        return manager;
    }

    /**
     * Cache manual (chave-valor) para provedores de streaming.
     *
     * <p>Usado pelo {@code DailyReleasesService} para evitar chamadas repetidas ao Watchmode
     * dentro da mesma execução (a cada 6h).
     *
     * <p><b>Nota (Correção 3):</b> este cache manual é <b>redundante</b> com o cache
     * {@code @Cacheable} do {@code WatchmodeClient}. Ambos foram mantidos para não quebrar
     * compatibilidade, mas a recomendação é remover um dos dois em refactor futuro.
     */
    @Bean
    public Cache<String, String> providerCache() {
        return Caffeine.newBuilder().expireAfterWrite(24, TimeUnit.HOURS).maximumSize(500).build();
    }
}
