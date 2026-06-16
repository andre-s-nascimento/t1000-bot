package net.ddns.adambravo79.tmill.dto;

import java.util.List;

public record TmdbDiscoverTvResponse(
        int page, List<TvResult> results, int total_pages, int total_results) {}
