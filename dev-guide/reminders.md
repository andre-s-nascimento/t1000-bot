# 📢 Reminders (Anúncios Configuráveis)

Sistema genérico de envios agendados. **Substitui o `WeeklyReminderService` hardcoded** e move a
fonte de verdade de `config/reminders.json` para o **PostgreSQL**.

## Visão Geral

Cada **reminder** é um envio agendado de uma mensagem (opcionalmente com mídia) para uma lista de
chats, disparado por uma expressão cron.

| Aspecto                | Detalhe                                                                            |
| ---------------------- | ---------------------------------------------------------------------------------- |
| **Fonte de verdade**   | PostgreSQL — tabelas `reminders`, `reminder_chats`, `reminder_tags`                |
| **Bootstrap**          | `config/reminders.json` **apenas** na primeira subida (tabela vazia + flag ligada) |
| **Agendador**          | `ThreadPoolTaskScheduler` dinâmico (não usa `@Scheduled`)                          |
| **Optimistic locking** | Coluna `version INTEGER` + trigger `updated_at`                                    |
| **Feature flag**       | `reminders.enabled` (pausa global do envio)                                        |
| **Rollback**           | `POST /admin/reminders/import?replace=true` restaura o estado a partir de um JSON  |

## Fluxo (resumo)

```
Admin → UI / API → ReminderController → ReminderService → ReminderRepository → PostgreSQL
                                │
                                │ (após mutação: notifyReload)
                                ▼
                       ReminderScheduler (reagenda)
                                │
                                │ (a cada cron)
                                ▼
                       ReminderDispatcher → TelegramFacade → Telegram
```

## Schema (V4 migration)

```sql
-- V4__create_reminders_table.sql
CREATE TABLE reminders (
    id          VARCHAR(100) PRIMARY KEY,
    name        VARCHAR(255),
    enabled     BOOLEAN       NOT NULL DEFAULT TRUE,
    cron        VARCHAR(100)  NOT NULL,
    timezone    VARCHAR(100),
    message     TEXT          NOT NULL,
    media_url   VARCHAR(2048),
    version     INTEGER       NOT NULL DEFAULT 1,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ   NULL
);

CREATE TABLE reminder_chats (
    reminder_id VARCHAR(100) NOT NULL REFERENCES reminders(id) ON DELETE CASCADE,
    chat_id     BIGINT       NOT NULL,
    PRIMARY KEY (reminder_id, chat_id)
);

CREATE TABLE reminder_tags (
    reminder_id VARCHAR(100) NOT NULL REFERENCES reminders(id) ON DELETE CASCADE,
    tag         VARCHAR(100) NOT NULL,
    PRIMARY KEY (reminder_id, tag)
);

-- Trigger: updated_at = NOW() a cada UPDATE
CREATE TRIGGER trg_reminders_updated_at
    BEFORE UPDATE ON reminders
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
```

## API REST

| Método   | Endpoint                                 | Descrição                                  |
| -------- | ---------------------------------------- | ------------------------------------------ |
| `GET`    | `/admin/reminders`                       | Lista todos                                |
| `GET`    | `/admin/reminders/{id}`                  | Busca por ID                               |
| `POST`   | `/admin/reminders`                       | Cria novo (body = `Reminder`)              |
| `PUT`    | `/admin/reminders/{id}`                  | Atualiza (`version` do body deve bater)    |
| `POST`   | `/admin/reminders/{id}/toggle?enabled=X` | Alterna `enabled`                          |
| `DELETE` | `/admin/reminders/{id}`                  | Remove (hard delete, filhas em cascata)    |
| `POST`   | `/admin/reminders/{id}/test?chatId=X`    | Envia manualmente para um chat             |
| `POST`   | `/admin/reminders/reload`                | Re-notifica o scheduler (não relê)         |
| `GET`    | `/admin/reminders/status`                | Diagnóstico (total, enabled, scheduled)    |
| `GET`    | `/admin/reminders/export`                | Exporta como `ReminderJson` (backup)       |
| `POST`   | `/admin/reminders/import?replace=X`      | Importa `ReminderJson` (bootstrap/restore) |

### Códigos HTTP

| Código | Quando                                                             |
| ------ | ------------------------------------------------------------------ |
| `200`  | GET, PUT, PATCH, POST test/reload/import                           |
| `201`  | POST criação                                                       |
| `204`  | DELETE                                                             |
| `400`  | Body inválido, cron inválido, ID divergente, lista de import vazia |
| `404`  | Reminder não encontrado                                            |
| `409`  | ID duplicado no POST, **conflito de versão** no PUT/toggle         |
| `500`  | Erro inesperado                                                    |

