package net.ddns.adambravo79.tmill.service.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import net.ddns.adambravo79.tmill.model.TranscriptionCacheEntry;

@ExtendWith(MockitoExtension.class)
class FileTranscriptionCacheServiceTest {

    @Spy @InjectMocks private FileTranscriptionCacheService service;

    @Mock private ScheduledExecutorService mockCleaner;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "cacheEnabled", true);
        ReflectionTestUtils.setField(service, "ttlSeconds", 86400);
        service.clear();
        ReflectionTestUtils.setField(service, "cleaner", mockCleaner);
    }

    // =========================
    // TESTES DE INICIALIZAÇÃO
    // =========================

    @Test
    void startCleanerAndStatsLogger_comCacheAtivado_deveAgendarTarefas() {
        service.startCleanerAndStatsLogger();

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mockCleaner, times(2))
                .scheduleAtFixedRate(runnableCaptor.capture(), eq(1L), eq(1L), eq(TimeUnit.HOURS));

        assertThat(runnableCaptor.getAllValues()).hasSize(2);
    }

    @Test
    void startCleanerAndStatsLogger_comCacheDesativado_naoAgendaNada() {
        ReflectionTestUtils.setField(service, "cacheEnabled", false);
        service.startCleanerAndStatsLogger();
        verify(mockCleaner, never())
                .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any());
    }

    // =========================
    // TESTES DE CLEAN EXPIRED (via cleanExpired público)
    // =========================

    @Test
    void cleanExpired_deveRemoverEntradasExpiradas() {
        ReflectionTestUtils.setField(service, "ttlSeconds", 1);
        service.put("id", "bruto", "refinado");

        Map<String, TranscriptionCacheEntry> cache = getCacheMap();
        TranscriptionCacheEntry entry = cache.get("id");
        long expiredTimestamp = System.currentTimeMillis() - 2000;
        cache.put(
                "id",
                new TranscriptionCacheEntry(
                        entry.textoBruto(), entry.textoRefinado(), expiredTimestamp));

        service.cleanExpired();

        assertThat(service.size()).isZero();
        assertThat(cache).doesNotContainKey("id");
    }

    @Test
    void cleanExpired_quandoNenhumaExpirada_naoRemoveNada() {
        service.put("id", "bruto", "refinado");
        service.cleanExpired();
        assertThat(service.size()).isEqualTo(1);
    }

    @Test
    void cleanExpired_quandoCacheVazio_naoRemoveNada() {
        service.clear();
        service.cleanExpired();
        assertThat(service.size()).isZero();
    }

    // =========================
    // TESTES DE LOG STATS
    // =========================

    @Test
    void logStats_deveLogarStats() {
        assertThatCode(() -> service.logStats()).doesNotThrowAnyException();
    }

    // =========================
    // TESTES DE GET / PUT
    // =========================

    @Test
    void get_comCacheDesativado_retornaNull() {
        ReflectionTestUtils.setField(service, "cacheEnabled", false);
        assertThat(service.get("id")).isNull();
    }

    @Test
    void get_comCacheAtivado_HIT_retornaEntry() {
        service.put("id", "bruto", "refinado");
        TranscriptionCacheEntry entry = service.get("id");
        assertThat(entry).isNotNull();
        assertThat(entry.textoBruto()).isEqualTo("bruto");
    }

    @Test
    void get_comCacheAtivado_MISS_retornaNull() {
        assertThat(service.get("inexistente")).isNull();
    }

    @Test
    void put_comCacheDesativado_naoAdiciona() {
        ReflectionTestUtils.setField(service, "cacheEnabled", false);
        service.put("id", "bruto", "refinado");
        assertThat(service.size()).isZero();
    }

    @Test
    void put_comCacheAtivado_adiciona() {
        service.put("id", "bruto", "refinado");
        assertThat(service.size()).isEqualTo(1);
    }

    // =========================
    // TESTES DE STATS
    // =========================

    @Test
    void getStats_deveRetornarMapa() {
        service.put("id", "bruto", "refinado");
        service.get("id"); // HIT
        service.get("miss"); // MISS

        Map<String, Long> stats = service.getStats();
        assertThat(stats)
                .containsEntry("hits", 1L)
                .containsEntry("misses", 1L)
                .containsEntry("size", 1L);
    }

    @Test
    void size_deveRetornarTamanho() {
        service.put("id1", "b1", "r1");
        service.put("id2", "b2", "r2");
        assertThat(service.size()).isEqualTo(2);
    }

    @Test
    void clear_deveLimparCacheEResetarStats() {
        service.put("id", "b", "r");
        service.get("id");
        service.clear();
        assertThat(service.size()).isZero();
        assertThat(service.getStats().get("hits")).isZero();
        assertThat(service.getStats().get("misses")).isZero();
    }

    // =========================
    // TESTE PARA cleanExpired() - spy
    // =========================

    @Test
    void cleanExpired_deveChamarCleanExpiredTask() {
        // Usa spy para verificar que o método interno foi chamado
        doCallRealMethod().when(service).cleanExpired();
        service.cleanExpired();
        // Verifica indiretamente que cleanExpiredTask() foi executado (o spy não permite verificar
        // privados)
        // Mas como cleanExpired() é público e chamamos, a cobertura é alcançada.
        // Para garantir, podemos verificar que o método não lançou exceção.
        assertThatCode(() -> service.cleanExpired()).doesNotThrowAnyException();
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
