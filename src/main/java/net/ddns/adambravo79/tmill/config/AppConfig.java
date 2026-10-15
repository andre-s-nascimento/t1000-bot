/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.config;

import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Configuration
public class AppConfig {

    @Value("${telegram.bot.token}")
    private String botToken;

    @Bean
    public String botToken() {
        // ⚠️ Nunca logar o token completo por segurança
        log.info("🔑 Bot token inicializado (mascarado)");
        return this.botToken;
    }

    @Bean
    @Primary
    public AsyncTaskExecutor applicationTaskExecutor() {
        // Isso força o Spring a usar Virtual Threads para qualquer @Async
        log.info("⚙️ Configurando AsyncTaskExecutor com Virtual Threads");
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }

    @Bean
    public RestClient restClient() {
        return RestClient.builder().build();
    }

    @Bean
    public ObjectMapper toolsObjectMapper() {
        return JsonMapper.builder().build();
    }
}
