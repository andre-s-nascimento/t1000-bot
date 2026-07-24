// AutoResponseRuleEntry.java
package net.ddns.adambravo79.tmill.model;

import java.util.List;
import java.util.Map;

public record AutoResponseRuleEntry(
        List<String> triggers,
        String response,
        String animation,
        Map<String, String> timeRange,
        Map<String, UserOverride> userOverrides,
        Map<String, String> userResponse,
        Map<String, String> userAnimation) {}
