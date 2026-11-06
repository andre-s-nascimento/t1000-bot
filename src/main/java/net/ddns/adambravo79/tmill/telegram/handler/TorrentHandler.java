/* (c) 2026 | 05/11/2026 */
package net.ddns.adambravo79.tmill.telegram.handler;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.model.Update;

import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.model.TorrentLaunch;
import net.ddns.adambravo79.tmill.service.TorrentLaunchService;
import net.ddns.adambravo79.tmill.telegram.core.GroupAuthorizationService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;
import net.ddns.adambravo79.tmill.telegram.util.TelegramUtils;

/**
 * Handler especializado para o módulo Torrent (v2.8.0).
 *
 * <h2>Comandos</h2>
 *
 * <ul>
 *   <li>{@code t1000 torrent <mês>} — lista completa de lançamentos do mês (ex.: novembro)
 *   <li>{@code t1000 torrent <DD/MM>} — lançamentos de uma data específica (ex.: 02/11)
 *   <li>{@code t1000 torrent <título>} — ficha de um filme/série (ex.: Resident Evil)
 * </ul>
 *
 * <h2>Arquitetura</h2>
 *
 * <p>Segue o padrão {@link AbstractCommandHandler}: a superclasse cuida da autorização,
 * normalização de texto e tratamento de exceção. Este handler apenas interpreta o comando e
 * formata a resposta.
 *
 * <h2>Métricas</h2>
 *
 * <ul>
 *   <li>{@code comando_torrent} — sempre que o comando é processado (com sucesso ou erro de
 *       validação).
 * </ul>
 */
@Slf4j
@Component
@SuppressWarnings("java:S3457") // \n é intencional: Telegram usa \n, não \r\n
public class TorrentHandler extends AbstractCommandHandler {

    private static final String COMANDO_TORRENT = "comando_torrent";

    /** Prefixo canônico. */
    public static final String PREFIXO = "t1000 torrent";

    /**
     * Regex para extrair o argumento após o prefixo.
     *
     * <p><b>Correção S8786:</b> o {@code \s*} antes de {@code (.*)} causava backtracking
     * super-linear. Agora usa {@code \s*+} (possessivo), que consome todos os espaços sem
     * devolver. O {@code (?s)} (DOTALL) permite que {@code .} case quebras de linha (raro, mas
     * seguro).
     */
    private static final java.util.regex.Pattern REGEX_ARGUMENTO =
            java.util.regex.Pattern.compile(
                    "^t1000\\s+torrent\\s*+(.*)$",
                    java.util.regex.Pattern.CASE_INSENSITIVE
                            | java.util.regex.Pattern.UNICODE_CASE
                            | java.util.regex.Pattern.DOTALL);

    /** Regex para data DD/MM. */
    private static final java.util.regex.Pattern REGEX_DATA =
            java.util.regex.Pattern.compile("^\\d{1,2}/\\d{1,2}$");

    /**
     * Mensagem de ajuda (constante).
     *
     * <p><b>Correção S3400:</b> era um método que só retornava string — virou constante.
     */
    private static final String AJUDA_MESSAGE =
            """
            🎬 <b>Módulo Torrent</b>

            Comandos disponíveis:

            <code>t1000 torrent &lt;mês&gt;</code> — lista todos os lançamentos do mês.
            <code>t1000 torrent &lt;DD/MM&gt;</code> — lançamentos de uma data.
            <code>t1000 torrent &lt;título&gt;</code> — ficha de um filme/série.

            💡 <i>Exemplos:</i>
            • <code>t1000 torrent novembro</code>
            • <code>t1000 torrent 02/11</code>
            • <code>t1000 torrent Resident Evil</code>
            """;

    private final TorrentLaunchService torrentService;
    private final MetricsService metricsService;

    public TorrentHandler(
            TelegramFacade telegramFacade,
            GroupAuthorizationService authService,
            TelegramUtils utils,
            TorrentLaunchService torrentService,
            MetricsService metricsService) {
        super(telegramFacade, authService, utils);
        this.torrentService = torrentService;
        this.metricsService = metricsService;
    }

    // =========================================================================
    // CONTRATO
    // =========================================================================

