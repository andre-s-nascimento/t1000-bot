package net.ddns.adambravo79.tmill.service.config;

import static net.ddns.adambravo79.tmill.constant.BotMessages.BRAZIL_ZONE;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.dto.SalaVipJson;
import net.ddns.adambravo79.tmill.model.SalaVipMovie;
import net.ddns.adambravo79.tmill.model.SalaVipSnapshot;
import net.ddns.adambravo79.tmill.repository.SalaVipRepository;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalaVipJsonLoader {
    @Value("${salavip.data.file:file:/app/config/sala-vip.json}")
    private String externalPath;

    @Value("${salavip.data.classpath-fallback:classpath:sala-vip.json}")
    private String classpathFallback;

    static final String LETTERBOXD_FILM_BASE = "https://letterboxd.com/film/";

    private final SalaVipRepository repository;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    /**
     * Lê o JSON, valida, e cria um novo snapshot SE o conteúdo mudou
     * em relação ao último snapshot salvo. Idempotente.
     *
     * @return número de filmes no snapshot recém-criado, ou 0 se nada mudou.
     * @throws IllegalStateException se o JSON estiver ausente, vazio ou inválido.
     */
    public int carregar() {
        SalaVipJson json = lerJson();
        validar(json);

        List<SalaVipMovie> filmes = json.filmes().stream().map(this::paraMovie).toList();

        if (jaTemSnapshotIgual(filmes)) {
            log.info("Sala VIP: JSON sem alterações desde o último snapshot. Nada a fazer.");
            return 0;
        }

        SalaVipSnapshot snapshot =
                new SalaVipSnapshot(
                        OffsetDateTime.now(ZoneId.of(BRAZIL_ZONE)), filmes.size(), filmes);
        SalaVipSnapshot salvo = repository.saveSnapshot(snapshot);

        log.info(
                "Sala VIP: novo snapshot criado (id={}, {} filmes, version={}, updatedAt={})",
                salvo.id(),
                salvo.itemCount(),
                json.version(),
                json.updatedAt());
        return filmes.size();
    }

    private SalaVipMovie paraMovie(SalaVipJson.Item item) {
        return new SalaVipMovie(
                item.numero(),
                item.episodio(),
                item.slug(),
                item.titulo(),
                item.ano(),
                LETTERBOXD_FILM_BASE + item.slug() + "/",
                item.nota());
    }

    private boolean jaTemSnapshotIgual(List<SalaVipMovie> novos) {
        Optional<Long> id = repository.findLatestSnapshotId();
        if (id.isEmpty()) {
            return false;
        }
        List<SalaVipMovie> antigos = repository.findMoviesBySnapshotId(id.get());
        return conteudoIgual(antigos, novos);
    }

    private boolean conteudoIgual(List<SalaVipMovie> a, List<SalaVipMovie> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            SalaVipMovie x = a.get(i);
            SalaVipMovie y = b.get(i);
            if (!Objects.equals(x.salaVipNumber(), y.salaVipNumber())) return false;
            if (!Objects.equals(x.salaVipEpisode(), y.salaVipEpisode())) return false;
            if (!Objects.equals(x.letterboxdSlug(), y.letterboxdSlug())) return false;
            if (!Objects.equals(x.title(), y.title())) return false;
            if (!Objects.equals(x.year(), y.year())) return false;
            if (!Objects.equals(x.noteText(), y.noteText())) return false;
        }
        return true;
    }

    private SalaVipJson lerJson() {
        Resource external = resourceLoader.getResource(externalPath);
        Resource chosen =
                external.exists() ? external : resourceLoader.getResource(classpathFallback);
        try (InputStream is = chosen.getInputStream()) {
            return objectMapper.readValue(is, SalaVipJson.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Não foi possível ler " + chosen + ": " + e.getMessage(), e);
        }
    }

    void validar(SalaVipJson json) {
        if (json == null || json.filmes() == null || json.filmes().isEmpty()) {
            throw new IllegalStateException("JSON de Sala VIP vazio ou inválido");
        }
        Set<Integer> numeros = new HashSet<>();
        Set<String> slugs = new HashSet<>();
        for (SalaVipJson.Item item : json.filmes()) {
            if (item.numero() <= 0) {
                throw new IllegalStateException("numero inválido: " + item.numero());
            }
            if (item.episodio() <= 0) {
                throw new IllegalStateException("episodio inválido para " + item.slug());
            }
            if (item.slug() == null || item.slug().isBlank()) {
                throw new IllegalStateException("slug vazio no numero " + item.numero());
            }
            if (!numeros.add(item.numero())) {
                throw new IllegalStateException("numero duplicado: " + item.numero());
            }
            if (!slugs.add(item.slug())) {
                throw new IllegalStateException("slug duplicado: " + item.slug());
            }
        }
    }
}
