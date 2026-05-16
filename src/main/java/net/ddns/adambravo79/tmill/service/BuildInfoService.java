/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.service;

import java.io.InputStream;
import java.util.Properties;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BuildInfoService {

    @PostConstruct
    public void logBuildInfo() {
        try {
            ClassPathResource resource = new ClassPathResource("build-info.properties");
            if (resource.exists()) {
                Properties props = new Properties();
                try (InputStream is = resource.getInputStream()) {
                    props.load(is);
                }
                log.info(
                        "🚀 Build info - branch: {}, commit: {}, data: {}",
                        props.getProperty("build.branch", "?"),
                        props.getProperty("build.commit", "?"),
                        props.getProperty("build.time", "?"));
            } else {
                log.info("🚀 Build info não disponível (build-info.properties não encontrado)");
            }
        } catch (Exception e) {
            log.warn("Não foi possível ler informações de build", e);
        }
    }
}
