package net.ddns.adambravo79.tmill.telegram.core;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.pengrad.telegrambot.model.Update;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GroupAuthorizationService {

    @Value("${bot.allowed-chats:}")
    private String allowedChatsStr;

    private final Set<Long> allowedGroups = new HashSet<>();
    private final Set<Long> warnedGroups = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void init() {
        if (allowedChatsStr != null && !allowedChatsStr.isBlank()) {
            for (String s : allowedChatsStr.split(",")) {
                try {
                    long id = Long.parseLong(s.trim());
                    if (id < 0) {
                        allowedGroups.add(id);
                        log.info("✅ Grupo autorizado: {}", id);
                    } else {
                        log.warn("ID positivo ignorado (apenas grupos negativos): {}", id);
                    }
                } catch (NumberFormatException e) {
                    log.warn("ID inválido em bot.allowed-chats: {}", s);
                }
            }
        }
        if (allowedGroups.isEmpty()) {
            log.info("📋 Nenhum grupo autorizado configurado – todos os grupos serão aceitos.");
        } else {
            log.info("📋 Grupos autorizados: {}", allowedGroups);
        }
    }

    /**
     * Verifica se um update é autorizado. - Chats privados (ID > 0) sempre são permitidos. - Grupos
     * só são permitidos se estiverem na lista allowedGroups. - Se a lista estiver vazia, todos os
     * grupos são permitidos.
     */
    public boolean isAuthorized(Update update) {
        Long chatId = extractChatId(update);
        if (chatId == null) return true;
        if (chatId > 0) {
            log.debug("✅ Chat privado autorizado: {}", chatId);
            return true;
        }
        if (allowedGroups.isEmpty()) {
            log.debug("✅ Nenhum grupo restrito, permitindo {}", chatId);
            return true;
        }
        boolean allowed = allowedGroups.contains(chatId);
        if (!allowed) log.warn("⛔ Grupo não autorizado: {}", chatId);
        return allowed;
    }

    private Long extractChatId(Update update) {
        if (update.message() != null) {
            return update.message().chat().id();
        }
        if (update.callbackQuery() != null) {
            return update.callbackQuery().message().chat().id();
        }
        return null;
    }
}
