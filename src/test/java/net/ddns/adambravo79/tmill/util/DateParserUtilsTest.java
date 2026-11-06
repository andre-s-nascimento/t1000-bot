/* (c) 2026 | 04/11/2026 */
package net.ddns.adambravo79.tmill.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import net.ddns.adambravo79.tmill.constant.BotMessages;

/**
 * Testes do {@link DateParserUtils}.
 *
 * <p>Esta classe é o "cérebro" do parse de datas do bot — é usada por {@code WorldCupHandler},
 * futuramente pelo {@code TorrentHandler}, e centraliza os formatos aceitos. Um bug aqui afeta
 * todos os comandos que dependem de data.
 *
 * <p>Estrutura dos testes:
 *
 * <ul>
 *   <li>{@code Parse — relativos}: {@code "hoje"}, {@code "ontem"} e variantes
 *   <li>{@code Parse — formatos numéricos}: {@code dd/MM}, {@code dd-MM}, {@code dd/MM/yyyy},
 *       {@code yyyy-MM-dd}
 *   <li>{@code Parse — com ruído}: strings que contêm a data no meio
 *   <li>{@code Parse — inválidos}: retornam {@code null} sem lançar
 *   <li>{@code Helpers estáticos}: {@link DateParserUtils#hoje()} e {@link
 *       DateParserUtils#ontem()}
 *   <li>{@code tryParseWithPattern (package-private)}: testes de regex individual
 *   <li>{@code tryParseFallback (package-private)}: testes de fallback
 * </ul>
 *
 * <p><b>Nota sobre timezone:</b> os testes usam {@link ZoneId#of(String)} com {@link
 * BotMessages#BRAZIL_ZONE} para comparar com o esperado — não {@code LocalDate.now()} puro, que
 * usaria o default da JVM.
 */
class DateParserUtilsTest {

    private static final ZoneId BRAZIL_ZONE = ZoneId.of(BotMessages.BRAZIL_ZONE);

    // =========================================================================
    // parse() — datas relativas
    // =========================================================================

    @Nested
    @DisplayName("parse() — datas relativas")
    class ParseRelativos {

        @Test
        @DisplayName("'hoje' retorna a data atual em Brasília")
        void hoje() {
            assertThat(DateParserUtils.parse("hoje")).isEqualTo(LocalDate.now(BRAZIL_ZONE));
        }

        @Test
        @DisplayName("'HOJE' (uppercase) é aceito")
        void hojeUppercase() {
            assertThat(DateParserUtils.parse("HOJE")).isEqualTo(LocalDate.now(BRAZIL_ZONE));
        }

        @Test
        @DisplayName("'Hoje' (mixed case) é aceito")
        void hojeMixedCase() {
            assertThat(DateParserUtils.parse("Hoje")).isEqualTo(LocalDate.now(BRAZIL_ZONE));
        }

        @Test
        @DisplayName("'de hoje' é aceito")
        void deHoje() {
            assertThat(DateParserUtils.parse("de hoje")).isEqualTo(LocalDate.now(BRAZIL_ZONE));
        }

        @Test
        @DisplayName("'  hoje  ' (com espaços) é aceito")
        void hojeComEspacos() {
            assertThat(DateParserUtils.parse("  hoje  ")).isEqualTo(LocalDate.now(BRAZIL_ZONE));
        }

        @Test
        @DisplayName("'ontem' retorna a data de ontem em Brasília")
        void ontem() {
            assertThat(DateParserUtils.parse("ontem"))
                    .isEqualTo(LocalDate.now(BRAZIL_ZONE).minusDays(1));
        }

        @Test
        @DisplayName("'de ontem' é aceito")
        void deOntem() {
            assertThat(DateParserUtils.parse("de ontem"))
                    .isEqualTo(LocalDate.now(BRAZIL_ZONE).minusDays(1));
        }

        @Test
        @DisplayName("'ONTEM' (uppercase) é aceito")
        void ontemUppercase() {
            assertThat(DateParserUtils.parse("ONTEM"))
                    .isEqualTo(LocalDate.now(BRAZIL_ZONE).minusDays(1));
        }
    }

    // =========================================================================
    // parse() — formatos numéricos
    // =========================================================================

    @Nested
    @DisplayName("parse() — formatos numéricos")
    class ParseNumericos {

        @ParameterizedTest(name = "[{index}] ''{0}'' → {1}")
        @DisplayName("Formatos com barra e hífen")
        @CsvSource({
            // dd/MM e dd-MM (sem ano → usa DEFAULT_YEAR = 2026)
            "20/06, 2026-06-20",
            "20-06, 2026-06-20",
            "5/10,  2026-10-05",
            "1/1,   2026-01-01",
            "31/12, 2026-12-31",
            // dd/MM/yyyy e dd-MM-yyyy
            "20/06/2026, 2026-06-20",
            "20-06-2026, 2026-06-20",
            "01/01/2025, 2025-01-01",
            "31/12/2030, 2030-12-31",
            // ISO yyyy-MM-dd
            "2026-06-20, 2026-06-20",
            "2025-01-01, 2025-01-01",
            "2030-12-31, 2030-12-31"
        })
        void formatosNumericos(String input, LocalDate esperado) {
            assertThat(DateParserUtils.parse(input)).isEqualTo(esperado);
        }

