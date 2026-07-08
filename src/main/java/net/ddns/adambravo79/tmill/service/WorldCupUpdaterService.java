package net.ddns.adambravo79.tmill.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class WorldCupUpdaterService {

    private final StaticWorldCupService worldCupService;
    private final RestClient restClient;

    @Value("${worldcup.update.enabled:false}")
    private boolean updateEnabled;

    @Value(
            "${worldcup.update.url:https://raw.githubusercontent.com/openfootball/worldcup.json/master/2026/worldcup.json}")
    private String updateUrl;

    @Value("${worldcup.update.destination:/app/config/worldcup2026.json}")
    private String destinationPath;

    public WorldCupUpdaterService(StaticWorldCupService worldCupService) {
        this.worldCupService = worldCupService;
        this.restClient = RestClient.builder().build();
    }

    @PostConstruct
    public void init() {
        if (updateEnabled) {
            log.info("🔄 Atualização automática da Copa ativada (fonte: {})", updateUrl);
        }
    }

    @Scheduled(cron = "${worldcup.update.cron:0 0 3 * * *}", zone = "America/Sao_Paulo")
    public void updateWorldCupData() {
        if (!updateEnabled) {
            log.debug("Atualização automática desativada");
            return;
        }
        try {
            log.info("🔄 Baixando dados atualizados da Copa...");
            byte[] jsonData = restClient.get().uri(updateUrl).retrieve().body(byte[].class);

            if (jsonData == null || jsonData.length == 0) {
                log.warn("Dados vazios ou nulos recebidos da URL: {}", updateUrl);
                return;
            }

            Path destPath = Paths.get(destinationPath);
            Files.createDirectories(destPath.getParent());
            Files.write(
                    destPath,
                    jsonData,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            log.info("✅ Arquivo JSON atualizado com sucesso ({} bytes)", jsonData.length);
            worldCupService.loadMatches();

        } catch (IOException e) {
            log.error("Erro ao salvar arquivo JSON: {}", e.getMessage(), e);
        } catch (RestClientException e) {
            log.error("Erro ao baixar JSON: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Erro inesperado na atualização: {}", e.getMessage(), e);
        }
    }

    public void forceUpdate() {
        updateWorldCupData();
    }
}
