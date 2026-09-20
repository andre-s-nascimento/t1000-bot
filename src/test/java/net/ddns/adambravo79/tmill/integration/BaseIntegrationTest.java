package net.ddns.adambravo79.tmill.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:15-alpine")
                    .withDatabaseName("t1000_test_db")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @Container
    static final GenericContainer<?> kafka =
            new GenericContainer<>("confluentinc/cp-kafka:7.8.0")
                    .withEnv("KAFKA_PROCESS_ROLES", "broker,controller")
                    .withEnv("KAFKA_NODE_ID", "1")
                    .withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@localhost:9093")
                    .withEnv(
                            "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP",
                            "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT")
                    .withEnv(
                            "KAFKA_LISTENERS", "PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093")
                    .withEnv("KAFKA_ADVERTISED_LISTENERS", "PLAINTEXT://localhost:9092")
                    .withEnv("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER")
                    .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "PLAINTEXT")
                    .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
                    .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
                    .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
                    .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0")
                    .withEnv("CLUSTER_ID", "MkU3OEVBNTcwNTJENDM2Qk")
                    .withExposedPorts(9092)
                    .waitingFor(
                            new org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy()
                                    .withRegEx(".*Transitioning from RECOVERY to RUNNING.*")
                                    .withTimes(1));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add(
                "spring.kafka.bootstrap-servers",
                () -> kafka.getHost() + ":" + kafka.getMappedPort(9092));
    }
}
