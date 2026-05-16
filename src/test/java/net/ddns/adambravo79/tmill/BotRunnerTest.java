/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

import net.ddns.adambravo79.tmill.controller.TelegramController;

class BotRunnerTest {

    @Test
    void deveRegistrarBotComSucesso() throws Exception {
        TelegramController controller = mock(TelegramController.class);

        try (MockedConstruction<TelegramBotsLongPollingApplication> mocked =
                Mockito.mockConstruction(TelegramBotsLongPollingApplication.class)) {

            BotRunner runner = new BotRunner(controller, "token-teste");
            runner.setKeepAlive(false); // evita bloqueio no join

            runner.run();

            TelegramBotsLongPollingApplication botApp = mocked.constructed().get(0);
            verify(botApp).registerBot("token-teste", controller);
        }
    }
}
