/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.controller.TelegramController;

/**
 * Runner responsável por inicializar e registrar o bot T1000 no Telegram.
 *
 * <p>Principais responsabilidades: - Registrar o {@link TelegramController} no {@link
 * TelegramBotsLongPollingApplication}. - Manter a thread principal viva para evitar encerramento do
 * {@link CommandLineRunner}.
 *
 * <p>Este componente é executado automaticamente na inicialização da aplicação Spring Boot.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BotRunner implements CommandLineRunner {

    private final TelegramController telegramController;
    private final String botToken;
    private boolean keepAlive = true;

    /**
     * Inicializa o bot e registra o controller no Telegram.
     *
     * @param args argumentos de linha de comando (não utilizados).
     * @throws Exception em caso de falha no registro do bot.
     */
    @Override
    public void run(String... args) throws Exception {
        log.info("🤖 Iniciando registro manual do bot T1000...");

        @SuppressWarnings("resource")
        TelegramBotsLongPollingApplication botApp = new TelegramBotsLongPollingApplication();

        // Registra o Controller
        botApp.registerBot(botToken, telegramController);

        log.info("🚀 T1000 operacional e aguardando comandos!");

        // Mantém a thread principal viva para o CommandLineRunner não encerrar
        if (keepAlive) {
            Thread.currentThread().join();
        }
    }

    /**
     * Define se a thread principal deve permanecer viva após inicialização.
     *
     * @param keepAlive {@code true} para manter viva, {@code false} para encerrar após registro.
     */
    void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }
}
