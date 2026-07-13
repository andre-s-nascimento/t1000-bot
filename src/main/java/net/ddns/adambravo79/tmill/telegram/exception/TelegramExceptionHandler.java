package net.ddns.adambravo79.tmill.telegram.exception;

import java.io.IOException;

import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.TelegramException;

import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.exception.AudioProcessingException;
import net.ddns.adambravo79.tmill.telegram.core.TelegramSender;

@Slf4j
@Component
public class TelegramExceptionHandler {

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

        // Exceções específicas da biblioteca Pengrad
        if (e instanceof TelegramException) {
            TelegramException te = (TelegramException) e;
            String msg = te.getMessage();
            if (msg != null && msg.contains("429")) {
                return "⏳ Muitas requisições. Tente novamente em alguns segundos.";
            }
            if (msg != null && msg.contains("400") && msg.contains("chat not found")) {
                return "❌ Não consegui encontrar este chat. Verifique se o ID está correto.";
            }
            if (msg != null && msg.toLowerCase().contains("unauthorized")) {
                return "🔑 Token inválido ou expirado. Verifique suas credenciais.";
            }
            if (msg != null && msg.toLowerCase().contains("file is too big")) {
                return "📂 O arquivo enviado é muito grande. Tente reduzir o tamanho.";
            }
            if (msg != null && msg.toLowerCase().contains("wrong file type")) {
                return "🛑 Formato de arquivo não suportado. Envie em outro formato.";
            }
            if (msg != null && msg.toLowerCase().contains("timeout")) {
                return "⏱️ O servidor demorou a responder. Tente novamente em instantes.";
            }
            return "⚠️ Erro ao falar com o Telegram: " + (msg != null ? msg : "erro desconhecido");
        }

        // Fallback baseado em mensagem
        String msg = e.getMessage();
        if (msg != null) {
            String lower = msg.toLowerCase();
            if (lower.contains("429"))
                return "⏳ Muitas requisições. Tente novamente em alguns segundos.";
            if (lower.contains("timeout"))
                return "⏱️ O servidor demorou a responder. Tente novamente em instantes.";
            if (lower.contains("401") || lower.contains("unauthorized")) {
                return "🔑 Token inválido ou expirado. Verifique suas credenciais.";
            }
            if (lower.contains("400") && lower.contains("chat not found")) {
                return "❌ Não consegui encontrar este chat. Verifique se o ID está correto.";
            }
            if (lower.contains("file is too big")) {
                return "📂 O arquivo enviado é muito grande. Tente reduzir o tamanho.";
            }
            if (lower.contains("wrong file type")) {
                return "🛑 Formato de arquivo não suportado. Envie em outro formato.";
            }
        }

        return "⚠️ Ocorreu um erro inesperado.";
    }
}
