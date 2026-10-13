/* (c) 2026 | 26/09/2026 */
package net.ddns.adambravo79.tmill.controller;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAdjusters;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import tools.jackson.databind.ObjectMapper;

/**
 * Utilitários compartilhados entre {@link AdminController} (API REST) e {@link AdminWebController}
 * (painel Thymeleaf).
 *
 * <p>Centraliza:
 *
 * <ul>
 *   <li>Constantes comuns (ex.: {@link #BRAZIL_ZONE})
 *   <li>Mascaramento de tokens para exibição segura em logs/JSON
 *   <li>Parseamento tolerante de datas ({@code "hoje"}, {@code "ontem"}, {@code "20/06"}, {@code
 *       "20-06"}, {@code "2026-06-20"})
 *   <li>Parseamento de horário no formato {@code HH:mm}
 *   <li>Cálculo de períodos para reenvio de podcast (semana completa sexta → sexta)
 *   <li>Validação de URLs HTTP/HTTPS
 *   <li>Carregamento de arquivos JSON de configuração (via propriedade, classpath, /app/config/ ou
 *       ./config/)
 *   <li>Leitura de propriedades de ambiente com fallback
 * </ul>
 *
 * <p>Esta classe não é instanciável.
 */
public final class AdminUtils {

    // =========================================================================
    // CONSTANTES
    // =========================================================================

    /** Timezone de negócio (Brasília). */
    public static final String BRAZIL_ZONE = "America/Sao_Paulo";

    /** Ano default para datas sem ano explícito (ex.: "20/06" → 2026-06-20). */
    private static final int DEFAULT_YEAR = 2026;

    /** Formato de hora aceito em parâmetros de query. */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /** Regex para extrair data de string livre (dd/MM, dd-MM, dd/MM/yyyy, yyyy-MM-dd). */
    private static final Pattern DATE_PATTERN =
            Pattern.compile("\\b(\\d{1,2}[/-]\\d{2}(?:[/-]\\d{4})?|\\d{4}-\\d{2}-\\d{2})\\b");

    /** Regex para limpar preposições/artigos comuns em comandos em português. */
    private static final Pattern CLEANUP_PATTERN =
            Pattern.compile("(?i)\\b(do|dia|de|da|as|os|dias)\\b");

    private AdminUtils() {
        // classe utilitária, não instanciar
    }

    // =========================================================================
    // MASCARAMENTO DE TOKENS
    // =========================================================================

