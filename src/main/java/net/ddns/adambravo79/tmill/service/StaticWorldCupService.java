/* (c) 2026 | 11/06/2026 */
// src/main/java/net/ddns/adambravo79/tmill/service/StaticWorldCupService.java
package net.ddns.adambravo79.tmill.service;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.model.WorldCupMatch;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class StaticWorldCupService {

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${worldcup.data.file:classpath:worldcup2026.json}")
    private String dataFileLocation;

    private final Map<LocalDate, List<WorldCupMatch>> matchesByDate = new ConcurrentHashMap<>();

    public StaticWorldCupService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void loadMatches() {
        log.info("▶️ Carregando dados da Copa...");
        try {
            Resource resource = resourceLoader.getResource(dataFileLocation);
            log.info("Arquivo resolvido: {}", resource.exists() ? "EXISTE" : "NÃO EXISTE");
            log.info("Caminho/URL: {}", resource.getURL());
            if (!resource.exists()) {
                log.warn("Arquivo da Copa não encontrado: {}", dataFileLocation);
                return;
            }
            try (InputStream is = resource.getInputStream()) {
                Map<String, Object> root = objectMapper.readValue(is, new TypeReference<>() {});
                log.info("Arquivo JSON carregado, root keys: {}", root.keySet());
                List<WorldCupMatch> allMatches =
                        objectMapper.convertValue(
                                root.get("matches"), new TypeReference<List<WorldCupMatch>>() {});
                log.info("Total de matches lidos: {}", allMatches.size());
                matchesByDate.clear();
                for (WorldCupMatch match : allMatches) {
                    matchesByDate
                            .computeIfAbsent(match.getLocalDate(), k -> new ArrayList<>())
                            .add(match);
                }

                if (allMatches != null && !allMatches.isEmpty()) {
                    WorldCupMatch sample =
                            allMatches.stream()
                                    .filter(m -> m.score() != null)
                                    .findFirst()
                                    .orElse(null);
                    if (sample != null) {
                        log.info(
                                "✅ Exemplo com placar: {} x {} = {}x{}",
                                sample.homeTeam(),
                                sample.awayTeam(),
                                sample.score().ft().get(0),
                                sample.score().ft().get(1));
                    } else {
                        log.warn("⚠️ Nenhum jogo com placar encontrado no JSON!");
                    }
                }
                log.info(
                        "✅ Carregados {} jogos da Copa 2026 ({} datas diferentes)",
                        allMatches.size(),
                        matchesByDate.size());
            }
        } catch (Exception e) {
            log.error("Falha ao carregar dados da Copa: {}", e.getMessage(), e);
        }
    }

    public void reload() {
        loadMatches();
    }

    public List<WorldCupMatch> getMatchesForDay(LocalDate date) {
        ZoneId brazilZone = ZoneId.of("America/Sao_Paulo");
        log.debug("Buscando jogos para data {} – disponíveis: {}", date, matchesByDate.keySet());
        return matchesByDate.getOrDefault(date, Collections.emptyList()).stream()
                .sorted(Comparator.comparing(m -> m.getMatchDateTime(brazilZone)))
                .toList(); // substitui collect(Collectors.toList())
    }

    public Optional<WorldCupMatch> getFirstMatchOfDay(LocalDate date) {
        return getMatchesForDay(date).stream().findFirst();
    }

    public boolean hasMatches(LocalDate date) {
        return matchesByDate.containsKey(date) && !matchesByDate.get(date).isEmpty();
    }

    public Map<LocalDate, List<WorldCupMatch>> getAllMatches() {
        return matchesByDate;
    }
}
