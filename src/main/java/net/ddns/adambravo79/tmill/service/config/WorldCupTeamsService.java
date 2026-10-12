/* (c) 2026 | 12/10/2026 */
package net.ddns.adambravo79.tmill.service.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço que carrega do JSON a tradução pt-BR e a bandeira (emoji) de cada time
 * da Copa do Mundo.
 *
 * <p>Centraliza o que antes era um par de {@code Map.ofEntries(...)} gigantes
 * dentro do {@link net.ddns.adambravo79.tmill.service.WorldCupSchedulerService}.
 * Permite recarga em runtime sem recompilar.
 */
@Slf4j
@Service
public class WorldCupTeamsService {

    private final JsonConfigLoader jsonConfigLoader;

    @Value("${worldcup.teams.file:classpath:worldcup-teams.json}")
    private String teamsFile;

    private volatile Map<String, TeamInfo> teams = Map.of();

    public WorldCupTeamsService(JsonConfigLoader jsonConfigLoader) {
        this.jsonConfigLoader = jsonConfigLoader;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    /** Recarrega os times do JSON. Em caso de falha, mantém o estado anterior. */
    public synchronized void reload() {
        try {
            var loaded =
                    jsonConfigLoader.loadConfig(
                            "worldcup-teams.json",
                            Map.class,
                            teamsFile != null && !teamsFile.isBlank() ? teamsFile : null);

            if (loaded.isEmpty()) {
                log.warn("⚠️ Nenhum arquivo de times da Copa encontrado.");
                return;
            }

            Object teamsNode = loaded.get().get("teams");
            if (!(teamsNode instanceof Map<?, ?> rawMap)) {
                log.warn("⚠️ Chave 'teams' ausente ou inválida no JSON.");
                return;
            }

            Map<String, TeamInfo> novo = new ConcurrentHashMap<>();
            rawMap.forEach(
                    (k, v) -> {
                        if (k == null || !(v instanceof Map<?, ?> info)) return;
                        String namePt =
                                info.get("namePt") != null ? info.get("namePt").toString() : null;
                        String flag = info.get("flag") != null ? info.get("flag").toString() : null;
                        if (namePt != null && flag != null) {
                            novo.put(k.toString().toLowerCase(), new TeamInfo(namePt, flag));
                        }
                    });

            this.teams = Map.copyOf(novo);
            log.info("✅ Times da Copa carregados: {} de {}", teams.size(), teamsFile);
        } catch (Exception e) {
            log.error("❌ Falha ao carregar times da Copa — mantendo os anteriores", e);
        }
    }

    /** Traduz o nome de um time (com fallback para substring e, em último caso, o próprio input). */
    public String translateTeam(String teamName) {
        if (teamName == null || teamName.isBlank()) return "?";
        String key = teamName.toLowerCase().trim();

        TeamInfo exact = teams.get(key);
        if (exact != null) return exact.namePt();

        // Fallback: contains (comportamento antigo preservado)
        for (var entry : teams.entrySet()) {
            if (key.contains(entry.getKey())) {
                return entry.getValue().namePt();
            }
        }
        return teamName;
    }

    /** Bandeira (emoji) de um time. */
    public String flagEmoji(String team) {
        if (team == null || team.isBlank()) return "🏳️";
        String key = team.toLowerCase().trim();

        TeamInfo exact = teams.get(key);
        if (exact != null) return exact.flag();

        for (var entry : teams.entrySet()) {
            if (key.contains(entry.getKey())) {
                return entry.getValue().flag();
            }
        }
        log.warn("Bandeira nao encontrada para: {}", team);
        return "🏳️";
    }

    /** Exposição para debug/testes. */
    public Map<String, TeamInfo> getAll() {
        return teams;
    }

    public record TeamInfo(String namePt, String flag) {}
}
