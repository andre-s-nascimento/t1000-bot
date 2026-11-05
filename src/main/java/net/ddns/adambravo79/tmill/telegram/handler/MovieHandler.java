/* (c) 2026 | 04/11/2026 */
package net.ddns.adambravo79.tmill.telegram.handler;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;

import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.exception.MovieNotFoundException;
import net.ddns.adambravo79.tmill.model.MovieOrchestrationResponse;
import net.ddns.adambravo79.tmill.model.MovieRecord;
import net.ddns.adambravo79.tmill.model.MovieSearchResponse;
import net.ddns.adambravo79.tmill.service.MovieService;
import net.ddns.adambravo79.tmill.service.WeeklyReleasesService;
import net.ddns.adambravo79.tmill.telegram.core.GroupAuthorizationService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;
import net.ddns.adambravo79.tmill.telegram.util.TelegramUtils;
import net.ddns.adambravo79.tmill.util.LogSanitizer;

/**
 * Handler especializado para os comandos de filmes:
 *
 * <ul>
 *   <li>{@code t1000 buscar <nome>} — busca por nome
 *   <li>{@code t1000 estreias da semana} / {@code t1000 lancamentos} — lançamentos da semana
 * </ul>
 *
 * <h2>Responsabilidades</h2>
 *
 * <h3>{@code t1000 buscar}</h3>
 * <ol>
 *   <li>Validar tamanho do termo (3–100 chars).
 *   <li>Buscar no TMDB via {@link MovieService#buscarFilme}.
 *   <li>Se 1 resultado: buscar detalhes completos e enviar foto + texto.
 *   <li>Se N resultados: enviar botões inline para desambiguação.
 *   <li>Se 0 resultados: informar "não encontrado".
 * </ol>
 *
 * <h3>{@code t1000 estreias da semana}</h3>
 * <ol>
 *   <li>Delegar a formatação para {@link WeeklyReleasesService#getWeeklyReleasesMessage}.
 *   <li>Enviar resposta.
 * </ol>
 *
 * <h2>Métricas</h2>
 *
 * <ul>
 *   <li>{@code comando_buscar} — sempre que {@code t1000 buscar} é processado
 *   <li>{@code comando_estreias} — sempre que {@code estreias}/{@code lancamentos} é processado
 * </ul>
 *
 * <p>As métricas TMDB ({@code tmdb_buscar_filme}, {@code tmdb_buscar_detalhes}) são emitidas
 * pelo {@link MovieService}, não por este handler.
 */
@Slf4j
@Component
public class MovieHandler extends AbstractCommandHandler {

    private static final String COMANDO_BUSCAR = "comando_buscar";

    private static final String COMANDO_ESTREIAS = "comando_estreias";

    /** Prefixo do comando de busca. */
    public static final String PREFIXO_BUSCAR = "t1000 buscar";

    /** Prefixo do comando de estreias (variante 1). */
    public static final String PREFIXO_ESTREIAS = "t1000 estreias da semana";

    /** Prefixo do comando de estreias (variante 2 — sem "da semana"). */
    public static final String PREFIXO_LANCAMENTOS = "t1000 lancamentos";

    /** Tamanho mínimo do termo de busca. */
    private static final int MIN_TERMO = 3;

    /** Tamanho máximo do termo de busca. */
    private static final int MAX_TERMO = 100;

    /** Máximo de botões inline na desambiguação (limite do Telegram na prática). */
    private static final int MAX_BOTOES = 10;

    private final MovieService movieService;
    private final WeeklyReleasesService weeklyReleasesService;
    private final MetricsService metricsService;

    public MovieHandler(
            TelegramFacade telegramFacade,
            GroupAuthorizationService authService,
            TelegramUtils utils,
            MovieService movieService,
            WeeklyReleasesService weeklyReleasesService,
            MetricsService metricsService) {
        super(telegramFacade, authService, utils);
        this.movieService = movieService;
        this.weeklyReleasesService = weeklyReleasesService;
        this.metricsService = metricsService;
    }

    // =========================================================================
    // CONTRATO
    // =========================================================================

