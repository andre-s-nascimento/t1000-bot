/* (c) 2026 | 30/09/2026 */
package net.ddns.adambravo79.tmill.service.config;

/**
 * Contrato genérico para serviços que carregam configuração de uma fonte externa (JSON, arquivo,
 * banco) e mantêm o estado em memória.
 *
 * <p>Implementações típicas:
 *
 * <ul>
 *   <li>{@code PromptRegistryService} — prompts e personas de LLM
 *   <li>{@code BirthdayConfigService} — config de aniversários
 *   <li>{@code WorldCupTeamsService} — traduções e bandeiras de times
 *   <li>{@code WeeklyReminderConfigService} — config do lembrete semanal
 * </ul>
 *
 * <p><b>Contrato de ciclo de vida</b>:
 *
 * <ol>
 *   <li>No boot, a implementação carrega a config da fonte padrão.
 *   <li>{@link #get()} retorna o estado atual em memória (nunca {@code null}).
 *   <li>{@link #reload()} recarrega da fonte, substituindo o estado anterior.
 *   <li>Se a fonte estiver indisponível, {@link #reload()} mantém o estado atual e registra um
 *       warning (não lança exceção).
 * </ol>
 *
 * <p><b>Thread-safety</b>: implementações devem garantir que {@link #get()} e {@link #reload()}
 * possam ser chamados concorrentemente sem corromper o estado. A estratégia recomendada é {@link
 * java.util.concurrent.atomic.AtomicReference} ou {@code volatile}.
 *
 * @param <T> tipo da configuração gerenciada (record, classe imutável, etc.)
 */
public interface ConfigRegistry<T> {

    /**
     * Recarrega a configuração da fonte externa.
     *
     * <p>Implementações devem:
     *
     * <ul>
     *   <li>Substituir o estado atual apenas se o carregamento for bem-sucedido.
     *   <li>Manter o estado anterior em caso de falha (fallback silencioso).
     *   <li>Registrar log {@code INFO} em sucesso e {@code WARN} em falha.
     * </ul>
     */
    void reload();

    /**
     * Retorna o estado atual da configuração em memória.
     *
     * <p><b>Nunca retorna {@code null}</b>. Se a config nunca foi carregada com sucesso, retorna uma
     * instância vazia (não nula) para evitar NPE nos consumidores.
     *
     * @return configuração atual, nunca {@code null}
     */
    T get();
}
