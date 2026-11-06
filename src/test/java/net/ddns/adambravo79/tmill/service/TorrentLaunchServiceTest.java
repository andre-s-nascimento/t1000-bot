/* (c) 2026 | 05/11/2026 */
package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import lombok.SneakyThrows;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TorrentLaunchServiceTest {

    @Mock private ResourceLoader resourceLoader;
    @Mock private Resource resource;

    private TorrentLaunchService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String JSON_VALIDO =
            """
            {
              "versao": 1,
              "mes_referencia": "novembro",
              "ano": 2026,
              "lancamentos": [
                {
                  "titulo": "Resident Evil",
                  "data_lancamento": "02/11",
                  "tipo": "filme",
                  "diretor": "Zach Cragger",
                  "atores": [],
                  "letterboxd_url": "https://letterboxd.com/film/resident-evil/"
                },
                {
                  "titulo": "A Odisséia",
                  "data_lancamento": "17/11",
                  "tipo": "filme",
                  "diretor": "Christopher Nolan",
                  "atores": ["Matt Damon"],
                  "letterboxd_url": "https://letterboxd.com/film/odyssey/"
                }
              ]
            }
            """;

    @BeforeEach
    void setUp() {
        service = new TorrentLaunchService(resourceLoader, objectMapper);
        ReflectionTestUtils.setField(service, "externalPath", "file:/app/config/test.json");
        ReflectionTestUtils.setField(service, "classpathFallback", "classpath:test.json");
    }

    private void mockJson(String json) throws Exception {
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream())
                .thenReturn(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        service.carregar();
    }

    // =========================================================================
    // CARREGAMENTO
    // =========================================================================

    @Test
    @DisplayName("Carrega JSON válido e popula dados")
    void carregaJsonValido() throws Exception {
        mockJson(JSON_VALIDO);

        assertThat(service.listarTodos()).hasSize(2);
        assertThat(service.getMesReferencia()).isEqualTo("novembro");
        assertThat(service.getAno()).isEqualTo(2026);
    }

    @Test
    @DisplayName("Não quebra se arquivo não existir")
    @SneakyThrows
    void arquivoNaoExiste() {
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(false);

        service.carregar();

        assertThat(service.listarTodos()).isEmpty();
    }

    @Test
    @DisplayName("Não quebra com JSON inválido (mantém estado anterior)")
    void jsonInvalido() throws Exception {
        mockJson(JSON_VALIDO);
        assertThat(service.listarTodos()).hasSize(2);

        // Simula erro de IO
        when(resource.getInputStream()).thenThrow(new RuntimeException("Erro de leitura"));
        service.carregar();

        assertThat(service.listarTodos()).hasSize(2); // Manteve os antigos
    }

    @Test
    @DisplayName("Carrega JSON vazio e limpa a lista")
    void jsonVazio() throws Exception {
        mockJson(JSON_VALIDO);
        assertThat(service.listarTodos()).hasSize(2);

        mockJson(
                "{\"versao\": 1, \"mes_referencia\": \"dezembro\", \"ano\": 2026, \"lancamentos\":"
                        + " []}");

        assertThat(service.listarTodos()).isEmpty();
        assertThat(service.getMesReferencia()).isEqualTo("dezembro");
    }

    // =========================================================================
    // BUSCA POR DATA
    // =========================================================================

    @Test
    @DisplayName("Busca por data válida")
    void buscaPorData() throws Exception {
        mockJson(JSON_VALIDO);

        assertThat(service.buscarPorData("02/11")).hasSize(1);
        assertThat(service.buscarPorData("99/11")).isEmpty();
        assertThat(service.buscarPorData(null)).isEmpty();
        assertThat(service.buscarPorData("  ")).isEmpty();
    }

    // =========================================================================
    // BUSCA POR TÍTULO (com e sem acento)
    // =========================================================================

    @Test
    @DisplayName("Busca por título exato (ignora acento)")
    void buscaPorTituloExato() throws Exception {
        mockJson(JSON_VALIDO);

        // Com acento (igual ao JSON)
        assertThat(service.buscarPrimeiroPorTitulo("A Odisséia")).isPresent();
        // Sem acento
        assertThat(service.buscarPrimeiroPorTitulo("A Odisseia")).isPresent();
        // Tudo minúsculo
        assertThat(service.buscarPrimeiroPorTitulo("a odisseia")).isPresent();
        // Inexistente
        assertThat(service.buscarPrimeiroPorTitulo("A Odisseia 2")).isEmpty();
        // Nulo
        assertThat(service.buscarPrimeiroPorTitulo(null)).isEmpty();
    }

    @Test
    @DisplayName("Busca por título parcial (ignora acento)")
    void buscaPorTituloParcial() throws Exception {
        mockJson(JSON_VALIDO);

        assertThat(service.buscarPorTitulo("odisseia")).hasSize(1);
        assertThat(service.buscarPorTitulo("ODISSÉIA")).hasSize(1);
        assertThat(service.buscarPorTitulo("resid")).hasSize(1);
        assertThat(service.buscarPorTitulo("xyz")).isEmpty();
        assertThat(service.buscarPorTitulo("")).isEmpty();
    }

    // =========================================================================
    // BUSCA POR MÊS
    // =========================================================================

    @Test
    @DisplayName("Busca por mês válido e inválido")
    void buscaPorMes() throws Exception {
        mockJson(JSON_VALIDO);

        assertThat(service.listarPorMes("novembro")).hasSize(2);
        assertThat(service.listarPorMes("NOVEMBRO")).hasSize(2);
        assertThat(service.listarPorMes("dezembro")).isEmpty();
        assertThat(service.listarPorMes(null)).isEmpty();
    }

    @Test
    @DisplayName("Meses disponíveis")
    void mesesDisponiveis() throws Exception {
        mockJson(JSON_VALIDO);
        assertThat(service.getMesesDisponiveis()).containsExactly("novembro");

        // Sem JSON
        service = new TorrentLaunchService(resourceLoader, objectMapper);
        assertThat(service.getMesesDisponiveis()).isEmpty();
    }
}
