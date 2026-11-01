package net.ddns.adambravo79.tmill.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.model.SalaVipMovie;
import net.ddns.adambravo79.tmill.repository.SalaVipRepository;
import net.ddns.adambravo79.tmill.service.config.SalaVipJsonLoader;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalaVipService {

    private final SalaVipRepository repository;
    private final SalaVipJsonLoader jsonLoader;

    /**
     * Recarrega a lista a partir do JSON versionado.
     * Mantido como ponto de entrada único para o admin ("Forçar Sync")
     * e para compatibilidade com chamadas existentes.
     *
     * @return número de filmes no snapshot recém-criado, ou 0 se nada mudou.
     */
    public int sincronizarLista() {
        return jsonLoader.carregar();
    }

    /**
     * Busca um filme pelo número da Sala VIP (ex: 119) no snapshot mais recente.
     */
    public Optional<SalaVipMovie> buscarPorNumero(Integer numero) {
        if (numero == null || numero < 1) {
            return Optional.empty();
        }
        return repository.findMovieByNumberInLatestSnapshot(numero);
    }

    /**
     * Busca filmes pelo título no snapshot mais recente.
     */
    public List<SalaVipMovie> buscarPorTitulo(String titulo) {
        return repository.findMoviesByTitleInLatestSnapshot(titulo);
    }

    /**
     * Busca filmes pelo episódio do RapaduraCast.
     * Pode retornar 2+ filmes (ex: Mad Max 1 e 2 no episódio 15).
     */
    public List<SalaVipMovie> buscarPorEpisodio(Integer episodio) {
        return repository.findMoviesByEpisodeInLatestSnapshot(episodio);
    }
}
