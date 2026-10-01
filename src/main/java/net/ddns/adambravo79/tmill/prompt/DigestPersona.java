/* (c) 2026 | 01/10/2026 */
package net.ddns.adambravo79.tmill.prompt;

/**
 * Representa uma persona dinâmica para geração de resumos/digests.
 * Permite a utilização das personas padrão pré-definidas ou a criação/resolução
 * dinâmica via arquivos de configuração/PromptRegistry.
 */
public record DigestPersona(String id) {

    // Personas Padrão (Manutenção de compatibilidade com constantes existentes)
    public static final DigestPersona T1000 = new DigestPersona("T1000");
    public static final DigestPersona BICENTENNIAL = new DigestPersona("BICENTENNIAL");
    public static final DigestPersona MATRIX_ARCHITECT = new DigestPersona("MATRIX_ARCHITECT");
    public static final DigestPersona ANALISTA = new DigestPersona("ANALISTA");

    /**
     * Compact Constructor: valida e sanitiza o ID.
     */
    public DigestPersona {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("O ID da persona não pode ser nulo ou vazio");
        }
        id = id.trim().toUpperCase();
    }

    /**
     * Mantém o método getId() para retrocompatibilidade com chamadas legadas (além do id() gerado pelo record).
     */
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
    public String toString() {
        return id;
    }
}
