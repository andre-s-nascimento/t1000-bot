package net.ddns.adambravo79.tmill.model;

import java.time.LocalDate;

public record FullRelease(
        long tmdbId,
        String mediaType,
        LocalDate releaseDate,
        String title,
        String overview,
        double rating,
        String providers,
        String posterPath) {}
