package net.ddns.adambravo79.tmill.telegram.exception;

import java.io.IOException;
import java.util.Map;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.TelegramException;

import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.exception.AudioProcessingException;
import net.ddns.adambravo79.tmill.telegram.core.TelegramSender;

@Slf4j
@Component
public class TelegramExceptionHandler {

    // Mapeamento de padrões de erro para mensagens amigáveis
    private static final Map<Predicate<String>, String> ERROR_MESSAGES =
            Map.of(
                    msg -> msg.contains("429"),
                            "⏳ Muitas requisições. Tente novamente em alguns segundos.",
                    msg -> msg.contains("timeout"),
                            "⏱️ O servidor demorou a responder. Tente novamente em instantes.",
                    msg -> msg.contains("unauthorized"),
                            "🔑 Token inválido ou expirado. Verifique suas credenciais.",
                    msg -> msg.contains("chat not found"),
                            "❌ Não consegui encontrar este chat. Verifique se o ID está correto.",
                    msg -> msg.contains("file is too big"),
                            "📂 O arquivo enviado é muito grande. Tente reduzir o tamanho.",
                    msg -> msg.contains("wrong file type"),
                            "🛑 Formato de arquivo não suportado. Envie em outro formato.");

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
        // Exceções customizadas do projeto
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

        // Exceções do Telegram usando pattern matching
        if (e instanceof TelegramException te) {
            return mapearPorMensagem(te.getMessage());
        }

        // Fallback genérico
        return mapearPorMensagem(e.getMessage());
    }

    private String mapearPorMensagem(String msg) {
        if (msg == null) {
            return BotMessages.ERRO_GENERICO;
        }

        String lower = msg.toLowerCase();
        for (Map.Entry<Predicate<String>, String> entry : ERROR_MESSAGES.entrySet()) {
            if (entry.getKey().test(lower)) {
                return entry.getValue();
            }
        }
        return "⚠️ Ocorreu um erro inesperado: " + msg;
    }
}
