/* (c) 2026 | 06/11/2026 */
package net.ddns.adambravo79.tmill.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.model.Reminder;
import net.ddns.adambravo79.tmill.service.feature.FeatureFlagAdminService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;

/**
 * Dispatcher de reminders — envia o lembrete para todos os chats alvo.
 *
 * <p><b>Responsabilidades:</b>
 *
 * <ol>
 *   <li>Verificar feature flag {@code reminders.enabled} (rollback global).
 *   <li>Buscar o reminder no {@link ReminderService} (fonte de verdade).
 *   <li>Para cada chat alvo: enviar via {@link TelegramFacade#enviarMidia} (se houver mídia) ou
 *       {@link TelegramFacade#enviarMensagemHtml} (só texto).
 *   <li>Registrar métricas: sucesso/falha por reminder.
 * </ol>
 *
 * <h2>Isolamento por chat</h2>
 *
 * <p>Cada chat é tratado independentemente: se o envio para o chat A falhar, o chat B ainda é
 * tentado. Um único chat inválido (ex.: bot removido do grupo) não derruba o reminder inteiro.
 *
 * <h2>Retry</h2>
 *
 * <p><b>Não</b> fazemos retry aqui. O {@link TelegramFacade#enviarMidia} já tem fallback interno
 * (texto puro) e o {@code TelegramSafeExecutor} loga + notifica em caso de falha. Se o envio falhar
 * mesmo assim, apenas registramos erro — o próximo cron vai tentar de novo (ex.: daqui a 1 semana).
 * Retry imediato seria redundante e poderia causar spam em caso de falha sistêmica.
 *
 * <h2>Feature flag</h2>
 *
 * <p>{@code reminders.enabled} controla <b>todos</b> os reminders. Se {@code false}, {@link
 * #dispatch(String)} retorna imediatamente com warning — útil para pausar todos em um incidente sem
 * editar cada reminder individualmente.
 *
 * <p>Note que {@code weekly.reminder.enabled} (flag antiga) <b>não</b> é consultada aqui — o
 * {@code ReminderService} continua funcionando mesmo com ela {@code false}. Isso permite migração
 * gradual: admins podem desabilitar a flag antiga enquanto o novo sistema está em testes, sem
 * afetar reminders já migrados.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderDispatcher {

    /** Feature flag global (pausa todos os reminders sem editar cada um). */
    private static final String FLAG_REMINDERS_ENABLED = "reminders.enabled";

    /** Prefixo de métrica de sucesso (contador global). */
    private static final String METRIC_SUCCESS = "reminder_dispatch_success";

    /** Prefixo de métrica de falha (contador global). */
    private static final String METRIC_FAILURE = "reminder_dispatch_failure";

    /** Prefixo de métrica de pausa por feature flag. */
    private static final String METRIC_SKIPPED = "reminder_dispatch_skipped";

    private final ReminderService reminderService;
    private final TelegramFacade telegramFacade;
    private final FeatureFlagAdminService featureFlags;
    private final MetricsService metricsService;

    // =========================================================================
    // DISPATCH PRINCIPAL (chamado pelo ReminderScheduler)
    // =========================================================================

    /**
     * Dispara um reminder para todos os chats alvo.
     *
     * <p>Chamado pelo {@link ReminderScheduler} no horário do cron. Também pode ser chamado
     * manualmente pelo admin (endpoint {@code POST /admin/reminders/{id}/test}) para validar antes
     * de agendar de verdade.
     *
     * @param reminderId identificador do reminder
     */
    public void dispatch(String reminderId) {
        if (!featureFlags.isEnabled(FLAG_REMINDERS_ENABLED)) {
            log.info(
                    "📴 Reminder '{}' ignorado — flag '{}' desabilitada",
                    reminderId,
                    FLAG_REMINDERS_ENABLED);
            metricsService.success(METRIC_SKIPPED);
            return;
        }

        Optional<Reminder> opt = reminderService.getById(reminderId);
        if (opt.isEmpty()) {
            log.warn("⚠️ Reminder '{}' não encontrado no registry — ignorando", reminderId);
            metricsService.error(METRIC_FAILURE);
            return;
        }

        Reminder reminder = opt.get();
        if (!reminder.enabled()) {
            log.info("📴 Reminder '{}' desabilitado — ignorando", reminderId);
            metricsService.success(METRIC_SKIPPED);
            return;
        }

        List<String> chatIds = reminder.targetChatIds();
        if (chatIds.isEmpty()) {
            log.warn("⚠️ Reminder '{}' sem chats alvo — ignorando", reminderId);
            metricsService.error(METRIC_FAILURE);
            return;
        }

        log.info(
                "📢 Disparando reminder '{}' para {} chat(s) (media={})",
                reminder.id(),
                chatIds.size(),
                reminder.mediaUrl() != null ? "sim" : "não");

        int enviados = 0;
        int falhas = 0;

        for (String chatIdStr : chatIds) {
            if (dispatchToChatSafe(reminder, chatIdStr)) {
                enviados++;
            } else {
                falhas++;
            }
        }

        log.info(
                "📊 Reminder '{}' concluído: {} enviados, {} falhas (total {})",
                reminder.id(),
                enviados,
                falhas,
                chatIds.size());

        if (falhas > 0) {
            metricsService.error(METRIC_FAILURE);
        } else {
            metricsService.success(METRIC_SUCCESS);
        }
    }

    // =========================================================================
    // DISPATCH MANUAL (chamado pelo admin)
    // =========================================================================

    /**
     * Envia um reminder para um chat específico, ignorando {@code targetChatIds}.
     *
     * <p>Usado pelo admin para testar antes de agendar (ex.: {@code POST
     * /admin/reminders/{id}/test?chatId=123}).
     *
     * <p><b>Não</b> valida feature flag — é uma ação explícita do admin (mesmo padrão do {@code
     * WeeklyReminderService.sendReminderToChat}).
     *
     * @param reminderId identificador
     * @param chatId chat alvo (pode ser positivo para privado ou negativo para grupo)
     * @return {@code true} se enviou, {@code false} se falhou/reminder inexistente
     */
    public boolean dispatchToChat(String reminderId, long chatId) {
        Optional<Reminder> opt = reminderService.getById(reminderId);
        if (opt.isEmpty()) {
            log.warn("⚠️ Reminder '{}' não encontrado — teste ignorado", reminderId);
            return false;
        }

        Reminder reminder = opt.get();
        log.info(
                "🧪 Teste manual: reminder '{}' para chat {} (media={})",
                reminderId,
                chatId,
                reminder.mediaUrl() != null ? "sim" : "não");

        boolean ok = sendToChat(reminder, chatId);
        if (ok) {
            metricsService.success(METRIC_SUCCESS);
        } else {
            metricsService.error(METRIC_FAILURE);
        }
        return ok;
    }

    // =========================================================================
    // ENVIO POR CHAT (isolado)
    // =========================================================================

    /**
     * Envia o reminder para um único chat, capturando toda exceção.
     *
     * @return {@code true} se enviou com sucesso, {@code false} caso contrário
     */
    private boolean dispatchToChatSafe(Reminder reminder, String chatIdStr) {
        long chatId;
        try {
            chatId = Long.parseLong(chatIdStr.trim());
        } catch (NumberFormatException e) {
            log.warn(
                    "⚠️ Chat ID inválido no reminder '{}': '{}' — ignorando este chat",
                    reminder.id(),
                    chatIdStr);
            return false;
        }
        return sendToChat(reminder, chatId);
    }

    /**
     * Envia o reminder para um chat específico.
     *
     * <p>Se {@code mediaUrl} está presente, usa {@link TelegramFacade#enviarMidia} (que já tem
     * fallback interno para texto puro se a mídia falhar). Caso contrário, envia só texto via
     * {@link TelegramFacade#enviarMensagemHtml}.
     *
     * @return {@code true} se o envio foi confirmado, {@code false} se falhou
     */
    private boolean sendToChat(Reminder reminder, long chatId) {
        try {
            boolean ok;
            if (reminder.mediaUrl() != null && !reminder.mediaUrl().isBlank()) {
                ok = telegramFacade.enviarMidia(chatId, reminder.mediaUrl(), reminder.message());
            } else {
                telegramFacade.enviarMensagemHtml(chatId, reminder.message());
                ok = true; // enviarMensagemHtml não retorna boolean; assume-se sucesso se não
                // lançou
            }

            if (ok) {
                log.info("✅ Reminder '{}' enviado para chat {}", reminder.id(), chatId);
            } else {
                log.warn(
                        "⚠️ Reminder '{}' NÃO entregue ao chat {} (enviarMidia retornou false)",
                        reminder.id(),
                        chatId);
            }
            return ok;

        } catch (RuntimeException e) {
            log.error("❌ Falha ao enviar reminder '{}' para chat {}", reminder.id(), chatId, e);
            return false;
        }
    }
}
