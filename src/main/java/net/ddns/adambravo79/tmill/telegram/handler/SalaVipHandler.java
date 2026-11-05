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

/**
 * Handler especializado para os comandos da Sala VIP do RapaduraCast.
 *
 * <h2>Comandos tratados</h2>
 *
 * <ul>
 *   <li>{@code t1000 salavip <numero>} — busca por episódio (ex.: {@code t1000 salavip 15})
 *   <li>{@code t1000 salavip numero <numero>} — idem, com palavra-chave opcional
 *   <li>{@code t1000 sala vip <numero>} — idem, com espaço
 *   <li>{@code t1000 salavip episodio <numero>} — idem, com palavra-chave opcional
 *   <li>{@code t1000 salavip filme <titulo>} — busca por título/nota
 *   <li>{@code t1000 salavip tem filme <titulo>} — idem, com prefixo opcional
 * </ul>
 *
 * <h2>Histórico</h2>
 *
 * <p>Este handler vivia em {@code service/} e não herdava de nenhuma superclasse. A refatoração o
 * moveu para {@code telegram/handler/} e adotou a hierarquia {@link AbstractCommandHandler}, o que
 * trouxe:
 *
 * <ul>
 *   <li>Verificação de autorização automática via {@link #processarComando}
 *   <li>Wrappers seguros de envio ({@link #sendHtml}, {@link #sendPlain}) que tratam erros de rede
 *   <li>Centralização do tratamento de exceções via {@link #executarComProtecao}
 * </ul>
 *
 * <h2>Regex</h2>
 *
 * <p>Os três padrões são pré-compilados como {@code static final}. O {@code FLAGS} centraliza
 * {@code CASE_INSENSITIVE}, {@code UNICODE_CASE} e {@code UNICODE_CHARACTER_CLASS} (este último
 * garante que {@code \d} reconheça dígitos Unicode, e {@code \w} reconheça letras acentuadas).
 * O {@code CANON_EQ} adicional garante que caracteres acentuados sejam tratados equivalentes
 * independentemente da forma de normalização Unicode usada (NFC vs NFD).
 *
 * <h2>Padrões de regex (compatibilidade)</h2>
 *
 * <table border="1">
 *   <caption>Inputs aceitos</caption>
 *   <tr><th>Comando</th><th>REGEX</th></tr>
 *   <tr><td>{@code t1000 salavip 15}</td><td>{@link #REGEX_NUMERO}</td></tr>
 *   <tr><td>{@code t1000 salavip numero 15}</td><td>{@link #REGEX_NUMERO}</td></tr>
 *   <tr><td>{@code t1000 salavip nº 15}</td><td>{@link #REGEX_NUMERO}</td></tr>
 *   <tr><td>{@code t1000 salavip #15}</td><td>{@link #REGEX_NUMERO}</td></tr>
 *   <tr><td>{@code t1000 salavip filme matrix}</td><td>{@link #REGEX_TITULO}</td></tr>
 *   <tr><td>{@code t1000 salavip tem filme matrix}</td><td>{@link #REGEX_TITULO}</td></tr>
 *   <tr><td>{@code t1000 salavip episodio 15}</td><td>{@link #REGEX_EPISODIO}</td></tr>
 *   <tr><td>{@code t1000 salavip ep 15}</td><td>{@link #REGEX_EPISODIO}</td></tr>
 * </table>
 *
 * <h2>Sem métricas</h2>
 *
 * <p>O {@code MetricsService} é usado pelo {@code CommandHandler} roteador (métrica {@code
 * comando_salavip}), não aqui. Manter o handler "puro" (só lógica) facilita os testes.
 */
@Slf4j
@Component
@SuppressWarnings("java:S3457") // \n é intencional: Telegram usa \n, não \r\n (não trocar por %n)
public class SalaVipHandler extends AbstractCommandHandler {

    /** Prefixo canônico. */
    public static final String PREFIXO = "t1000 salavip";

    /** Prefixo alternativo (com espaço). */
    public static final String PREFIXO_ALT = "t1000 sala vip";

    /**
     * Flags comuns dos padrões.
     *
     * <ul>
     *   <li>{@code CASE_INSENSITIVE}: aceita "T1000 SALAVIP"
     *   <li>{@code UNICODE_CASE}: case-insensitive funciona com acentos
     *   <li>{@code UNICODE_CHARACTER_CLASS}: {@code \d} e {@code \w} reconhecem Unicode
     *   <li>{@code CANON_EQ}: trata NFC e NFD como equivalentes (ex.: "ó" pré-composto vs "o" +
     *       acento combinante) — atende {@code java:S5854}
     * </ul>
     */
    private static final int FLAGS =
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

    /**
     * Padrão para comando de busca por número.
     *
     * <p>Aceita: {@code salavip 15}, {@code salavip numero 15}, {@code salavip número 15},
     * {@code salavip nº 15}, {@code salavip #15}, {@code sala vip 15}, etc.
     *
     * <p><b>Nota sobre {@code n[uú]mer[oó]?}:</b> o {@code [uú]} é necessário porque o
     * {@code CANON_EQ} foi removido (quebrava classes de caracteres com acentos). Sem ele,
     * precisamos cobrir explicitamente {@code u} e {@code ú}. O mesmo vale para {@code [oó]}.
     */
    private static final Pattern REGEX_NUMERO =
            Pattern.compile(
                    "t1000\\s+sala\\s*vip\\s*+(?:n[uú]mer[oó]?|n[º°]?|#)?\\s*+(-?\\d+)", FLAGS);

