/* (c) 2026 */
package net.ddns.adambravo79.tmill.service;

import org.springframework.stereotype.Service;

import com.pengrad.telegrambot.model.ChatPermissions;
import com.pengrad.telegrambot.request.RestrictChatMember;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramModerationService {

    private final TelegramFacade telegramFacade;

    /**
     * Silencia um usuário em um chat por um período.
     *
     * @param chatId ID do chat (grupo)
     * @param userId ID do usuário a ser silenciado
     * @param durationMinutes Duração em minutos (0 para permissão total, -1 para permanente)
     * @return true se a operação foi bem-sucedida
     */
    public boolean muteUser(long chatId, long userId, int durationMinutes) {
        // Cria permissões zeradas (mudo total)
        ChatPermissions mutePermissions =
                new ChatPermissions()
                        .canSendMessages(false)
                        .canSendAudios(false)
                        .canSendDocuments(false)
                        .canSendPhotos(false)
                        .canSendVideos(false)
                        .canSendVideoNotes(false)
                        .canSendVoiceNotes(false)
                        .canSendPolls(false)
                        .canSendOtherMessages(false)
                        .canAddWebPagePreviews(false);

        RestrictChatMember request = new RestrictChatMember(chatId, userId, mutePermissions);

        // Se durationMinutes > 0, define a data de expiração
        if (durationMinutes > 0) {
            long untilDate = (System.currentTimeMillis() / 1000) + (durationMinutes * 60L);
            request.untilDate((int) untilDate);
        } else if (durationMinutes == -1) {
            // -1 significa permanente (sem expiração)
            // O Telegram trata untilDate 0 como "para sempre" por padrão na API.
            request.untilDate(0);
        }

        try {
            telegramFacade.executar(request);
            log.info(
                    "🔇 Usuário {} silenciado no chat {} por {} minutos",
                    userId,
                    chatId,
                    durationMinutes);
            return true;
        } catch (Exception e) {
            log.error("❌ Falha ao silenciar usuário {} no chat {}", userId, chatId, e);
            return false;
        }
    }

    /**
     * Remove o silenciamento (desmuta) de um usuário.
     */
    public boolean unmuteUser(long chatId, long userId) {
        // Permissões liberadas (padrão de um usuário comum)
        ChatPermissions unmutePermissions =
                new ChatPermissions()
                        .canSendMessages(true)
                        .canSendAudios(true)
                        .canSendDocuments(true)
                        .canSendPhotos(true)
                        .canSendVideos(true)
                        .canSendVideoNotes(true)
                        .canSendVoiceNotes(true)
                        .canSendPolls(true)
                        .canSendOtherMessages(true)
                        .canAddWebPagePreviews(true);

        RestrictChatMember request = new RestrictChatMember(chatId, userId, unmutePermissions);

        try {
            telegramFacade.executar(request);
            log.info("🔊 Usuário {} desmutado no chat {}", userId, chatId);
            return true;
        } catch (Exception e) {
            log.error("❌ Falha ao desmutar usuário {} no chat {}", userId, chatId, e);
            return false;
        }
    }
}
