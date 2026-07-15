package net.ddns.adambravo79.tmill.telegram.util;

import static com.pengrad.telegrambot.model.Chat.Type.Private;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.model.*;

@Component
public class TelegramUtils {

    static final int TELEGRAM_LIMIT = 3900;

    public String buildFullName(User user) {
        if (user == null) return "";
        String lastName = user.lastName();
        return (lastName != null && !lastName.isBlank())
                ? user.firstName() + " " + lastName
                : user.firstName();
    }

    public String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public String buildUserMention(User user) {
        if (user == null) return "Usuário";
        String name = user.firstName();
        if (user.lastName() != null && !user.lastName().isBlank()) {
            name += " " + user.lastName();
        }
        String escapedName = escapeHtml(name);
        // Não há suporte direto a tg://user?id= no Pengrad? Podemos apenas usar texto.
        // O Telegram aceita @username, mas se não tiver, usamos o nome.
        if (user.username() != null && !user.username().isBlank()) {
            return "@" + user.username();
        }
        return escapedName;
    }

    public List<String> splitMessage(String text) {
        return splitMessage(text, TELEGRAM_LIMIT);
    }

    public List<String> splitMessage(String text, int limit) {
        List<String> parts = new ArrayList<>();
        if (text == null || text.isBlank()) return parts;
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + limit, text.length());
            if (end < text.length()) {
                int lastBreak = text.lastIndexOf("\n", end);
                // Se encontrarmos uma quebra de linha dentro do trecho, cortamos nela
                if (lastBreak > start) {
                    end = lastBreak;
                }
            }
            parts.add(text.substring(start, end).trim());
            start = end;
        }
        return parts;
    }

    public String getChatName(Message message) {
        com.pengrad.telegrambot.model.Chat chat = message.chat();
        if (chat.type() == Private) {
            return "privado";
        } else {
            return chat.title() != null ? chat.title() : "grupo";
        }
    }
}