        @Test
        @DisplayName("'29/02' (2026, não bissexto) retorna null — strict parsing")
        void vinteNoveFevereiroNaoBissexto() {
            // 2026 NÃO é bissexto → 29/02/2026 é inválido
            assertThat(DateParserUtils.parse("29/02")).isNull();
        }

        @Test
        @DisplayName("'29/02/2024' (ano bissexto explícito) é aceito")
        void vinteNoveFevereiroBissexto() {
            assertThat(DateParserUtils.parse("29/02/2024"))
                    .isEqualTo(LocalDate.of(2024, Month.FEBRUARY, 29));
        }

        @ParameterizedTest(name = "[{index}] ''{0}'' → null")
        @DisplayName("Datas impossíveis retornam null")
        @ValueSource(
                strings = {
                    "32/01", // dia > 31
                    "31/02", // fevereiro não tem 31
                    "31/04", // abril não tem 31
                    "31/06", // junho não tem 31
                    "31/09", // setembro não tem 31
                    "31/11", // novembro não tem 31
                    "00/01", // dia 0
                    "01/00", // mês 0
                    "01/13" // mês 13
                })
        void datasImpossiveis(String input) {
            assertThat(DateParserUtils.parse(input)).isNull();
        }
    }

    // =========================================================================
    // parse() — com ruído (data no meio de string)
    // =========================================================================

    @Nested
    @DisplayName("parse() — com ruído")
    class ParseComRuido {

        @ParameterizedTest(name = "[{index}] ''{0}'' → {1}")
        @DisplayName("Extrai data de strings com preposições/artigos")
        @CsvSource({
            "'resultados do dia 20/06', 2026-06-20",
            "'resultados do dia 20-06', 2026-06-20",
            "'jogos de 20/06', 2026-06-20",
            "'quero ver 20/06', 2026-06-20",
            "'placar de 2026-06-20', 2026-06-20",
            "'ontem foi 20/06', 2026-06-20",
            "'partida do dia 25/12/2025', 2025-12-25"
        })
        void comRuido(String input, LocalDate esperado) {
            assertThat(DateParserUtils.parse(input)).isEqualTo(esperado);
        }

        @Test
        @DisplayName("'do dia 20/06 valeu' extrai a data mesmo com texto depois")
        void comTextoDepois() {
            assertThat(DateParserUtils.parse("do dia 20/06 valeu"))
                    .isEqualTo(LocalDate.of(2026, Month.JUNE, 20));
        }

        @Test
        @DisplayName("String sem data numérica cai no fallback e retorna null")
        void semDataNumerica() {
            assertThat(DateParserUtils.parse("alguma coisa aleatória")).isNull();
        }
    }

    // =========================================================================
    // parse() — entradas inválidas
    // =========================================================================

    @Nested
    @DisplayName("parse() — entradas inválidas")
    class ParseInvalidos {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("null, vazio e whitespace retornam null")
        void nullVazioWhitespace(String input) {
            assertThat(DateParserUtils.parse(input)).isNull();
        }

        @ParameterizedTest(name = "[{index}] ''{0}'' → null")
        @DisplayName("Strings sem data retornam null")
        @ValueSource(
                strings = {
                    "abc",
                    "xyz",
                    "sem data",
                    "amanhã", // não é suportado
                    "depois de amanhã",
                    "próxima semana",
                    "2026", // só ano — não é suportado
                    "20", // só dia — não é suportado
                    "//",
                    "--",
                    "/"
                })
        void semData(String input) {
            assertThat(DateParserUtils.parse(input)).isNull();
        }
    }

    // =========================================================================
    // Helpers estáticos
    // =========================================================================

    @Nested
    @DisplayName("hoje() e ontem()")
    class HelpersEstaticos {

        @Test
        @DisplayName("hoje() retorna data atual em Brasília")
        void hojeHelper() {
            assertThat(DateParserUtils.hoje()).isEqualTo(LocalDate.now(BRAZIL_ZONE));
        }

        @Test
        @DisplayName("ontem() retorna data de ontem em Brasília")
        void ontemHelper() {
            assertThat(DateParserUtils.ontem()).isEqualTo(LocalDate.now(BRAZIL_ZONE).minusDays(1));
        }

        @Test
        @DisplayName("hoje() - ontem() = 1 dia de diferença")
        void hojeMenosOntemIgualUmDia() {
            long diff = DateParserUtils.hoje().toEpochDay() - DateParserUtils.ontem().toEpochDay();
            assertThat(diff).isEqualTo(1L);
        }
    }

    // =========================================================================
    // tryParseWithPattern (package-private)
    // =========================================================================

    @Nested
    @DisplayName("tryParseWithPattern (package-private)")
    class TryParseWithPattern {

