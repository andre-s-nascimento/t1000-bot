/* (c) 2026 | 04/11/2026 */
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
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;

import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import net.ddns.adambravo79.tmill.util.DateParserUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * Utilitários compartilhados entre {@link AdminController} (API REST) e {@link AdminWebController}
 * (painel Thymeleaf).
 */
public final class AdminUtils {

    // =========================================================================
    // CONSTANTES
    // =========================================================================

    /** Timezone de negócio (Brasília). */
    public static final String BRAZIL_ZONE = "America/Sao_Paulo";

    /** Formato de hora aceito em parâmetros de query. */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private AdminUtils() {
        // classe utilitária, não instanciar
    }

    // =========================================================================
    // MASCARAMENTO DE TOKENS
    // =========================================================================

    public static String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "***";
        }
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }

    // =========================================================================
    // PARSEAMENTO DE DATAS — DELEGAÇÃO PARA DateParserUtils
    // =========================================================================

    /**
     * Parseia uma data em formato tolerante.
     *
     * <p><b>Refatoração v2.7.1:</b> a lógica de parse foi movida para {@link DateParserUtils}
     * para evitar duplicação de código (SonarQube 7.2%). Este método agora delega.
     *
     * @param param string de entrada (pode ser null/blank)
     * @return {@link LocalDate} parseada, ou {@code null} se não for possível
     */
    public static LocalDate parseDateParam(String param) {
        return DateParserUtils.parse(param);
    }

    // =========================================================================
    // PARSEAMENTO DE HORÁRIO
    // =========================================================================

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
    // PODCAST — CÁLCULO DE PERÍODO
    // =========================================================================

    public static LocalDate ultimaSexta(LocalDate reference) {
        LocalDate ref = (reference != null) ? reference : LocalDate.now(ZoneId.of(BRAZIL_ZONE));
        return ref.with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY));
    }

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

    public static Object loadConfigFile(
            ResourceLoader resourceLoader,
            Environment environment,
            ObjectMapper objectMapper,
            String propertyKey,
            String fileName,
            String defaultLocation)
            throws IOException {

        String configuredLocation = environment.getProperty(propertyKey, defaultLocation);
        Object content = tryLoad(resourceLoader, objectMapper, configuredLocation);
        if (content != null) return content;

        content = tryLoad(resourceLoader, objectMapper, "classpath:" + fileName);
        if (content != null) return content;

        content = tryLoad(resourceLoader, objectMapper, "file:/app/config/" + fileName);
        if (content != null) return content;

        content = tryLoad(resourceLoader, objectMapper, "file:./config/" + fileName);
        if (content != null) return content;

        throw new IOException(
                "Arquivo não encontrado em nenhum local: "
                        + fileName
                        + " (propriedade '"
                        + propertyKey
                        + "'="
                        + configuredLocation
                        + ")");
    }

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

    public static String getPropertyOrDefault(
            Environment environment, String key, String defaultValue) {
        String value = environment.getProperty(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }
}
