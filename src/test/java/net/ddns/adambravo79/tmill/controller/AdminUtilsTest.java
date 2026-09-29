/* (c) 2026 | 28/09/2026 */
package net.ddns.adambravo79.tmill.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AdminUtilsTest {

    @Mock private ResourceLoader resourceLoader;
    @Mock private Environment environment;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // =========================================================================
    // maskToken
    // =========================================================================

    @Nested
    @DisplayName("maskToken")
    class MaskToken {

        @Test
        @DisplayName("token longo é mascarado (4 primeiros + 4 últimos)")
        void longToken() {
            assertThat(AdminUtils.maskToken("1234567890:ABCdefGHIjklMNOpqrsTUVwxyz"))
                    .isEqualTo("1234...wxyz");
        }

        @Test
        @DisplayName("token com exatamente 8 chars é mascarado")
        void exactEight() {
            assertThat(AdminUtils.maskToken("12345678")).isEqualTo("1234...5678");
        }

        @Test
        @DisplayName("token com 7 chars vira '***'")
        void sevenChars() {
            assertThat(AdminUtils.maskToken("1234567")).isEqualTo("***");
        }

        @Test
        @DisplayName("token null vira '***'")
        void nullToken() {
            assertThat(AdminUtils.maskToken(null)).isEqualTo("***");
        }

        @Test
        @DisplayName("token vazio vira '***'")
        void emptyToken() {
            assertThat(AdminUtils.maskToken("")).isEqualTo("***");
        }
    }

    // =========================================================================
    // parseDateParam
    // =========================================================================

    @Nested
    @DisplayName("parseDateParam")
    class ParseDateParam {

        @Test
        @DisplayName("'hoje' retorna hoje em Brasília")
        void hoje() {
            LocalDate esperado = LocalDate.now(java.time.ZoneId.of(AdminUtils.BRAZIL_ZONE));
            assertThat(AdminUtils.parseDateParam("hoje")).isEqualTo(esperado);
            assertThat(AdminUtils.parseDateParam("de hoje")).isEqualTo(esperado);
            assertThat(AdminUtils.parseDateParam("HOJE")).isEqualTo(esperado);
        }

        @Test
        @DisplayName("'ontem' retorna ontem em Brasília")
        void ontem() {
            LocalDate esperado =
                    LocalDate.now(java.time.ZoneId.of(AdminUtils.BRAZIL_ZONE)).minusDays(1);
            assertThat(AdminUtils.parseDateParam("ontem")).isEqualTo(esperado);
            assertThat(AdminUtils.parseDateParam("de ontem")).isEqualTo(esperado);
        }

        @Test
        @DisplayName("'20/06' retorna 2026-06-20")
        void ddMM() {
            assertThat(AdminUtils.parseDateParam("20/06"))
                    .isEqualTo(LocalDate.of(2026, Month.JUNE, 20));
        }

        @Test
        @DisplayName("'20-06' retorna 2026-06-20")
        void ddMMComHifen() {
            assertThat(AdminUtils.parseDateParam("20-06"))
                    .isEqualTo(LocalDate.of(2026, Month.JUNE, 20));
        }

        @Test
        @DisplayName("'20/06/2026' retorna 2026-06-20")
        void ddMMYYYY() {
            assertThat(AdminUtils.parseDateParam("20/06/2026"))
                    .isEqualTo(LocalDate.of(2026, Month.JUNE, 20));
        }

        @Test
        @DisplayName("'2026-06-20' retorna 2026-06-20")
        void yyyyMMdd() {
            assertThat(AdminUtils.parseDateParam("2026-06-20"))
                    .isEqualTo(LocalDate.of(2026, Month.JUNE, 20));
        }

        @Test
        @DisplayName("string com ruído ('resultados do dia 20/06') extrai a data")
        void comRuido() {
            assertThat(AdminUtils.parseDateParam("resultados do dia 20/06"))
                    .isEqualTo(LocalDate.of(2026, Month.JUNE, 20));
        }

        @Test
        @DisplayName("null retorna null")
        void nullParam() {
            assertThat(AdminUtils.parseDateParam(null)).isNull();
        }

        @Test
        @DisplayName("vazio retorna null")
        void emptyParam() {
            assertThat(AdminUtils.parseDateParam("")).isNull();
            assertThat(AdminUtils.parseDateParam("   ")).isNull();
        }

        @Test
        @DisplayName("data inválida retorna null")
        void invalida() {
            assertThat(AdminUtils.parseDateParam("xyz")).isNull();
            assertThat(AdminUtils.parseDateParam("99/99")).isNull();
        }
    }

    // =========================================================================
    // tryParseWithPattern
    // =========================================================================

    @Nested
    @DisplayName("tryParseWithPattern")
    class TryParseWithPattern {

        @Test
        @DisplayName("null retorna null")
        void nullInput() {
            assertThat(AdminUtils.tryParseWithPattern(null)).isNull();
        }

        @Test
        @DisplayName("string sem data retorna null")
        void semData() {
            assertThat(AdminUtils.tryParseWithPattern("nada de data")).isNull();
        }
    }

    // =========================================================================
    // tryParseFallback
    // =========================================================================

    @Nested
    @DisplayName("tryParseFallback")
    class TryParseFallback {

        @Test
        @DisplayName("aceita formato dd/MM")
        void ddMM() {
            assertThat(AdminUtils.tryParseFallback("20/06", "dd/MM"))
                    .isEqualTo(LocalDate.of(2026, Month.JUNE, 20));
        }

        @Test
        @DisplayName("formato inválido retorna null")
        void invalido() {
            assertThat(AdminUtils.tryParseFallback("abc", "dd/MM")).isNull();
        }

        @Test
        @DisplayName("tenta múltiplos padrões em ordem")
        void multiplos() {
            assertThat(AdminUtils.tryParseFallback("20-06", "dd/MM", "dd-MM"))
                    .isEqualTo(LocalDate.of(2026, Month.JUNE, 20));
        }
    }

    // =========================================================================
    // parseTime
    // =========================================================================

    @Nested
    @DisplayName("parseTime")
    class ParseTime {

        @Test
        @DisplayName("HH:mm válido")
        void valido() {
            assertThat(AdminUtils.parseTime("14:30")).isEqualTo(LocalTime.of(14, 30));
        }

        @Test
        @DisplayName("formato 'H:mm' (1 dígito) NÃO é aceito")
        void umDigitoNaoAceito() {
            assertThat(AdminUtils.parseTime("8:05")).isNull();
        }

        @Test
        @DisplayName("inválido retorna null")
        void invalido() {
            assertThat(AdminUtils.parseTime("25:00")).isNull();
            assertThat(AdminUtils.parseTime("abc")).isNull();
        }

        @Test
        @DisplayName("null e vazio retornam null")
        void nullEVazio() {
            assertThat(AdminUtils.parseTime(null)).isNull();
            assertThat(AdminUtils.parseTime("")).isNull();
            assertThat(AdminUtils.parseTime("   ")).isNull();
        }
    }

    // =========================================================================
    // isValidUrl
    // =========================================================================

    @Nested
    @DisplayName("isValidUrl")
    class IsValidUrl {

        @Test
        @DisplayName("http válido retorna true")
        void http() {
            assertThat(AdminUtils.isValidUrl("http://exemplo.com")).isTrue();
        }

        @Test
        @DisplayName("https válido retorna true")
        void https() {
            assertThat(AdminUtils.isValidUrl("https://exemplo.com/path?a=1")).isTrue();
        }

        @Test
        @DisplayName("esquema não-http retorna false")
        void ftp() {
            assertThat(AdminUtils.isValidUrl("ftp://exemplo.com")).isFalse();
        }

        @Test
        @DisplayName("URL sem host retorna false")
        void semHost() {
            assertThat(AdminUtils.isValidUrl("http:///path")).isFalse();
        }

        @Test
        @DisplayName("string malformada retorna false")
        void malformada() {
            assertThat(AdminUtils.isValidUrl("http://host with spaces")).isFalse();
        }

        @Test
        @DisplayName("null e vazio retornam false")
        void nullEVazio() {
            assertThat(AdminUtils.isValidUrl(null)).isFalse();
            assertThat(AdminUtils.isValidUrl("")).isFalse();
            assertThat(AdminUtils.isValidUrl("   ")).isFalse();
        }
    }

    // =========================================================================
    // loadConfigFile (3 args — deprecated)
    // =========================================================================

    @Nested
    @DisplayName("loadConfigFile (3 args)")
    class LoadConfigFile3Args {

        @Test
        @DisplayName("carrega do classpath quando existe")
        void classpath() throws IOException {
            Resource resource = mock(Resource.class);
            when(resource.exists()).thenReturn(true);
            when(resource.getInputStream())
                    .thenReturn(
                            new ByteArrayInputStream("{\"a\":1}".getBytes(StandardCharsets.UTF_8)));
            when(resourceLoader.getResource("classpath:test.json")).thenReturn(resource);

            @SuppressWarnings("deprecation")
            Object result = AdminUtils.loadConfigFile(resourceLoader, objectMapper, "test.json");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("cai para /app/config/ quando classpath não existe")
        void appConfig() throws IOException {
            Resource classpathRes = mock(Resource.class);
            when(classpathRes.exists()).thenReturn(false);

            Resource appConfigRes = mock(Resource.class);
            when(appConfigRes.exists()).thenReturn(true);
            when(appConfigRes.getInputStream())
                    .thenReturn(
                            new ByteArrayInputStream("{\"b\":2}".getBytes(StandardCharsets.UTF_8)));

            when(resourceLoader.getResource("classpath:test.json")).thenReturn(classpathRes);
            when(resourceLoader.getResource("file:/app/config/test.json")).thenReturn(appConfigRes);

            @SuppressWarnings("deprecation")
            Object result = AdminUtils.loadConfigFile(resourceLoader, objectMapper, "test.json");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("cai para ./config/ quando os dois primeiros não existem")
        void devConfig() throws IOException {
            Resource notFound = mock(Resource.class);
            when(notFound.exists()).thenReturn(false);

            Resource devRes = mock(Resource.class);
            when(devRes.exists()).thenReturn(true);
            when(devRes.getInputStream())
                    .thenReturn(
                            new ByteArrayInputStream("{\"c\":3}".getBytes(StandardCharsets.UTF_8)));

            when(resourceLoader.getResource("classpath:test.json")).thenReturn(notFound);
            when(resourceLoader.getResource("file:/app/config/test.json")).thenReturn(notFound);
            when(resourceLoader.getResource("file:./config/test.json")).thenReturn(devRes);

            @SuppressWarnings("deprecation")
            Object result = AdminUtils.loadConfigFile(resourceLoader, objectMapper, "test.json");

            assertThat(result).isNotNull();
        }

        @SuppressWarnings("deprecation")
        @Test
        @DisplayName("lança IOException quando não encontra em lugar nenhum")
        void notFound() {
            Resource notFound = mock(Resource.class);
            when(notFound.exists()).thenReturn(false);
            when(resourceLoader.getResource(anyString())).thenReturn(notFound);

            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () ->
                                    AdminUtils.loadConfigFile(
                                            resourceLoader, objectMapper, "test.json"))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("não encontrado");
        }
    }

    // =========================================================================
    // getPropertyOrDefault
    // =========================================================================

    @Nested
    @DisplayName("getPropertyOrDefault")
    class GetPropertyOrDefault {

        @Test
        @DisplayName("retorna valor quando presente")
        void presente() {
            when(environment.getProperty("chave")).thenReturn("valor");
            assertThat(AdminUtils.getPropertyOrDefault(environment, "chave", "default"))
                    .isEqualTo("valor");
        }

        @Test
        @DisplayName("retorna default quando ausente")
        void ausente() {
            when(environment.getProperty("chave")).thenReturn(null);
            assertThat(AdminUtils.getPropertyOrDefault(environment, "chave", "default"))
                    .isEqualTo("default");
        }

        @Test
        @DisplayName("retorna default quando em branco")
        void emBranco() {
            when(environment.getProperty("chave")).thenReturn("   ");
            assertThat(AdminUtils.getPropertyOrDefault(environment, "chave", "default"))
                    .isEqualTo("default");
        }
    }

    // =========================================================================
    // loadConfigFile (6 args — versão nova)
    // =========================================================================

    @Nested
    @DisplayName("loadConfigFile (6 args)")
    class LoadConfigFile6Args {

        @Test
        @DisplayName("carrega da propriedade quando o arquivo existe")
        void fromProperty() throws IOException {
            Resource resource = mock(Resource.class);
            when(resource.exists()).thenReturn(true);
            when(resource.getInputStream())
                    .thenReturn(
                            new ByteArrayInputStream("{\"x\":1}".getBytes(StandardCharsets.UTF_8)));

            when(environment.getProperty("minha.property", "classpath:default.json"))
                    .thenReturn("file:./config/test.json");
            when(resourceLoader.getResource("file:./config/test.json")).thenReturn(resource);

            Object result =
                    AdminUtils.loadConfigFile(
                            resourceLoader,
                            environment,
                            objectMapper,
                            "minha.property",
                            "test.json",
                            "classpath:default.json");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("cai para classpath quando a propriedade aponta para arquivo inexistente")
        void fallbackToClasspath() throws IOException {
            Resource notFound = mock(Resource.class);
            when(notFound.exists()).thenReturn(false);

            Resource classpathRes = mock(Resource.class);
            when(classpathRes.exists()).thenReturn(true);
            when(classpathRes.getInputStream())
                    .thenReturn(
                            new ByteArrayInputStream("{\"y\":2}".getBytes(StandardCharsets.UTF_8)));

            when(environment.getProperty("minha.property", "classpath:default.json"))
                    .thenReturn("file:./config/inexistente.json");
            when(resourceLoader.getResource("file:./config/inexistente.json")).thenReturn(notFound);
            when(resourceLoader.getResource("classpath:test.json")).thenReturn(classpathRes);

            Object result =
                    AdminUtils.loadConfigFile(
                            resourceLoader,
                            environment,
                            objectMapper,
                            "minha.property",
                            "test.json",
                            "classpath:default.json");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("lança IOException com mensagem detalhada quando não encontra em nenhum local")
        void notFound() {
            Resource notFound = mock(Resource.class);
            when(notFound.exists()).thenReturn(false);
            when(environment.getProperty("minha.property", "classpath:default.json"))
                    .thenReturn("file:./config/inexistente.json");
            when(resourceLoader.getResource(anyString())).thenReturn(notFound);

            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () ->
                                    AdminUtils.loadConfigFile(
                                            resourceLoader,
                                            environment,
                                            objectMapper,
                                            "minha.property",
                                            "test.json",
                                            "classpath:default.json"))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("não encontrado em nenhum local");
        }
    }
}
