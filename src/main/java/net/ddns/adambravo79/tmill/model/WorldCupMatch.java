// src/main/java/net/ddns/adambravo79/tmill/model/WorldCupMatch.java
package net.ddns.adambravo79.tmill.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WorldCupMatch(
        @JsonProperty("round") String round,
        @JsonProperty("date") String date,
        @JsonProperty("time") String time,
        @JsonProperty("team1") String homeTeam,
        @JsonProperty("team2") String awayTeam,
        @JsonProperty("group") String group,
        @JsonProperty("ground") String stadium,
        @JsonProperty("score") Score score,
        @JsonProperty("goals1") List<Goal> goals1,
        @JsonProperty("goals2") List<Goal> goals2) {
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public LocalDate getLocalDate() {
        return LocalDate.parse(date, DATE_FORMATTER);
    }

    public ZonedDateTime getMatchDateTime(ZoneId targetZone) {
        String[] parts = time.split(" ");
        String timePart = parts[0];
        String offsetPart = parts[1];
        int offsetHours = Integer.parseInt(offsetPart.replace("UTC", ""));
        ZoneId offsetZone = ZoneId.ofOffset("UTC", ZoneOffset.ofHours(offsetHours));
        LocalDateTime ldt = LocalDateTime.of(getLocalDate(), LocalTime.parse(timePart));
        ZonedDateTime zdt = ldt.atZone(offsetZone);
        return zdt.withZoneSameInstant(targetZone);
    }

    public boolean hasScore() {
        return score != null && score.ft() != null && !score.ft().isEmpty();
    }
}
