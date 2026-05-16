/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.config;

import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class AppConfig {

    @Value("${bot.token}")
    private String botToken;

    @Bean
    public String botToken() {
        // ⚠️ Nunca logar o token completo por segurança
        log.info("🔑 Bot token inicializado (mascarado)");
        return this.botToken;
    }

    @Bean
    public AsyncTaskExecutor applicationTaskExecutor() {
        // Isso força o Spring a usar Virtual Threads para qualquer @Async
        log.info("⚙️ Configurando AsyncTaskExecutor com Virtual Threads");
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
