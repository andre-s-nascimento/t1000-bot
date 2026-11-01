package net.ddns.adambravo79.tmill.model;

import java.time.OffsetDateTime;
import java.util.List;

public record SalaVipSnapshot(
        Long id, OffsetDateTime capturedAt, int itemCount, List<SalaVipMovie> movies) {
    public SalaVipSnapshot(OffsetDateTime capturedAt, int itemCount, List<SalaVipMovie> movies) {
        this(null, capturedAt, itemCount, movies);
    }
}
