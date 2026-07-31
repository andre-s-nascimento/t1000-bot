package net.ddns.adambravo79.tmill;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
        locations = "classpath:application-test.yml",
        properties = {
            "TMDB_READ_TOKEN=dummy",
            "TMDB_TOKEN=dummy",
            "GROQ_API_KEY=dummy",
            "WATCHMODE_API_KEY=dummy",
            "TELEGRAM_BOT_TOKEN=dummy",
            "TELEGRAM_OWNER_ID=123456789",
            "podcast.publish.chat-id=123456789",
            "podcast.target.user-id=123456789",
            "podcast.script.max-tokens=3000",
            "podcast.publish.cron=0 0 22 * * 0",
            "spring.task.scheduling.enabled=false",
            "podcast.schedule.cron=0 0 22 * * 0"
        })
class TmillApplicationTest {
    @Test
    void contextLoads() {}
}
