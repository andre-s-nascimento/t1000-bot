package net.ddns.adambravo79.tmill.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.github.benmanes.caffeine.cache.Cache;

/**
 * Testes do {@link CaffeineConfig}.
 *
 * <p>Verifica que o bean {@code providerCache} é criado, aceita operações básicas e é compatível
 * com a API esperada pelo {@code DailyReleasesService} (mapa chave → valor com expiração).
 */
class CaffeineConfigTest {

    private final CaffeineConfig config = new CaffeineConfig();

    @Test
    @DisplayName("providerCache: cria cache não nulo")
    void providerCache_naoNulo() {
        Cache<String, String> cache = config.providerCache();
        assertThat(cache).isNotNull();
    }

    @Test
    @DisplayName("providerCache: aceita put/get básicos")
    void providerCache_putGet() {
        Cache<String, String> cache = config.providerCache();

        cache.put("movie_123", "Netflix, Prime Video");

        assertThat(cache.getIfPresent("movie_123")).isEqualTo("Netflix, Prime Video");
    }

    @Test
    @DisplayName("providerCache: usa o valor computado quando a chave não existe")
    void providerCache_computaSeAusente() {
        Cache<String, String> cache = config.providerCache();

        String resultado = cache.get("tv_456", k -> "Disney+");

        assertThat(resultado).isEqualTo("Disney+");
        assertThat(cache.getIfPresent("tv_456")).isEqualTo("Disney+");
    }

    @Test
    @DisplayName("providerCache: não recalcula se a chave já existe")
    void providerCache_naoRecalcula() {
        Cache<String, String> cache = config.providerCache();
        cache.put("movie_1", "Netflix");

        // Se a chave existe, o lambda NÃO é executado
        String resultado = cache.get("movie_1", k -> "Outro valor");

        assertThat(resultado).isEqualTo("Netflix");
    }

    @Test
    @DisplayName("providerCache: suporta chaves no formato usado pelo DailyReleasesService")
    void providerCache_formatoDeChave() {
        Cache<String, String> cache = config.providerCache();

        // Formato real: "<tipo>_<tmdbId>" — ex.: "movie_1", "tv_2"
        cache.put("movie_1", "Netflix");
        cache.put("tv_2", "Disney+");

        assertThat(cache.getIfPresent("movie_1")).isEqualTo("Netflix");
        assertThat(cache.getIfPresent("tv_2")).isEqualTo("Disney+");
    }

    @Test
    @DisplayName("providerCache: TTL configurado em 24h")
    void providerCache_ttl() {
        Cache<String, String> cache = config.providerCache();

        long ttlNanos =
                cache.policy().expireAfterWrite().get().getExpiresAfter(TimeUnit.NANOSECONDS);
        long ttlHoras = TimeUnit.NANOSECONDS.toHours(ttlNanos);

        assertThat(ttlHoras).isEqualTo(24);
    }

    @Test
    @DisplayName("providerCache: tamanho máximo configurado em 500")
    void providerCache_maxSize() {
        Cache<String, String> cache = config.providerCache();

        long maxSize = cache.policy().eviction().get().getMaximum();

        assertThat(maxSize).isEqualTo(500);
    }
}
