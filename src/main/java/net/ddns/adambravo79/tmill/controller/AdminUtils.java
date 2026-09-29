/* (c) 2026 | 26/09/2026 */
package net.ddns.adambravo79.tmill.controller;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
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
 *   <li>Constantes comuns (ex.: {@link #SHOWCASE_CHAT_ID})
 *   <li>Mascaramento de tokens para exibição segura em logs/JSON
 *   <li>Parseamento tolerante de datas ({@code "hoje"}, {@code "ontem"}, {@code "20/06"}, {@code
 *       "20-06"}, {@code "2026-06-20"})
 *   <li>Parseamento de horário no formato {@code HH:mm}
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

    /** Canal de showcase — usado como destino padrão em ações manuais. */
    public static final long SHOWCASE_CHAT_ID = -5283244164L;

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
     * <p><b>Robustez:</b> cada tentativa passa por {@link #tryLoad}, que trata {@code null} do {@link
     * ResourceLoader} e {@code resource.exists() == false} sem lançar NPE.
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
     * Carrega um arquivo JSON apenas pelo nome, sem consultar propriedades do Spring.
     *
     * <p><b>Preferir a versão com {@code propertyKey}</b> — esta versão existe apenas por
     * compatibilidade com chamadas legadas.
     *
     * <p>Ordem de busca:
     *
     * <ol>
     *   <li>{@code classpath:<fileName>} (dentro do JAR)
     *   <li>{@code file:/app/config/<fileName>} (container prod)
     *   <li>{@code file:./config/<fileName>} (dev local)
     * </ol>
     *
     * @param resourceLoader loader do Spring
     * @param objectMapper mapper Jackson
     * @param fileName nome do arquivo (ex.: {@code "easter-eggs.json"})
     * @return conteúdo parseado como {@link Object}
     * @throws IOException se o arquivo não for encontrado em nenhum local
     * @deprecated usar {@link #loadConfigFile(ResourceLoader, Environment, ObjectMapper, String,
     *     String, String)}
     */
    @Deprecated(since = "2.5.5")
    public static Object loadConfigFile(
            ResourceLoader resourceLoader, ObjectMapper objectMapper, String fileName)
            throws IOException {

        Object content = tryLoad(resourceLoader, objectMapper, "classpath:" + fileName);
        if (content != null) {
            return content;
        }

        content = tryLoad(resourceLoader, objectMapper, "file:/app/config/" + fileName);
        if (content != null) {
            return content;
        }

        content = tryLoad(resourceLoader, objectMapper, "file:./config/" + fileName);
        if (content != null) {
            return content;
        }

        throw new IOException("Arquivo não encontrado em nenhum local: " + fileName);
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
     * <p>Este método é a <b>única</b> fronteira segura para interagir com {@link ResourceLoader} —
     * todo {@code loadConfigFile} passa por aqui. Isso evita NPE quando o loader é mal configurado
     * (ex.: mock de teste que não cobre um path específico).
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
        if (resource == null || !resource.exists()) {
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
