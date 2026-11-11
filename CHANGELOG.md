# Changelog

Todas as mudanças notáveis deste projeto serão documentadas neste arquivo.

O formato segue [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/),
e o projeto adere ao [Semantic Versioning](https://semver.org/lang/pt-BR/).

## [2.12.0] — 2026-11-10

### Added

- **Testes E2E com Playwright**: cobertura completa dos deep links (aliases e legacy) do painel web, garantindo que os atalhos de navegação (`#copa`, `#features`, `#aniversarios`, `#monitoramento`, `#anuncios`, `#mensagens`) continuem funcionando após refatorações.
- **Configuração de projetos Playwright**: `chromium` (desktop) e `mobile-chrome` (emulação mobile) para validar a responsividade do painel.

### Fixed

- **Testes E2E falhando por ausência da aplicação**: os testes agora exigem que o servidor de desenvolvimento esteja rodando (`localhost:9323`) antes da execução. Documentado no fluxo de testes.

### Changed

- **`deep-links.spec.ts`**: ajustado para refletir a estrutura atual do painel após o merge da `develop` (v2.11.1).

### Tests

- **Cobertura E2E**: 12 cenários (6 deep links × 2 projetos) cobrindo navegação e legacy aliases.
- **Status**: ✅ Todos os testes passando em `chromium` e `mobile-chrome`.

## [2.11.1] — 2026-11-08

### Fixed

- **Teste Manual de Anúncios no Painel Web:** adicionado botão "Testar" (avião de papel) na tabela de anúncios e no modal de edição. Agora é possível validar a configuração de um anúncio (incluindo mídia e legenda) sem precisar habilitar o cron ou usar o terminal via `curl`.
- **Validação de `chatId` no teste:** o endpoint `POST /admin-web/reminders/{id}/test` agora exige o `chatId`, evitando envios acidentais para todos os chats de produção.

### Added

- **Endpoint `POST /admin-web/reminders/{id}/test`**: proxy do `ReminderDispatcher.dispatchToChat()` para uso no painel web (Thymeleaf), contornando bloqueios de CSRF/OAuth da API REST no browser.
- **JavaScript `testReminder(id)`:** função no `admin.html` que solicita o Chat ID (com fallback para o showcase) e dispara o teste.
- **`AdminWebControllerTest`**: 3 novos testes cobrindo o sucesso, falha de envio e exceção no endpoint de teste manual.

## [2.11.0] — 2026-11-08

### Removed

- **`WeeklyReminderService`** (legado) — substituído pelo `ReminderScheduler` +
  `ReminderDispatcher`. O lembrete "Quatro Horas de Quarta" agora é um `Reminder`
  configurável via `/admin-web` (aba `📢 Anúncios`) e via API `/admin/reminders`.
  O registro é criado pela migration `V5__seed_weekly_reminder_as_reminder.sql`
  (idempotente, `ON CONFLICT DO NOTHING`).
- Endpoints `POST /admin/test-weekly-reminder` e
  `POST /admin/test-weekly-reminder-showcase`.
- Aba `📅 Lembretes` do painel administrativo.
- Feature flag `weekly.reminder.enabled` (substituída por `reminders.enabled`).
- Property `weekly.reminder.media-file` (agora é o campo `mediaUrl` do
  reminder no banco, com resolução de placeholders `${VAR}` no bootstrap).

### Added

- **`config/reminders.json`** — bootstrap do reminder `quatro-horas-quarta`.
- **`ReminderBootstrapService`** agora resolve placeholders `${VAR:default}` (ex.:
  `${QUATRO_HORAS_QUARTA_FILE:https://...}`) no JSON de bootstrap. Sem isso, o
  JSON versionado gravaria a string literal `${...}` no banco.
- **`WeeklyReminderMigrationTest`** — teste de paridade: garante que o `Reminder`
  `quatro-horas-quarta` tem **byte-a-byte** a mesma mensagem do serviço legado,
  além de cron, timezone e tags.
- Atalho **"Editar Quatro Horas de Quarta"** na aba `📢 Anúncios` do painel.

### Changed

- **`AdminController`** e **`AdminWebController`** não dependem mais de
  `WeeklyReminderService`.
- **`FeatureFlagAdminService`**: `weekly.reminder.enabled` removida do registro
  (total passa de 24 → 23 flags).
- **`application.properties`** e **`application-prod.properties`**: removidas as
  properties `weekly.reminder.enabled` e `weekly.reminder.media-file`.
- **`admin.html`**: aba `📅 Lembretes` removida; o caso de uso (disparo manual
  do lembrete semanal) passa a ser coberto pela aba `📢 Anúncios` (botão de
  testar no modal de edição do reminder).

### Tests

- **`WeeklyReminderMigrationTest`** (novo, ~3 testes): Testcontainers Postgres +
  `@SpringBootTest` isolado (só Postgres, sem Kafka/Mongo). Valida a migration
  `V5` (paridade, idempotência, FK das filhas).

## [2.10.0] — 2026-11-07

### Added

#### Reminders em PostgreSQL (fonte de verdade migrada)

- **Migração de fonte de verdade**: os reminders saem do `config/reminders.json` e passam a viver
  em PostgreSQL (tabelas `reminders`, `reminder_chats`, `reminder_tags`), via Flyway `V4`.
  O JSON vira **apenas** bootstrap / backup / restore / migração entre ambientes.
- **`V4__create_reminders_table.sql`**: schema com `version INTEGER` (optimistic locking), trigger
  `trg_reminders_updated_at` (sobrescreve `updated_at` a cada UPDATE) e índices parciais
  (`enabled = TRUE`, `deleted_at IS NOT NULL`). `deleted_at` existe como reserva para soft delete
  futuro (não usado nesta versão — `deleteById` faz hard delete, com paridade ao JSON).
- **`ReminderRepository`** (novo): `JdbcTemplate` + `RowMapper`, seguindo o padrão de
  `BirthdayRepository`/`ReleaseNotifiedRepository`. Hidratação N+1 controlado (2 queries por linha
  pai) via `hydrate()`. `@Transactional` nos métodos de escrita. `save` força `version = 1`;
  `update(Reminder, int expectedVersion)` faz optimistic locking real (`WHERE id = ? AND version = ?`)
  e lança `OptimisticLockingFailureException` se a versão não bater. `deleteById` remove em cascata
  (FK `ON DELETE CASCADE`).
- **`ReminderBootstrapService`** (novo): `ApplicationRunner` que importa o JSON no boot **se** a
  tabela estiver vazia **e** `reminders.bootstrap.from-json.enabled=true`. Idempotente.
- **`GET /admin/reminders/export`**: gera `ReminderJson` a partir do `getAll()`, com
  `Content-Disposition: attachment`. Útil para backup, versionamento em git e migração entre
  ambientes.
- **`POST /admin/reminders/import?replace=X`**: importa `ReminderJson` com upsert transacional.
  `replace=false` (default) preserva os reminders ausentes; `replace=true` faz restore exato
  (remove os ausentes do lote). Rejeita lista vazia (`400`) para evitar apagar o banco
  acidentalmente.

### Changed

- **`Reminder` (record)**: ganhou o 10º campo `version` (int primitivo, `>= 1`, default 1 no
  construtor de conveniência de 9 args). `withEnabled` e `withVersion` propagam `version`
  explicitamente. `@JsonIgnoreProperties(ignoreUnknown = true)` para tolerar campos futuros no
  bootstrap.
- **`ReminderService`**: reescrito. Deixa de ler `JsonConfigLoader` e `ObjectMapper`; passa a
  depender **apenas** de `ReminderRepository`. Mantém a API pública (`getAll`, `getEnabled`,
  `getById`, `count`, `create`, `update`, `toggle`, `delete`, `reload`, `setReloadListener`).
  `importAll(List, boolean)` é `@Transactional` (lote inteiro ou nada). `reload()` apenas
  re-notifica o scheduler (não relê — o estado vive no banco). Métodos `persistJsonAndNotify`,
  `isJsonPersistable`, `lastPersistedAt` foram **removidos** (não fazem mais sentido).
- **`ReminderController`**: `PUT`/`toggle` mapeiam `IllegalStateException` (conflito de versão) para
  **`409 Conflict`**. Adicionados `GET /export` e `POST /import`. Resto dos endpoints preservados.
- **`application.properties`**:
  - Removida a flag `reminders.storage.type` (não faz mais sentido — só database).
  - Mantidas `reminders.bootstrap.from-json.enabled` (default `false`) e `reminders.data.file`
    (default `classpath:reminders.json`).
  - **Paths de JSON** (`auto.response.file`, `easter-egg.file`, `worldcup.data.file`,
    `worldcup.update.destination`, `salavip.data.file`, `salavip.config.file`) foram trocados de
    `file:/app/config/...` para `classpath:` / `./config/` no base — o `application-prod.properties`
    já sobrescreve tudo para `file:/app/config/...`, então prod não muda. O base fica honesto
    (funciona em dev/test sem depender de fallback silencioso).
  - **Reorganização de feature flags**: divisão por **domínio funcional** (`13.1 Podcast /
lembretes / aniversários`, `13.2 Digest / Copa / lançamentos`, `13.3 Observabilidade / cache /
debug`), sem a divisão por "Ondas".
  - Seção `THYMELEAF` promovida a seção `11` (antes estava dentro de `DEV ONLY`); `DEV ONLY` virou
    `12`; `FEATURE FLAGS` virou `13`. Índice atualizado.
  - `admin.security.disabled` ganhou default explícito `:false` (antes estava sem default —
    poderia falhar em runtime).
- **`application-dev.properties`**: enriquecido com paths locais (Sala VIP), `admin.security.disabled=true`
  e logging cirúrgico (DEBUG em `repository`, `controller`, `ReminderService`,
  `ReminderScheduler`, `ReminderDispatcher`, `ReminderBootstrapService`,
  `JdbcTemplate`, `StatementCreatorUtils`). `app.temp.dir=/tmp/t1000-temp`.
- **`application-prod.properties`**: reorganizado espelhando o base (`9.1` / `9.2` / `9.3`), sem
  divisão por ondas. Comentário expandido em `admin.security.disabled=false` (falha segura).

### Fixed

- **`FeatureFlagAdminService`**: `reminders.enabled` já estava registrada (v2.9.0), sem mudança
  nesta versão.
- **`reminders.enabled` em `ReminderDispatcher`**: mantido o comportamento (pausa global do envio
  quando `false`). O que mudou é **onde** o reminder vive, não **se** ele é enviado.

### Removed

- **`reminders.storage.type`**: flag removida (decisão: só database). Rollback agora é via
  `POST /admin/reminders/import?replace=true` a partir de um export.
- **Modo JSON do `ReminderService`**: métodos `persistJsonAndNotify`, `isJsonPersistable`,
  `lastPersistedAt` e o campo `AtomicReference<List<Reminder>>` foram removidos.

### Tests

- **`ReminderRepositoryTest`** (novo, ~13 testes): Testcontainers Postgres. `@JdbcTest` +
  `@Import(ReminderRepository.class)` + `@AutoConfigureTestDatabase(replace = NONE)`.
  Cobre: `save` (pai + filhas em transação, força `version=1`, ignora chat ID inválido com warn),
  `findById` (hidrata chats/tags, `empty` para inexistente), `findAll` (ordenado por id),
  `findEnabled` (filtra), `update` (incrementa `version`, substitui chats/tags, trigger sobrescreve
  `updated_at`), `update` com versão errada → `OptimisticLockingFailureException`, `deleteById`
  (cascata), `count`/`existsById`.
- **`ReminderServiceTest`** (reescrito, ~30 testes): mock do `ReminderRepository`. Cobre leitura
  (delegação), CRUD, optimistic locking (conflito → `IllegalStateException`), `importAll` (merge,
  `replace`, lista vazia/nula, falha no meio → exceção), listener (no-op, exceção não propaga,
  chamado por mutação), `reload` (só notifica, não chama `findAll`).
- **`ReminderControllerTest`** (10 novos testes, arquivo completo): export (JSON + `Content-Disposition`
  - lista vazia), import (merge, replace, lista vazia → 400, `IllegalArgumentException` → 400,
    `RuntimeException` → 500), `PUT` com conflito → 409, `toggle` com conflito → 409.
- **`ReminderStorageIntegrationTest`** (novo, ~8 testes): Testcontainers + `@SpringBootTest` +
  `@AutoConfigureMockMvc`. Sobe Postgres + contexto completo. Cobre: `create` via controller
  (persiste), `update` com versão correta (incrementa), `update` com versão errada (409 **real**,
  não mock), `export` (JSON do banco), `import?replace=true` (substitui o banco), `reload`
  (scheduler reagenda), `delete` (cascata), ciclo completo
  (`create → update → export → import → delete`).
- **`ReminderTest`** e **`ReminderJsonTest`**: ajustados para o `version` no record (mas excluídos
  do JaCoCo/Sonar por serem `model/**` e `dto/**`).

### Coverage

- Global: **~94–95%** (meta: ≥90%).
- Código novo: **~95%** (meta: ≥80%).

### Notes (migração)

- **Primeira subida em prod:** `reminders.bootstrap.from-json.enabled=true` (default do profile
  `prod`) + tabela vazia → importa `file:/app/config/reminders.json`. Bot sobe com os reminders
  configurados.
- **Subidas subsequentes:** tabela tem registros → bootstrap é inerte. Para forçar reimport:
  `POST /admin/reminders/import?replace=true`.
- **Rollback:** se algo der errado, o operador tem duas opções:
  1. Reverter para v2.9.0 (reminders voltam a ser lidos do JSON — mas perde `version`, export,
     import, 409).
  2. Continuar na v2.10.0 e restaurar o JSON via `POST /admin/reminders/import?replace=true`.
- **Volume Docker:** já deve estar montado (`/app/config/`), então `reminders.json` continua
  acessível. Sem mudança de deploy.

---

## [2.9.0] — 2026-11-06

### Adicionado

#### Módulo de Reminders (Anúncios Configuráveis)

- **Sistema de reminders em runtime**: substitui o `WeeklyReminderService` hardcoded por um registry persistente em JSON (`config/reminders.json`), com CRUD completo via painel admin e API REST.
- **`Reminder` (record)**: modelo imutável com validação de `id` (slug kebab-case), `cron` (6 campos) e `message` (não vazio). Listas (`targetChatIds`, `tags`) são copiadas defensivamente (`List.copyOf`).
- **`ReminderJson` (record)**: envelope do arquivo, com `version`, `updatedAt` e `reminders`. Fábricas: `of(...)`, `withReminders(...)`, `empty()`.
- **`ReminderService`**: registry central. Carrega do disco no boot, mantém estado em `AtomicReference`, persiste com escrita atômica (`.tmp` + `ATOMIC_MOVE`), expõe `setReloadListener(Runnable)` para desacoplar do scheduler.
- **`ReminderScheduler`**: agendador dinâmico baseado em `ThreadPoolTaskScheduler` + `ScheduledFuture` gerenciados manualmente. Reagenda em runtime via `reloadAll()`, cancela individualmente (`cancel(id)`) ou em massa (`cancelAll()`, `@PreDestroy`).
- **`ReminderDispatcher`**: envia para múltiplos chats, isolando falha por chat. Usa `enviarMidia` (se `mediaUrl` presente) ou `enviarMensagemHtml` (só texto). Feature flag `reminders.enabled` permite pausar tudo sem editar cada reminder.
- **`ReminderController`** (REST): 9 endpoints sob `/admin/reminders` — CRUD, `toggle`, `test`, `reload`, `status`. Valida cron com `CronExpression.parse` antes de delegar ao service.
- **UI de Anúncios** (`admin.html`): nova aba `📢 Anúncios` com tabela, modal de criar/editar, toggle, delete, reload do disco e painel de diagnóstico (total vs. agendados).
- **Feature flag `reminders.enabled`**: registrada no `FeatureFlagAdminService`. Default `true`.

#### Migração de Configuração

- **`config/reminders.json`**: novo arquivo de configuração, versionado no repositório, com metadados (`version`, `updatedAt`) e lista de reminders.
- **Property `reminders.data.file`**: configurável (`classpath:` em dev/testes, `file:/app/config/reminders.json` em prod).
- **Properties `application.properties`/`application-prod.properties`**: adicionadas com default explícito (env var `REMINDERS_ENABLED`).

### Alterado

- **`FeatureFlagAdminService.registerKnownFlags()`**: `TOTAL_FLAGS` de 23 → 24; registro de `reminders.enabled` como editável.
- **`admin.html`**: nova aba na navegação; `nav-tabs` agora tem 11 abas (scroll horizontal preservado).
- **`HELP_TEXT`** (opcional): mantido — os reminders não são comandos de bot, são envios agendados.

### Testes

- **`ReminderTest`** (opcional) — ~26 testes: validações do construtor compacto (id, cron, message), imutabilidade, `displayName`, `effectiveTimezone`, `withEnabled`, `equals`/`hashCode`.
- **`ReminderJsonTest`** (opcional) — ~24 testes: normalização (`version`, `reminders`), `of`, `withReminders`, `empty`, `CURRENT_VERSION`, imutabilidade, `equals`/`hashCode`.
- **`ReminderServiceTest`** — ~45 testes: carregamento, CRUD, toggle, delete, persistência atômica, listener, classpath vs. file:, concorrência (2 threads criando IDs diferentes).
- **`ReminderSchedulerTest`** — ~25 testes: `init`, `reloadAll`, cron inválido, timezone inválida, `cancel`, `cancelAll`, `destroy`, `dispatchSafe` (via cron de 1s), listener, `scheduledIds` imutável.
- **`ReminderDispatcherTest`** — ~30 testes: feature flag, reminder inexistente/desabilitado/sem chats, envio com/sem mídia, isolamento por chat, chat inválido, `dispatchToChat` (teste manual), métricas.
- **`ReminderControllerTest`** — ~25 testes MockMvc: 9 endpoints + validações (400/404/409/500) + cron inválido + ID divergente.
- **`FeatureFlagAdminServiceTest`**: atualizado para `TOTAL_FLAGS = 24` e novo teste da flag `reminders.enabled`.

### Cobertura

- **Global: ~95%** (meta: ≥90%).
- **Código novo (Sonar Quality Gate): ~95%** (meta: ≥80%).
- **`Reminder`/`ReminderJson`**: excluídos do JaCoCo e do Sonar (`model/**`, `dto/**`), mas 100% testados por robustez.

### Corrigido

- **Flag `reminders.enabled` ausente no `FeatureFlagAdminService`**: o `ReminderDispatcher` consultava a flag, mas ela não existia → `FeatureFlagService.isEnabled()` retornava `false` (default para flag desconhecida), e todos os reminders eram silenciosamente ignorados. Log: `⚠️ Consulta a flag desconhecida: 'reminders.enabled'. Retornando false.`

### Removido

- Nenhum código do `WeeklyReminderService` foi removido nesta versão (compatibilidade com a UI legada). A migração para o novo sistema é gradual — ver [Notas de Migração](#notas-de-migração).

### Notas de Migração

- **`WeeklyReminderService` legado**: continua funcionando na aba `📅 Lembretes`. O novo módulo (`📢 Anúncios`) é independente. Para migrar:
  1. Criar um reminder em `📢 Anúncios` com o mesmo `cron` e `message`.
  2. Desabilitar a flag `weekly.reminder.enabled` (a UI legada para de disparar).
  3. (Opcional) Remover a UI legada numa versão futura.
- **Volume Docker**: montar `config/reminders.json` em `/app/config/reminders.json` em produção (mesmo padrão de `easter-eggs.json`, `worldcup2026.json`, `torrent-lancamentos.json`).
- **Feature flag**: `reminders.enabled` pode ser desligada em runtime via `/admin/features` ou `/admin-web` (aba 🎛️ Features) — útil em incidentes.

### Segurança

- **Sem `integrity` nas CDNs**: o `admin.html` mantém as CDNs do Bootstrap 5.3 e Icons 1.11.3 sem `integrity`/`crossorigin`. Correção adiada para a Fase 2 (refatoração geral do admin).
- **`maximum-scale=1.0`**: mantido no `<meta viewport>` (Sonar Web S7926). Correção adiada para a Fase 2.

## [2.8.1] — 2026-11-06

### Corrigido (Hotfix)

- **Podcast SilasCast (Scheduler):** Corrigido o bug em que a publicação automática de sexta-feira falhava ao sintetizar o áudio via Azure TTS em background, enviando o roteiro como texto por engano.
  - Aumentado o `readTimeout` do `AzureTtsClient` para 300s (5 minutos) para suportar roteiros longos.
  - Adicionado log de erro detalhado na síntese do TTS para facilitar o diagnóstico.
  - Adicionado retry genérico (`includes = Exception.class`) no `synthesizeSinglePart`.
- **Módulo Torrent (`TorrentLaunchService`):** Substituído o uso de `volatile List` por `AtomicReference<List>` para garantir thread-safety real no acesso ao cache em memória, sanando o aviso `java:S3077` do SonarQube.
- **Sala VIP (`SalaVipScheduler`):** Suprimido aviso do JDT sobre variável não utilizada (`@SuppressWarnings("unused")`), já que o `configService` é injetado apenas para uso via SpEL no `@Scheduled`.

### Adicionado

- **Testes de Cobertura:** Adicionados testes unitários para `TorrentLaunchService`, `TorrentHandler`, e endpoints de reload no `AdminController` / `AdminWebController`, elevando a cobertura de código novo (Quality Gate) para acima de 90%.

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
