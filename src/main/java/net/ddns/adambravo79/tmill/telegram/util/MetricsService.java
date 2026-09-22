package net.ddns.adambravo79.tmill.telegram.util;

import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Serviço responsável por registrar métricas no Prometheus. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final MeterRegistry meterRegistry;

    /**
     * Incrementa o contador de sucesso para uma chave específica no Prometheus.
     *
     * @param key identificador da operação ou contexto.
     */
    public void success(String key) {
        meterRegistry
                .counter("t1000.operations.total", "operation", key, "status", "success")
                .increment();
        log.debug("Métrica de sucesso enviada para a operação: {}", key);
    }

    /**
     * Incrementa o contador de erro para uma chave específica no Prometheus.
     *
     * @param key identificador da operação ou contexto.
     */
    public void error(String key) {
        meterRegistry
                .counter("t1000.operations.total", "operation", key, "status", "error")
                .increment();
        log.debug("Métrica de erro enviada para a operação: {}", key);
    }
}
