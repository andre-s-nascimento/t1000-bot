package net.ddns.adambravo79.tmill.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class KafkaConfig {

    @PostConstruct
    public void init() {
        log.info("🔧 KafkaConfig carregado");
    }

    @Bean
    public NewTopic audioReceivedTopic() {
        log.info("🔧 Criando tópico t1000.audio.received");
        return TopicBuilder.name("t1000.audio.received").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic audioProcessedTopic() {
        log.info("🔧 Criando tópico t1000.audio.processed");
        return TopicBuilder.name("t1000.audio.processed").partitions(1).replicas(1).build();
    }
}
