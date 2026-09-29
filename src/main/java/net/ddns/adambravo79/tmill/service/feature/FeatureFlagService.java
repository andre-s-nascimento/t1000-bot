/* (c) 2026 | 26/09/2026 */
package net.ddns.adambravo79.tmill.service.feature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

/**
 * Serviço central de feature flags.
 *
 * <p><b>Modelo híbrido</b>:
 *
 * <ul>
 *   <li><b>Runtime</b>: estado mantido em memória ({@link ConcurrentHashMap}), leitura O(1),
 *       alteração via UI/API aplicada imediatamente.
 *   <li><b>Persistência opcional</b>: se {@code feature-flags.persist.enabled=true}, o estado é
 *       gravado em {@code ./config/feature-flags.json} a cada mudança, sobrevivendo a restarts.
 * </ul>
 *
 * <p><b>Ordem de precedência no boot</b>:
 *
 * <ol>
 *   <li>Defaults do {@code application.properties} (via {@link #register})
 *   <li>Valores do {@code feature-flags.json} (sobrescrevem, exceto se readOnly)
 * </ol>
 *
 * <p><b>Flags readOnly</b> (ex.: {@code migration.enabled}) não podem ser alteradas em runtime —
 * apenas refletem o valor do {@code application.properties}.
 */
@Slf4j
@Service
public class FeatureFlagService {

    private final ObjectMapper objectMapper;
    private final Path persistPath;
    private final boolean persistEnabled;

    private final ConcurrentHashMap<String, FeatureFlagState> states = new ConcurrentHashMap<>();

    // FeatureFlagService.java
    public void init() {
        if (persistEnabled) {
            loadFromDisk();
        }
    }

    public FeatureFlagService(
            ObjectMapper objectMapper,
            @Value("${feature-flags.persist.enabled:true}") boolean persistEnabled,
            @Value("${feature-flags.persist.path:./config/feature-flags.json}")
                    String persistPathStr) {
        this.objectMapper = objectMapper;
        this.persistEnabled = persistEnabled;
        this.persistPath = Paths.get(persistPathStr).toAbsolutePath().normalize();
    }

    // =========================================================================
    // CICLO DE VIDA
    // =========================================================================

    /**
     * Registra uma flag editável em runtime. Deve ser chamado antes de {@link #init()}, portanto
     * usa-se em {@code @PostConstruct} de um {@code @Configuration} dedicado, ou via método público
     * exposto para o controller registrar no boot.
     *
     * <p>Registros são idempotentes: se a chave já existe, mantém o valor atual (que pode ter vindo
     * do disco) e apenas atualiza descrição/readOnly.
     */
    public void register(String key, boolean defaultValue, String description) {
        registerInternal(key, defaultValue, description, false);
    }

    /** Registra uma flag <b>read-only</b>. Só pode ser lida, nunca alterada via UI. */
    public void registerReadOnly(String key, boolean defaultValue, String description) {
        registerInternal(key, defaultValue, description, true);
    }

    private void registerInternal(
            String key, boolean defaultValue, String description, boolean readOnly) {
        states.compute(
                key,
                (k, existing) -> {
                    if (existing != null) {
                        // Mantém o valor atual (pode ter vindo do disco), atualiza metadados
                        return new FeatureFlagState(key, existing.enabled(), description, readOnly);
                    }
                    return new FeatureFlagState(key, defaultValue, description, readOnly);
                });
    }

    // =========================================================================
    // LEITURA
    // =========================================================================

    /**
     * Retorna {@code true} se a flag estiver habilitada. Flags desconhecidas retornam {@code false} e
     * emitem um warning (uma vez por chave).
     */
    public boolean isEnabled(String key) {
        FeatureFlagState state = states.get(key);
        if (state == null) {
            log.warn("⚠️ Consulta a flag desconhecida: '{}'. Retornando false.", key);
            return false;
        }
        return state.enabled();
    }

    /** Lista todas as flags, ordenadas por chave. */
    public List<FeatureFlagState> list() {
        return states.values().stream()
                .sorted(Comparator.comparing(FeatureFlagState::key))
                .toList();
    }

