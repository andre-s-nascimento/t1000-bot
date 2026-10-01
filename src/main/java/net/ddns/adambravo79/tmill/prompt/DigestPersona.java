/* (c) 2026 | 01/10/2026 */
package net.ddns.adambravo79.tmill.prompt;

import java.util.Objects;

/**
 * Representa uma persona dinâmica para geração de resumos/digests.
 * Permite a utilização das personas padrão pré-definidas ou a criação/resolução
 * dinâmica via arquivos de configuração/PromptRegistry.
 */
public final class DigestPersona {

    // Personas Padrão (Manutenção de compatibilidade com constantes existentes)
    public static final DigestPersona T1000 = new DigestPersona("T1000");
    public static final DigestPersona BICENTENNIAL = new DigestPersona("BICENTENNIAL");
    public static final DigestPersona MATRIX_ARCHITECT = new DigestPersona("MATRIX_ARCHITECT");
    public static final DigestPersona ANALISTA = new DigestPersona("ANALISTA");

    private final String id;

    public DigestPersona(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("O ID da persona não pode ser nulo ou vazio");
        }
        this.id = id.trim().toUpperCase();
    }

    public String getId() {
        return id;
    }

    /**
     * Resolve ou cria uma instância de DigestPersona a partir de uma String.
     * Caso a string seja nula ou em branco, retorna a persona fallback (ANALISTA).
     */
    public static DigestPersona fromString(String name) {
        if (name == null || name.isBlank()) {
            return ANALISTA;
        }
        return new DigestPersona(name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DigestPersona that = (DigestPersona) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return id;
    }
}
