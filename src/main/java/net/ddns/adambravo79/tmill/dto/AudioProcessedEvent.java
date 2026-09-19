package net.ddns.adambravo79.tmill.dto;

public record AudioProcessedEvent(
        String fileId,
        long chatId,
        long senderId,
        String senderName,
        boolean sucesso,
        String mensagemErro,
        int duration) {}