    /**
     * Retorna um mapa {@code key → {enabled, description, readOnly}} para serialização JSON no
     * endpoint {@code GET /features}.
     */
    public Map<String, Map<String, Object>> listAsMap() {
        return states.values().stream()
                .sorted(Comparator.comparing(FeatureFlagState::key))
                .collect(
                        Collectors.toMap(
                                FeatureFlagState::key,
                                s -> {
                                    Map<String, Object> m = new LinkedHashMap<>();
                                    m.put("enabled", s.enabled());
                                    m.put("description", s.description());
                                    m.put("readOnly", s.readOnly());
                                    return m;
                                },
                                (a, b) -> a,
                                LinkedHashMap::new));
    }

    public Optional<FeatureFlagState> get(String key) {
        return Optional.ofNullable(states.get(key));
    }

    // =========================================================================
    // ESCRITA
    // =========================================================================

    /**
     * Altera uma flag.
     *
     * @throws IllegalArgumentException se a flag não existe
     * @throws IllegalStateException se a flag é readOnly
     */
    public void toggle(String key, boolean enabled) {
        FeatureFlagState current = states.get(key);
        if (current == null) {
            throw new IllegalArgumentException("Flag desconhecida: " + key);
        }
        if (current.readOnly()) {
            throw new IllegalStateException(
                    "Flag '" + key + "' é read-only e não pode ser alterada em runtime.");
        }
        if (current.enabled() == enabled) {
            log.debug("Flag '{}' já está em {}. Nada a fazer.", key, enabled);
            return;
        }

        states.put(key, current.withEnabled(enabled));
        log.info("🎛️ Flag alterada: {} = {}", key, enabled);

        if (persistEnabled) {
            persistToDisk();
        }
    }

    // =========================================================================
    // PERSISTÊNCIA
    // =========================================================================

    private void loadFromDisk() {
        if (!Files.exists(persistPath)) {
            log.debug("Arquivo de feature flags ainda não existe: {}", persistPath);
            return;
        }
        try {
            String json = Files.readString(persistPath);
            JavaType mapType =
                    objectMapper
                            .getTypeFactory()
                            .constructMapType(Map.class, String.class, FeatureFlagState.class);

            Map<String, FeatureFlagState> loaded = objectMapper.readValue(json, mapType);

            int aplicadas = 0;
            int ignoradasReadOnly = 0;

            for (Map.Entry<String, FeatureFlagState> entry : loaded.entrySet()) {
                String key = entry.getKey();
                FeatureFlagState persisted = entry.getValue();

                if (persisted == null) {
                    log.warn("⚠️ Flag '{}' presente no disco com valor null. Ignorando.", key);
                    continue;
                }

                // Só aplica se a flag já foi registrada (ou será registrada depois) e não é RO
                FeatureFlagState current = states.get(key);
                boolean isReadOnly =
                        (current != null && current.readOnly())
                                || (persisted != null && persisted.readOnly());

                if (isReadOnly) {
                    ignoradasReadOnly++;
                    continue;
                }

                // Aplica o valor do disco (sobrescreve o default)
                states.put(
                        key,
                        new FeatureFlagState(
                                key,
                                persisted.enabled(),
                                persisted.description() != null
                                        ? persisted.description()
                                        : (current != null ? current.description() : ""),
                                false));
                aplicadas++;
            }

            log.info(
                    "🎛️ Feature flags carregadas do disco: {} aplicadas, {} read-only ignoradas",
                    aplicadas,
                    ignoradasReadOnly);
        } catch (IOException | JacksonException e) {
            log.warn(
                    "⚠️ Falha ao ler feature flags de {}: {}. Usando defaults.",
                    persistPath,
                    e.getMessage());
        }
    }

    private void persistToDisk() {
        try {
            Files.createDirectories(persistPath.getParent());

            // Snapshot ordenado para o JSON ficar estável
            Map<String, FeatureFlagState> snapshot =
                    states.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .collect(
                                    Collectors.toMap(
                                            Map.Entry::getKey,
                                            Map.Entry::getValue,
                                            (a, b) -> a,
                                            LinkedHashMap::new));

            String json =
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot);

            // Escrita atômica: grava em .tmp e faz move
            Path tmp = persistPath.resolveSibling(persistPath.getFileName() + ".tmp");
            Files.writeString(tmp, json);
            Files.move(
                    tmp,
                    persistPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            log.debug("💾 Feature flags persistidas em {}", persistPath);
        } catch (IOException e) {
            log.error("❌ Falha ao persistir feature flags em {}", persistPath, e);
        }
    }
}
