package net.ddns.adambravo79.tmill.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AudioReceivedEvent(
        String fileId,
        long chatId,
        long userId,
        String userName,
        long groupId,
        String tipoFluxo,
        int duration) {}
