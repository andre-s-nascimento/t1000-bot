/* (c) 2026 | 21/05/2026 */
package net.ddns.adambravo79.tmill.model;

import java.time.LocalTime;
import java.util.Map;

public record AutoResponseRule(
        String response,
        String animation,
        LocalTime startTime,
        LocalTime endTime,
        Map<String, AutoResponseOverride> userOverrides) {}
