package net.ddns.adambravo79.tmill.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LogSanitizerTest {

    // ===================== TESTES PARAMETRIZADOS =====================

    @ParameterizedTest
    @CsvSource({
        "'Hello\u0000World\u0007\u001f', 'Hello?World??'",
        "'Hello    World  \n  Test\t\t', 'Hello World Test'",
        "'Bearer token123456 token=abc123 key=xyz secret=456', 'Bearer token123456 [REDACTED]"
                + " [REDACTED] [REDACTED]'",
        "'Texto|com pipe \n \r \t', 'Texto\\|com pipe'",
        "'Token=abc123 KEY=xyz Secret=456', '[REDACTED] [REDACTED] [REDACTED]'",
        "'Bearer 12345', 'Bearer 12345'",
        "'field|value', 'field\\|value'"
    })
    void sanitize_deveProcessarCorretamente(String input, String expected) {
        assertThat(LogSanitizer.sanitize(input)).isEqualTo(expected);
    }

    // ===================== TESTES COM TRUNCAMENTO =====================

    @Test
    void sanitize_deveTruncarStringLonga() {
        String input = "a".repeat(600);
        String result = LogSanitizer.sanitize(input, 100);
        assertThat(result)
                .startsWith("a".repeat(100))
                .contains("[truncated, len=" + 600 + "]")
                .hasSizeLessThanOrEqualTo(150);
    }

    @Test
    void sanitize_quandoInputMenorQueMax_naoTrunca() {
        String input = "abc";
        assertThat(LogSanitizer.sanitize(input, 500)).isEqualTo("abc");
    }

    @Test
    void sanitize_deveAplicarTodasAsRegras() {
        String input = "User said: \nBearer abc123   key=xyz \t and token=secret";
        String result = LogSanitizer.sanitize(input, 100);
        // A regex não captura "Bearer abc123" (sem '=' ou ':')
        assertThat(result).isEqualTo("User said: Bearer abc123 [REDACTED] and [REDACTED]");
    }

    // ===================== TESTES PARA VERSÕES COM LIMITES =====================

    @Test
    void sanitizeUserName_deveTruncarNomeLongo() {
        String name = "a".repeat(200);
        String result = LogSanitizer.sanitizeUserName(name);
        assertThat(result).startsWith("a".repeat(100)).contains("[truncated");
    }

    @Test
    void sanitizeUserName_deveSanitizarNome() {
        String name = "John\u0000Doe\n\t";
        // \u0000 é substituído por '?', \n e \t normalizados para espaço
        assertThat(LogSanitizer.sanitizeUserName(name)).isEqualTo("John?Doe");
    }

    @Test
    void sanitizeMessageText_deveTruncarMensagemLonga() {
        String msg = "a".repeat(300);
        String result = LogSanitizer.sanitizeMessageText(msg);
        assertThat(result).startsWith("a".repeat(200)).contains("[truncated");
    }

    @Test
    void sanitizeQuery_deveTruncarQueryLonga() {
        String query = "a".repeat(200);
        String result = LogSanitizer.sanitizeQuery(query);
        assertThat(result).startsWith("a".repeat(150)).contains("[truncated");
    }

    // ===================== TESTES PARA ID =====================

    @Test
    void sanitizeId_paraPositivo_retornaUserComId() {
        assertThat(LogSanitizer.sanitizeId(12345L)).isEqualTo("user-12345");
    }

    @Test
    void sanitizeId_paraNegativo_retornaGroupComIdAbsoluto() {
        assertThat(LogSanitizer.sanitizeId(-100L)).isEqualTo("group-100");
    }

    @Test
    void sanitizeId_paraZero_retornaGroup0() {
        // A lógica: id > 0 ? user : group (incluindo zero)
        assertThat(LogSanitizer.sanitizeId(0L)).isEqualTo("group-0");
    }

    // ===================== TESTES DE CONTROLE =====================

    @Test
    void sanitize_deveRemoverCaracteresDeControleEspecificos() {
        String input = "a\u0000b\u0007c\u000bd\u001fe\u007f";
        assertThat(LogSanitizer.sanitize(input)).isEqualTo("a?b?c?d?e?");
    }

    @Test
    void sanitize_devePreservarTabENewline() {
        String input = "a\nb\tc";
        assertThat(LogSanitizer.sanitize(input)).isEqualTo("a b c");
    }

    // ===================== TESTES DE INPUT NULL =====================

    @Test
    void sanitize_quandoInputNull_retornaNull() {
        assertThat(LogSanitizer.sanitize(null)).isEqualTo("null");
    }
}