## Optimistic locking

O record `Reminder` expõe o campo `version` (gerenciado pelo banco). O fluxo:

1. `GET /admin/reminders/{id}` → retorna `version = N`.
2. `PUT /admin/reminders/{id}` com body contendo `"version": N` → o repository executa
   `UPDATE ... WHERE id = ? AND version = N`. Se OK, `version` vira `N+1`.
3. Se outra instância atualizou o registro entre os passos 1 e 2, o `UPDATE` afeta `0` linhas
   → `OptimisticLockingFailureException` → **`409 Conflict`** no cliente.

O `POST /{id}/toggle` **não exige** `version` no body: ele lê a versão atual do banco e usa na
mesma transação `synchronized` (read + update atômico).

## Import / Export

### Export

`GET /admin/reminders/export` gera um `ReminderJson` (envelope com `version`, `updatedAt` e a
lista de reminders) e devolve com `Content-Disposition: attachment; filename="reminders-<ISO>.json"`.

Útil para:

- **Backup manual** antes de operações destrutivas.
- **Versionar em git** (`config/reminders.json` versionado como snapshot).
- **Migração entre ambientes** (dev → prod, prod → dev).

### Import

`POST /admin/reminders/import?replace=false`:

- Para cada reminder do lote: **upsert** (`update` se existe, `save` se não existe).
- **Reminders existentes que não estão no lote são preservados** (merge).
- Roda em uma **única transação**: ou tudo, ou nada.

`POST /admin/reminders/import?replace=true`:

- Idem, mas **remove** os reminders do banco que não estão no lote.
- Use para **restore exato** (a partir de um export).

**Proteção:** o controller rejeita `import` com lista vazia (`400`) — evita que um `replace=true`
acidental apague o banco inteiro.

## Bootstrap

No boot (`@ApplicationRunner`), o `ReminderBootstrapService`:

1. Checa `reminders.bootstrap.from-json.enabled` (default `false` em dev/test, `true` em prod).
2. Checa se a tabela `reminders` está **vazia**.
3. Se sim, importa `reminders.data.file` via `importAll(reminders, false)` (merge).
4. Se a tabela **não** está vazia, **não faz nada** (idempotente).

Isso significa que:

- **Primeira subida em prod:** a flag está `true`, tabela vazia → importa o JSON → bot sobe com os
  reminders configurados.
- **Subidas subsequentes:** tabela tem registros → não toca no JSON (evita sobrescrever o banco com
  um JSON desatualizado).
- **Dev/test:** a flag está `false` (default) → não importa; os testes criam via controller.

Para forçar reimport: `POST /admin/reminders/import?replace=true`.

## Arquivos de Configuração

| Property                                | Default dev                | Default prod                      | Descrição                                |
| --------------------------------------- | -------------------------- | --------------------------------- | ---------------------------------------- |
| `reminders.bootstrap.from-json.enabled` | `false`                    | `true`                            | Importa JSON no boot se tabela vazia     |
| `reminders.data.file`                   | `classpath:reminders.json` | `file:/app/config/reminders.json` | Path do JSON (bootstrap + export/import) |
| `reminders.enabled` (feature flag)      | `true`                     | `true`                            | Pausa global do envio                    |

## Componentes

| Componente                 | Responsabilidade                                                               |
| -------------------------- | ------------------------------------------------------------------------------ |
| `Reminder`                 | Record imutável (id slug, cron obrigatório, `version` para optimistic locking) |
| `ReminderJson`             | Envelope JSON (`version`, `updatedAt`, `reminders`), tolerante a campos extras |
| `ReminderRepository`       | JDBC (`JdbcTemplate` + `RowMapper`), filhas em N+1 controlado                  |
| `ReminderService`          | CRUD, import em lote (`@Transactional`), listener de reload                    |
| `ReminderScheduler`        | Agendamento dinâmico, cancelamento, diagóstico                                 |
| `ReminderDispatcher`       | Envio por chat (isolamento), feature flag global, métricas                     |
| `ReminderController`       | REST: CRUD, toggle, test, reload, status, export, import                       |
| `ReminderBootstrapService` | `ApplicationRunner` que importa o JSON se a tabela estiver vazia               |

## Ciclo de vida

1. **Boot:**
   - `ReminderService.init()` loga total/habilitados (não relê do banco — o estado vive lá).
   - `ReminderScheduler.init()` (via `@PostConstruct`) cria o `ThreadPoolTaskScheduler`, registra
     o listener no `ReminderService` e agenda os reminders habilitados.
   - `ReminderBootstrapService.run()` (`ApplicationRunner`) importa o JSON **se** a tabela estiver
     vazia e a flag ligada.
