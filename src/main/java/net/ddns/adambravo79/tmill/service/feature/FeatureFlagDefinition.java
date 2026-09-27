/* (c) 2026 | 26/09/2026 */
package net.ddns.adambravo79.tmill.service.feature;

/**
 * Definição de uma feature flag.
 *
 * @param key identificador único (ex.: {@code "worldcup.enabled"})
 * @param description descrição legível para exibição na UI
 * @param readOnly se {@code true}, não pode ser alterada via UI (requer restart)
 * @param defaultValue valor inicial lido do {@code application.properties}
 */
public record FeatureFlagDefinition(
        String key, String description, boolean readOnly, boolean defaultValue) {

    public FeatureFlagDefinition {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key não pode ser vazia");
        }
    }
}
