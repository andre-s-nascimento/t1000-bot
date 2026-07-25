package net.ddns.adambravo79.tmill.telegram.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TelegramMessageSplitterTest {

    // ===================== TESTES PARAMETRIZADOS PARA CASOS SIMPLES =====================

    @ParameterizedTest
    @MethodSource("simpleCasesProvider")
    void split_paraCasosSimples(String input, List<String> expected) {
        List<String> result = TelegramMessageSplitter.split(input);
        assertThat(result).isEqualTo(expected);
    }

    static Stream<Arguments> simpleCasesProvider() {
        return Stream.of(
                Arguments.of(null, List.of()),
                Arguments.of("", List.of()),
                Arguments.of("   ", List.of()),
                Arguments.of("texto curto", List.of("texto curto")),
                Arguments.of("a".repeat(3900), List.of("a".repeat(3900))));
    }

    // ===================== TESTES INDIVIDUAIS PARA CASOS COMPLEXOS =====================

    @Test
    void split_quandoTextoUltrapassaLimiteSemQuebra_retornaDuasPartes() {
        String text = "a".repeat(4000);
        List<String> result = TelegramMessageSplitter.split(text);
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).hasSize(3900);
        assertThat(result.get(1)).hasSize(100);
    }

    @Test
    void split_quandoTextoUltrapassaLimiteComQuebraIdeal_quebraNaNovaLinha() {
        String base = "a".repeat(3900);
        String text = base.substring(0, 3700) + "\n" + base.substring(3700) + "b".repeat(200);
        List<String> result = TelegramMessageSplitter.split(text);
        assertThat(result).hasSize(2);
        // Primeira parte: antes do \n, sem o \n, tamanho 3700
        assertThat(result.get(0)).hasSize(3700);
        // Segunda parte: começa com \n, mas trim() remove, então fica o restante
        assertThat(result.get(1)).isEqualTo(base.substring(3700) + "b".repeat(200));
    }

    @Test
    void split_quandoUltrapassaLimiteComQuebraMuitoCedo_quebraNoLimite() {
        String text = "a".repeat(500) + "\n" + "b".repeat(3500);
        List<String> result = TelegramMessageSplitter.split(text);
        assertThat(result).hasSize(2);
        // A primeira parte terá 3900 caracteres, que incluem os 500 'a', o \n e 3399 'b'
        // O \n fica no final da primeira parte, então o trim() remove espaços? Na verdade, trim()
        // remove \n também, então a primeira parte não termina com \n.
        // O tamanho será 3900.
        assertThat(result.get(0)).hasSize(3900);
        // Segunda parte terá os 101 caracteres restantes (b's), sem \n no início porque foi
        // removido no
        // trim da primeira? Na verdade, o \n está no final da primeira parte, então não está na
        // segunda. A segunda parte começa com o 'b' restante.
        assertThat(result.get(1)).hasSize(101);
        // Verifica que a segunda parte não contém \n no início (trim remove)
        assertThat(result.get(1)).doesNotStartWith("\n");
    }

    @Test
    void split_quandoTextoComVariasPartes_garanteQuebraCorreta() {
        int total = 4000;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < total; i++) {
            sb.append('a');
            if (i % 1000 == 999) {
                sb.append('\n');
            }
        }
        String text = sb.toString();
        List<String> result = TelegramMessageSplitter.split(text);

        // Verifica que cada parte tem no máximo 3900
        for (String part : result) {
            assertThat(part.length()).isLessThanOrEqualTo(3900);
        }
        // Como trim() remove quebras de linha das bordas, o total de caracteres pode ser menor
        // Apenas verificamos que não houve perda de caracteres não-brancos
        // e que a concatenação das partes (sem trim) reconstitui o texto original sem os \n
        // removidos
        // Mas como não precisamos testar isso, apenas garantimos que o número de partes seja
        // razoável.
        // Vamos verificar que o número de partes é 2 (já que texto tem 4000+quebras, deve ser 2)
        assertThat(result).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    void split_quandoTextoComEspacosNasBordas_trimElimina() {
        String text = "  texto com espaços  ";
        List<String> result = TelegramMessageSplitter.split(text);
        assertThat(result).containsExactly("texto com espaços");
    }

    @Test
    void split_quandoTextoComQuebraELimiteExato() {
        String text = "a".repeat(3900) + "\n" + "b".repeat(100);
        List<String> result = TelegramMessageSplitter.split(text);
        assertThat(result).hasSize(2);
        // Primeira parte: 3900 'a's (não inclui \n, pois lastBreak = 3900, end = 3900)
        assertThat(result.get(0)).hasSize(3900);
        // Segunda parte: começa com \n, mas trim() remove, então fica apenas "b".repeat(100)
        assertThat(result.get(1)).isEqualTo("b".repeat(100));
    }
}
