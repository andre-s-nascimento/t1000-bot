package net.ddns.adambravo79.tmill.service.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import net.ddns.adambravo79.tmill.model.TranscriptionCacheEntry;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileTranscriptionCacheServiceTest {

    @InjectMocks private FileTranscriptionCacheService service;

    // =========================
    // TESTES DE INICIALIZAÇÃO
    // =========================

    @Test
    void startCleanerAndStatsLogger_deveIniciarCleanerEStatsLoggerQuandoCacheAtivado() {
        ReflectionTestUtils.setField(service, "cacheEnabled", true);
        ReflectionTestUtils.setField(service, "ttlSeconds", 86400);

        service.startCleanerAndStatsLogger();

        var cleaner = ReflectionTestUtils.getField(service, "cleaner");
        assertThat(cleaner).isNotNull();
    }

    @Test
    void startCleanerAndStatsLogger_deveLogarQueCacheDesativado() {
        ReflectionTestUtils.setField(service, "cacheEnabled", false);

        // O método apenas loga, não faz nada. Verificamos que não lança exceção.
        assertThat(service).isNotNull();
        service.startCleanerAndStatsLogger();
    }

    // =========================
    // TESTES DE GET
    // =========================

    @Test
    void get_deveRetornarNullQuandoCacheDesativado() {
        ReflectionTestUtils.setField(service, "cacheEnabled", false);

        TranscriptionCacheEntry result = service.get("file-id");

        assertThat(result).isNull();
    }

    @Test
    void get_deveRetornarEntryQuandoCacheHIT() {
        ReflectionTestUtils.setField(service, "cacheEnabled", true);
        String fileId = "file-id";

        service.put(fileId, "bruto", "refinado");

        TranscriptionCacheEntry result = service.get(fileId);

        assertThat(result).isNotNull();
        assertThat(result.textoBruto()).isEqualTo("bruto");
        assertThat(result.textoRefinado()).isEqualTo("refinado");
    }

    @Test
    void get_deveRetornarNullQuandoCacheMISS() {
        ReflectionTestUtils.setField(service, "cacheEnabled", true);

        TranscriptionCacheEntry result = service.get("inexistente");

        assertThat(result).isNull();
    }

    // =========================
    // TESTES DE PUT
    // =========================

    @Test
    void put_deveArmazenarEntryQuandoCacheAtivado() {
        ReflectionTestUtils.setField(service, "cacheEnabled", true);
        String fileId = "file-id";

        service.put(fileId, "bruto", "refinado");

        TranscriptionCacheEntry result = service.get(fileId);
        assertThat(result).isNotNull();
        assertThat(result.textoBruto()).isEqualTo("bruto");
        assertThat(result.textoRefinado()).isEqualTo("refinado");
        assertThat(result.timestamp()).isGreaterThan(0);
    }

    @Test
    void put_deveIgnorarQuandoCacheDesativado() {
        ReflectionTestUtils.setField(service, "cacheEnabled", false);
        String fileId = "file-id";

        service.put(fileId, "bruto", "refinado");

        TranscriptionCacheEntry result = service.get(fileId);
        assertThat(result).isNull();
    }

    // =========================
    // TESTES DE CLEANUP (TTL) - SEM Thread.sleep()
    // =========================

    @Test
    void cleanup_deveRemoverEntradasExpiradas() throws Exception {
        ReflectionTestUtils.setField(service, "cacheEnabled", true);
        ReflectionTestUtils.setField(service, "ttlSeconds", 1);

        String fileId = "file-id";
        service.put(fileId, "bruto", "refinado");

        // Modifica o timestamp para expirado
        Map<String, TranscriptionCacheEntry> cache = getCacheMap();
        TranscriptionCacheEntry entry = cache.get(fileId);
        long expiredTimestamp = System.currentTimeMillis() - 2000;
        cache.put(
                fileId,
                new TranscriptionCacheEntry(
                        entry.textoBruto(), entry.textoRefinado(), expiredTimestamp));

        // Chama o método público de limpeza
        service.cleanExpired();

        TranscriptionCacheEntry result = service.get(fileId);
        assertThat(result).isNull();
        assertThat(cache).doesNotContainKey(fileId);
    }

    // =========================
    // TESTES DE STATS
    // =========================

    @Test
    void getStats_deveRetornarHitsEMisses() {
        ReflectionTestUtils.setField(service, "cacheEnabled", true);
        String fileId = "file-id";

        service.put(fileId, "bruto", "refinado");

        service.get(fileId); // HIT
        service.get("inexistente"); // MISS

        Map<String, Long> stats = service.getStats();
        assertThat(stats)
                .containsEntry("hits", 1L)
                .containsEntry("misses", 1L)
                .containsEntry("size", 1L);
    }

    @Test
    void size_deveRetornarQuantidadeAtual() {
        ReflectionTestUtils.setField(service, "cacheEnabled", true);

        service.put("id1", "bruto1", "refinado1");
        service.put("id2", "bruto2", "refinado2");

        assertThat(service.size()).isEqualTo(2);
    }

    @Test
    void clear_deveRemoverTodosOsDadosEResetarStats() {
        ReflectionTestUtils.setField(service, "cacheEnabled", true);

        service.put("id1", "bruto1", "refinado1");
        service.get("id1"); // hits = 1
        service.get("inexistente"); // misses = 1

        service.clear();

        assertThat(service.size()).isZero();
        Map<String, Long> stats = service.getStats();
        assertThat(stats.get("hits")).isZero();
        assertThat(stats.get("misses")).isZero();
    }

    // =========================
    // TESTE DE CACHE COM ENTRY COMPLETO
    // =========================

    @Test
    void get_deveRetornarEntryCompleto() {
        ReflectionTestUtils.setField(service, "cacheEnabled", true);
        String fileId = "file-id";
        String bruto = "texto bruto longo";
        String refinado = "texto refinado longo";

        service.put(fileId, bruto, refinado);
        TranscriptionCacheEntry entry = service.get(fileId);

        assertThat(entry.textoBruto()).isEqualTo(bruto);
        assertThat(entry.textoRefinado()).isEqualTo(refinado);
        assertThat(entry.timestamp()).isGreaterThan(0);
    }

    // =========================
    // MÉTODO AUXILIAR
    // =========================

    @SuppressWarnings("unchecked")
    private Map<String, TranscriptionCacheEntry> getCacheMap() {
        return (Map<String, TranscriptionCacheEntry>)
                ReflectionTestUtils.getField(service, "cache");
    }
}
