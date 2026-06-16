/* (c) 2026 | 25/05/2026 */
package net.ddns.adambravo79.tmill.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StreamingAvailabilityResponse(
        @JsonProperty("streamingInfo") Map<String, CountryStreamingInfo> streamingInfo) {
    public record CountryStreamingInfo(@JsonProperty("br") List<Service> br) {}

    public record Service(@JsonProperty("service") String serviceName) {}
}
