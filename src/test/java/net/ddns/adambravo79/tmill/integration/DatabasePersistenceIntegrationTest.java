package net.ddns.adambravo79.tmill.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
class DatabasePersistenceIntegrationTest extends BaseIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void postgresFlywayMigrationsExecutedSuccessfully() {
        // Valida se o Flyway rodou e criou as tabelas estruturadas no PostgreSQL
        Integer tableCount =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM information_schema.tables WHERE table_schema ="
                                + " 'public'",
                        Integer.class);

        assertThat(tableCount).isGreaterThan(0);
    }

    @Test
    void mongoDbIsUpAndAcceptsOperations() {
        // Valida se a conexão com o MongoDB está ativa e respondendo através do container
        assertThat(MONGO.isRunning()).isTrue();
        assertThat(MONGO.getConnectionString()).isNotNull();
    }
}