    @Override
    public String getPrefixo() {
        // Prefixo "raiz" — cobre "t1000 buscar" e "t1000 estreias"/"lancamentos" via match
        // no roteador. O roteador chama este handler se o texto começar com qualquer um dos
        // prefixos aceitos.
        return "t1000 ";
    }

    @Override
    protected void handleCommand(Update update, String textoNormalizado) {
        // Dispatch interno entre "buscar" e "estreias".
        //
        // CORREÇÃO S1172: handleBuscar() não recebe textoNormalizado porque extrai o termo
        // do texto BRUTO (preserva acentos). Só handleEstreias() precisa do normalizado
        // (mas também não usa — só o update). Portanto, o parâmetro só é usado AQUI,
        // no dispatch, e ambos os métodos privados recebem apenas o update.
        if (textoNormalizado.startsWith(PREFIXO_BUSCAR)) {
            handleBuscar(update);
        } else if (textoNormalizado.startsWith(PREFIXO_ESTREIAS)
                || textoNormalizado.startsWith(PREFIXO_LANCAMENTOS)) {
            handleEstreias(update);
        } else {
            log.warn(
                    "MovieHandler recebeu comando desconhecido: '{}'",
                    LogSanitizer.sanitize(textoNormalizado));
            sendPlain(chatId(update), BotMessages.COMANDO_NAO_RECONHECIDO);
        }
    }

    // =========================================================================
    // BUSCA DE FILME
    // =========================================================================

    /**
     * Fluxo de busca de filme.
     *
     * <p>Não recebe o texto normalizado porque extrai o termo do texto bruto
     * (preserva acentos/maiúsculas no termo enviado ao TMDB).
     *
     * @param update update do Telegram
     */
    private void handleBuscar(Update update) {
        long chatId = chatId(update);

        // Extrai o termo do TEXTO BRUTO (preserva acentos)
        String termo = extrairTermoBusca(textoBruto(update));

        // 1. Validação: tamanho
        if (termo.length() < MIN_TERMO) {
            sendPlain(chatId, BotMessages.BUSCA_TERM_CURTO);
            metricsService.success(COMANDO_BUSCAR);
            return;
        }
        if (termo.length() > MAX_TERMO) {
            sendPlain(chatId, BotMessages.BUSCA_TERM_LONGO);
            metricsService.success(COMANDO_BUSCAR);
            return;
        }

        // 2. Busca no TMDB
        MovieSearchResponse busca;
        try {
            busca = movieService.buscarFilme(termo);
        } catch (MovieNotFoundException e) {
            log.info(
                    "🎬 Busca sem resultado para '{}' (chatId={})",
                    LogSanitizer.sanitizeQuery(termo),
                    LogSanitizer.sanitizeId(chatId));
            sendPlain(chatId, "❌ " + e.getMessage());
            metricsService.success(COMANDO_BUSCAR);
            return;
        }

        // 3. Sem resultados (caso raro: o service pode retornar null sem lançar exceção)
        if (busca == null || busca.results() == null || busca.results().isEmpty()) {
            sendPlain(chatId, "❌ " + BotMessages.FILME_NAO_ENCONTRADO);
            metricsService.success(COMANDO_BUSCAR);
            return;
        }

        // 4. Um resultado: envia detalhes diretamente
        if (busca.results().size() == 1) {
            enviarFilmeUnico(chatId, busca.results().get(0).id());
            metricsService.success(COMANDO_BUSCAR);
            return;
        }

        // 5. Múltiplos: envia botões de desambiguação
        enviarOpcoesDesambiguacao(chatId, busca.results());
        metricsService.success(COMANDO_BUSCAR);
    }

    /**
     * Extrai o termo de busca do texto bruto, removendo o prefixo.
     *
     * <p>Usa o texto bruto (não normalizado) para preservar acentos/maiúsculas no termo enviado
     * ao TMDB.
     *
     * @param rawText texto bruto do usuário
     * @return termo (pode ser vazio se o usuário só digitou o comando)
     */
    private String extrairTermoBusca(String rawText) {
        if (rawText == null) {
            return "";
        }
        return rawText.replaceFirst("(?i)^t1000\\s+buscar\\s*", "").trim();
    }

