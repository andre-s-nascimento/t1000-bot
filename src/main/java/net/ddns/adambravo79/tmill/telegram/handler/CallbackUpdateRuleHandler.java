package net.ddns.adambravo79.tmill.telegram.handler;

import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.model.Update;

import io.ksilisk.telegrambot.core.interceptor.UpdateInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.controller.CallbackHandler;
import net.ddns.adambravo79.tmill.telegram.core.GroupAuthorizationService;

@Slf4j
@Component
@RequiredArgsConstructor
public class CallbackUpdateRuleHandler implements UpdateInterceptor {

    private final GroupAuthorizationService authService;
    private final CallbackHandler callbackHandler;

    @Override
    public Update intercept(Update update) {
        // Se não for um clique de botão (callback query), deixa o fluxo seguir intacto
        if (update == null || update.callbackQuery() == null) {
            return update;
        }

        log.info(
                "🔘 [CALLBACK INTERCEPTED] Update ID {} capturado no estágio de interceptação.",
                update.updateId());

        if (!authService.isAuthorized(update)) {
            log.warn(
                    "⛔ Update ID {} recusado por regras de autorização no Interceptor.",
                    update.updateId());
            return null; // Retorna null para descartar o update inválido e parar o processamento
        }

        String callbackData = update.callbackQuery().data();
        log.info("🔘 Redirecionando clique para CallbackHandler. Data: {}", callbackData);

        // Processa a regra de negócio do TMDB ou áudio no seu controller
        callbackHandler.handleCallbackUpdate(update);

        // Retorna null para interromper a cadeia do Ksilisk especificamente para este clique,
        // evitando que caia no roteador estático nativo e gere warnings de "Handler not found"
        return null;
    }
}
