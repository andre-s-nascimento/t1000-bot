/* (c) 2026 | 06/11/2026 */
package net.ddns.adambravo79.tmill.model;

import java.util.List;

/**
 * Record imutável que representa um lembrete configurável.
 *
 * <p>Um lembrete é um envio agendado de uma mensagem (opcionalmente com mídia) para
 * um ou mais chats do Telegram, disparado por uma expressão cron.
 *
 * <p><b>Imutabilidade:</b> todas as listas são copiadas defensivamente no construtor
 * compacto via {@link List#copyOf}, garantindo que o record não compartilha estado
 * mutável entre threads. Seguro para uso concorrente em virtual threads.
 *
 * <p><b>Validação:</b> o construtor compacto rejeita {@code id} nulo/vazio e
 * {@code cron} nulo/vazio, falhando rápido em erros de configuração.
 *
 * <h2>Exemplo no JSON ({@code config/reminders.json})</h2>
 *
 * <pre>
 * {
 *   "id": "quatro-horas-quarta",
 *   "name": "Quatro Horas de Quarta",
 *   "enabled": true,
 *   "cron": "0 0 16 * * WED",
 *   "timezone": "America/Sao_Paulo",
 *   "message": "&lt;b&gt;Muito Prazer (1979)&lt;/b&gt;",
 *   "mediaUrl": "https://exemplo.com/video.mp4",
 *   "targetChatIds": ["-100123", "-100456"],
 *   "tags": ["cinema", "semanal"]
 * }
 * </pre>
 *
 * @param id identificador único (obrigatório, não vazio)
 * @param name nome de exibição (opcional; se nulo, usa {@code id} na UI)
 * @param enabled se {@code false}, o scheduler ignora este lembrete
 * @param cron expressão cron do Spring ({@code 6} campos: seg min hora dia mês diaSemana)
 * @param timezone fuso IANA (ex.: {@code America/Sao_Paulo}); se nulo, usa o default da JVM
 * @param message texto a enviar (HTML aceito pelo {@code ParseMode.HTML})
 * @param mediaUrl URL ou caminho local da mídia (opcional; se nulo, envia só texto)
 * @param targetChatIds chats alvo (vazio = nada é enviado)
 * @param tags rótulos para filtro/agrupamento futuro (nunca {@code null})
 */
public record Reminder(
        String id,
        String name,
        boolean enabled,
        String cron,
        String timezone,
        String message,
        String mediaUrl,
        List<String> targetChatIds,
        List<String> tags) {

    /** Regex de ID aceito: slug kebab-case (letra inicial, depois alfanumérico/hífen). */
    private static final String ID_PATTERN = "^[a-z][a-z0-9-]*$";

    public Reminder {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id não pode ser nulo/vazio");
        }
        if (!id.matches(ID_PATTERN)) {
            throw new IllegalArgumentException(
                    "id deve ser slug kebab-case (minúsculo, letra inicial, ex.:"
                            + " 'quatro-horas-quarta'): "
                            + id);
        }
        if (cron == null || cron.isBlank()) {
            throw new IllegalArgumentException("cron não pode ser nulo/vazio: " + id);
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message não pode ser nula/vazia: " + id);
        }
        targetChatIds = targetChatIds == null ? List.of() : List.copyOf(targetChatIds);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    /**
     * Retorna o nome de exibição, usando {@link #id} como fallback quando {@link #name} é nulo/vazio.
     *
     * @return nome de exibição (nunca nulo)
     */
    public String displayName() {
        return (name == null || name.isBlank()) ? id : name;
    }

    /**
     * Retorna o timezone como string válida, usando o default da JVM quando nulo/vazio.
     *
     * @return timezone IANA (nunca nulo)
     */
    public String effectiveTimezone() {
        return (timezone == null || timezone.isBlank())
                ? java.time.ZoneId.systemDefault().getId()
                : timezone;
    }

    /** Cópia com novo valor de {@code enabled} — útil para o toggle rápido no admin. */
    public Reminder withEnabled(boolean newEnabled) {
        return new Reminder(
                id, name, newEnabled, cron, timezone, message, mediaUrl, targetChatIds, tags);
    }
}
