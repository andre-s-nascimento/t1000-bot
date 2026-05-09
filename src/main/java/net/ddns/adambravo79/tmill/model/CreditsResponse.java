/* (c) 2026 | 06/05/2026 */
package net.ddns.adambravo79.tmill.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreditsResponse(
        @JsonProperty("cast") List<CastRecord> cast, @JsonProperty("crew") List<CrewRecord> crew) {}
