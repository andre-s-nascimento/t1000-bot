/* (c) 2026 | 26/09/2026 */
package net.ddns.adambravo79.tmill.service.feature;

import org.jspecify.annotations.NonNull;

/**
 * Estado atual de uma feature flag.
 *
 * <p>Serializado para {@code ./config/feature-flags.json} quando a persistência está habilitada.
 */
public record FeatureFlagState(
        @NonNull String key, boolean enabled, @NonNull String description, boolean readOnly) {

    public static FeatureFlagState fromDefinition(FeatureFlagDefinition def) {
        return new FeatureFlagState(
                def.key(), def.defaultValue(), def.description(), def.readOnly());
    }

    public FeatureFlagState withEnabled(boolean newValue) {
        return new FeatureFlagState(key, newValue, description, readOnly);
    }
}
