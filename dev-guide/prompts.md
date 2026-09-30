# 📖 Guia do Desenvolvedor: Gestão Externa de Prompts e Personas

Este documento descreve o funcionamento, a estrutura e a manutenção do sistema de prompts externalizados do T-Mill Bot.

---

## 📌 Visão Geral

Historicamente, os prompts e personas do sistema eram definidos de forma _hardcoded_ em classes Java como `DigestPromptFactory` e `PodcastScriptService`.

A partir das versões mais recentes, todos os prompts de geração de resumos (Digest) e roteiros (Podcast) foram externalizados para arquivos `.json` e `.txt` dentro de `src/main/resources/prompts/`.

---

## 📁 Estrutura de Arquivos em `src/main/resources/prompts/`

| Arquivo                    | Descrição                                                                                           |
| :------------------------- | :-------------------------------------------------------------------------------------------------- |
| `digest-personas.json`     | Contém o prompt de sistema de cada persona (`T1000`, `BICENTENNIAL`, `MATRIX_ARCHITECT`).           |
| `digest-contexts.json`     | Define as instruções contextuais de acordo com o período (`MADRUGADA` e `DIA`).                     |
| `digest-user-template.txt` | Template do prompt de usuário do digest com o placeholder `{messages}`.                             |
| `podcast-system.json`      | Diretrizes de estilo e papel da IA para o podcast Silas Cast.                                       |
| `podcast-config.json`      | Regras configuráveis do podcast, como limites de caracteres e frases de fechamento (`closingLine`). |

---

## 🎛️ Feature Flag e Modos de Operação

A transição entre os prompts internos (_hardcoded_) e os prompts externalizados em JSON é controlada em _runtime_ via Feature Flag.

- **Flag**: `prompts.external.enabled`
- **Default**: `false` (utiliza código Java interno)
- **Ativado (`true`)**: O `PromptRegistryService` carrega e fornece as definições dos arquivos JSON em `src/main/resources/prompts/` ou do diretório de _override_.

A flag pode ser alterada via painel Web Admin (`/admin-web`) na aba **Features** ou via API REST:

```bash
POST /admin/features/prompts.external.enabled?enabled=true

```

---

## 🔄 Recarregamento Hot-Reload (Sem Restart)

Alterações feitas nos arquivos JSON/TXT podem ser aplicadas em tempo de execução sem reiniciar a aplicação.

### 1. Painel Web Admin

Acesse a aba **⚙️ Admin** e clique no botão **🔄 Recarregar Prompts**.

### 2. Endpoint REST

```bash
POST /admin/reload-prompts

```

---

## 🛠️ Como Adicionar ou Alterar uma Persona

1. Abra o arquivo `src/main/resources/prompts/digest-personas.json`.
2. Adicione uma nova chave sob o objeto `"personas"`:

```json
"MINHA_PERSONA": {
  "systemPrompt": "Você é..."
}

```

3. Se necessário, adicione o correspondente na `enum DigestPersona` caso deseje tipagem estática.
4. Execute o reload via endpoint REST ou painel Web.

```

```
