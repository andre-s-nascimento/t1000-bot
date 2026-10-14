package net.ddns.adambravo79.tmill.config;

import java.util.concurrent.TimeUnit;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
@EnableCaching
public class CaffeineConfig {

    @Bean
    public Cache<String, String> providerCache() {
        return Caffeine.newBuilder().expireAfterWrite(24, TimeUnit.HOURS).maximumSize(500).build();
    }
}
