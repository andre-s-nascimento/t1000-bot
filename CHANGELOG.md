# Changelog

Todas as mudanças notáveis deste projeto serão documentadas neste arquivo.

O formato segue [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/),
e o projeto adere ao [Semantic Versioning](https://semver.org/lang/pt-BR/).

## [2.8.0] — 2026-11-05

### Adicionado

- **Módulo Torrent**: consulta a lançamentos de filmes e séries (torrent) do mês.
  - `t1000 torrent <mês>` — lista completa.
  - `t1000 torrent <DD/MM>` — lançamentos de uma data.
  - `t1000 torrent <título>` — ficha detalhada.
- **JSON externo**: `torrent-lancamentos.json` (mesmo padrão da Sala VIP).
- **Upload via painel admin**: `/admin-web/upload-json?target=torrent`.
- **Reload em runtime**: `POST /admin/reload-torrent`.
- **`TorrentLaunchService`**: carrega o JSON em memória (sem banco), suporta reload.
- **`TorrentHandler`**: herda de `AbstractCommandHandler` (autorização, envio, tratamento de erro).
- **Testes**: `TorrentHandlerTest` cobre os três comandos + autorização + métricas.

### Alterado

- `CommandHandler` (roteador): 1 linha para rotear `t1000 torrent`.
- `AdminWebController`: alvo `"torrent"` no upload e reload.
- `BotMessages.HELP_TEXT`: documentação do novo comando.
- `README.md` e Dev Guide: seções do Torrent.

## [2.7.1] — 2026-11-05

### Refatoração (breaking change interno)

- **Arquitetura de handlers especializados**: extração de toda a lógica de negócio do `CommandHandler` (God Class, ~350 linhas) para handlers especializados.
- **`AbstractCommandHandler`** (novo): superclasse com Template Method (`processarComando`), wrappers seguros de envio (`sendHtml`, `sendPlain`, `sendWithButtons`, `sendPhoto`), tratamento genérico de exceções (`executarComProtecao`) e helpers de extração.
- **`IdeaHandler`** (novo): `t1000 anotar ideia ...`
- **`BirthdayHandler`** (novo): `t1000 registrar aniversario ...` (6 variantes de prefixo).
- **`MovieHandler`** (novo): `t1000 buscar ...`, `t1000 estreias da semana`, `t1000 lancamentos`.
- **`WorldCupHandler`** (novo): `t1000 jogos ...`, `t1000 copa ...`, `t1000 resultados ...`.
- **`HelpCommandHandler`** (refatorado): herda de `AbstractCommandHandler` + implementa `CommandUpdateHandler` (ksilisk).
- **`SalaVipHandler`** (movido + refatorado): saiu de `service/` e foi para `telegram/handler/`, agora herda de `AbstractCommandHandler`.
- **`CommandHandler`** (roteador puro): sem lógica de negócio; faz apenas analytics, salvamento no DB, detecção de spoiler, auto-resposta e roteamento.
- **`DateParserUtils`** (novo): parser de datas centralizado (antes duplicado em `CommandHandler` e `AdminUtils`).

### Corrigido (bugs latentes encontrados durante a refatoração)

- **`WorldCupHandler.handleJogos`** agora valida `data == null` (antes passava `null` ao scheduler, que respondia "nenhum jogo programado" em vez de "data inválida").
- **`CommandHandler.normalizarTexto`** agora colapsa espaços múltiplos (`"t1000   jogos   hoje"` → `"t1000 jogos hoje"`).
- **`SalaVipHandler.REGEX_NUMERO`** agora aceita `número` (com acento) — `n[uú]mer[oó]?`.
- **`SalaVipHandler.FLAGS`** removido `CANON_EQ` (quebrava match de acentos em classes de caracteres).

### Testes

- **`DateParserUtilsTest`** (novo) — ~60 testes
- **`AbstractCommandHandlerTest`** (novo) — ~45 testes
- **`IdeaHandlerTest`** (novo) — ~17 testes
- **`BirthdayHandlerTest`** (novo) — ~22 testes
- **`MovieHandlerTest`** (novo) — ~28 testes
- **`WorldCupHandlerTest`** (novo) — ~22 testes
- **`HelpCommandHandlerTest`** (refatorado) — ~18 testes
- **`SalaVipHandlerTest`** (refatorado, movido de `service/` para `telegram/handler/`) — ~35 testes
- **`CommandHandlerTest`** (refatorado drasticamente — foca só no roteamento) — ~30 testes

### Documentação

- Novo guia: [`docs/dev-guide/arquitetura.md`](docs/dev-guide/arquitetura.md) — handlers especializados, Template Method, como criar novos handlers.
- README atualizado: badges, versão 2.7.1, seção de arquitetura.
- Dev Guide atualizado: índice, estrutura de pastas, convenções de handlers.

### Cobertura

- Global: **~94%** (meta: ≥90%).
- Por classe: **≥70%** (excluindo `config`, `client`, `dto`, `model`, `document`).

---

## [2.6.3] — 2026-10-15

### Adicionado

- **Comando `/help`**: lista todos os comandos e funcionalidades do bot com formatação HTML.
- **Módulo de Moderação (`TelegramModerationService`)**: permite silenciar (mute) e dessilenciar (unmute) usuários em grupos.
- **Comandos no Telegram**: `/mute [minutos]` e `/unmute` (funcionam respondendo à mensagem do alvo).
- **Painel Web**: nova aba "🔨 Moderação" para aplicar as ações visualmente.
- **API REST**: endpoints `POST /admin/mute` e `POST /admin/unmute` para automações.

### Alterado

- Ajuste no `MessageUpdateRuleHandler` para ignorar comandos com barra (`/`) e evitar conflito com o roteador do ksilisk.
- `TelegramFacade` expõe o método genérico `executar(BaseRequest)` para maior flexibilidade.

---

## [2.6.2] — 2026-10-10

### Corrigido

- **Ciclo de vida do token de transcrição**: o token gerado para os botões de transcrição (Bruto/Refinado) não é mais removido do cache imediatamente após o primeiro clique. Agora ele respeita o TTL de 7 dias, permitindo que o usuário clique no segundo botão sem receber "Token inválido ou expirado".

---

## [2.6.1] — 2026-10-05

### Adicionado

- **Múltiplas animações no Auto-Response**: cada regra pode ter uma lista de pares `(texto, animação)`; sorteio aleatório do par inteiro a cada disparo.
- **Precedência**: `responses` > `animations` > `animation` (retrocompatibilidade total).
- **Fallback**: `"Olá!"` quando o par não tem texto e a regra não tem `response`.
- **`AdminController.debugAutoResponse`**: exibe o par sorteado no JSON (campo `sorteio`).

### Alterado

- Refatoração de `getResponseRule` para reduzir complexidade cognitiva (Sonar `S3776`).
- Extração de `resolverPares` e `escolherPar` para testes determinísticos (sem mockar `ThreadLocalRandom`).

### Testes

- 13 novos testes unitários cobrindo todos os caminhos do sorteio.

---

## [2.6.0] — 2026-09-28

### Adicionado

- **Onda 3: Feature Flags & Documentação**
  - 23 feature flags runtime com persistência em disco (`./config/feature-flags.json`).
  - UI de toggle no painel admin.
  - Catálogo completo em `docs/dev-guide/feature-flags.md`.

---

## [2.5.5] — 2026-09-15

### Adicionado

- **Onda 2: Estabilidade & Testes**
  - Testes de integração com Testcontainers (PostgreSQL, MongoDB, Kafka).
  - Cobertura mínima de 90% global, 70% por classe.
  - SonarCloud integrado ao CI.

---

## [2.1.0] — 2026-06-01

### Adicionado

- **Migração arquitetural**:
  - SQLite → PostgreSQL (dados estruturados) + MongoDB (documentos).
  - Processamento de áudio em workers Kafka (escalável horizontalmente).
  - Virtual Threads habilitados (`spring.threads.virtual.enabled=true`).
  - Spring Boot 4.1.2 + Spring Security 7.1.1 + Java 21.
  - Spotless + JaCoCo + SonarCloud no CI.

---

## [1.3.1] — 2026-03-15

### Adicionado

- Primeira versão estável com:
  - Busca de filmes (TMDB).
  - Transcrição de áudio (Groq Whisper).
  - Digest diário (Groq Llama).
  - Podcast semanal (Azure TTS).
  - Lembretes de aniversário.
  - Copa do Mundo 2026.
  - Lançamentos de streaming (TMDB + Watchmode).
  - Auto-respostas.
  - Easter eggs.

---

## [1.0.0] — 2026-01-20

### Adicionado

- Versão inicial do T1000-Bot:
  - Busca de filmes.
  - Transcrição de áudio.
  - Comandos básicos.
