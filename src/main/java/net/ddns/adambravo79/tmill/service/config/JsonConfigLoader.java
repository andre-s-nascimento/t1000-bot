/* (c) 2026 | 30/09/2026 */
package net.ddns.adambravo79.tmill.service.config;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import net.ddns.adambravo79.tmill.exception.ConfigLoadException;
import tools.jackson.databind.ObjectMapper;

/**
 * Utilitário para carregamento de arquivos JSON com estratégia de fallback em 4 etapas.
 */
@Component
public class JsonConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(JsonConfigLoader.class);
    private final ObjectMapper objectMapper;

    public JsonConfigLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Tenta carregar e converter um JSON para o tipo especificado.
     *
     * @param fileName Nome do arquivo (ex: "digest-personas.json") ou caminho relativo
     * @param targetType Classe de destino (Record ou DTO)
     * @param overridePath Caminho opcional informado via property/env (pode ser null)
     * @param <T> Tipo do objeto de saída
     * @return Optional contendo o objeto carregado ou Optional.empty() se não encontrado
     * @throws ConfigLoadException se o arquivo for encontrado mas o JSON estiver inválido
     */
    public <T> Optional<T> loadConfig(String fileName, Class<T> targetType, String overridePath) {
        // 1. Override via Property/Env
        if (overridePath != null && !overridePath.isBlank()) {
            File overrideFile = new File(overridePath);
            if (overrideFile.exists() && overrideFile.isFile()) {
                log.info("Carregando config de override: {}", overrideFile.getAbsolutePath());
                return Optional.of(parseFile(overrideFile, targetType));
            }
        }

        // 2. Diretório local ./config/
        Path localPath = Paths.get("./config", fileName);
        if (localPath.toFile().exists() && localPath.toFile().isFile()) {
            log.info("Carregando config de diretório local: {}", localPath.toAbsolutePath());
            return Optional.of(parseFile(localPath.toFile(), targetType));
        }

        // 3. Diretório do container /app/config/
        Path appConfigPath = Paths.get("/app/config", fileName);
        if (appConfigPath.toFile().exists() && appConfigPath.toFile().isFile()) {
            log.info("Carregando config de diretório do app: {}", appConfigPath.toAbsolutePath());
            return Optional.of(parseFile(appConfigPath.toFile(), targetType));
        }

        // 4. Classpath (resources)
        String resourcePath = fileName.startsWith("/") ? fileName : "/" + fileName;
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is != null) {
                log.info("Carregando config do classpath: {}", resourcePath);
                return Optional.of(objectMapper.readValue(is, targetType));
            }
        } catch (Exception e) {
            throw new ConfigLoadException(resourcePath, "Erro ao ler JSON do classpath", e);
        }

        log.warn("Arquivo de configuração não encontrado em nenhuma das origens: {}", fileName);
        return Optional.empty();
    }

    private <T> T parseFile(File file, Class<T> targetType) {
        try {
            return objectMapper.readValue(file, targetType);
        } catch (Exception e) {
            throw new ConfigLoadException(
                    file.getAbsolutePath(), "JSON malformado ou incompatível com o modelo", e);
        }
    }
}
