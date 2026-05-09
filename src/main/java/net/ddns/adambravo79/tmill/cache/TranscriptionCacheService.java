/* (c) 2026 | 09/05/2026 */
package net.ddns.adambravo79.tmill.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    @Value("${cache.transcription.enabled:true}")
    private boolean cacheEnabled;

    @Value("${cache.transcription.ttl-seconds:86400}") // 24 horas padrão
    private long ttlSeconds;

    @PostConstruct
    public void startCleaner() {
        if (cacheEnabled) {
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
            log.info("Cache de transcrições ativado (TTL: {} segundos)", ttlSeconds);
        } else {
            log.info("Cache de transcrições desativado");
        }
    }

    public TranscriptionCacheEntry get(String fileId) {
        if (!cacheEnabled) return null;
        TranscriptionCacheEntry entry = cache.get(fileId);
        if (entry != null) {
            log.debug("Cache HIT para fileId={}", fileId);
            return entry;
        }
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

    public int size() {
        return cache.size();
    }

    public void clear() {
        cache.clear();
        log.info("Cache de transcrições limpo manualmente");
    }
}
