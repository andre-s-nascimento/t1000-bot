/* (c) 2026 | 04/11/2026 */
package net.ddns.adambravo79.tmill.telegram.handler;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.model.Update;

import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.constant.BotMessages;
import net.ddns.adambravo79.tmill.service.BirthdayService;
import net.ddns.adambravo79.tmill.telegram.core.GroupAuthorizationService;
import net.ddns.adambravo79.tmill.telegram.core.TelegramFacade;
import net.ddns.adambravo79.tmill.telegram.util.MetricsService;
import net.ddns.adambravo79.tmill.telegram.util.TelegramUtils;
import net.ddns.adambravo79.tmill.util.LogSanitizer;

/**
 * Handler especializado para o comando {@code t1000 registrar aniversário DD/MM}.
 *
 * <h2>Responsabilidades</h2>
 *
 * <ol>
 *   <li>Detectar se o texto começa com o prefixo do comando (via {@link #getPrefixo}).
 *   <li>Extrair a data do texto após o prefixo.
 *   <li>Delegar a validação e persistência para {@link BirthdayService#registrar}.
 *   <li>Enviar a resposta do service de volta ao usuário.
 * </ol>
 *
 * <h2>Variantes de prefixo aceitas</h2>
 *
 * <p>Todas as combinações abaixo são reconhecidas (case-insensitive, com ou sem acento):
 *
 * <pre>
 * t1000 registrar aniversário DD/MM
 * t1000 registrar aniversario DD/MM
 * t1000 anotar aniversário DD/MM
 * t1000 anotar aniversario DD/MM
 * t1000 aniversário DD/MM
 * t1000 aniversario DD/MM
 * </pre>
 *
 * <p>Como o {@link #getPrefixo()} retorna apenas {@code "t1000 registrar aniversario"} (a
 * variante canônica), o roteador ({@code CommandHandler}) faz o match pelas variantes — a
 * responsabilidade de normalização fica no roteador, não aqui. Este handler recebe qualquer texto
 * que o roteador decidir que é dele.
 *
 * <h2>Formato da data</h2>
 *
 * <p>A regex {@link #PATTERN_ANIVERSARIO} captura e remove o prefixo, deixando a data "crua".
 * Exemplos:
 *
 * <ul>
 *   <li>{@code "t1000 registrar aniversário 05/10"} → data extraída: {@code "05/10"}
 *   <li>{@code "t1000 anotar aniversário 5/10"} → data extraída: {@code "5/10"}
 *   <li>{@code "t1000 aniversário"} → data extraída: {@code ""} (dispara "peça a data")
 * </ul>
 *
 * <p>O {@code BirthdayService} é quem valida a data (formato, existência do dia/mês).
 *
 * <h2>Métricas</h2>
 *
 * <p>Emite {@code comando_aniversario} via {@link MetricsService} sempre que o comando é
 * reconhecido.
 */
@Slf4j
@Component
public class BirthdayHandler extends AbstractCommandHandler {

    private static final String COMANDO_ANIVERSARIO = "comando_aniversario";

    /** Prefixo canônico do comando. */
    public static final String PREFIXO = "t1000 registrar aniversario";

    /**
     * Regex que remove o prefixo do comando, deixando apenas a data.
     *
     * <p>Cobre todas as variantes aceitas (registrar/anotar, com/sem acento, com/sem espaço).
     *
     * <p>{@code UNICODE_CASE} garante que {@code á} e {@code a} sejam tratados equivalentes em
     * {@code [aá]}.
     */
    private static final Pattern PATTERN_ANIVERSARIO =
            Pattern.compile(
                    "^t1000\\s+(?:registrar\\s+|anotar\\s+)?anivers[aá]rio\\s*",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final BirthdayService birthdayService;
    private final MetricsService metricsService;

    public BirthdayHandler(
            TelegramFacade telegramFacade,
            GroupAuthorizationService authService,
            TelegramUtils utils,
            BirthdayService birthdayService,
            MetricsService metricsService) {
        super(telegramFacade, authService, utils);
        this.birthdayService = birthdayService;
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
        // Usa o texto BRUTO (preserva acentos e a data como o usuário digitou)
        String rawText = textoBruto(update);

        // Remove o prefixo, deixando a data crua
        String dataTexto = PATTERN_ANIVERSARIO.matcher(rawText).replaceFirst("").trim();

        long chatId = chatId(update);

        // 1. Sem data → pede ao usuário
        if (dataTexto.isEmpty()) {
            sendHtml(chatId, BotMessages.ANIVERSARIO_PEDE_DATA);
            metricsService.success(COMANDO_ANIVERSARIO);
            return;
        }

        // 2. Delega validação + persistência ao service
        long userId = userId(update);
        String userName = userName(update);

        String resposta;
        try {
            resposta = birthdayService.registrar(userId, userName, dataTexto);
        } catch (RuntimeException e) {
            log.error(
                    "❌ Falha ao registrar aniversário (userId={}, data='{}')",
                    LogSanitizer.sanitizeId(userId),
                    LogSanitizer.sanitize(dataTexto),
                    e);
            // Não relança: a superclasse já envia mensagem de erro genérica.
            // Aqui só queremos registrar a métrica e não duplicar mensagem.
            sendPlain(chatId, BotMessages.ERRO_GENERICO);
            metricsService.error(COMANDO_ANIVERSARIO);
            return;
        }

        // 3. Envia a resposta do service (pode ser sucesso OU erro de validação)
        sendHtml(chatId, resposta);

        metricsService.success(COMANDO_ANIVERSARIO);

        log.info(
                "🎂 Comando de aniversário processado: userId={} data='{}'",
                LogSanitizer.sanitizeId(userId),
                LogSanitizer.sanitize(dataTexto));
    }
}