        @ParameterizedTest(name = "[{index}] ''{0}'' → {1}")
        @DisplayName("Formatos aceitos diretamente pelo regex")
        @CsvSource({
            "20/06, 2026-06-20",
            "20-06, 2026-06-20",
            "20/06/2026, 2026-06-20",
            "20-06-2026, 2026-06-20",
            "2026-06-20, 2026-06-20"
        })
        void formatosAceitos(String input, LocalDate esperado) {
            assertThat(DateParserUtils.tryParseWithPattern(input)).isEqualTo(esperado);
        }

        @Test
        @DisplayName("null e vazio retornam null")
        void nullVazio() {
            assertThat(DateParserUtils.tryParseWithPattern(null)).isNull();
            assertThat(DateParserUtils.tryParseWithPattern("")).isNull();
            assertThat(DateParserUtils.tryParseWithPattern("   ")).isNull();
        }

        @Test
        @DisplayName("String sem data retorna null")
        void semData() {
            assertThat(DateParserUtils.tryParseWithPattern("abc")).isNull();
        }

        @Test
        @DisplayName("Data com ruído à esquerda é extraída")
        void comRuidoAEsquerda() {
            assertThat(DateParserUtils.tryParseWithPattern("xyz 20/06 abc"))
                    .isEqualTo(LocalDate.of(2026, Month.JUNE, 20));
        }

        @Test
        @DisplayName("Data inválida dentro de string com ruído retorna null")
        void dataInvalidaComRuido() {
            assertThat(DateParserUtils.tryParseWithPattern("xyz 99/99 abc")).isNull();
        }
    }

    // =========================================================================
    // tryParseFallback (package-private)
    // =========================================================================

    @Nested
    @DisplayName("tryParseFallback (package-private)")
    class TryParseFallback {

        @Test
        @DisplayName("Tenta formatos em ordem e retorna o primeiro que casar")
        void primeiroQueCasar() {
            assertThat(DateParserUtils.tryParseFallback("20/06", "dd/MM", "dd-MM"))
                    .isEqualTo(LocalDate.of(2026, Month.JUNE, 20));

            assertThat(DateParserUtils.tryParseFallback("20-06", "dd/MM", "dd-MM"))
                    .isEqualTo(LocalDate.of(2026, Month.JUNE, 20));
        }

        @Test
        @DisplayName("Nenhum formato casa → null")
        void nenhumCasa() {
            assertThat(DateParserUtils.tryParseFallback("abc", "dd/MM", "dd-MM")).isNull();
        }

        @Test
        @DisplayName("String com espaços é trimada antes do parse")
        void trimAntesDoParse() {
            assertThat(DateParserUtils.tryParseFallback("  20/06  ", "dd/MM"))
                    .isEqualTo(LocalDate.of(2026, Month.JUNE, 20));
        }

        @Test
        @DisplayName("null/empty/blank retornam null")
        void nullVazio() {
            assertThat(DateParserUtils.tryParseFallback(null, "dd/MM")).isNull();
            assertThat(DateParserUtils.tryParseFallback("", "dd/MM")).isNull();
            assertThat(DateParserUtils.tryParseFallback("   ", "dd/MM")).isNull();
        }

        @Test
        @DisplayName("varargs null retorna null")
        void varargsNull() {
            assertThat(DateParserUtils.tryParseFallback("20/06", (String[]) null)).isNull();
        }
    }

    // =========================================================================
    // Consistência entre parse() e helpers
    // =========================================================================

    @Nested
    @DisplayName("Consistência interna")
    class Consistencia {

        @Test
        @DisplayName("parse('hoje') == hoje()")
        void parseHojeIgualHojeHelper() {
            assertThat(DateParserUtils.parse("hoje")).isEqualTo(DateParserUtils.hoje());
        }

        @Test
        @DisplayName("parse('ontem') == ontem()")
        void parseOntemIgualOntemHelper() {
            assertThat(DateParserUtils.parse("ontem")).isEqualTo(DateParserUtils.ontem());
        }

        @Test
        @DisplayName("parse('2026-06-20') == parse('20/06/2026')")
        void formatosEquivalentes() {
            LocalDate a = DateParserUtils.parse("2026-06-20");
            LocalDate b = DateParserUtils.parse("20/06/2026");
            LocalDate c = DateParserUtils.parse("20-06-2026");
            assertThat(a).isEqualTo(b).isEqualTo(c);
        }

        @Test
        @DisplayName("DEFAULT_YEAR é 2026")
        void defaultYear() {
            assertThat(DateParserUtils.DEFAULT_YEAR).isEqualTo(2026);
        }
    }

    // =========================================================================
    // Streams de teste (para clareza)
    // =========================================================================

    /** Provider auxiliar para testes que precisam de pares input/esperado. */
    static Stream<Arguments> formatosAceitosProvider() {
        return Stream.of(
                Arguments.of("20/06", LocalDate.of(2026, Month.JUNE, 20)),
                Arguments.of("20-06", LocalDate.of(2026, Month.JUNE, 20)),
                Arguments.of("20/06/2026", LocalDate.of(2026, Month.JUNE, 20)),
                Arguments.of("20-06-2026", LocalDate.of(2026, Month.JUNE, 20)),
                Arguments.of("2026-06-20", LocalDate.of(2026, Month.JUNE, 20)));
    }
}
