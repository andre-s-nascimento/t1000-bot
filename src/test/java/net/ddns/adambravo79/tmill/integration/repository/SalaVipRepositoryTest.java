package net.ddns.adambravo79.tmill.integration.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import net.ddns.adambravo79.tmill.model.SalaVipMovie;
import net.ddns.adambravo79.tmill.model.SalaVipSnapshot;
import net.ddns.adambravo79.tmill.repository.SalaVipRepository;

@JdbcTest
@Import(SalaVipRepository.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers // Adicione esta anotação
class SalaVipRepositoryTest { // REMOVA o "extends BaseIntegrationTest"

    @Container
    @SuppressWarnings(
            "resource") // 🔧 FIX: Evita o aviso de resource leak do JDT para containers estáticos
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:15-alpine")
                    .withDatabaseName("test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SalaVipRepository repository;

    @BeforeEach
    void setUp() {
        // Limpa o banco antes de cada teste
        jdbcTemplate.execute("TRUNCATE TABLE sala_vip_snapshot CASCADE");
    }

    @Test
    void deveSalvarSnapshotComFilmesEGerarId() {
        // Arrange
        SalaVipMovie movie1 =
                new SalaVipMovie(
                        119, // salaVipNumber
                        42, // salaVipEpisode  ← NOVO (escolha um número plausível)
                        "killers-of-the-flower-moon",
                        "Killers of the Flower Moon",
                        2023,
                        "https://letterboxd.com/film/killers-of-the-flower-moon/",
                        "Sala VIP #119");

        SalaVipMovie movie2 =
                new SalaVipMovie(
                        118,
                        41, // ← NOVO
                        "past-lives",
                        "Past Lives",
                        2023,
                        "https://letterboxd.com/film/past-lives/",
                        null);

        SalaVipSnapshot snapshot =
                new SalaVipSnapshot(OffsetDateTime.now(ZoneOffset.UTC), 2, List.of(movie1, movie2));

        // Act
        SalaVipSnapshot saved = repository.saveSnapshot(snapshot);

        // Assert
        assertThat(saved.id()).isNotNull();
        assertThat(saved.itemCount()).isEqualTo(2);

        // Verifica no banco se os filmes foram salvos com o snapshot_id correto
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM sala_vip_movie WHERE snapshot_id = ?",
                        Integer.class,
                        saved.id());
        assertThat(count).isEqualTo(2);
    }

    @Test
    void deveBuscarFilmePorNumeroNoUltimoSnapshot() {
        // Arrange - Cria um snapshot antigo (com o filme 119)
        SalaVipMovie oldMovie =
                new SalaVipMovie(119, 42, "antigo", "Filme Antigo", 2000, "url", null);
        repository.saveSnapshot(
                new SalaVipSnapshot(OffsetDateTime.now().minusDays(7), 1, List.of(oldMovie)));

        SalaVipMovie newMovie = new SalaVipMovie(119, 43, "novo", "Filme Novo", 2024, "url", null);
        repository.saveSnapshot(new SalaVipSnapshot(OffsetDateTime.now(), 1, List.of(newMovie)));

        // Act
        Optional<SalaVipMovie> result = repository.findMovieByNumberInLatestSnapshot(119);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().title())
                .isEqualTo("Filme Novo"); // Deve pegar o do snapshot mais recente
    }

    @Test
    void deveRetornarVazioAoBuscarNumeroInexistente() {
        repository.saveSnapshot(
                new SalaVipSnapshot(
                        OffsetDateTime.now(),
                        1,
                        List.of(new SalaVipMovie(119, 42, "filme", "Filme", 2023, "url", null))));

        Optional<SalaVipMovie> result = repository.findMovieByNumberInLatestSnapshot(999);

        assertThat(result).isEmpty();
    }

    @Test
    void deveBuscarFilmesPorTituloIgnorandoCaseNoUltimoSnapshot() {
        // Arrange
        SalaVipMovie movie =
                new SalaVipMovie(
                        120, 44, "killers", "Killers of the Flower Moon", 2023, "url", null);
        repository.saveSnapshot(new SalaVipSnapshot(OffsetDateTime.now(), 1, List.of(movie)));

        // Act
        List<SalaVipMovie> results = repository.findMoviesByTitleInLatestSnapshot("KILLERS");

        // Assert
        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("Killers of the Flower Moon");
    }

    @Test
    void deveIgnorarSnapshotsAntigosAoBuscarPorTitulo() {
        // Arrange
        SalaVipMovie oldMovie = new SalaVipMovie(1, 10, "old", "Inception", 2010, "url", null);
        repository.saveSnapshot(
                new SalaVipSnapshot(OffsetDateTime.now().minusWeeks(2), 1, List.of(oldMovie)));

        SalaVipMovie newMovie = movie(2, 11, "new", "Duna", 2021, "url", null);
        repository.saveSnapshot(new SalaVipSnapshot(OffsetDateTime.now(), 1, List.of(newMovie)));

        // Act
        List<SalaVipMovie> results = repository.findMoviesByTitleInLatestSnapshot("Inception");

        // Assert
        assertThat(results).isEmpty(); // "Inception" estava no snapshot antigo, não no mais recente
    }

    private static SalaVipMovie movie(
            int number, int episode, String slug, String title, int year, String url, String note) {
        return new SalaVipMovie(number, episode, slug, title, year, url, note);
    }
}
