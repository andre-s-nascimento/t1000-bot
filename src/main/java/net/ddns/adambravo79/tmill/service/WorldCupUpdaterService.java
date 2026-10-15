/* (c) 2026 | 01/10/2026 */
package net.ddns.adambravo79.tmill.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.service.config.JsonConfigLoader;
import net.ddns.adambravo79.tmill.service.feature.FeatureFlagAdminService;

@Slf4j
@Service
public class WorldCupUpdaterService implements SchedulingConfigurer {

    private final StaticWorldCupService worldCupService;
    private final RestClient restClient;
    private final FeatureFlagAdminService featureFlags;
    private final JsonConfigLoader jsonConfigLoader;

    @Value(
            "${worldcup.update.url:https://raw.githubusercontent.com/openfootball/worldcup.json/master/2026/worldcup.json}")
    private String updateUrl;

    @Value("${worldcup.update.destination:/app/config/worldcup2026.json}")
    private String destinationPath;

    public WorldCupUpdaterService(
            StaticWorldCupService worldCupService,
            FeatureFlagAdminService featureFlags,
            RestClient restClient,
            JsonConfigLoader jsonConfigLoader) {
        this.worldCupService = worldCupService;
        this.featureFlags = featureFlags;
        this.restClient = restClient;
        this.jsonConfigLoader = jsonConfigLoader;
    }

    @PostConstruct
    public void init() {
        if (featureFlags.isEnabled("worldcup.update.enabled")) {
            log.info("🔄 Atualização automática da Copa ativada (fonte: {})", updateUrl);
        }
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(
                this::updateWorldCupData,
                ctx ->
                        new CronTrigger(getCron(), java.time.ZoneId.of("America/Sao_Paulo"))
                                .nextExecution(ctx));
    }

    private String getCron() {
        return jsonConfigLoader
                .loadConfig("config/worldcup-config.json", Map.class, "config/worldcup-config.json")
                .map(m -> (String) m.get("updateCron"))
                .orElse("0 0 3 * * *");
    }

    public void updateWorldCupData() {
        if (!featureFlags.isEnabled("worldcup.update.enabled")) {
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
