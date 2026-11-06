/* (c) 2026 | 05/11/2026 */
package net.ddns.adambravo79.tmill.service;

import java.io.InputStream;
import java.text.Normalizer;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ddns.adambravo79.tmill.dto.TorrentJson;
import net.ddns.adambravo79.tmill.model.TorrentLaunch;
import tools.jackson.databind.ObjectMapper;

/**
 * Serviço de lançamentos Torrent, carregados de um JSON externo em memória.
 *
 * <p>Arquitetura similar ao {@code SalaVipJsonLoader}, mas <b>sem banco de dados</b>: o JSON é
 * carregado no boot e mantido em memória (lista imutável). Alterações no arquivo exigem reload via
 * {@link #carregar()} (endpoint admin ou reinício).
 *
 * <p>O carregamento tenta os seguintes locais, em ordem:
 *
 * <ol>
 *   <li>Property {@code torrent.data.file} (ex.: {@code file:/app/config/torrent-lancamentos.json})
 *   <li>Fallback classpath ({@code classpath:torrent-lancamentos.json})
 * </ol>
 *
 * <p>Se o arquivo não existir em nenhum local, o serviço inicia com lista vazia (não quebra o
 * boot).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TorrentLaunchService {

    @Value("${torrent.data.file:file:/app/config/torrent-lancamentos.json}")
    private String externalPath;

    @Value("${torrent.data.classpath-fallback:classpath:torrent-lancamentos.json}")
    private String classpathFallback;

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    /**
     * Lista imutável mantida em memória.
     *
     * <p>Usa {@link AtomicReference} para garantir visibilidade e atomicidade, evitando o aviso
     * {@code java:S3077} do SonarQube.
     */
    private final AtomicReference<List<TorrentLaunch>> lancamentosRef =
            new AtomicReference<>(Collections.emptyList());

    /** Metadados do JSON (mês de referência, ano). */
    private volatile String mesReferencia = "";

    private volatile int ano = 0;

    @PostConstruct
    public void init() {
        carregar();
    }

    /**
     * Carrega o JSON do disco/classpath. Se falhar, mantém a lista atual (não quebra o boot).
     */
    public void carregar() {
        Resource external = resourceLoader.getResource(externalPath);
        Resource chosen =
                external.exists() ? external : resourceLoader.getResource(classpathFallback);

        if (!chosen.exists()) {
            log.warn(
                    "⚠️ Arquivo de lançamentos torrent não encontrado em nenhum local "
                            + "(external='{}', classpath='{}'). Módulo Torrent ficará vazio.",
                    externalPath,
                    classpathFallback);
            return;
        }

        try (InputStream is = chosen.getInputStream()) {
            TorrentJson json = objectMapper.readValue(is, TorrentJson.class);

            if (json == null || json.lancamentos() == null || json.lancamentos().isEmpty()) {
                log.warn("⚠️ JSON de lançamentos torrent vazio ou inválido: {}", chosen);
                this.lancamentosRef.set(Collections.emptyList());
                this.mesReferencia =
                        json != null && json.mesReferencia() != null ? json.mesReferencia() : "";
                this.ano = json != null ? json.ano() : 0;
                return;
            }

            // Cópia imutável (segura para leitura concorrente)
            this.lancamentosRef.set(List.copyOf(json.lancamentos()));
            this.mesReferencia = json.mesReferencia() != null ? json.mesReferencia() : "";
            this.ano = json.ano();

            log.info(
                    "✅ Lançamentos torrent carregados: {} itens (mês={}, ano={})",
                    this.lancamentosRef.get().size(),
                    this.mesReferencia,
                    this.ano);

        } catch (Exception e) {
            log.error("❌ Falha ao carregar lançamentos torrent. Mantendo lista anterior.", e);
        }
    }

    // =========================================================================
    // CONSULTAS
    // =========================================================================

    /** Retorna todos os lançamentos (lista imutável). */
    public List<TorrentLaunch> listarTodos() {
        return lancamentosRef.get();
    }

    /** Retorna o mês de referência do JSON (ex.: "novembro"). */
    public String getMesReferencia() {
        return mesReferencia;
    }

    /** Retorna o ano de referência do JSON (ex.: 2026). */
    public int getAno() {
        return ano;
    }

    /**
     * Busca lançamentos por data específica (formato {@code DD/MM}).
     *
     * @param data string no formato {@code DD/MM} (ex.: {@code "02/11"})
     * @return lista de lançamentos naquela data (pode ser vazia)
     */
    public List<TorrentLaunch> buscarPorData(String data) {
        if (data == null || data.isBlank()) {
            return List.of();
        }
        String busca = data.trim();
        return lancamentosRef.get().stream()
                .filter(l -> busca.equalsIgnoreCase(l.dataLancamento()))
                .toList();
    }

    /**
     * Busca lançamentos por título (case-insensitive, substring).
     *
     * <p>Usa "contains" para permitir buscas parciais (ex.: "Resident" encontra "Resident
     * Evil"). Retorna até 10 resultados para evitar respostas gigantes.
     *
     * @param termo trecho do título (pode ser vazio)
     * @return lista de lançamentos (vazia se não encontrar)
     */
    public List<TorrentLaunch> buscarPorTitulo(String termo) {
        if (termo == null || termo.isBlank()) {
            return List.of();
        }
        String busca = normalizar(termo);
        return lancamentosRef.get().stream()
                .filter(l -> l.titulo() != null && normalizar(l.titulo()).contains(busca))
                .limit(10)
                .toList();
    }

    /**
     * Busca o primeiro lançamento por título exato (case-insensitive).
     *
     * @param titulo título completo (ex.: "Resident Evil")
     * @return {@link Optional} com o lançamento ou vazio
     */
    public Optional<TorrentLaunch> buscarPrimeiroPorTitulo(String titulo) {
        if (titulo == null || titulo.isBlank()) {
            return Optional.empty();
        }
        String busca = normalizar(titulo);
        return lancamentosRef.get().stream()
                .filter(l -> l.titulo() != null && normalizar(l.titulo()).equals(busca))
                .findFirst();
    }

    /**
     * Filtra lançamentos pelo mês de referência.
     *
     * <p>Compara o nome do mês (ex.: {@code "novembro"}) com o {@link #mesReferencia} do JSON.
     *
     * @param mes nome do mês (ex.: {@code "novembro"})
     * @return lista de lançamentos do mês (vazia se o mês não casar com o JSON)
     */
    public List<TorrentLaunch> listarPorMes(String mes) {
        if (mes == null || mes.isBlank()) {
            return List.of();
        }
        // Se o mês informado não bate com o JSON, retorna vazio
        if (!mes.trim().equalsIgnoreCase(mesReferencia)) {
            return List.of();
        }
        return lancamentosRef.get();
    }

    /**
     * Lista os meses disponíveis (útil para mensagem de erro).
     *
     * <p>Como o JSON tem um único mês de referência, retorna um singleton (ou vazio, se não
     * carregado).
     */
    public List<String> getMesesDisponiveis() {
        return mesReferencia.isBlank() ? List.of() : List.of(mesReferencia);
    }

    /**
     * Normaliza uma string para busca: minúsculas + sem acentos.
     */
    private String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