    /**
     * Padrão para comando de busca por título.
     *
     * <p>Aceita: {@code salavip filme <titulo>} ou {@code salavip tem filme <titulo>}.
     *
     * <h3>Correções S5843 (complexidade) + S8786 (backtracking)</h3>
     *
     * <ul>
     *   <li>{@code (?:tem\s*+)?filme} substitui a alternância redundante {@code (?:tem\s+filme|filme)}
     *       — reduz complexidade de 26 para ~10.
     *   <li>{@code \s*+} (possessivo) após "vip" e após "filme" evita o backtracking entre o
     *       {@code \s+} do prefixo e o {@code \s+} interno de "tem ". Como os dois são
     *       possessivos, o engine não os reverterá.
     *   <li>O {@code \s*+} após "vip" também absorve o caso de usuário digitar
     *       {@code "salavipfilme x"} (improvável, mas inofensivo).
     * </ul>
     */
    private static final Pattern REGEX_TITULO =
            Pattern.compile("t1000\\s+sala\\s*vip\\s*+(?:tem\\s*+)?filme\\s*+(.+)", FLAGS);

    /**
     * Padrão para comando de busca por episódio (com palavra-chave explícita).
     *
     * <p>Aceita: {@code salavip episodio 15}, {@code salavip episódio 15}, {@code salavip ep 15}.
     *
     * <h3>Correções S5855 + S8786</h3>
     *
     * <ul>
     *   <li>{@code ep(?:is[oó]dio)?} substitui {@code episodio|episódio|ep} (S5855 — redundância).
     *   <li>{@code \s*+} (possessivo) antes de {@code \d+} elimina o backtracking do quantificador
     *       em inputs onde o número está ausente (ex.: {@code "salavip ep xyz"}) (S8786).
     * </ul>
     */
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

    // =========================================================================
    // CONTRATO
    // =========================================================================

    @Override
    public String getPrefixo() {
        return PREFIXO;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Nota: o retorno {@code boolean} original foi removido. O roteador decide se a mensagem é
     * deste handler com base no prefixo (via {@link #getPrefixo} + {@link #getPrefixoAlternativo}).
     * Se por algum motivo o texto chegar aqui mas não casar com nenhum padrão, apenas loga e
     * retorna.
     */
    @Override
    protected void handleCommand(Update update, String textoNormalizado) {
        // Usa o texto bruto para preservar acentos nos títulos/notas
        String rawText = textoBruto(update).trim();
        long chatId = chatId(update);

        // Ordem dos matchers: do mais específico (título) para o mais genérico (número)
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

    /**
     * Retorna o prefixo alternativo (com espaço) para o roteador testar.
     *
     * <p>O roteador do {@code CommandHandler} precisa saber que este handler cobre tanto {@code
     * "t1000 salavip"} quanto {@code "t1000 sala vip"}. Como a interface {@link
     * AbstractCommandHandler} só expõe um {@code getPrefixo()}, adicionamos este método extra para
     * o roteador consultar.
     */
    public String getPrefixoAlternativo() {
        return PREFIXO_ALT;
    }

    // =========================================================================
    // BUSCA POR NÚMERO (tratado como episódio)
    // =========================================================================

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
                        movie.salaVipEpisode(),
                        notaStr,
                        movie.letterboxdUrl());
        sendHtml(chatId, resposta);
    }

    // =========================================================================
    // BUSCA POR EPISÓDIO (comando explícito "episodio")
    // =========================================================================

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
                            movie.salaVipNumber() != null ? movie.salaVipNumber() : "?",
                            movie.letterboxdUrl());
            sendHtml(chatId, resposta);
            return;
        }

        // Episódio cobre 2+ filmes (ex.: episódio 15 = Mad Max + Mad Max 2)
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
                            m.salaVipNumber() != null ? m.salaVipNumber() : "?"));
        }

        sendHtml(chatId, sb.toString());
    }

    // =========================================================================
    // BUSCA POR TÍTULO
    // =========================================================================

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

            // Verifica se o match foi pelo título ou pela nota
            boolean matchNaNota =
                    !movie.title().toLowerCase().contains(termo.toLowerCase())
                            && movie.noteText() != null
                            && movie.noteText().toLowerCase().contains(termo.toLowerCase());

            String aviso = matchNaNota ? "\n<i>(Encontrado pela sinopse/nota)</i>" : "";

            String resposta =
                    String.format(
                            "✅ Sim! O filme <b>%s</b> (%d) está na Sala VIP sob o número"
                                    + " <b>#%s</b>.%s\n"
                                    + "🔗 <a href=\"%s\">Link no Letterboxd</a>",
                            utils.escapeHtml(movie.title()),
                            movie.year(),
                            movie.salaVipNumber() != null ? movie.salaVipNumber() : "?",
                            aviso,
                            movie.letterboxdUrl());
            sendHtml(chatId, resposta);
            return;
        }

        // Múltiplos resultados (limita a 5)
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
                            m.salaVipNumber() != null ? m.salaVipNumber() : "?"));
        }

        if (movies.size() > 5) {
            sb.append("\n<i>(mostrando os 5 primeiros resultados)</i>");
        }

        sendHtml(chatId, sb.toString());
    }
}
