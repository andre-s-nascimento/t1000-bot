/* (c) 2026 | 04/11/2026 */
package net.ddns.adambravo79.tmill.util;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.ddns.adambravo79.tmill.constant.BotMessages;

/**
 * Utilitário centralizado para parseamento de datas em comandos do bot.
 *
 * <h2>Motivação</h2>
 *
 * <p>Antes da refatoração, a lógica de parse de data estava duplicada em três lugares:
 *
 * <ul>
 *   <li>{@code CommandHandler.parseDateParam} (para comandos da Copa)
 *   <li>{@code AdminUtils.parseDateParam} (para o painel admin)
 *   <li>(futuro) {@code TorrentHandler} (para busca por data)
 * </ul>
 *
 * <p>Esta classe unifica os dois primeiros casos e serve de base para o terceiro.
 *
 * <h2>Formatos aceitos</h2>
 *
 * <ul>
 *   <li><b>Relativos:</b> {@code "hoje"}, {@code "de hoje"}, {@code "ontem"}, {@code "de ontem"}
 *       (interpretados no fuso {@link BotMessages#BRAZIL_ZONE})
 *   <li><b>Curto:</b> {@code "20/06"} → 2026-06-20, {@code "20-06"} → 2026-06-20, {@code "5/10"}
 *       → 2026-10-05, {@code "1/1"} → 2026-01-01
 *   <li><b>Com ano:</b> {@code "20/06/2026"}, {@code "20-06-2026"}, {@code "2026-06-20"}
 *   <li><b>Com ruído:</b> {@code "resultados do dia 20/06"} → 2026-06-20
 * </ul>
 *
 * <h2>Ano padrão</h2>
 *
 * <p>Quando o ano é omitido, o parser usa {@link #DEFAULT_YEAR} (2026). Isso é intencional: a Copa
 * do Mundo de 2026 é o caso de uso principal, e o formato {@code dd/MM} é sempre interpretado no
 * ano corrente do torneio.
 *
 * <h2>Strict vs Lenient</h2>
 *
 * <p>Usa {@link ResolverStyle#STRICT} em todos os formatters. Isso significa que:
 *
 * <ul>
 *   <li>{@code "31/02"} retorna {@code null} (fevereiro não tem 31 dias)
 *   <li>{@code "29/02"} (2026, não bissexto) retorna {@code null}
 *   <li>{@code "29/02/2024"} (bissexto) é aceito
 *   <li>Sem lenient parsing, datas inválidas NÃO são "corrigidas" silenciosamente
 * </ul>
 *
 * <h2>Thread-safety</h2>
 *
 * <p>Classe utilitária stateless (todos os métodos são {@code static} e usam apenas variáveis
 * locais). Segura para uso concorrente em virtual threads.
 */
public final class DateParserUtils {

    /** Ano default para datas sem ano explícito (ex.: "20/06" → 2026-06-20). */
    public static final int DEFAULT_YEAR = 2026;

    /** Fuso de negócio (Brasília). */
    private static final ZoneId BRAZIL_ZONE = ZoneId.of(BotMessages.BRAZIL_ZONE);

    /**
     * Regex para extrair data de string livre.
     *
     * <p>Aceita: {@code dd/MM}, {@code dd-MM}, {@code d/M}, {@code d-M}, {@code dd/MM/yyyy},
     * {@code dd-MM-yyyy}, {@code yyyy-MM-dd}.
     *
     * <p>O {@link Pattern#UNICODE_CASE} é necessário para que {@code \b} funcione corretamente com
     * caracteres acentuados (ex.: {@code "de hoje"}).
     *
     * <p><b>Nota:</b> usa {@code \d{1,2}} no mês (não {@code \d{2}}) para aceitar {@code "5/10"} e
     * {@code "1/1"}. O fallback via {@code tryParseWithPattern} valida se a data é realmente
     * válida (e.g., {@code "31/02"} passa na regex mas falha no parse strict).
     */
    private static final Pattern DATE_PATTERN =
            Pattern.compile(
                    "\\b(\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{4})?|\\d{4}-\\d{2}-\\d{2})\\b",
                    Pattern.UNICODE_CASE);

    /**
     * Regex para limpar preposições/artigos comuns em comandos em português.
     *
     * <p>Ex.: {@code "resultados do dia 20/06"} → {@code "resultados   20/06"}.
     *
     * <p>O {@code UNICODE_CASE} garante que {@code \b} reconheça limites com acentos.
     */
    private static final Pattern CLEANUP_PATTERN =
            Pattern.compile("(?i)\\b(do|dia|de|da|as|os|dias)\\b", Pattern.UNICODE_CASE);

    private DateParserUtils() {
        // classe utilitária, não instanciar
    }

    // =========================================================================
    // API PÚBLICA
    // =========================================================================

