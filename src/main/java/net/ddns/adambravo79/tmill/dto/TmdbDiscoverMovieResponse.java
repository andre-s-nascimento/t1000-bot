package net.ddns.adambravo79.tmill.dto;

import java.util.List;

public record TmdbDiscoverMovieResponse(
        int page, List<MovieResult> results, int total_pages, int total_results) {}
