/* (c) 2026 | 04/11/2026 */
package net.ddns.adambravo79.tmill.telegram.handler;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.model.Update;

import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.model.SalaVipMovie;
import net.ddns.adambravo79.tmill.service.SalaVipService;
import net.ddns.adambravo79.tmill.telegram.core.GroupAuthorizationService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.TelegramUtils;
import net.ddns.adambravo79.tmill.util.LogSanitizer;

@Slf4j
@Component
@SuppressWarnings("java:S3457")
public class SalaVipHandler extends AbstractCommandHandler {

    public static final String PREFIXO = "t1000 salavip";
    public static final String PREFIXO_ALT = "t1000 sala vip";

    private static final int FLAGS =
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

    private static final Pattern REGEX_NUMERO =
            Pattern.compile(
                    "t1000\\s+sala\\s*vip\\s*+(?:n[uú]mer[oó]?|n[º°]?|#)?\\s*+(-?\\d+)", FLAGS);

    private static final Pattern REGEX_TITULO =
            Pattern.compile("t1000\\s+sala\\s*vip\\s*+(?:tem\\s*+)?filme\\s*+(.+)", FLAGS);

    private static final Pattern REGEX_EPISODIO =
            Pattern.compile("t1000\\s+sala\\s*vip\\s+ep(?:is[oó]dio)?\\s*+(\\d+)", FLAGS);

    private final SalaVipService salaVipService;

    public SalaVipHandler(
            TelegramFacade telegramFacade,
            GroupAuthorizationService authService,
            TelegramUtils utils,
            SalaVipService salaVipService) {
        super(telegramFacade, authService, utils);
        this.salaVipService = salaVipService;
    }

    @Override
    public String getPrefixo() {
        return PREFIXO;
    }

    @Override
    protected void handleCommand(Update update, String textoNormalizado) {
        String rawText = textoBruto(update).trim();
        long chatId = chatId(update);

        Matcher matcherTitulo = REGEX_TITULO.matcher(rawText);
        if (matcherTitulo.matches()) {
            String termo = matcherTitulo.group(1).trim();
            buscarPorTitulo(chatId, termo);
            return;
        }

        Matcher matcherEp = REGEX_EPISODIO.matcher(rawText);
        if (matcherEp.matches()) {
            buscarPorEpisodio(chatId, Integer.parseInt(matcherEp.group(1)));
            return;
        }

        Matcher matcherNumero = REGEX_NUMERO.matcher(rawText);
        if (matcherNumero.matches()) {
            int numero = Integer.parseInt(matcherNumero.group(1));
            if (numero < 1) {
                sendPlain(chatId, "❌ O número do episódio da Sala VIP deve ser maior que zero.");
                return;
            }
            buscarPorNumero(chatId, numero);
            return;
        }

        log.warn(
                "SalaVipHandler: texto não casou com nenhum padrão (chatId={}) text='{}'",
                LogSanitizer.sanitizeId(chatId),
                LogSanitizer.sanitize(rawText));
    }

    public String getPrefixoAlternativo() {
        return PREFIXO_ALT;
    }

    private void buscarPorNumero(long chatId, int numero) {
        Optional<SalaVipMovie> movieOpt = salaVipService.buscarPrimeiroPorEpisodio(numero);

        if (movieOpt.isEmpty()) {
            sendPlain(
                    chatId,
                    "❌ Não encontrei nenhum filme no episódio #" + numero + " da Sala VIP.");
            return;
        }

        SalaVipMovie movie = movieOpt.get();
        String notaStr =
                (movie.noteText() != null && !movie.noteText().isBlank())
                        ? "\n\n" + utils.escapeHtml(movie.noteText())
                        : "";

        String resposta =
                String.format(
                        "✅ Sim! O filme <b>%s</b> (%d) é o episódio número <b>%d</b> do Sala"
                                + " VIP.%s\n"
                                + "🔗 <a href=\"%s\">Link no Letterboxd</a>",
                        utils.escapeHtml(movie.title()),
                        movie.year(),
                        // Proteção extra caso o episódio venha nulo do banco
                        movie.salaVipEpisode() != null ? movie.salaVipEpisode() : "?",
                        notaStr,
                        movie.letterboxdUrl());
        sendHtml(chatId, resposta);
    }

