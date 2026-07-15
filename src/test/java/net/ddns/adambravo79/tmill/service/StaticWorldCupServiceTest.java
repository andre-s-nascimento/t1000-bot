package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import net.ddns.adambravo79.tmill.model.WorldCupMatch;

@ExtendWith(MockitoExtension.class)
class StaticWorldCupServiceTest {

    @Mock private ResourceLoader resourceLoader;
    @Mock private Resource resource;

    private StaticWorldCupService service;

    // JSON real com dados válidos (trecho do worldcup2026.json)
    private static final String JSON_VALIDO =
            """
      {
        "name": "World Cup 2026",
        "matches": [
          {
            "round": "Matchday 1",
            "date": "2026-06-11",
            "time": "13:00 UTC-6",
            "team1": "Mexico",
            "team2": "South Africa",
            "score": { "ft": [2, 0], "ht": [1, 0] },
            "goals1": [
              { "name": "Julián Quiñones", "minute": "9" },
              { "name": "Raúl Jiménez", "minute": "67" }
            ],
            "goals2": [],
            "group": "Group A",
            "ground": "Mexico City"
          },
          {
            "round": "Round of 32",
            "num": 74,
            "date": "2026-06-29",
            "time": "16:30 UTC-4",
            "team1": "Germany",
            "team2": "Paraguay",
            "score": { "p": [3, 4], "et": [1, 1], "ft": [1, 1], "ht": [0, 1] },
            "goals1": [{ "name": "Kai Havertz", "minute": "54" }],
            "goals2": [{ "name": "Julio Enciso", "minute": "42" }],
            "ground": "Boston (Foxborough)"
          }
        ]
      }
      """;

    private static final String JSON_VAZIO =
            """
      {
        "name": "World Cup 2026",
        "matches": []
      }
      """;

    private static final String JSON_MALFORMADO =
            """
      {
        "name": "World Cup 2026",
        "matches": [
          {
            "round": "Matchday 1",
            "date": "2026-06-11",
            "time": "13:00 UTC-6",
            "team1": "Mexico",
            "team2": "South Africa",
            "score": { "ft": [2, 0] },
            "goals1": [ { "name": "Julián Quiñones" } ],
            "goals2": []
          }
        ]
      """; // falta fechamento

    @BeforeEach
    void setUp() {
        service = new StaticWorldCupService(resourceLoader);
        ReflectionTestUtils.setField(service, "dataFileLocation", "classpath:worldcup2026.json");
    }

    // =========================
    // CARREGAMENTO COM SUCESSO
    // =========================

    @Test
    void deveCarregarDadosComSucesso() throws Exception {
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream())
                .thenReturn(new ByteArrayInputStream(JSON_VALIDO.getBytes(StandardCharsets.UTF_8)));

        service.loadMatches();

