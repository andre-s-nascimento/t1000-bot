/* (c) 2026 | 06/11/2026 */
package net.ddns.adambravo79.tmill.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.ddns.adambravo79.tmill.model.Reminder;

/**
 * Envelope do arquivo {@code config/reminders.json}.
 *
 * <p>Estrutura raiz do JSON: metadados ({@code version}, {@code updatedAt}) + lista
 * de {@link Reminder}.
 *
 * <h2>Exemplo</h2>
 *
 * <pre>
 * {
 *   "version": 1,
 *   "updatedAt": "2026-11-06T10:00:00Z",
 *   "reminders": [
 *     {
 *       "id": "quatro-horas-quarta",
 *       "name": "Quatro Horas de Quarta",
 *       "enabled": true,
 *       "cron": "0 0 16 * * WED",
 *       "timezone": "America/Sao_Paulo",
 *       "message": "&lt;b&gt;Muito Prazer (1979)&lt;/b&gt;",
 *       "mediaUrl": "https://exemplo.com/video.mp4",
 *       "targetChatIds": ["-100123"],
 *       "tags": ["cinema"]
 *     }
 *   ]
 * }
 * </pre>
 *
 * <p><b>Compatibilidade:</b> {@link JsonIgnoreProperties} tolera campos extras que
 * versões futuras possam adicionar (ex.: {@code "createdBy"}, {@code "priority"}),
 * permitindo evolução sem quebrar leitores antigos.
 *
 * <p><b>Imutabilidade:</b> a lista é copiada defensivamente via {@link List#copyOf}
 * no construtor compacto, garantindo que o record não expõe estado mutável.
 *
 * @param version versão do schema (para futuras migrações); default {@code 1} se ausente
 * @param updatedAt timestamp ISO-8601 da última escrita ({@code null} em arquivos nunca salvos)
 * @param reminders lista de lembretes (nunca {@code null} após o construtor)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReminderJson(
        @JsonProperty("version") Integer version,
        @JsonProperty("updatedAt") String updatedAt,
        @JsonProperty("reminders") List<Reminder> reminders) {

    /** Versão atual do schema — bump quando houver mudança incompatível. */
    public static final int CURRENT_VERSION = 1;

    public ReminderJson {
        // Normaliza version ausente/inválida para CURRENT_VERSION
        if (version == null || version < 1) {
            version = CURRENT_VERSION;
        }
        // Lista nunca nula — vazio é um estado válido (nenhum lembrete configurado)
        reminders = reminders == null ? List.of() : List.copyOf(reminders);
    }

    /**
     * Fábrica para criar um envelope novo a partir de uma lista de lembretes,
     * já com {@code updatedAt = agora} e {@code version = CURRENT_VERSION}.
     *
     * @param reminders lista de lembretes (não nula)
     * @return envelope pronto para persistir
     */
    public static ReminderJson of(List<Reminder> reminders) {
        return new ReminderJson(
                CURRENT_VERSION,
                java.time.Instant.now().toString(),
                reminders == null ? List.of() : reminders);
    }

    /**
     * Cópia com novos lembretes e {@code updatedAt} renovado.
     *
     * @param newReminders nova lista (não nula)
     * @return envelope atualizado
     */
    public ReminderJson withReminders(List<Reminder> newReminders) {
        return new ReminderJson(
                version,
                java.time.Instant.now().toString(),
                newReminders == null ? List.of() : newReminders);
    }

    /** Cópia vazia, útil como fallback quando o arquivo não existe. */
    public static ReminderJson empty() {
        return new ReminderJson(CURRENT_VERSION, null, List.of());
    }
}
