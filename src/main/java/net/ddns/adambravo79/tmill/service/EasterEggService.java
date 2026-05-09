/* (c) 2026 | 06/05/2026 */
package net.ddns.adambravo79.tmill.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EasterEggService {

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${easter-egg.file:classpath:easter-eggs.json}")
    private String easterEggFileLocation;

    private Map<Long, String> easterEggs = new HashMap<>();

    public EasterEggService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void loadEasterEggs() {
        try {
            Resource resource = resourceLoader.getResource(easterEggFileLocation);
            if (!resource.exists()) {
                log.warn(
                        "Arquivo de easter eggs não encontrado: {}. Nenhum easter egg será"
                                + " carregado.",
                        easterEggFileLocation);
                return;
            }
            try (InputStream is = resource.getInputStream()) {
                if (is.available() == 0) {
                    log.warn("Arquivo de easter eggs está vazio: {}", easterEggFileLocation);
                    return;
                }
                Map<String, String> rawMap =
                        objectMapper.readValue(is, new TypeReference<Map<String, String>>() {});
                easterEggs.clear();
                for (Map.Entry<String, String> entry : rawMap.entrySet()) {
                    try {
                        Long movieId = Long.parseLong(entry.getKey());
                        easterEggs.put(movieId, entry.getValue());
                    } catch (NumberFormatException e) {
                        log.warn("Chave inválida no easter-eggs.json: {}", entry.getKey());
                    }
                }
                log.info(
                        "✅ Carregados {} easter eggs do arquivo {}",
                        easterEggs.size(),
                        easterEggFileLocation);
            }
        } catch (IOException e) {
            log.error("Erro ao carregar easter eggs: {}", e.getMessage(), e);
            // não lança exceção, apenas loga erro e mantém mapa vazio
        }
    }

    public Optional<String> getEasterEgg(long movieId) {
        return Optional.ofNullable(easterEggs.get(movieId));
    }

    public void reload() {
        loadEasterEggs();
    }
}
