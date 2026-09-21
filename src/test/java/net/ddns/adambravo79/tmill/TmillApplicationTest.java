package net.ddns.adambravo79.tmill;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import net.ddns.adambravo79.tmill.integration.BaseIntegrationTest;

@ActiveProfiles("test")
class TmillApplicationTest extends BaseIntegrationTest {

    @Test
    void contextLoads() {
        // Contexto carregado com sucesso utilizando os containers efêmeros
    }
}
