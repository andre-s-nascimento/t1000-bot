### 🍿 Sala VIP do RapaduraCast

O módulo da Sala VIP é dividido em três partes principais:

1. **`SalaVipConfigService`**: Lê o `salavip-config.json` (cron, timezone e URL da lista do Letterboxd). Externaliza o agendamento, permitindo alterar a frequência de sincronização sem recompilar o código.
2. **`SalaVipJsonLoader`**: Lê o JSON versionado (`sala-vip.json`), valida os dados (números duplicados, slugs vazios) e compara com o último snapshot salvo no PostgreSQL. Se houver mudanças, cria um novo snapshot.
3. **`SalaVipHandler`** (Telegram): Interpreta o comando do usuário via Regex e chama o `SalaVipService`. As respostas são formatadas em HTML com link para o Letterboxd.

> **🆕 v2.7.1:** o `SalaVipHandler` **mudou de pacote**. Antes ficava em `service/`, agora está em
> `telegram/handler/` e **herda de `AbstractCommandHandler`**. Isso trouxe:
>
> - **Autorização automática** (via `processarComando`).
> - **Wrappers seguros de envio** (`sendHtml`, `sendPlain`).
> - **Tratamento de exceção** centralizado (`executarComProtecao`).
>
> O método público mudou de `handle(long chatId, String rawText)` para `handleCommand(Update,
String)` (via herança). O roteador (`CommandHandler`) chama `salaVipHandler.processarComando(update)`.

#### 🔎 Busca por Título e Sinopse (Nota)

A query do repositório (`findMoviesByTitleInLatestSnapshot`) faz um `LIKE` tanto no campo `title` quanto no `note_text`.
Isso é útil porque a lista do RapaduraCast frequentemente contém o título original em inglês, mas a sinopse pode conter o título em português. (Exemplo: buscar "Sinais" retorna o filme "Signs").

#### 🛠️ Configuração em Produção

- Os arquivos de dados (`sala-vip.json` e `salavip-config.json`) devem estar no diretório mapeado pelo volume Docker (`/app/config/`).
- As propriedades devem estar definidas no `application-prod.properties`:
  ```properties
  salavip.data.file=file:/app/config/sala-vip.json
  t1000.salavip.config.file=file:/app/config/salavip-config.json
  ```

#### 🆕 Regexes do `SalaVipHandler` (v2.7.1)

O handler usa três `Pattern` pré-compilados:

| Pattern          | Aceita                                                                                 |
| ---------------- | -------------------------------------------------------------------------------------- |
| `REGEX_NUMERO`   | `salavip 15`, `sala vip 15`, `numero 15`, `número 15`, `nº 15`, `n° 15`, `n 15`, `#15` |
| `REGEX_TITULO`   | `salavip filme matrix`, `salavip tem filme matrix`                                     |
| `REGEX_EPISODIO` | `salavip episodio 15`, `salavip episódio 15`, `salavip ep 15`, `salavip ep15`          |

**Mudança na v2.7.1:** o `FLAGS` removeu `Pattern.CANON_EQ` (quebrava o match de acentos em
classes de caracteres como `[oó]`). O regex `REGEX_NUMERO` agora aceita `n[uú]mer[oó]?` (com
`u` e `ú`).
