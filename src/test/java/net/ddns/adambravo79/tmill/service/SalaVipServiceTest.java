package net.ddns.adambravo79.tmill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import net.ddns.adambravo79.tmill.model.SalaVipMovie;
import net.ddns.adambravo79.tmill.repository.SalaVipRepository;
import net.ddns.adambravo79.tmill.service.config.SalaVipJsonLoader;

@ExtendWith(MockitoExtension.class)
class SalaVipServiceTest {

    @Mock private SalaVipRepository repository;
    @Mock private SalaVipJsonLoader jsonLoader;

    @InjectMocks private SalaVipService salaVipService;

    // ------------------------------------------------------------------
    // sincronizarLista() — delega ao loader
    // ------------------------------------------------------------------

    @Test
    void sincronizarListaDelegaAoLoader() {
        when(jsonLoader.carregar()).thenReturn(48);

        int total = salaVipService.sincronizarLista();

        assertThat(total).isEqualTo(48);
        verify(jsonLoader).carregar();
        verifyNoInteractions(repository);
    }

    @Test
    void sincronizarListaPropagaZeroQuandoNadaMudou() {
        when(jsonLoader.carregar()).thenReturn(0);

        assertThat(salaVipService.sincronizarLista()).isZero();
    }

    // ------------------------------------------------------------------
    // buscarPorNumero / buscarPorTitulo / buscarPorEpisodio — delegam
    // ------------------------------------------------------------------

    @Test
    void deveBuscarPorNumero() {
        SalaVipMovie movie = movie(15, 15, "mad-max", "Mad Max", 1979);
        when(repository.findMovieByNumberInLatestSnapshot(15)).thenReturn(Optional.of(movie));

        Optional<SalaVipMovie> result = salaVipService.buscarPorNumero(15);

        assertThat(result).isPresent().get().extracting(SalaVipMovie::title).isEqualTo("Mad Max");
        verify(repository).findMovieByNumberInLatestSnapshot(15);
    }

    @Test
    void deveBuscarPorTitulo() {
        SalaVipMovie movie =
                movie(1, 1, "killers-of-the-flower-moon", "Killers of the Flower Moon", 2023);
        when(repository.findMoviesByTitleInLatestSnapshot("killers")).thenReturn(List.of(movie));

        List<SalaVipMovie> result = salaVipService.buscarPorTitulo("killers");

        assertThat(result)
                .hasSize(1)
                .first()
                .extracting(SalaVipMovie::title)
                .isEqualTo("Killers of the Flower Moon");
    }

    @Test
    void deveBuscarPorEpisodioRetornandoDoisFilmes() {
        SalaVipMovie m1 = movie(15, 15, "mad-max", "Mad Max", 1979);
        SalaVipMovie m2 = movie(16, 15, "mad-max-2", "Mad Max 2", 1981);
        when(repository.findMoviesByEpisodeInLatestSnapshot(15)).thenReturn(List.of(m1, m2));

        List<SalaVipMovie> result = salaVipService.buscarPorEpisodio(15);

        assertThat(result)
                .hasSize(2)
                .extracting(SalaVipMovie::title)
                .containsExactly("Mad Max", "Mad Max 2");
    }

    @Test
    void buscarPorNumero_deveRetornarVazioQuandoNumeroForMenorQueUm() {
        // Testa numero = 0
        Optional<SalaVipMovie> result = salaVipService.buscarPorNumero(0);
        assertThat(result).isEmpty();
        verifyNoInteractions(repository); // Garante que não foi no banco

        // Testa numero = -5
        result = salaVipService.buscarPorNumero(-5);
        assertThat(result).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void buscarPorNumero_deveRetornarVazioQuandoNumeroForNull() {
        Optional<SalaVipMovie> result = salaVipService.buscarPorNumero(null);
        assertThat(result).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void buscarPorNumero_deveChamarRepositoryQuandoNumeroValido() {
        var movie = new SalaVipMovie(1L, 1L, 1, 1, "slug", "Title", 2020, "url", "nota");
        when(repository.findMovieByNumberInLatestSnapshot(1)).thenReturn(Optional.of(movie));

        Optional<SalaVipMovie> result = salaVipService.buscarPorNumero(1);

        assertThat(result).isPresent();
        verify(repository).findMovieByNumberInLatestSnapshot(1);
    }

    // ------------------------------------------------------------------
    // helper
    // ------------------------------------------------------------------

    private SalaVipMovie movie(int numero, int episodio, String slug, String titulo, int ano) {
        return new SalaVipMovie(
                numero,
                episodio,
                slug,
                titulo,
                ano,
                "https://letterboxd.com/film/" + slug + "/",
                null);
    }
}