        Map<LocalDate, List<WorldCupMatch>> allMatches = service.getAllMatches();
        assertThat(allMatches)
                .containsKeys(LocalDate.parse("2026-06-11"), LocalDate.parse("2026-06-29"));
        assertThat(allMatches.get(LocalDate.parse("2026-06-11"))).hasSize(1);
        assertThat(allMatches.get(LocalDate.parse("2026-06-29"))).hasSize(1);
    }

    @Test
    void deveCarregarDadosComPlacarDeProrrogacaoEPenaltis() throws Exception {
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream())
                .thenReturn(new ByteArrayInputStream(JSON_VALIDO.getBytes(StandardCharsets.UTF_8)));

        service.loadMatches();

        List<WorldCupMatch> matches = service.getMatchesForDay(LocalDate.parse("2026-06-29"));
        assertThat(matches).isNotEmpty();
        WorldCupMatch match = matches.get(0);
        assertThat(match.score()).isNotNull();
        assertThat(match.score().ft()).containsExactly(1, 1);
        assertThat(match.score().et()).containsExactly(1, 1);
        assertThat(match.score().p()).containsExactly(3, 4);
    }

    // =========================
    // ARQUIVO INEXISTENTE
    // =========================

    @Test
    void deveIgnorarArquivoInexistente() throws Exception {
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(false);

        service.loadMatches();

        assertThat(service.getAllMatches()).isEmpty();
    }

    // =========================
    // JSON MALFORMADO
    // =========================

    @Test
    void deveTratarJsonMalformado() throws Exception {
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream())
                .thenReturn(
                        new ByteArrayInputStream(JSON_MALFORMADO.getBytes(StandardCharsets.UTF_8)));

        // O método captura a exceção e loga, não propaga
        service.loadMatches();

        // O mapa deve permanecer vazio
        assertThat(service.getAllMatches()).isEmpty();
    }

    // =========================
    // LISTA DE JOGOS VAZIA
    // =========================

    @Test
    void deveCarregarListaVaziaDeJogos() throws Exception {
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream())
                .thenReturn(new ByteArrayInputStream(JSON_VAZIO.getBytes(StandardCharsets.UTF_8)));

        service.loadMatches();

        assertThat(service.getAllMatches()).isEmpty();
        assertThat(service.hasMatches(LocalDate.now())).isFalse();
    }

    // =========================
    // ERRO DE LEITURA (IOException)
    // =========================

    @Test
    void deveTratarErroAoLerArquivo() throws Exception {
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenThrow(new IOException("Erro de leitura"));

        service.loadMatches();

        assertThat(service.getAllMatches()).isEmpty();
    }

    // =========================
    // CONSULTAS
    // =========================

    @Test
    void deveRetornarMatchesParaDataComJogos() throws Exception {
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream())
                .thenReturn(new ByteArrayInputStream(JSON_VALIDO.getBytes(StandardCharsets.UTF_8)));

        service.loadMatches();

        List<WorldCupMatch> matches = service.getMatchesForDay(LocalDate.parse("2026-06-11"));
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).homeTeam()).isEqualTo("Mexico");
    }

    @Test
    void deveRetornarListaVaziaParaDataSemJogos() throws Exception {
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream())
                .thenReturn(new ByteArrayInputStream(JSON_VALIDO.getBytes(StandardCharsets.UTF_8)));

        service.loadMatches();

        List<WorldCupMatch> matches = service.getMatchesForDay(LocalDate.parse("2026-06-12"));
        assertThat(matches).isEmpty();
    }

    @Test
    void getFirstMatchOfDay_deveRetornarPrimeiroJogo() throws Exception {
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream())
                .thenReturn(new ByteArrayInputStream(JSON_VALIDO.getBytes(StandardCharsets.UTF_8)));

        service.loadMatches();

        Optional<WorldCupMatch> match = service.getFirstMatchOfDay(LocalDate.parse("2026-06-11"));
        assertThat(match).isPresent();
        assertThat(match.get().homeTeam()).isEqualTo("Mexico");
        assertThat(match.get().awayTeam()).isEqualTo("South Africa");
    }

    @Test
    void getFirstMatchOfDay_deveRetornarEmptyParaDataSemJogos() throws Exception {
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream())
                .thenReturn(new ByteArrayInputStream(JSON_VALIDO.getBytes(StandardCharsets.UTF_8)));

        service.loadMatches();

        Optional<WorldCupMatch> match = service.getFirstMatchOfDay(LocalDate.parse("2026-06-12"));
        assertThat(match).isEmpty();
    }

    @Test
    void hasMatches_deveRetornarTrueParaDataComJogos() throws Exception {
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream())
                .thenReturn(new ByteArrayInputStream(JSON_VALIDO.getBytes(StandardCharsets.UTF_8)));

        service.loadMatches();

        assertThat(service.hasMatches(LocalDate.parse("2026-06-11"))).isTrue();
    }

    @Test
    void hasMatches_deveRetornarFalseParaDataSemJogos() throws Exception {
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream())
                .thenReturn(new ByteArrayInputStream(JSON_VALIDO.getBytes(StandardCharsets.UTF_8)));

        service.loadMatches();

        assertThat(service.hasMatches(LocalDate.parse("2026-06-12"))).isFalse();
    }

    @Test
    void getAllMatches_deveRetornarMapaCompleto() throws Exception {
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream())
                .thenReturn(new ByteArrayInputStream(JSON_VALIDO.getBytes(StandardCharsets.UTF_8)));

        service.loadMatches();

        Map<LocalDate, List<WorldCupMatch>> allMatches = service.getAllMatches();
        assertThat(allMatches)
                .containsKeys(LocalDate.parse("2026-06-11"), LocalDate.parse("2026-06-29"));
        assertThat(allMatches.get(LocalDate.parse("2026-06-11"))).hasSize(1);
    }

    // =========================
    // RECARREGAMENTO
    // =========================

    @Test
    void reload_deveChamarLoadMatches() {
        // Cria um spy para verificar se loadMatches foi chamado
        StaticWorldCupService spyService = spy(service);
        doNothing().when(spyService).loadMatches();

        spyService.reload();

        verify(spyService).loadMatches();
    }

    @Test
    void reload_deveRecarregarDados() throws Exception {
        // Carrega dados iniciais
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream())
                .thenReturn(new ByteArrayInputStream(JSON_VALIDO.getBytes(StandardCharsets.UTF_8)));

        service.loadMatches();
        assertThat(service.getAllMatches()).isNotEmpty();

        // Novo JSON com dados diferentes
        String novoJson =
                """
        {
          "name": "World Cup 2026",
          "matches": [
            {
              "round": "New Round",
              "date": "2026-07-20",
              "time": "22:00 UTC",
              "team1": "Team X",
              "team2": "Team Y",
              "score": { "ft": [0, 0] },
              "goals1": [],
              "goals2": [],
              "group": "Z",
              "ground": "Stadium Z"
            }
          ]
        }
        """;
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream())
                .thenReturn(new ByteArrayInputStream(novoJson.getBytes(StandardCharsets.UTF_8)));

        service.reload();

        Map<LocalDate, List<WorldCupMatch>> allMatches = service.getAllMatches();
        assertThat(allMatches).hasSize(1);
        assertThat(allMatches).containsKey(LocalDate.parse("2026-07-20"));
        assertThat(allMatches.get(LocalDate.parse("2026-07-20")).get(0).homeTeam())
                .isEqualTo("Team X");
    }
}