    private void buscarPorEpisodio(long chatId, int episodio) {
        List<SalaVipMovie> movies = salaVipService.buscarPorEpisodio(episodio);

        if (movies.isEmpty()) {
            sendPlain(
                    chatId,
                    "❌ Não encontrei nenhum filme no episódio #" + episodio + " da Sala VIP.");
            return;
        }

        if (movies.size() == 1) {
            SalaVipMovie movie = movies.get(0);
            String resposta =
                    String.format(
                            "✅ O episódio <b>#%d</b> da Sala VIP é o filme <b>%s</b> (%d),"
                                    + " Sala VIP #%s.\n"
                                    + "🔗 <a href=\"%s\">Link no Letterboxd</a>",
                            episodio,
                            utils.escapeHtml(movie.title()),
                            movie.year(),
                            movie.salaVipEpisode() != null ? movie.salaVipEpisode() : "?",
                            movie.letterboxdUrl());
            sendHtml(chatId, resposta);
            return;
        }

        StringBuilder sb =
                new StringBuilder(
                        String.format(
                                "🎬 O episódio <b>#%d</b> da Sala VIP cobre %d filmes:\n\n",
                                episodio, movies.size()));

        for (SalaVipMovie m : movies) {
            sb.append(
                    String.format(
                            "• <b>%s</b> (%d) — Sala VIP #%s\n",
                            utils.escapeHtml(m.title()),
                            m.year(),
                            m.salaVipEpisode() != null ? m.salaVipEpisode() : "?"));
        }

        sendHtml(chatId, sb.toString());
    }

    private void buscarPorTitulo(long chatId, String termo) {
        List<SalaVipMovie> movies = salaVipService.buscarPorTitulo(termo);

        if (movies.isEmpty()) {
            sendPlain(
                    chatId,
                    "❌ Não encontrei nenhum filme com o título '" + termo + "' na Sala VIP.");
            return;
        }

        if (movies.size() == 1) {
            SalaVipMovie movie = movies.get(0);

            boolean matchNaNota =
                    !movie.title().toLowerCase().contains(termo.toLowerCase())
                            && movie.noteText() != null
                            && movie.noteText().toLowerCase().contains(termo.toLowerCase());

            String aviso = matchNaNota ? "\n<i>(Encontrado pela sinopse/nota)</i>" : "";

            String resposta =
                    String.format(
                            "✅ Sim! O filme <b>%s</b> (%d) está na Sala VIP sob o episódio"
                                    + " <b>#%s</b>.%s\n"
                                    + "🔗 <a href=\"%s\">Link no Letterboxd</a>",
                            utils.escapeHtml(movie.title()),
                            movie.year(),
                            movie.salaVipEpisode() != null ? movie.salaVipEpisode() : "?",
                            aviso,
                            movie.letterboxdUrl());
            sendHtml(chatId, resposta);
            return;
        }

        StringBuilder sb =
                new StringBuilder("🤔 Encontrei mais de um filme com esse nome na Sala VIP:\n\n");

        int limit = Math.min(movies.size(), 5);
        for (int i = 0; i < limit; i++) {
            SalaVipMovie m = movies.get(i);
            sb.append(
                    String.format(
                            "• <b>%s</b> (%d) — Sala VIP #%s\n",
                            utils.escapeHtml(m.title()),
                            m.year(),
                            m.salaVipEpisode() != null ? m.salaVipEpisode() : "?"));
        }

        if (movies.size() > 5) {
            sb.append("\n<i>(mostrando os 5 primeiros resultados)</i>");
        }

        sendHtml(chatId, sb.toString());
    }
}