    /**
     * Parseia uma data em formato tolerante, aceitando expressões relativas e formatos variados.
     *
     * <p>Retorna {@code null} se a string for nula, vazia ou não contiver uma data válida.
     *
     * @param param string de entrada (pode ser {@code null})
     * @return {@link LocalDate} parseada, ou {@code null}
     */
    public static LocalDate parse(String param) {
        if (param == null || param.isBlank()) {
            return null;
        }

        String lower = param.toLowerCase().trim();

        // 1. Relativos (mais rápidos: comparação direta de string)
        if (lower.equals("hoje") || lower.equals("de hoje")) {
            return LocalDate.now(BRAZIL_ZONE);
        }
        if (lower.equals("ontem") || lower.equals("de ontem")) {
            return LocalDate.now(BRAZIL_ZONE).minusDays(1);
        }

        // 2. Remove ruído ("do dia", "de", "da", etc.)
        String cleaned = CLEANUP_PATTERN.matcher(param).replaceAll(" ").trim();

        // 3. Tenta extrair data com regex (aceita ruído residual)
        LocalDate parsed = tryParseWithPattern(cleaned);
        if (parsed != null) {
            return parsed;
        }

        // 4. Fallback: assume que a string INTEIRA é uma data curta (d/M ou d-M)
        return tryParseFallback(param, "d/M", "d-M");
    }

    /**
     * Retorna {@link LocalDate} de hoje no fuso de Brasília.
     *
     * @return data atual
     */
    public static LocalDate hoje() {
        return LocalDate.now(BRAZIL_ZONE);
    }

    /**
     * Retorna {@link LocalDate} de ontem no fuso de Brasília.
     *
     * @return data de ontem
     */
    public static LocalDate ontem() {
        return LocalDate.now(BRAZIL_ZONE).minusDays(1);
    }

    // =========================================================================
    // HELPERS INTERNOS (package-private para testes)
    // =========================================================================

    /**
     * Tenta extrair uma data de uma string usando regex. Aceita os formatos:
     *
     * <ul>
     *   <li>{@code d/M} ou {@code d-M} → {@link #DEFAULT_YEAR}
     *   <li>{@code dd/MM/yyyy} ou {@code dd-MM-yyyy}
     *   <li>{@code yyyy-MM-dd}
     * </ul>
     *
     * <p>Usa {@link ResolverStyle#STRICT}, então datas inválidas (ex.: {@code "31/02"}) retornam
     * {@code null} em vez de serem "corrigidas" para o último dia do mês.
     *
     * @param cleaned string já limpa de preposições
     * @return {@link LocalDate} ou {@code null}
     */
    static LocalDate tryParseWithPattern(String cleaned) {
        if (cleaned == null || cleaned.isBlank()) {
            return null;
        }

        Matcher matcher = DATE_PATTERN.matcher(cleaned);
        if (!matcher.find()) {
            return null;
        }

        String dateStr = matcher.group(1).trim();
        try {
            // ISO: yyyy-MM-dd
            if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(dateStr);
            }

            // d/M ou d-M (sem ano) — aceita 1 ou 2 dígitos no dia E no mês
            if (dateStr.matches("\\d{1,2}[/-]\\d{1,2}")) {
                DateTimeFormatter fmt =
                        new DateTimeFormatterBuilder()
                                .appendPattern(dateStr.contains("/") ? "d/M" : "d-M")
                                .parseDefaulting(ChronoField.YEAR, DEFAULT_YEAR)
                                .toFormatter()
                                .withResolverStyle(ResolverStyle.STRICT);
                return LocalDate.parse(dateStr, fmt);
            }

            // dd/MM/yyyy ou dd-MM-yyyy
            if (dateStr.matches("\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}")) {
                DateTimeFormatter fmt =
                        DateTimeFormatter.ofPattern(dateStr.contains("/") ? "d/M/uuuu" : "d-M-uuuu")
                                .withResolverStyle(ResolverStyle.STRICT);
                return LocalDate.parse(dateStr, fmt);
            }
        } catch (DateTimeParseException ignored) {
            // data inválida (ex.: 99/99, 31/02) — cai no fallback do caller
        }
        return null;
    }

    /**
     * Tenta parsear a string INTEIRA usando os formatos fornecidos, com ano default.
     *
     * <p>Usa {@link ResolverStyle#STRICT}, então datas inválidas retornam {@code null}.
     *
     * @param param string de entrada
     * @param patterns formatos aceitos (ex.: {@code "d/M"}, {@code "d-M"})
     * @return {@link LocalDate} ou {@code null}
     */
    static LocalDate tryParseFallback(String param, String... patterns) {
        if (param == null || param.isBlank() || patterns == null) {
            return null;
        }
        for (String p : patterns) {
            try {
                DateTimeFormatter fmt =
                        new DateTimeFormatterBuilder()
                                .appendPattern(p)
                                .parseDefaulting(ChronoField.YEAR, DEFAULT_YEAR)
                                .toFormatter()
                                .withResolverStyle(ResolverStyle.STRICT);
                return LocalDate.parse(param.trim(), fmt);
            } catch (DateTimeParseException ignored) {
                // tenta próximo formato
            }
        }
        return null;
    }
}
