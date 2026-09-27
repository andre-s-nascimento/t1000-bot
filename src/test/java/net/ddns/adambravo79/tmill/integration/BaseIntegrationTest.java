package net.ddns.adambravo79.tmill.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIntegrationTest {

    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:15-alpine")
                    .withDatabaseName("test")
                    .withUsername("test")
                    .withPassword("test");

    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.2.1"));

    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:6.0");

    static {
        // Os containers são iniciados uma única vez por JVM de testes.
        //
        // Não usar @BeforeAll/@AfterAll aqui.
        // O Spring pode reutilizar o ApplicationContext entre classes,
        // portanto os endpoints dos containers precisam permanecer estáveis
        // durante toda a execução da suíte.

        POSTGRES.start();
        KAFKA.start();
        MONGO.start();
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) { // --- PostgreSQL ---
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // --- MongoDB ---
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);

        // --- Kafka ---
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.properties.security.protocol", () -> "PLAINTEXT");
        registry.add("spring.kafka.properties.sasl.mechanism", () -> "");
        registry.add("spring.kafka.properties.sasl.jaas.config", () -> "");
        registry.add("spring.kafka.properties.ssl.truststore.location", () -> "");
        registry.add("spring.kafka.properties.ssl.truststore.password", () -> "");
        registry.add("spring.kafka.properties.ssl.truststore.type", () -> "");

        // --- Fallbacks para @Value sem default ---
        registry.add("telegram.owner.id", () -> "0");
        registry.add("podcast.publish.chat-id", () -> "-123456789");
        registry.add("digest.chat-ids", () -> "");
    }
}
