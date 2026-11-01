package net.ddns.adambravo79.tmill.service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import net.ddns.adambravo79.tmill.model.SalaVipMovie;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.TelegramUtils;

@SuppressWarnings({"java:S5855", "java:S8786", "java:S5854"})
@Component
@RequiredArgsConstructor
public class SalaVipHandler {

    private final SalaVipService salaVipService;
    private final TelegramFacade telegramFacade;
    private final TelegramUtils utils;

    private static final int FLAGS =
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

    private static final Pattern REGEX_NUMERO =
            Pattern.compile(
                    "t1000\\s+sala\\s*vip\\s+(?:(?:numero|n[uú]mero|n[º°]?|#)\\s*)?(\\d+)", FLAGS);

    private static final Pattern REGEX_TITULO =
            Pattern.compile("t1000\\s+sala\\s*vip\\s+(?:tem\\s+filme|filme)\\s+(.+)", FLAGS);

    // A alternância é intencional para cobrir grafias com e sem acento; suprimir aviso de
    // redundância
    @SuppressWarnings("java:S5855")
    private static final Pattern REGEX_EPISODIO =
            Pattern.compile("t1000\\s+sala\\s*vip\\s+(?:episodio|episódio|ep)\\s*(\\d+)", FLAGS);

    public boolean handle(long chatId, String rawText) {
        String text = rawText.trim();

        // Usamos matches() que exige que toda a string bata, eliminando a necessidade de ^ e $
        // Isso resolve o aviso S8786 (backtracking)
        Matcher matcherNumero = REGEX_NUMERO.matcher(text);
        if (matcherNumero.matches()) {
            Integer numero = Integer.parseInt(matcherNumero.group(1));
            buscarPorNumero(chatId, numero);
            return true;
        }

        Matcher matcherTitulo = REGEX_TITULO.matcher(text);
        if (matcherTitulo.matches()) {
            String termo = matcherTitulo.group(1).trim();
            buscarPorTitulo(chatId, termo);
            return true;
        }

        Matcher matcherEp = REGEX_EPISODIO.matcher(text);
        if (matcherEp.matches()) {
            buscarPorEpisodio(chatId, Integer.parseInt(matcherEp.group(1)));
            return true;
        }

        return false;
    }

    @SuppressWarnings("java:S3457") //
    private void buscarPorNumero(long chatId, Integer numero) {
        Optional<SalaVipMovie> movieOpt = salaVipService.buscarPorNumero(numero);

        if (movieOpt.isPresent()) {
            SalaVipMovie movie = movieOpt.get();
            String resposta =
                    String.format(
                            "✅ Sim, a <b>Sala VIP #%d</b> é o filme <b>%s</b> (%d).\n"
                                    + "🔗 <a href=\"%s\">Link no Letterboxd</a>",
                            numero,
                            utils.escapeHtml(movie.title()),
                            movie.year(),
                            movie.letterboxdUrl());
            telegramFacade.enviarMensagemHtml(chatId, resposta);
        } else {
            telegramFacade.enviarMensagem(
                    chatId,
                    "❌ Não encontrei nenhum filme com a Sala VIP #" + numero + " na lista atual.");
        }
    }

    @SuppressWarnings("java:S3457")
    private void buscarPorEpisodio(long chatId, Integer episodio) {
        List<SalaVipMovie> movies = salaVipService.buscarPorEpisodio(episodio);

        if (movies.isEmpty()) {
            telegramFacade.enviarMensagem(
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
                            movie.salaVipNumber() != null ? movie.salaVipNumber() : "?",
                            movie.letterboxdUrl());
            telegramFacade.enviarMensagemHtml(chatId, resposta);
            return;
        }

        // Episódio pode cobrir 2+ filmes (ex.: episódio 15 = Mad Max + Mad Max 2)
        StringBuilder sb =
                new StringBuilder(
                        String.format(
                                "🎬 O episódio <b>#%d</b> da Sala VIP cobre %d filmes:\n\n",
                                episodio, movies.size()));
        movies.forEach(
                m ->
                        sb.append(
                                String.format(
                                        "• <b>%s</b> (%d) — Sala VIP #%s\n",
                                        utils.escapeHtml(m.title()),
                                        m.year(),
                                        m.salaVipNumber() != null ? m.salaVipNumber() : "?")));
        telegramFacade.enviarMensagemHtml(chatId, sb.toString());
    }

    @SuppressWarnings("java:S3457") //
    private void buscarPorTitulo(long chatId, String termo) {
        List<SalaVipMovie> movies = salaVipService.buscarPorTitulo(termo);

        if (movies.isEmpty()) {
            telegramFacade.enviarMensagem(
                    chatId,
                    "❌ Não encontrei nenhum filme com o título '" + termo + "' na Sala VIP.");
            return;
        }

        if (movies.size() == 1) {
            SalaVipMovie movie = movies.get(0);
            String resposta =
                    String.format(
                            "✅ Sim! O filme <b>%s</b> (%d) está na Sala VIP sob o número"
                                    + " <b>#%s</b>.\n"
                                    + "🔗 <a href=\"%s\">Link no Letterboxd</a>",
                            utils.escapeHtml(movie.title()),
                            movie.year(),
                            movie.salaVipNumber() != null ? movie.salaVipNumber() : "?",
                            movie.letterboxdUrl());
            telegramFacade.enviarMensagemHtml(chatId, resposta);
        } else {
            StringBuilder sb =
                    new StringBuilder(
                            "🤔 Encontrei mais de um filme com esse nome na Sala VIP:\n\n");
            movies.stream()
                    .limit(5)
                    .forEach(
                            m -> {
                                sb.append(
                                        String.format(
                                                "• <b>%s</b> (%d) — Sala VIP #%s\n",
                                                utils.escapeHtml(m.title()),
                                                m.year(),
                                                m.salaVipNumber() != null
                                                        ? m.salaVipNumber()
                                                        : "?"));
                            });
            if (movies.size() > 5) {
                sb.append("\n<i>(mostrando os 5 primeiros resultados)</i>");
            }
            telegramFacade.enviarMensagemHtml(chatId, sb.toString());
        }
    }
}
