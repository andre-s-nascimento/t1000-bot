# Arquivos de Configuração

Documentação dos arquivos JSON externos usados pelo T-Mill Bot. Cada arquivo é carregado por
um `ConfigService` dedicado, com suporte a **reload em runtime** e **fallback seguro**.

> **Última atualização:** v2.7.1 (2026-11-05)
>
> **Nota:** a v2.7.1 **não alterou o schema de nenhum arquivo de configuração**. A refatoração
> foi arquitetural (handlers de comando), sem mudanças nos JSONs.

## Sumário

- [Visão geral](#visão-geral)
- [Localização e precedência](#localização-e-precedência)
- [Ciclo de vida](#ciclo-de-vida)
- [Catálogo de arquivos](#catálogo-de-arquivos)
- [Detalhamento por arquivo](#detalhamento-por-arquivo)
  - [audio-config.json](#audio-configjson)
  - [groq-config.json](#groq-configjson)
  - [refinement-config.json](#refinement-configjson)
  - [admin-config.json](#admin-configjson)
  - [worldcup-teams.json](#worldcup-teamsjson)
  - [tmdb-aliases.json](#tmdb-aliasesjson)
  - [feature-flags.json](#feature-flagsjson)
  - [prompts/](#prompts)
- [Como adicionar um novo arquivo de configuração](#como-adicionar-um-novo-arquivo-de-configuração)
- [Reload em runtime](#reload-em-runtime)
- [Troubleshooting](#troubleshooting)

<!-- O restante do documento permanece IDÊNTICO ao atual. -->

## 🆕 Utilitários Relacionados (v2.7.1)

A v2.7.1 introduziu o **`DateParserUtils`** (`src/main/java/.../util/DateParserUtils.java`), que
**não é um arquivo de configuração**, mas é um utilitário compartilhado para parse de datas.

### Por que existe

Antes da v2.7.1, a lógica de parse de data estava duplicada em:

- `CommandHandler.parseDateParam` (comandos da Copa)
- `AdminUtils.parseDateParam` (painel admin)

O `DateParserUtils` unifica os dois e serve de base para futuros comandos (ex.: `TorrentHandler`).

### Formatos aceitos

- **Relativos:** `hoje`, `ontem`, `de hoje`, `de ontem`
- **Curto:** `20/06`, `20-06`, `5/10`, `1/1`
- **Com ano:** `20/06/2026`, `20-06-2026`, `2026-06-20`
- **Com ruído:** `"resultados do dia 20/06"` → 2026-06-20

### Métodos públicos

| Método          | Descrição                                       |
| --------------- | ----------------------------------------------- |
| `parse(String)` | Parse tolerante. Retorna `LocalDate` ou `null`. |
| `hoje()`        | Data atual no fuso de Brasília.                 |
| `ontem()`       | Data de ontem no fuso de Brasília.              |
| `DEFAULT_YEAR`  | Ano padrão para datas sem ano (2026).           |

### Nota sobre `ResolverStyle.STRICT`

O `DateParserUtils` usa `ResolverStyle.STRICT` em todos os `DateTimeFormatter`. Isso significa
que datas inválidas (ex.: `31/02`, `29/02` em ano não bissexto) retornam `null` em vez de
serem "corrigidas" para o último dia do mês.

### Testes

Cobertura completa em `src/test/java/.../util/DateParserUtilsTest.java` (~60 testes).

## `reminders.json`

**Localização padrão:** `classpath:reminders.json` (dev/testes), `file:/app/config/reminders.json` (prod)
**Property:** `reminders.data.file`
**Loader:** `ReminderService` (via `JsonConfigLoader`)
**Model:** `ReminderJson` → `List<Reminder>`

**Estrutura:**

```json
{
  "version": 1,
  "updatedAt": "2026-11-06T10:00:00Z",
  "reminders": [
    {
      "id": "slug-kebab-case",
      "name": "Nome de Exibição",
      "enabled": true,
      "cron": "0 0 16 * * WED",
      "timezone": "America/Sao_Paulo",
      "message": "<b>HTML aceito</b>",
      "mediaUrl": "https://exemplo.com/video.mp4",
      "targetChatIds": ["-100123"],
      "tags": ["cinema"]
    }
  ]
}
```
