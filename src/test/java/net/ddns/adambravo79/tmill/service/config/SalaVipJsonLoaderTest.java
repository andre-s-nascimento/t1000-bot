package net.ddns.adambravo79.tmill.service.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import net.ddns.adambravo79.tmill.dto.SalaVipJson;
import net.ddns.adambravo79.tmill.model.SalaVipMovie;
import net.ddns.adambravo79.tmill.model.SalaVipSnapshot;
import net.ddns.adambravo79.tmill.repository.SalaVipRepository;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class SalaVipJsonLoaderTest {

    private static final String JSON_RESOURCE = "config/sala-vip.json";

    @Mock SalaVipRepository repository;
    @Mock ResourceLoader resourceLoader;

    SalaVipJsonLoader loader;
    ObjectMapper mapper;
    int expectedItems;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();

        Resource externalFake = mock(Resource.class);
        lenient().when(externalFake.exists()).thenReturn(false); // ✅

        Resource classpathReal = new ClassPathResource(JSON_RESOURCE);

        lenient()
                .when(resourceLoader.getResource("file:/app/config/sala-vip.json"))
                .thenReturn(externalFake); // ✅
        lenient()
                .when(resourceLoader.getResource("classpath:sala-vip.json"))
                .thenReturn(classpathReal); // ✅

        loader = new SalaVipJsonLoader(repository, mapper, resourceLoader);
        ReflectionTestUtils.setField(loader, "externalPath", "file:/app/config/sala-vip.json");
        ReflectionTestUtils.setField(loader, "classpathFallback", "classpath:sala-vip.json");

        expectedItems = lerJsonDoClasspath().filmes().size();
    }

    // ------------------------------------------------------------------
    // carregar() — caminho feliz
    // ------------------------------------------------------------------

    @Test
    void carregarCriaSnapshotQuandoNaoExisteAnterior() {
        when(repository.findLatestSnapshotId()).thenReturn(Optional.empty());
        when(repository.saveSnapshot(any(SalaVipSnapshot.class)))
                .thenAnswer(
                        inv -> {
                            SalaVipSnapshot s = inv.getArgument(0);
                            return new SalaVipSnapshot(
                                    1L, s.capturedAt(), s.itemCount(), s.movies());
                        });

        int total = loader.carregar();

        assertThat(total).isEqualTo(expectedItems);

        ArgumentCaptor<SalaVipSnapshot> captor = ArgumentCaptor.forClass(SalaVipSnapshot.class);
        verify(repository).saveSnapshot(captor.capture());

        SalaVipSnapshot snapshot = captor.getValue();
        assertThat(snapshot.itemCount()).isEqualTo(expectedItems);
        assertThat(snapshot.movies()).hasSize(expectedItems);

        // sanidade do conteúdo
        SalaVipMovie primeiro = snapshot.movies().get(0);
        assertThat(primeiro.salaVipNumber()).isEqualTo(1);
        assertThat(primeiro.salaVipEpisode()).isEqualTo(1);
        assertThat(primeiro.letterboxdSlug()).isEqualTo("killers-of-the-flower-moon");
        assertThat(primeiro.letterboxdUrl())
                .isEqualTo("https://letterboxd.com/film/killers-of-the-flower-moon/");
    }

    @Test
    void carregarNaoCriaSnapshotQuandoConteudoIgual() {
        // "antigos" = mesma lista do JSON, mas com id/snapshotId preenchidos
        List<SalaVipMovie> antigos = snapshotComIds(42L);
        when(repository.findLatestSnapshotId()).thenReturn(Optional.of(42L));
        when(repository.findMoviesBySnapshotId(42L)).thenReturn(antigos);

        int total = loader.carregar();

        assertThat(total).isZero();
        verify(repository, never()).saveSnapshot(any());
    }

    @Test
    void carregarCriaSnapshotQuandoTituloMudou() {
        List<SalaVipMovie> antigos = new ArrayList<>(snapshotComIds(42L));

        // altera o título do primeiro para forçar diferença
        SalaVipMovie p = antigos.get(0);
        antigos.set(
                0,
                new SalaVipMovie(
                        p.id(),
                        p.snapshotId(),
                        p.salaVipNumber(),
                        p.salaVipEpisode(),
                        p.letterboxdSlug(),
                        p.title() + " (editado)",
                        p.year(),
                        p.letterboxdUrl(),
                        p.noteText()));

        when(repository.findLatestSnapshotId()).thenReturn(Optional.of(42L));
        when(repository.findMoviesBySnapshotId(42L)).thenReturn(antigos);
        when(repository.saveSnapshot(any(SalaVipSnapshot.class)))
                .thenAnswer(
                        inv -> {
                            SalaVipSnapshot s = inv.getArgument(0);
                            return new SalaVipSnapshot(
                                    43L, s.capturedAt(), s.itemCount(), s.movies());
                        });

        int total = loader.carregar();

        assertThat(total).isEqualTo(expectedItems);
        verify(repository).saveSnapshot(any());
    }

    @Test
    void carregarCriaSnapshotQuandoQuantidadeDiferente() {
        // "antigos" com 119 filmes (falta um) → deve criar snapshot novo
        List<SalaVipMovie> antigos = new ArrayList<>(snapshotComIds(42L));
        antigos.remove(antigos.size() - 1);

        when(repository.findLatestSnapshotId()).thenReturn(Optional.of(42L));
        when(repository.findMoviesBySnapshotId(42L)).thenReturn(antigos);
        when(repository.saveSnapshot(any(SalaVipSnapshot.class)))
                .thenAnswer(
                        inv -> {
                            SalaVipSnapshot s = inv.getArgument(0);
                            return new SalaVipSnapshot(
                                    43L, s.capturedAt(), s.itemCount(), s.movies());
                        });

        int total = loader.carregar();

        assertThat(total).isEqualTo(expectedItems);
        verify(repository).saveSnapshot(any());
    }

    @Test
    void carregarCriaSnapshotQuandoEpisodioMudou() {
        List<SalaVipMovie> antigos = new ArrayList<>(snapshotComIds(42L));

        SalaVipMovie p = antigos.get(1); // Past Lives, episódio 2 no JSON
        antigos.set(
                1,
                new SalaVipMovie(
                        p.id(),
                        p.snapshotId(),
                        p.salaVipNumber(),
                        999,
                        p.letterboxdSlug(),
                        p.title(),
                        p.year(),
                        p.letterboxdUrl(),
                        p.noteText()));

        when(repository.findLatestSnapshotId()).thenReturn(Optional.of(42L));
        when(repository.findMoviesBySnapshotId(42L)).thenReturn(antigos);
        when(repository.saveSnapshot(any(SalaVipSnapshot.class)))
                .thenAnswer(
                        inv -> {
                            SalaVipSnapshot s = inv.getArgument(0);
                            return new SalaVipSnapshot(
                                    43L, s.capturedAt(), s.itemCount(), s.movies());
                        });

        int total = loader.carregar();

        assertThat(total).isEqualTo(expectedItems);
        verify(repository).saveSnapshot(any());
    }

    // ------------------------------------------------------------------
    // validar() — branches de falha (não precisam do JSON do classpath)
    // ------------------------------------------------------------------

    @Test
    void validarFalhaSeJsonNulo() {
        assertThatThrownBy(() -> loader.validar(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vazio ou inválido");
    }

    @Test
    void validarFalhaSeListaDeFilmesNula() {
        SalaVipJson json = new SalaVipJson(1, null, null, null);
        assertThatThrownBy(() -> loader.validar(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vazio ou inválido");
    }

    @Test
    void validarFalhaSeListaDeFilmesVazia() {
        SalaVipJson json = new SalaVipJson(1, null, null, List.of());
        assertThatThrownBy(() -> loader.validar(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vazio ou inválido");
    }

    @Test
    void validarFalhaSeNumeroInvalido() {
        SalaVipJson json = jsonComUmItem(0, 1, "slug", "Titulo");
        assertThatThrownBy(() -> loader.validar(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("numero inválido");
    }

    @Test
    void validarFalhaSeEpisodioInvalido() {
        SalaVipJson json = jsonComUmItem(1, 0, "slug", "Titulo");
        assertThatThrownBy(() -> loader.validar(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("episodio inválido");
    }

    @Test
    void validarFalhaSeSlugEmBranco() {
        SalaVipJson json = jsonComUmItem(1, 1, "   ", "Titulo");
        assertThatThrownBy(() -> loader.validar(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("slug vazio");
    }

    @Test
    void validarFalhaSeNumeroDuplicado() {
        SalaVipJson.Item item = new SalaVipJson.Item(1, 1, "a", "A", 2020, null);
        SalaVipJson json = new SalaVipJson(1, null, null, List.of(item, item));
        assertThatThrownBy(() -> loader.validar(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("numero duplicado");
    }

    @Test
    void validarFalhaSeSlugDuplicado() {
        SalaVipJson.Item a = new SalaVipJson.Item(1, 1, "mesmo-slug", "A", 2020, null);
        SalaVipJson.Item b = new SalaVipJson.Item(2, 2, "mesmo-slug", "B", 2021, null);
        SalaVipJson json = new SalaVipJson(1, null, null, List.of(a, b));
        assertThatThrownBy(() -> loader.validar(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("slug duplicado");
    }

    @Test
    void carregarUsaArquivoExternoQuandoEleExiste() throws Exception {
        // Escreve um JSON mínimo num temp file e faz o Resource externo apontar pra ele
        Path temp = Files.createTempFile("sala-vip-", ".json");
        Files.writeString(
                temp,
                """
                {
                  "version": 1,
                  "updatedAt": "2026-01-01",
                  "filmes": [
                    {"numero": 1, "episodio": 1, "slug": "externo", "titulo": "Externo", "ano": 2024, "nota": null}
                  ]
                }
                """);

        Resource external = new FileSystemResource(temp); // existe de verdade
        when(resourceLoader.getResource("file:/app/config/sala-vip.json")).thenReturn(external);
        // Não mocka o classpath: se o loader tentar o fallback, o mock padrão devolve null
        // e o teste quebra — o que é o comportamento desejado.

        when(repository.findLatestSnapshotId()).thenReturn(Optional.empty());
        when(repository.saveSnapshot(any()))
                .thenAnswer(
                        inv -> {
                            SalaVipSnapshot s = inv.getArgument(0);
                            return new SalaVipSnapshot(
                                    1L, s.capturedAt(), s.itemCount(), s.movies());
                        });

        int total = loader.carregar();

        assertThat(total).isEqualTo(1);
        ArgumentCaptor<SalaVipSnapshot> captor = ArgumentCaptor.forClass(SalaVipSnapshot.class);
        verify(repository).saveSnapshot(captor.capture());
        assertThat(captor.getValue().movies().get(0).letterboxdSlug()).isEqualTo("externo");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * Lê o JSON real do classpath e devolve a mesma lista de filmes,
     * mas com id/snapshotId preenchidos — simula o resultado de
     * {@code repository.findMoviesBySnapshotId(...)}.
     */
    private List<SalaVipMovie> snapshotComIds(Long snapshotId) {
        SalaVipJson json = lerJsonDoClasspath();
        long nextId = 1L;
        List<SalaVipMovie> filmes = new ArrayList<>(json.filmes().size());
        for (SalaVipJson.Item item : json.filmes()) {
            filmes.add(
                    new SalaVipMovie(
                            nextId++,
                            snapshotId,
                            item.numero(),
                            item.episodio(),
                            item.slug(),
                            item.titulo(),
                            item.ano(),
                            SalaVipJsonLoader.LETTERBOXD_FILM_BASE + item.slug() + "/",
                            item.nota()));
        }
        return filmes;
    }

    private SalaVipJson lerJsonDoClasspath() {
        try (InputStream is = new ClassPathResource(JSON_RESOURCE).getInputStream()) {
            return mapper.readValue(is, SalaVipJson.class);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler " + JSON_RESOURCE, e);
        }
    }

    private SalaVipJson jsonComUmItem(int numero, int episodio, String slug, String titulo) {
        return new SalaVipJson(
                1,
                null,
                null,
                List.of(new SalaVipJson.Item(numero, episodio, slug, titulo, 2020, null)));
    }
}
