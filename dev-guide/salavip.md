### 🍿 Sala VIP do RapaduraCast

O módulo da Sala VIP é dividido em três partes principais:

1. **`SalaVipConfigService`**: Lê o `salavip-config.json` (cron, timezone e URL da lista do Letterboxd). Externaliza o agendamento, permitindo alterar a frequência de sincronização sem recompilar o código.
2. **`SalaVipJsonLoader`**: Lê o JSON versionado (`sala-vip.json`), valida os dados (números duplicados, slugs vazios) e compara com o último snapshot salvo no PostgreSQL. Se houver mudanças, cria um novo snapshot.
3. **`SalaVipHandler`** (Telegram): Interpreta o comando do usuário via Regex e chama o `SalaVipService`. As respostas são formatadas em HTML com link para o Letterboxd.

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
