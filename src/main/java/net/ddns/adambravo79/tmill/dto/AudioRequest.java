/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.dto;

/**
 * DTO para armazenar informações de um pedido de transcrição de áudio em grupo.
 *
 * @param fileId Identificador do arquivo no Telegram.
 * @param groupId ID do chat do grupo onde o áudio foi enviado.
 * @param timestamp Momento da criação do pedido (usado para expiração).
 * @param senderId ID do usuário que enviou o áudio original.
 * @param senderName Nome (primeiro + último) do remetente.
 */
public record AudioRequest(
        String fileId, long groupId, long timestamp, long senderId, String senderName) {}
