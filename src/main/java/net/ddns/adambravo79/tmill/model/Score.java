package net.ddns.adambravo79.tmill.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Score(
        @JsonProperty("ft") List<Integer> ft,
        @JsonProperty("ht") List<Integer> ht,
        @JsonProperty("et") List<Integer> et,
        @JsonProperty("p") List<Integer> p) {}