    /**
     * Busca detalhes completos de um filme por ID e envia a resposta.
     *
     * <p>Centraliza o fluxo comum aos casos "resultado único" e "callback de seleção".
     */
    private void enviarFilmeUnico(long chatId, Long movieId) {
        MovieOrchestrationResponse resposta;
        try {
            resposta = movieService.buscarPorId(movieId);
        } catch (MovieNotFoundException e) {
            log.warn(
                    "🎬 Detalhes não encontrados para movieId={} (chatId={})",
                    movieId,
                    LogSanitizer.sanitizeId(chatId));
            sendPlain(chatId, "❌ " + e.getMessage());
            return;
        }

        log.info(
                "✅ Filme único enviado: chatId={} movieId={}",
                LogSanitizer.sanitizeId(chatId),
                movieId);
        exibirRespostaFilme(chatId, resposta);
    }

    /**
     * Exibe o filme: foto + texto se houver poster, senão apenas texto com aviso.
     */
    private void exibirRespostaFilme(long chatId, MovieOrchestrationResponse resposta) {
        String fotoUrl = resposta.urlFoto();

        if (isValidUrl(fotoUrl)) {
            sendPhoto(chatId, fotoUrl, resposta.textoFormatado());
        } else {
            sendHtml(chatId, resposta.textoFormatado() + "\n\n_(sem imagem)_");
        }
    }

    /**
     * Envia botões inline para o usuário escolher entre múltiplos resultados.
     *
     * <p>Os botões são organizados em linhas de 2. Cada botão tem o formato "Título (Ano)" e o
     * callback data é {@code "id:<movieId>"}.
     *
     * @param chatId destino
     * @param resultados lista de resultados (máximo {@link #MAX_BOTOES} serão exibidos)
     */
    private void enviarOpcoesDesambiguacao(long chatId, List<MovieRecord> resultados) {
        List<InlineKeyboardButton[]> rows = new ArrayList<>();
        List<InlineKeyboardButton> currentRow = new ArrayList<>();

        int limit = Math.min(resultados.size(), MAX_BOTOES);
        for (int i = 0; i < limit; i++) {
            MovieRecord filme = resultados.get(i);
            String ano = extrairAno(filme.releaseDate());
            String label = filme.title() + " (" + ano + ")";

            currentRow.add(new InlineKeyboardButton(label).callbackData("id:" + filme.id()));

            // 2 botões por linha, ou último elemento
            if (currentRow.size() == 2 || i == limit - 1) {
                rows.add(currentRow.toArray(new InlineKeyboardButton[0]));
                currentRow = new ArrayList<>();
            }
        }

        InlineKeyboardMarkup markup =
                new InlineKeyboardMarkup(rows.toArray(new InlineKeyboardButton[0][]));

        sendWithButtons(chatId, "🧐 Encontrei vários resultados. Qual você quer?", markup);
    }

    /**
     * Extrai o ano de uma data ISO ({@code yyyy-MM-dd}). Se a data for nula ou muito curta,
     * retorna {@code "S/A"}.
     *
     * @param releaseDate data no formato {@code yyyy-MM-dd} (pode ser {@code null})
     * @return ano com 4 dígitos ou {@code "S/A"}
     */
    private String extrairAno(String releaseDate) {
        if (releaseDate != null && releaseDate.length() >= 4) {
            return releaseDate.substring(0, 4);
        }
        return "S/A";
    }

    // =========================================================================
    // ESTREIAS DA SEMANA
    // =========================================================================

    /**
     * Fluxo de "estreias da semana".
     *
     * <p>Não recebe o texto normalizado porque a lógica não depende do texto (só do update).
     *
     * @param update update do Telegram
     */
    private void handleEstreias(Update update) {
        long chatId = chatId(update);

        String resposta;
        try {
            resposta = weeklyReleasesService.getWeeklyReleasesMessage();
        } catch (RuntimeException e) {
            log.error(
                    "❌ Falha ao obter estreias da semana (chatId={})",
                    LogSanitizer.sanitizeId(chatId),
                    e);
            sendPlain(chatId, BotMessages.ERRO_GENERICO);
            metricsService.error(COMANDO_ESTREIAS);
            return;
        }

        sendHtml(chatId, resposta);
        metricsService.success(COMANDO_ESTREIAS);
    }
}
