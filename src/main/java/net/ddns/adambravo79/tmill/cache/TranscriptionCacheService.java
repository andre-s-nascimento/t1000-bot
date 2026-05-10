/* (c) 2026 | 09/05/2026 */
package net.ddns.adambravo79.tmill.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TranscriptionCacheService {

    private final ConcurrentHashMap<String, TranscriptionCacheEntry> cache =
            new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);

    @Value("${cache.transcription.enabled:true}")
    private boolean cacheEnabled;

    @Value("${cache.transcription.ttl-seconds:86400}")
    private long ttlSeconds;

    @PostConstruct
    public void startCleanerAndStatsLogger() {
        if (cacheEnabled) {
            // Cleaner (remove entradas expiradas)
            cleaner.scheduleAtFixedRate(
                    () -> {
                        long now = System.currentTimeMillis();
                        int before = cache.size();
                        cache.entrySet()
                                .removeIf(
                                        entry ->
                                                now - entry.getValue().getTimestamp()
                                                        > ttlSeconds * 1000);
                        int after = cache.size();
                        if (before != after) {
                            log.debug(
                                    "Cache de transcrições limpo: {} entradas removidas, {}"
                                            + " restantes",
                                    before - after,
                                    after);
                        }
                    },
                    1,
                    1,
                    TimeUnit.HOURS);

            // Stats logger (a cada hora)
            cleaner.scheduleAtFixedRate(
                    () -> {
                        log.info(
                                "Cache stats: hits={}, misses={}, size={}",
                                hits.get(),
                                misses.get(),
                                cache.size());
                    },
                    1,
                    1,
                    TimeUnit.HOURS);

            log.info("Cache de transcrições ativado (TTL: {} segundos)", ttlSeconds);
        } else {
            log.info("Cache de transcrições desativado");
        }
    }

    public TranscriptionCacheEntry get(String fileId) {
        if (!cacheEnabled) return null;
        TranscriptionCacheEntry entry = cache.get(fileId);
        if (entry != null) {
            hits.incrementAndGet();
            log.debug("Cache HIT para fileId={}", fileId);
            return entry;
        }
        misses.incrementAndGet();
        log.debug("Cache MISS para fileId={}", fileId);
        return null;
    }

    public void put(String fileId, String textoBruto, String textoRefinado) {
        if (!cacheEnabled) return;
        cache.put(
                fileId,
                new TranscriptionCacheEntry(textoBruto, textoRefinado, System.currentTimeMillis()));
        log.info("Cache atualizado para fileId={} (tamanho atual: {})", fileId, cache.size());
    }

    public Map<String, Long> getStats() {
        return Map.of("hits", hits.get(), "misses", misses.get(), "size", (long) cache.size());
    }

    public int size() {
        return cache.size();
    }

    public void clear() {
        cache.clear();
        hits.set(0);
        misses.set(0);
        log.info("Cache de transcrições limpo manualmente");
    }
}