    @Override
    public String getPrefixo() {
        return PREFIXO;
    }

    @Override
    protected void handleCommand(Update update, String textoNormalizado) {
        long chatId = chatId(update);
        String rawText = textoBruto(update);
        String argumento = extrairArgumento(rawText);

        // Sem argumento: mostra ajuda
        if (argumento.isEmpty()) {
            sendHtml(chatId, AJUDA_MESSAGE);
            metricsService.success(COMANDO_TORRENT);
            return;
        }

        // 1. É uma data? (DD/MM)
        if (REGEX_DATA.matcher(argumento).matches()) {
            handlePorData(chatId, argumento);
            metricsService.success(COMANDO_TORRENT);
            return;
        }

        // 2. É um mês conhecido?
        if (torrentService.getMesesDisponiveis().stream()
                .anyMatch(m -> m.equalsIgnoreCase(argumento))) {
            handlePorMes(chatId, argumento);
            metricsService.success(COMANDO_TORRENT);
            return;
        }

        // 3. Senão, busca por título
        handlePorTitulo(chatId, argumento);
        metricsService.success(COMANDO_TORRENT);
    }

    // =========================================================================
    // SUBCOMANDOS
    // =========================================================================

    @SuppressWarnings("null") // JDT: dataLancamento() pode ser null (defensivo)
    private void handlePorMes(long chatId, String mes) {
        List<TorrentLaunch> lista = torrentService.listarPorMes(mes);

        if (lista.isEmpty()) {
            List<String> meses = torrentService.getMesesDisponiveis();
            String msg =
                    meses.isEmpty()
                            ? "📭 Nenhum lançamento disponível no momento."
                            : "📭 Nenhum lançamento encontrado para o mês '"
                                    + mes
                                    + "'. Meses disponíveis: "
                                    + String.join(", ", meses);
            sendPlain(chatId, msg);
            return;
        }

        // Ordena por data (DD/MM)
        List<TorrentLaunch> ordenados =
                lista.stream()
                        .sorted(
                                Comparator.comparing(
                                        TorrentLaunch::dataLancamento,
                                        Comparator.nullsLast(String::compareTo)))
                        .toList();

        String resposta =
                String.format(
                        """
                        Lançamentos do torrent de %s. 🎞

                        🚛💨🎁 Todos os lançamentos:

                        %s
                        """,
                        capitalize(mes), formatarLista(ordenados));

        sendHtml(chatId, resposta);
    }

