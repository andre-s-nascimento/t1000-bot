/* (c) 2026 | 25/05/2026 */

package net.ddns.adambravo79.tmill.model;

import java.util.Map;

public record WatchProvidersResponse(Map<String, ProviderDetails> results) {
    public record ProviderDetails(String provider_name, int provider_id) {}
}
