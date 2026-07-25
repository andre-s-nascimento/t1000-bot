package net.ddns.adambravo79.tmill;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
        locations = "classpath:application-test.yml",
        properties = {
            "TMDB_READ_TOKEN=dummy",
            "TMDB_TOKEN=dummy",
            "GROQ_API_KEY=dummy",
            "WATCHMODE_API_KEY=dummy",
            "TELEGRAM_BOT_TOKEN=dummy",
            "TELEGRAM_OWNER_ID=0"
        })
class TmillApplicationTest {
    @Test
    void contextLoads() {}
}
