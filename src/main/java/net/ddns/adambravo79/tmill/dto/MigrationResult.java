package net.ddns.adambravo79.tmill.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Resultado da migração SQLite → Postgres/Mongo.
 *
 * @param status "SUCCESS", "PARTIAL" ou "FAILED"
 * @param startedAt momento de início da migração
 * @param finishedAt momento de término
 * @param durationMs duração total em milissegundos
 * @param tablesMigrated mapa tabela → contagem de registros migrados
 * @param errors lista de mensagens de erro (vazia em caso de sucesso)
 */
public record MigrationResult(
        String status,
        Instant startedAt,
        Instant finishedAt,
        long durationMs,
        Map<String, Integer> tablesMigrated,
        java.util.List<String> errors) {

    public static MigrationResult success(Instant start, Instant end, Map<String, Integer> tables) {
        return new MigrationResult(
                "SUCCESS",
                start,
                end,
                end.toEpochMilli() - start.toEpochMilli(),
                tables,
                java.util.List.of());
    }

    public static MigrationResult partial(
            Instant start,
            Instant end,
            Map<String, Integer> tables,
            java.util.List<String> errors) {
        return new MigrationResult(
                "PARTIAL", start, end, end.toEpochMilli() - start.toEpochMilli(), tables, errors);
    }

    public static MigrationResult failed(
            Instant start, Instant end, java.util.List<String> errors) {
        return new MigrationResult(
                "FAILED", start, end, end.toEpochMilli() - start.toEpochMilli(), Map.of(), errors);
    }
}
