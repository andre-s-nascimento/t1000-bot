package net.ddns.adambravo79.tmill.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TvRecord(
        @JsonProperty("id") Long id,
        String name,
        String overview,
        @JsonProperty("vote_average") Double voteAverage,
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("first_air_date") String firstAirDate) {}
