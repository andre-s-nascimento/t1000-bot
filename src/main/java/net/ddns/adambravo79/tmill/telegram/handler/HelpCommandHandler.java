/* (c) 2026 */
package net.ddns.adambravo79.tmill.telegram.handler;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;

import io.ksilisk.telegrambot.core.executor.TelegramBotExecutor;
import io.ksilisk.telegrambot.core.handler.update.command.CommandUpdateHandler;
import io.ksilisk.telegrambot.core.update.Updates;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.telegram.core.GroupAuthorizationService;

@Component
@Slf4j
public class HelpCommandHandler implements CommandUpdateHandler {

    private final TelegramBotExecutor executor;
    private final GroupAuthorizationService authService;

    public HelpCommandHandler(TelegramBotExecutor executor, GroupAuthorizationService authService) {
        this.executor = executor;
        this.authService = authService;
        log.info("✅ HelpCommandHandler registrado");
    }

    @Override
    public void handle(Update update) {
        // Verifica autorização (se for grupo não autorizado, ignora)
        if (!authService.isAuthorized(update)) {
            return;
        }

        long chatId = Updates.chatId(update);

        String textoAjuda =
                """
                🤖 <b>Central de Ajuda do T-1000</b>

                Aqui está tudo o que eu posso fazer:

                🎬 <b>Filmes</b>
                <code>t1000 buscar &lt;nome&gt;</code> — Busca sinopse, elenco e onde assistir.
                <code>t1000 estreias da semana</code> — Lançamentos nos streamings.

                🎙️ <b>Áudio</b>
                Envie um áudio ou mensagem de voz. Eu transcrevo e refino o texto.

                ⚽ <b>Copa 2026</b>
                <code>t1000 jogos</code> — Jogos do dia.
                <code>t1000 resultados [ontem/hoje]</code> — Placar dos jogos.

                🎂 <b>Aniversários</b>
                <code>t1000 registrar aniversário DD/MM</code> — Anoto e te parabenizo no dia.

                💡 <b>Ideias</b>
                <code>t1000 anotar ideia &lt;texto&gt;</code> — Salva sua sugestão.

                📊 <b>Diversão</b>
                <code>t1000 bom dia</code> — Saudação com animações aleatórias.

                <i>Desenvolvido com ☕ e circuitos da Skynet.</i>
                """;

        executor.execute(new SendMessage(chatId, textoAjuda).parseMode(ParseMode.HTML));
    }

    @Override
    public Set<String> commands() {
        return Set.of("/help", "/ajuda", "/start");
    }
}