    /**
     * Mascara um token para exibição em logs/JSON. Exibe apenas os 4 primeiros e 4 últimos
     * caracteres. Tokens com menos de 8 caracteres viram {@code "***"}.
     *
     * <p>Exemplo: {@code "1234567890:ABCdef..."} → {@code "1234...wxyz"}
     *
     * @param token token original (pode ser null)
     * @return token mascarado, nunca null
     */
    public static String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "***";
        }
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }

    // =========================================================================
    // PARSEAMENTO DE DATAS
    // =========================================================================

    /**
     * Parseia uma data em formato tolerante, aceitando expressões relativas e formatos variados.
     *
     * <p>Formatos aceitos:
     *
     * <ul>
     *   <li>{@code "hoje"}, {@code "de hoje"} → hoje em Brasília
     *   <li>{@code "ontem"}, {@code "de ontem"} → ontem em Brasília
     *   <li>{@code "20/06"} → 2026-06-20
     *   <li>{@code "20-06"} → 2026-06-20
     *   <li>{@code "20/06/2026"} → 2026-06-20
     *   <li>{@code "2026-06-20"} → 2026-06-20
     *   <li>Strings com ruído: {@code "resultados do dia 20/06"} → 2026-06-20
     * </ul>
     *
     * @param param string de entrada (pode ser null/blank)
     * @return {@link LocalDate} parseada, ou {@code null} se não for possível
     */
    public static LocalDate parseDateParam(String param) {
        if (param == null || param.isBlank()) {
            return null;
        }

        String lower = param.toLowerCase().trim();
        if (lower.equals("hoje") || lower.equals("de hoje")) {
            return LocalDate.now(ZoneId.of(BRAZIL_ZONE));
        }
        if (lower.equals("ontem") || lower.equals("de ontem")) {
            return LocalDate.now(ZoneId.of(BRAZIL_ZONE)).minusDays(1);
        }

        // Remove preposições/artigos comuns ("do dia", "de", "da", etc.)
        String cleaned = CLEANUP_PATTERN.matcher(param).replaceAll(" ").trim();

        // Tenta extrair data com regex
        LocalDate parsed = tryParseWithPattern(cleaned);
        if (parsed != null) {
            return parsed;
        }

        // Fallback com formatos simples (sem ano)
        return tryParseFallback(param, "dd/MM", "dd-MM");
    }

    /**
     * Tenta extrair uma data de uma string usando regex. Aceita os formatos: {@code dd/MM}, {@code
     * dd-MM}, {@code dd/MM/yyyy}, {@code dd-MM-yyyy}, {@code yyyy-MM-dd}.
     *
     * @param cleaned string já limpa de preposições
     * @return {@link LocalDate} ou {@code null}
     */
    public static LocalDate tryParseWithPattern(String cleaned) {
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

            // dd/MM ou dd-MM (sem ano)
            if (dateStr.matches("\\d{1,2}[/-]\\d{2}")) {
                DateTimeFormatter fmt =
                        new DateTimeFormatterBuilder()
                                .appendPattern(dateStr.contains("/") ? "dd/MM" : "dd-MM")
                                .parseDefaulting(ChronoField.YEAR, DEFAULT_YEAR)
                                .toFormatter();
                return LocalDate.parse(dateStr, fmt);
            }

            // dd/MM/yyyy ou dd-MM-yyyy
            if (dateStr.matches("\\d{1,2}[/-]\\d{2}[/-]\\d{4}")) {
                DateTimeFormatter fmt =
                        DateTimeFormatter.ofPattern(
                                dateStr.contains("/") ? "dd/MM/yyyy" : "dd-MM-yyyy");
                return LocalDate.parse(dateStr, fmt);
            }
        } catch (DateTimeParseException ignored) {
            // cai no fallback do caller
        }
        return null;
    }

    /**
     * Tenta parsear a string inteira usando os formatos fornecidos, com ano default.
     *
     * @param param string de entrada
     * @param patterns formatos aceitos (ex.: {@code "dd/MM"}, {@code "dd-MM"})
     * @return {@link LocalDate} ou {@code null}
     */
    public static LocalDate tryParseFallback(String param, String... patterns) {
        for (String p : patterns) {
            try {
                DateTimeFormatter fmt =
                        new DateTimeFormatterBuilder()
                                .appendPattern(p)
                                .parseDefaulting(ChronoField.YEAR, DEFAULT_YEAR)
                                .toFormatter();
                return LocalDate.parse(param, fmt);
            } catch (DateTimeParseException ignored) {
                // tenta próximo formato
            }
        }
        return null;
    }

    // =========================================================================
    // PARSEAMENTO DE HORÁRIO
    // =========================================================================

    /**
     * Parseia um horário no formato {@code HH:mm}.
     *
     * @param timeStr string de entrada (pode ser null/blank/inválida)
     * @return {@link LocalTime} ou {@code null} se inválido
     */
    public static LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(timeStr, TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // =========================================================================
    // PODCAST — CÁLCULO DE PERÍODO (REENVIO POR SEMANA)
    // =========================================================================

    /**
     * Retorna a última sexta-feira anterior ou igual à data de referência. Se a data já for
     * sexta-feira, retorna ela mesma.
     *
     * <p>Útil para o endpoint de reenvio de podcast: como o podcast é publicado toda sexta, o
     * caso de uso mais comum é "reenviar a edição da última sexta".
     *
     * <p>Exemplos:
     *
     * <ul>
     *   <li>Referência = 2026-10-13 (terça) → retorna 2026-10-09 (sexta anterior)
     *   <li>Referência = 2026-10-09 (sexta) → retorna 2026-10-09 (ela mesma)
     *   <li>Referência = 2026-10-10 (sábado) → retorna 2026-10-09 (sexta anterior)
     *   <li>Referência = 2026-10-16 (sexta seguinte) → retorna 2026-10-16
     * </ul>
     *
     * @param reference data de referência. Se {@code null}, usa {@link LocalDate#now(ZoneId)} no
     *     fuso {@link #BRAZIL_ZONE}
     * @return a sexta-feira alvo (nunca {@code null})
     */
    public static LocalDate ultimaSexta(LocalDate reference) {
        LocalDate ref = (reference != null) ? reference : LocalDate.now(ZoneId.of(BRAZIL_ZONE));
        return ref.with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY));
    }

    /**
     * Calcula o período de uma semana completa de podcast que termina na sexta-feira informada.
     *
     * <p>Convenção: o podcast semanal cobre de <b>sexta-feira 12:00</b> (da semana anterior) até
     * <b>sexta-feira 11:59:59</b> (da semana alvo). Ou seja, exclui a manhã da sexta alvo para
     * não capturar mensagens que pertencem à próxima edição.
     *
     * <p>Exemplo: se {@code friday = 2026-10-09}, retorna:
     *
     * <ul>
     *   <li>{@code from = 2026-10-02T12:00:00}
     *   <li>{@code to   = 2026-10-09T11:59:59}
     * </ul>
     *
     * <p>Os limites são calculados no fuso {@link #BRAZIL_ZONE} — como {@link LocalDateTime} não
     * carrega fuso, o caller deve interpretá-los no mesmo fuso ao consultar o banco.
     *
     * @param friday sexta-feira de referência (não pode ser {@code null})
     * @return array de 2 posições: {@code [from, to]}
     * @throws IllegalArgumentException se {@code friday} for {@code null} ou não for sexta-feira
     */
    public static LocalDateTime[] calcularPeriodoPodcast(LocalDate friday) {
        if (friday == null) {
            throw new IllegalArgumentException("friday não pode ser nulo");
        }
        if (!friday.getDayOfWeek().equals(DayOfWeek.FRIDAY)) {
            throw new IllegalArgumentException(
                    "Data deve ser uma sexta-feira. Recebido: " + friday);
        }

        LocalDateTime from = friday.minusWeeks(1).atTime(12, 0, 0);
        LocalDateTime to = friday.atTime(11, 59, 59);
        return new LocalDateTime[] {from, to};
    }

    // =========================================================================
    // VALIDAÇÃO DE URL
    // =========================================================================

    /**
     * Verifica se a string é uma URL HTTP/HTTPS válida com host não vazio.
     *
     * @param url string de entrada (pode ser null/blank)
     * @return {@code true} se for URL HTTP/HTTPS válida
     */
    public static boolean isValidUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    // =========================================================================
    // CARREGAMENTO DE ARQUIVOS DE CONFIGURAÇÃO
    // =========================================================================

    /**
     * Carrega um arquivo JSON usando a propriedade do Spring que aponta para ele.
     *
     * <p>Ordem de busca:
     *
     * <ol>
     *   <li>Valor da propriedade (ex.: {@code easter-egg.file=file:./config/easter-eggs.json})
     *   <li>Fallback 1: {@code classpath:<fileName>} (dentro do JAR)
     *   <li>Fallback 2: {@code file:/app/config/<fileName>} (container em prod)
     *   <li>Fallback 3: {@code file:./config/<fileName>} (dev local)
     * </ol>
     *
     * <p>Isso garante que o controller leia o arquivo <b>do mesmo local que os services</b> (ex.:
     * {@code EasterEggService}, {@code AutoResponseService}, {@code StaticWorldCupService}).
     *
     * <p><b>Robustez:</b> cada tentativa passa por {@link #tryLoad}, que trata {@code null} do
     * {@link ResourceLoader} e {@code resource.exists() == false} sem lançar NPE.
     *
     * @param resourceLoader loader do Spring
     * @param environment ambiente do Spring (para resolver a propriedade)
     * @param objectMapper mapper Jackson
     * @param propertyKey chave da propriedade (ex.: {@code "easter-egg.file"})
     * @param fileName nome do arquivo (ex.: {@code "easter-eggs.json"})
     * @param defaultLocation valor default caso a propriedade não exista
     * @return conteúdo parseado como {@link Object}
     * @throws IOException se o arquivo não for encontrado em nenhum local
     */
    public static Object loadConfigFile(
            ResourceLoader resourceLoader,
            Environment environment,
            ObjectMapper objectMapper,
            String propertyKey,
            String fileName,
            String defaultLocation)
            throws IOException {

        // 1. Propriedade específica (ex: easter-egg.file)
        String configuredLocation = environment.getProperty(propertyKey, defaultLocation);
        Object content = tryLoad(resourceLoader, objectMapper, configuredLocation);
        if (content != null) {
            return content;
        }

        // 2. classpath:
        content = tryLoad(resourceLoader, objectMapper, "classpath:" + fileName);
        if (content != null) {
            return content;
        }

        // 3. /app/config/ (container prod)
        content = tryLoad(resourceLoader, objectMapper, "file:/app/config/" + fileName);
        if (content != null) {
            return content;
        }

        // 4. ./config/ (dev local)
        content = tryLoad(resourceLoader, objectMapper, "file:./config/" + fileName);
        if (content != null) {
            return content;
        }

        throw new IOException(
                "Arquivo não encontrado em nenhum local: "
                        + fileName
                        + " (propriedade '"
                        + propertyKey
                        + "'="
                        + configuredLocation
                        + ")");
    }

    /**
     * Tenta carregar e parsear um recurso. Retorna {@code null} se:
     *
     * <ul>
     *   <li>o {@link ResourceLoader} retornar {@code null} para a localização
     *   <li>o {@link Resource} não existir ({@code exists() == false})
     *   <li>o {@code location} for {@code null} ou blank
     * </ul>
     *
     *     * to do o {@code loadConfigFile} passa por aqui. Isso evita NPE quando o loader é mal <p>Este método é a <b>única</b> fronteira segura para interagir com {@link ResourceLoader} —
     * to do o {@code loadConfigFile} passa por aqui. Isso evita NPE quando o loader é mal
     * configurado (ex.: mock de teste que não cobre um path específico).
     *
     * @param resourceLoader loader do Spring
     * @param objectMapper mapper Jackson
     * @param location localização do recurso (ex.: {@code "classpath:foo.json"})
     * @return conteúdo parseado, ou {@code null} se não carregável
     * @throws IOException se o parse JSON falhar (não silencia erros de sintaxe)
     */
    private static Object tryLoad(
            ResourceLoader resourceLoader, ObjectMapper objectMapper, String location)
            throws IOException {

        if (location == null || location.isBlank()) {
            return null;
        }

        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            return null;
        }

        return objectMapper.readValue(resource.getInputStream(), Object.class);
    }

    // =========================================================================
    // LEITURA DE PROPRIEDADES COM FALLBACK
    // =========================================================================

    /**
     * Lê uma propriedade do ambiente, retornando um valor default caso ausente ou em branco.
     *
     * @param environment ambiente do Spring
     * @param key chave da propriedade
     * @param defaultValue valor default
     * @return valor da propriedade ou {@code defaultValue}
     */
    public static String getPropertyOrDefault(
            Environment environment, String key, String defaultValue) {
        String value = environment.getProperty(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }
}