    private void handlePorData(long chatId, String data) {
        List<TorrentLaunch> lista = torrentService.buscarPorData(data);

        if (lista.isEmpty()) {
            sendPlain(chatId, "📭 Nenhum lançamento encontrado para a data " + data + ".");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🎬 Lançamentos do dia ").append(data).append(":\n\n");
        for (TorrentLaunch l : lista) {
            sb.append("• ").append(formatarLinha(l)).append("\n");
        }

        sendHtml(chatId, sb.toString());
    }

    private void handlePorTitulo(long chatId, String termo) {
        // 1. Tenta match exato primeiro
        Optional<TorrentLaunch> exato = torrentService.buscarPrimeiroPorTitulo(termo);
        if (exato.isPresent()) {
            sendHtml(chatId, formatarFicha(exato.get()));
            return;
        }

        // 2. Busca parcial (até 10 resultados)
        List<TorrentLaunch> parciais = torrentService.buscarPorTitulo(termo);

        if (parciais.isEmpty()) {
            sendPlain(
                    chatId,
                    "📭 Nenhum lançamento encontrado com o título '"
                            + termo
                            + "'.\n\n"
                            + "💡 Dica: use <code>t1000 torrent "
                            + torrentService.getMesReferencia()
                            + "</code> para ver a lista completa.");
            return;
        }

        if (parciais.size() == 1) {
            sendHtml(chatId, formatarFicha(parciais.get(0)));
            return;
        }

        // 3. Múltiplos resultados: lista resumida
        StringBuilder sb = new StringBuilder();
        sb.append("🤔 Encontrei ")
                .append(parciais.size())
                .append(" lançamento(s) com '")
                .append(termo)
                .append("':\n\n");
        for (TorrentLaunch l : parciais) {
            sb.append("• ").append(formatarLinha(l)).append("\n");
        }
        sb.append("\n💡 Use <code>t1000 torrent &lt;título completo&gt;</code> para ver a ficha.");

        sendHtml(chatId, sb.toString());
    }

    // =========================================================================
    // FORMATAÇÃO
    // =========================================================================

    /**
     * Extrai o argumento após o prefixo, preservando o case original.
     *
     * @param rawText texto bruto do usuário
     * @return argumento (vazio se não houver)
     */
    private String extrairArgumento(String rawText) {
        if (rawText == null) {
            return "";
        }
        var matcher = REGEX_ARGUMENTO.matcher(rawText);
        if (!matcher.matches()) {
            return "";
        }
        return matcher.group(1).trim();
    }

    /** Formata uma linha de lista (usada no mês e na desambiguação). */
    private String formatarLinha(TorrentLaunch l) {
        String titulo = utils.escapeHtml(l.titulo());
        String data = l.dataLancamento() != null ? l.dataLancamento() : "S/D";
        String detalhes = buildDetalhesCurto(l);

        return data + " - " + titulo + (detalhes.isEmpty() ? "" : " (" + detalhes + ")");
    }

    /**
     * Monta os detalhes curtos: diretor + atores (ou "S/D" se não houver).
     *
     * <p>Formato: "dir. X" ou "X, Y; dir. Z" ou "X, Y" ou "S/D".
     */
    private String buildDetalhesCurto(TorrentLaunch l) {
        StringBuilder sb = new StringBuilder();

        if (l.atores() != null && !l.atores().isEmpty()) {
            sb.append(
                    l.atores().stream()
                            .limit(3)
                            .map(utils::escapeHtml)
                            .reduce((a, b) -> a + ", " + b)
                            .orElse(""));
        }

        if (l.diretor() != null && !l.diretor().isBlank()) {
            if (!sb.isEmpty()) {
                sb.append("; ");
            }
            sb.append("dir. ").append(utils.escapeHtml(l.diretor()));
        }

        return sb.isEmpty() ? "S/D" : sb.toString();
    }

    /** Formata a ficha completa de um lançamento (para busca por título). */
    private String formatarFicha(TorrentLaunch l) {
        String titulo = utils.escapeHtml(l.titulo());
        String tipo = l.isSerie() ? "Série" : "Filme";
        String ano = torrentService.getAno() > 0 ? " (" + torrentService.getAno() + ")" : "";
        String temporada =
                l.isSerie() && l.temporada() != null ? " (Temporada " + l.temporada() + ")" : "";

        StringBuilder sb = new StringBuilder();
        sb.append("🎞 Lançamento: ").append(titulo).append(ano).append("\n\n");
        sb.append("🗓 Data: ")
                .append(l.dataLancamento() != null ? l.dataLancamento() : "S/D")
                .append("\n");

        if (l.diretor() != null && !l.diretor().isBlank()) {
            sb.append("🎬 Diretor: ").append(utils.escapeHtml(l.diretor())).append("\n");
        }
        if (l.atores() != null && !l.atores().isEmpty()) {
            sb.append("👥 Atores: ")
                    .append(
                            l.atores().stream()
                                    .map(utils::escapeHtml)
                                    .reduce((a, b) -> a + ", " + b)
                                    .orElse(""))
                    .append("\n");
        }

        sb.append("📺 Tipo: ").append(tipo).append(temporada).append("\n");

        if (l.letterboxdUrl() != null && !l.letterboxdUrl().isBlank()) {
            sb.append("\n🔗 <a href=\"")
                    .append(utils.escapeHtml(l.letterboxdUrl()))
                    .append("\">Link no Letterboxd</a>");
        }

        return sb.toString();
    }

    /** Formata a lista completa de lançamentos (para o comando de mês). */
    private String formatarLista(List<TorrentLaunch> lista) {
        if (lista.isEmpty()) {
            return "📭 Nenhum lançamento.";
        }
        StringBuilder sb = new StringBuilder();
        for (TorrentLaunch l : lista) {
            sb.append(formatarLinha(l)).append("\n");
        }
        return sb.toString().trim();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
