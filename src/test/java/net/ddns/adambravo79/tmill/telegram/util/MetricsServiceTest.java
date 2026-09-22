package net.ddns.adambravo79.tmill.telegram.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Testes do MetricsService.
 *
 * <p>Usa {@link SimpleMeterRegistry} (registry in-memory real) em vez de mock, para validar o
 * comportamento efetivo dos contadores: criação, acúmulo, separação por tags e independência entre
 * operações.
 */
class MetricsServiceTest {

    private MeterRegistry meterRegistry;
    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new MetricsService(meterRegistry);
    }

    // =========================
    // SUCESSO
    // =========================

    @Test
    @DisplayName("success deve criar contador com tags operation e status=success")
    void success_deveCriarContadorComTags() {
        metricsService.success("comando_buscar");

        Counter counter =
                meterRegistry
                        .find("t1000.operations.total")
                        .tag("operation", "comando_buscar")
                        .tag("status", "success")
                        .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("success múltiplas vezes deve acumular no mesmo contador")
    void success_multiplasVezes_acumulaContador() {
        metricsService.success("comando_buscar");
        metricsService.success("comando_buscar");
        metricsService.success("comando_buscar");

        Counter counter =
                meterRegistry
                        .find("t1000.operations.total")
                        .tag("operation", "comando_buscar")
                        .tag("status", "success")
                        .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("success com chaves diferentes deve criar contadores diferentes")
    void success_chavesDiferentes_criamContadoresDiferentes() {
        metricsService.success("op_a");
        metricsService.success("op_b");
        metricsService.success("op_a");

        Counter a =
                meterRegistry
                        .find("t1000.operations.total")
                        .tag("operation", "op_a")
                        .tag("status", "success")
                        .counter();
        Counter b =
                meterRegistry
                        .find("t1000.operations.total")
                        .tag("operation", "op_b")
                        .tag("status", "success")
                        .counter();

        assertThat(a).isNotNull();
        assertThat(b).isNotNull();
        assertThat(a.count()).isEqualTo(2.0);
        assertThat(b.count()).isEqualTo(1.0);
    }

    // =========================
    // ERRO
    // =========================

    @Test
    @DisplayName("error deve criar contador com tags operation e status=error")
    void error_deveCriarContadorComTags() {
        metricsService.error("audio_transcricao");

        Counter counter =
                meterRegistry
                        .find("t1000.operations.total")
                        .tag("operation", "audio_transcricao")
                        .tag("status", "error")
                        .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("error múltiplas vezes deve acumular no mesmo contador")
    void error_multiplasVezes_acumulaContador() {
        metricsService.error("audio_transcricao");
        metricsService.error("audio_transcricao");

        Counter counter =
                meterRegistry
                        .find("t1000.operations.total")
                        .tag("operation", "audio_transcricao")
                        .tag("status", "error")
                        .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2.0);
    }

    // =========================
    // SUCESSO vs ERRO (tags diferentes)
    // =========================

    @Test
    @DisplayName("success e error com mesma operation criam contadores distintos")
    void successEerror_mesmaOperation_criamContadoresDistintos() {
        metricsService.success("op");
        metricsService.error("op");
        metricsService.success("op");

        Counter success =
                meterRegistry
                        .find("t1000.operations.total")
                        .tag("operation", "op")
                        .tag("status", "success")
                        .counter();
        Counter error =
                meterRegistry
                        .find("t1000.operations.total")
                        .tag("operation", "op")
                        .tag("status", "error")
                        .counter();

        assertThat(success).isNotNull();
        assertThat(error).isNotNull();
        assertThat(success.count()).isEqualTo(2.0);
        assertThat(error.count()).isEqualTo(1.0);
    }

    // =========================
    // MÉTRICAS NÃO REGISTRADAS
    // =========================

    @Test
    @DisplayName("Nenhuma métrica deve existir antes de qualquer chamada")
    void semChamadas_registryVazio() {
        Counter counter =
                meterRegistry
                        .find("t1000.operations.total")
                        .tag("operation", "qualquer")
                        .tag("status", "success")
                        .counter();

        assertThat(counter).isNull();
    }

    @Test
    @DisplayName("Chamar success N vezes não afeta contador de outra operation")
    void success_naoAfetaOutraOperation() {
        metricsService.success("op_a");
        metricsService.success("op_a");
        metricsService.success("op_a");

        Counter outra =
                meterRegistry
                        .find("t1000.operations.total")
                        .tag("operation", "op_b")
                        .tag("status", "success")
                        .counter();

        assertThat(outra).isNull();
    }

    // =========================
    // NOME DA MÉTRICA
    // =========================

    @Test
    @DisplayName("Nome da métrica é sempre t1000.operations.total")
    void nomeDaMetrica_ehSempreConstante() {
        metricsService.success("qualquer");
        metricsService.error("qualquer");

        assertThat(meterRegistry.find("t1000.operations.total").counters()).hasSize(2);
    }
}