2. **Mutação (via controller/service):**
   - `ReminderService` persiste via `ReminderRepository` (transação).
   - `notifyReload()` → `ReminderScheduler.reloadAll()` (cancela tasks antigas, recria).
3. **Shutdown:** `ReminderScheduler.@PreDestroy` cancela todas as tasks (graceful shutdown).

## Como adicionar um reminder

### Via UI

1. Aba `📢 Anúncios` → **Novo Anúncio**.
2. Preencha: ID (slug), cron (6 campos), mensagem, chats.
3. **Salvar** → agendado automaticamente.

### Via API

```bash
curl -X POST http://localhost:8082/admin/reminders \
  -H "Content-Type: application/json" \
  -d '{
    "id": "promo-sexta",
    "name": "Promoção Sexta",
    "enabled": true,
    "cron": "0 0 10 * * FRI",
    "timezone": "America/Sao_Paulo",
    "message": "🎬 <b>Promoção da Sexta!</b>",
    "targetChatIds": ["-100123"],
    "tags": ["promo"]
  }'
```

### Via JSON (bootstrap / restore)

1. Editar `config/reminders.json` (ou usar o export de outro ambiente).
2. `POST /admin/reminders/import` (ou `?replace=true` para restore exato).
3. Alternativa: ligar `reminders.bootstrap.from-json.enabled=true` **e** truncar a tabela
   `reminders` antes do boot.

## 🧪 Testando Anúncios Manualmente

A partir da v2.11.1, o painel de Anúncios permite testar qualquer anúncio (ativo ou pausado)
sem precisar esperar o cron.

### Pelo Painel Web

1. Vá em `📢 Anúncios`.
2. Na tabela, clique no botão **"Testar"** (ícone de avião de papel) ao lado do anúncio.
3. Digite o Chat ID de destino (ou deixe em branco para usar o showcase padrão).
4. O bot enviará a mensagem e/ou mídia imediatamente.

### Pela API REST / Terminal

```bash
curl -X POST "http://localhost:8082/admin/reminders/quatro-horas-quarta/test?chatId=-100123"
```

> Nota: o teste manual ignora a feature flag reminders.enabled e o campo enabled do anúncio.

## Troubleshooting

| Sintoma                                                              | Causa provável                                                   | Solução                                                           |
| -------------------------------------------------------------------- | ---------------------------------------------------------------- | ----------------------------------------------------------------- |
| `📴 Reminder '...' ignorado — flag 'reminders.enabled' desabilitada` | Flag global desligada                                            | `/admin/features/reminders.enabled?enabled=true`                  |
| Reminder habilitado mas não dispara                                  | Cron inválido (scheduler ignorou)                                | Verificar log `❌ Cron inválido no reminder '...'`; corrigir cron |
| `POST /import` retorna `400 Lista de reminders está vazia`           | Body sem reminders                                               | Enviar `ReminderJson` com pelo menos 1 item                       |
| `409 Conflito de versão` no PUT                                      | Outra instância alterou entre GET e PUT                          | `GET` de novo para pegar a versão atual, `PUT` com ela            |
| `status` mostra `scheduled < enabled`                                | Cron inválido ou falha de agendamento                            | Logs do `ReminderScheduler`                                       |
| Tabela `reminders` vazia em prod após boot                           | `reminders.bootstrap.from-json.enabled=false` **e** tabela vazia | `POST /admin/reminders/import` com o JSON desejado                |

## Testes

| Arquivo                          | Foco                                                                    | Cobertura  |
| -------------------------------- | ----------------------------------------------------------------------- | ---------- |
| `ReminderTest`                   | Validação do record (slug, cron, listas imutáveis)                      | ~20 testes |
| `ReminderJsonTest`               | Envelope (normalização de `version`, `reminders` nulos)                 | ~15 testes |
| `ReminderRepositoryTest`         | Integração Postgres (Testcontainers): CRUD, optimistic locking, cascata | ~13 testes |
| `ReminderServiceTest`            | Mock do repository: CRUD, import, listener                              | ~30 testes |
| `ReminderDispatcherTest`         | Envio por chat, feature flag, métricas                                  | ~20 testes |
| `ReminderSchedulerTest`          | Agendamento, cron inválido, cancelamento                                | ~15 testes |
| `ReminderControllerTest`         | REST: CRUD, export, import, 409                                         | ~30 testes |
| `ReminderStorageIntegrationTest` | Fluxo ponta-a-ponta com Testcontainers                                  | ~8 testes  |
