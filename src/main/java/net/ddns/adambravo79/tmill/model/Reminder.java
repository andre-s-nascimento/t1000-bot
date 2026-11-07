/* (c) 2026 | 06/11/2026 */
package net.ddns.adambravo79.tmill.model;

import java.time.ZoneId;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

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
 * <h2>Optimistic locking</h2>
 *
 * <p>O campo {@link #version} é gerenciado pelo banco (incrementado a cada UPDATE).
 * O {@code ReminderService} usa esse valor para detectar edições concorrentes entre
 * instâncias. O construtor de conveniência de 9 args (sem {@code version}) assume
 * {@code version = 1} — útil para criação e para testes.
 *
 * <p>Como o compacto normaliza {@code version < 1} para {@code 1}, um desserializador
 * JSON (bootstrap, import) que não traga {@code version} ou traga {@code 0} acaba
 * com {@code version = 1}. Isso é intencional: o valor real é atribuído pelo banco
 * no INSERT.
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
 * <p>O campo {@code version} não aparece no JSON de entrada — é gerado pelo banco.
 * No export (via {@code ReminderJson}), ele é incluído para diagnóstico.
 *
 * @param id identificador único (obrigatório, não vazio, slug kebab-case)
 * @param name nome de exibição (opcional; se nulo, usa {@code id} na UI)
 * @param enabled se {@code false}, o scheduler ignora este lembrete
 * @param cron expressão cron do Spring (6 campos: seg min hora dia mês diaSemana)
 * @param timezone fuso IANA (ex.: {@code America/Sao_Paulo}); se nulo, usa o default da JVM
 * @param message texto a enviar (HTML aceito pelo {@code ParseMode.HTML})
 * @param mediaUrl URL ou caminho local da mídia (opcional; se nulo, envia só texto)
 * @param targetChatIds chats alvo (vazio = nada é enviado)
 * @param tags rótulos para filtro/agrupamento futuro (nunca {@code null})
 * @param version versão para optimistic locking (>= 1; default 1 na criação)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Reminder(
        String id,
        String name,
        boolean enabled,
        String cron,
        String timezone,
        String message,
        @JsonProperty("mediaUrl") String mediaUrl,
        @JsonProperty("targetChatIds") List<String> targetChatIds,
        List<String> tags,
        int version) {

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
        if (version < 1) {
            version = 1;
        }
    }

    /**
     * Construtor de conveniência (sem {@code version}) — assume {@code version = 1}.
     *
     * <p>Útil para criação de novos reminders e para testes que não querem se preocupar
     * com optimistic locking.
     */
    public Reminder(
            String id,
            String name,
            boolean enabled,
            String cron,
            String timezone,
            String message,
            String mediaUrl,
            List<String> targetChatIds,
            List<String> tags) {
        this(id, name, enabled, cron, timezone, message, mediaUrl, targetChatIds, tags, 1);
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
        return (timezone == null || timezone.isBlank()) ? ZoneId.systemDefault().getId() : timezone;
    }

    /** Cópia com novo valor de {@code enabled} — útil para o toggle rápido no admin. */
    public Reminder withEnabled(boolean newEnabled) {
        return new Reminder(
                id,
                name,
                newEnabled,
                cron,
                timezone,
                message,
                mediaUrl,
                targetChatIds,
                tags,
                version);
    }

    /** Cópia com nova versão — usado pelo repository ao reler do banco. */
    public Reminder withVersion(int newVersion) {
        return new Reminder(
                id,
                name,
                enabled,
                cron,
                timezone,
                message,
                mediaUrl,
                targetChatIds,
                tags,
                newVersion);
    }
}
