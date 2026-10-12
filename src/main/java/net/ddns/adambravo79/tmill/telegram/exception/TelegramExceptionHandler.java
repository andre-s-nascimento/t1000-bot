package net.ddns.adambravo79.tmill.telegram.exception;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.TelegramException;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.exception.AudioProcessingException;
import net.ddns.adambravo79.tmill.service.config.JsonConfigLoader;
import net.ddns.adambravo79.tmill.telegram.core.TelegramSender;

@Slf4j
@Component
public class TelegramExceptionHandler {

    private final JsonConfigLoader jsonConfigLoader;

    @Value("${telegram.error-messages.file:classpath:telegram-error-messages.json}")
    private String errorMessagesFile;

    /** Lista imutável carregada do JSON; recarregável via {@link #reload()}. */
    private volatile List<ErrorRule> errorRules = List.of();

    public TelegramExceptionHandler(JsonConfigLoader jsonConfigLoader) {
        this.jsonConfigLoader = jsonConfigLoader;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    /** Recarrega as regras de erro do JSON. Em caso de falha, mantém o estado anterior. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public synchronized void reload() {
        try {
            var loaded =
                    jsonConfigLoader.loadConfig(
                            "telegram-error-messages.json",
                            Map.class,
                            errorMessagesFile != null && !errorMessagesFile.isBlank()
                                    ? errorMessagesFile
                                    : null);

            if (loaded.isEmpty()) {
                log.warn("⚠️ Nenhum arquivo de mensagens de erro do Telegram encontrado.");
                return;
            }

            Object rulesNode = loaded.get().get("rules");
            if (!(rulesNode instanceof List<?> rawList)) {
                log.warn("⚠️ Chave 'rules' ausente ou inválida.");
                return;
            }

            List<ErrorRule> novas =
                    rawList.stream()
                            .filter(Map.class::isInstance)
                            .map(Map.class::cast)
                            .map(
                                    m -> {
                                        String contains = optString(m, "contains");
                                        String message = optString(m, "message");
                                        return (contains != null && message != null)
                                                ? new ErrorRule(contains.toLowerCase(), message)
                                                : null;
                                    })
                            .filter(Objects::nonNull)
                            .toList();

            this.errorRules = novas;
            log.info("✅ Telegram: {} regras de erro carregadas", errorRules.size());
        } catch (Exception e) {
            log.error("❌ Falha ao carregar regras de erro do Telegram", e);
        }
    }

    private static String optString(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    public void handle(Exception e, long chatId, TelegramSender sender) {
        log.error("Erro no fluxo Telegram - chatId={}", chatId, e);
        String mensagemUsuario = mapearMensagem(e);
        try {
            sender.enviar(chatId, mensagemUsuario);
        } catch (Exception sendError) {
            log.error("Erro ao enviar mensagem de erro para o usuário", sendError);
        }
    }

    private String mapearMensagem(Exception e) {
        if (e instanceof TelegramFileException) {
            return "⚠️ Não consegui baixar o áudio.";
        }
        if (e instanceof AudioProcessingException) {
            return "🎧 Erro ao processar o áudio.";
        }
        if (e instanceof IOException) {
            return "📡 Problema de comunicação com o servidor.";
        }
        if (e instanceof org.springframework.web.client.ResourceAccessException) {
            return "⏱️ Timeout na comunicação com o servidor. Tente novamente.";
        }
        if (e instanceof TelegramException te) {
            return mapearPorMensagem(te.getMessage());
        }
        return mapearPorMensagem(e.getMessage());
    }

    private String mapearPorMensagem(String msg) {
        if (msg == null) {
            return BotMessages.ERRO_GENERICO;
        }
        String lower = msg.toLowerCase();
        for (ErrorRule rule : errorRules) {
            if (lower.contains(rule.contains())) {
                return rule.message();
            }
        }
        return "⚠️ Ocorreu um erro inesperado: " + msg;
    }

    /**
     * Exposto para testes: retorna a mensagem mapeada para um texto bruto, ou
     * {@link Optional#empty()} se nenhuma regra casar (ou se {@code rawMessage} for
     * {@code null}).
     *
     * <p>Este método <b>não lança</b> NPE em entrada nula — é seguro para uso
     * defensivo em testes e diagnósticos.
     */
    Optional<String> getMappedMessageFor(String rawMessage) {
        if (rawMessage == null) {
            return Optional.empty();
        }
        String lower = rawMessage.toLowerCase();
        return errorRules.stream()
                .filter(r -> lower.contains(r.contains()))
                .map(ErrorRule::message)
                .findFirst();
    }

    /** Exposto para testes: retorna as regras atualmente carregadas. */
    List<ErrorRule> getErrorRules() {
        return errorRules;
    }

    public record ErrorRule(String contains, String message) {}
}
