/* (c) 2026 | 04/11/2026 */
package net.ddns.adambravo79.tmill.telegram.handler;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.model.Update;

import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.service.WorldCupSchedulerService;
import net.ddns.adambravo79.tmill.telegram.core.GroupAuthorizationService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;
import net.ddns.adambravo79.tmill.telegram.util.TelegramUtils;
import net.ddns.adambravo79.tmill.util.DateParserUtils;
import net.ddns.adambravo79.tmill.util.LogSanitizer;

/**
 * Handler especializado para os comandos da Copa do Mundo 2026.
 *
 * <p>Comandos:
 *
 * <ul>
 *   <li>{@code t1000 jogos [data]} — jogos do dia (padrão: hoje)
 *   <li>{@code t1000 copa [data]} — sinônimo
 *   <li>{@code t1000 resultados [data]} — placares (padrão: hoje)
 * </ul>
 *
 * <h2>Tratamento de data inválida</h2>
 *
 * <p>Se o usuário digitar um argumento que não é data válida (ex.: {@code "t1000 jogos asdf"}),
 * ambos os fluxos respondem com {@link BotMessages#DATA_INVALIDA} — <b>não</b> caem no fallback
 * de "hoje" (isso confundiria o usuário, que receberia resultados de um dia que não pediu).
 *
 * <p>Correção do bug latente: antes, {@code handleJogos} passava {@code null} ao {@code
 * WorldCupSchedulerService#sendMatchesToChat}, que internamente caía em "nenhum jogo programado"
 * (mensagem enganosa). Agora, o handler valida {@code null} e responde {@code DATA_INVALIDA}.
 */
@Slf4j
@Component
public class WorldCupHandler extends AbstractCommandHandler {

    /** Prefixo do comando de jogos. */
    public static final String PREFIXO_JOGOS = "t1000 jogos";

    /** Prefixo do comando de jogos (sinônimo). */
    public static final String PREFIXO_COPA = "t1000 copa";

    /** Prefixo do comando de resultados. */
    public static final String PREFIXO_RESULTADOS = "t1000 resultados";

    private final WorldCupSchedulerService worldCupSchedulerService;
    private final MetricsService metricsService;

    @Value("${worldcup.enabled:false}")
    private boolean worldcupEnabled;

    public WorldCupHandler(
            TelegramFacade telegramFacade,
            GroupAuthorizationService authService,
            TelegramUtils utils,
            WorldCupSchedulerService worldCupSchedulerService,
            MetricsService metricsService) {
        super(telegramFacade, authService, utils);
        this.worldCupSchedulerService = worldCupSchedulerService;
        this.metricsService = metricsService;
    }

    // =========================================================================
    // CONTRATO
    // =========================================================================

    @Override
    public String getPrefixo() {
        return "t1000 ";
    }

    @Override
    protected void handleCommand(Update update, String textoNormalizado) {
        if (textoNormalizado.startsWith(PREFIXO_JOGOS)
                || textoNormalizado.startsWith(PREFIXO_COPA)) {
            handleJogos(update);
        } else if (textoNormalizado.startsWith(PREFIXO_RESULTADOS)) {
            handleResultados(update);
        } else {
            log.warn(
                    "WorldCupHandler recebeu comando desconhecido: '{}'",
                    LogSanitizer.sanitize(textoNormalizado));
            sendPlain(chatId(update), BotMessages.COMANDO_NAO_RECONHECIDO);
        }
    }

    // =========================================================================
    // JOGOS
    // =========================================================================

    private void handleJogos(Update update) {
        long chatId = chatId(update);

        // 1. Feature flag
        if (!worldcupEnabled) {
            sendPlain(chatId, BotMessages.WORLD_CUP_FINISHED);
            metricsService.success("comando_copa");
            return;
        }

        // 2. Extrai o argumento (data) após o prefixo
        String arg = extrairArgumento(update, PREFIXO_JOGOS, PREFIXO_COPA);
        LocalDate data = resolverData(arg);

        // 3. Validação: se o usuário digitou algo E o parse falhou → DATA_INVALIDA
        //    (sem isso, o scheduler receberia null e responderia "nenhum jogo programado")
        if (data == null) {
            sendPlain(chatId, BotMessages.DATA_INVALIDA);
            metricsService.success("comando_copa");
            return;
        }

        log.info("📅 Comando 'jogos' (chatId={}) data={}", LogSanitizer.sanitizeId(chatId), data);

        // 4. Delega ao scheduler
        try {
            worldCupSchedulerService.sendMatchesToChat(chatId, data);
        } catch (RuntimeException e) {
            log.error("❌ Falha ao enviar jogos da Copa (chatId={})", chatId, e);
            sendPlain(chatId, BotMessages.ERRO_GENERICO);
            metricsService.error("comando_copa");
            return;
        }

        metricsService.success("comando_copa");
    }

    // =========================================================================
    // RESULTADOS
    // =========================================================================

    private void handleResultados(Update update) {
        long chatId = chatId(update);

        // 1. Feature flag
        if (!worldcupEnabled) {
            sendPlain(chatId, BotMessages.WORLD_CUP_FINISHED);
            metricsService.success("comando_resultados");
            return;
        }

        // 2. Extrai o argumento (data) após o prefixo
        String arg = extrairArgumento(update, PREFIXO_RESULTADOS);
        LocalDate data = resolverData(arg);

        // 3. Validação: se o usuário digitou algo E o parse falhou → DATA_INVALIDA
        if (data == null) {
            sendPlain(chatId, BotMessages.DATA_INVALIDA);
            metricsService.success("comando_resultados");
            return;
        }

        log.info(
                "📅 Comando 'resultados' (chatId={}) arg='{}' data={}",
                LogSanitizer.sanitizeId(chatId),
                LogSanitizer.sanitize(arg),
                data);

        // 4. Delega ao scheduler
        try {
            worldCupSchedulerService.sendResultsToChat(chatId, data);
        } catch (RuntimeException e) {
            log.error("❌ Falha ao enviar resultados da Copa (chatId={})", chatId, e);
            sendPlain(chatId, BotMessages.ERRO_GENERICO);
            metricsService.error("comando_resultados");
            return;
        }

        metricsService.success("comando_resultados");
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /**
     * Extrai o argumento após o primeiro prefixo que casar.
     *
     * @param update update do Telegram
     * @param prefixos prefixos possíveis (ex.: "t1000 jogos", "t1000 copa")
     * @return argumento (pode ser vazio)
     */
    private String extrairArgumento(Update update, String... prefixos) {
        String normalized = normalizarTexto(textoBruto(update));

        for (String prefixo : prefixos) {
            if (normalized.startsWith(prefixo)) {
                return normalized.substring(prefixo.length()).trim();
            }
        }

        return "";
    }

    /**
     * Resolve uma data a partir de uma string de argumento.
     *
     * <p><b>Contrato:</b>
     *
     * <ul>
     *   <li>Arg nulo ou vazio → <b>hoje</b> (usuário não especificou data)
     *   <li>Arg parseável → data correspondente
     *   <li>Arg não-parseável → <b>{@code null}</b> (caller deve tratar como inválido)
     * </ul>
     *
     * <p>Note a diferença sutil: arg vazio significa "quero hoje", arg inválido significa "não
     * entendi o que você digitou". Os dois são tratados de forma diferente pelo caller.
     *
     * @param arg argumento do usuário (pode ser vazio)
     * @return {@link LocalDate} ou {@code null} se inválido
     */
    private LocalDate resolverData(String arg) {
        if (arg == null || arg.isBlank()) {
            return DateParserUtils.hoje();
        }
        return DateParserUtils.parse(arg);
    }
}
