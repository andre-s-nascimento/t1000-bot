# Feature Flags

Documentação completa do sistema de **feature flags** do T-Mill Bot.

## Sumário

- [Visão geral](#visão-geral)
- [Arquitetura](#arquitetura)
- [Ciclo de vida](#ciclo-de-vida)
- [Catálogo de flags](#catálogo-de-flags)
- [Como usar o painel admin](#como-usar-o-painel-admin)
- [Como usar via API](#como-usar-via-api)
- [Persistência](#persistência)
- [Como adicionar uma nova flag](#como-adicionar-uma-nova-flag)
- [Troubleshooting](#troubleshooting)

---

## Visão geral

O T-Mill Bot usa **feature flags runtime** para permitir que funcionalidades sejam ligadas e
desligadas **sem reiniciar a aplicação e sem deploy**.

Casos de uso típicos:

- **Incidente em produção:** desligar o podcast enquanto o Azure TTS está instável.
- **Teste A/B:** habilitar um recurso só para o showcase.
- **Auditoria:** congelar o painel admin (flag guarda-chuva) durante uma investigação.
- **Rollout gradual:** habilitar uma feature nova em prod após validar em dev.

As flags são lidas do `application.properties` no boot, podem ser alteradas em runtime via painel
admin ou API REST, e (opcionalmente) persistidas em disco para sobreviver a restarts.

---

## Arquitetura

O sistema é dividido em três camadas:

```
┌─────────────────────────────────────────────────────────────┐
│  Controllers (AdminController, AdminWebController)          │
│  → expõem endpoints REST e páginas Thymeleaf                │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│  FeatureFlagAdminService                                    │
│  → registra as flags conhecidas no boot                     │
│  → delega leitura/toggle para FeatureFlagService            │
│  → expõe toggleWithCheck (valida flag guarda-chuva)         │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│  FeatureFlagService                                         │
│  → mantém o estado em ConcurrentHashMap                     │
│  → persiste em ./config/feature-flags.json (opcional)       │
│  → regras: readOnly, valores default, ordenação             │
└─────────────────────────────────────────────────────────────┘
```

### `FeatureFlagService`

Camada de baixo nível. Responsável por:

- Guardar o **estado atual** em memória (`ConcurrentHashMap`).
- **Persistir** em disco (JSON) se `feature-flags.persist.enabled=true`.
- **Carregar** do disco no boot (valores do arquivo sobrepõem os defaults).
- Aplicar regras: flags `readOnly` não podem ser alteradas.

### `FeatureFlagAdminService`

Fachada administrativa. Responsável por:

- **Registrar** as flags conhecidas no `@PostConstruct` (lendo defaults do `Environment`).
- **Delegar** `isEnabled`, `list`, `toggle` para o `FeatureFlagService`.
- **Expor** `toggleWithCheck(key, enabled)`, que valida a flag guarda-chuva
  `admin.features.toggle-enabled` antes de permitir qualquer alteração.

### `FeatureFlagState` (record)

Estado imutável de uma flag:

```java
public record FeatureFlagState(
        String key,
        boolean enabled,
        String description,
        boolean readOnly) {

    public FeatureFlagState withEnabled(boolean newValue) { ... }
}
```

### `FeatureFlagDefinition` (record)

Definição inicial de uma flag (usada no registro):

```java
public record FeatureFlagDefinition(
        String key,
        String description,
        boolean readOnly,
        boolean defaultValue) { ... }
```

---

## Ciclo de vida

```
   BOOT                          RUNTIME                       RESTART
 ┌──────────────────┐         ┌──────────────────┐         ┌──────────────────┐
 │ Environment      │         │ Painel admin /   │         │ Persistência     │
 │ (application.    │         │ API REST         │         │ (feature-flags.  │
 │  properties)     │         │                  │         │  json)           │
 └────────┬─────────┘         └────────┬─────────┘         └────────┬─────────┘
          │                            │                            │
          ▼                            ▼                            ▼
 ┌──────────────────┐         ┌──────────────────┐         ┌──────────────────┐
 │ registerKnown    │         │ toggle(key,val)  │         │ loadFromDisk()   │
 │ Flags()          │         │                  │         │                  │
 └────────┬─────────┘         └────────┬─────────┘         └────────┬─────────┘
          │                            │                            │
          ▼                            ▼                            ▼
 ┌──────────────────┐         ┌──────────────────┐         ┌──────────────────┐
 │ FeatureFlag      │         │ Atualiza         │         │ Valores do disco │
 │ Service.init()   │         │ ConcurrentMap    │         │ sobrepõem os     │
 │ + loadFromDisk() │         │ + persistToDisk()│         │ defaults         │
 └──────────────────┘         └──────────────────┘         └──────────────────┘
```

**Ordem de precedência no boot:**

1. **Defaults hardcoded** em `registerKnownFlags` (ex.: `podcast.enabled` = `true`).
2. **`application.properties`** (via `Environment.getProperty("podcast.enabled")`) — sobrepõe o default.
3. **`feature-flags.json`** (se existir) — sobrepõe tudo, exceto flags `readOnly`.

---

## Catálogo de flags

### Alta prioridade — Onda 3.5.1

| Flag                              | Default | Editável | O que controla                                           |
| --------------------------------- | ------- | -------- | -------------------------------------------------------- |
| `podcast.enabled`                 | `true`  | ✅       | Publicação automática semanal do podcast (cron de sexta) |
| `weekly.reminder.enabled`         | `true`  | ✅       | Lembrete semanal de quarta-feira (cron de quarta às 16h) |
| `daily.releases.enabled`          | `true`  | ✅       | Notificação de lançamentos a cada 6h                     |
| `worldcup.reminders30min.enabled` | `true`  | ✅       | Aviso 30 min antes de cada jogo da Copa                  |
| `easter.eggs.enabled`             | `true`  | ✅       | Easter eggs nas buscas de filmes                         |
| `admin.features.toggle-enabled`   | `true`  | ✅       | Permite alterar flags via painel admin                   |

### Já existentes

| Flag                       | Default | Editável | O que controla                                    |
| -------------------------- | ------- | -------- | ------------------------------------------------- |
| `worldcup.enabled`         | `false` | ✅       | Envio de jogos da Copa do Mundo                   |
| `transcription.enabled`    | `true`  | ✅       | Transcrição de áudio (Whisper/Groq)               |
| `auto.response.enabled`    | `true`  | ✅       | Respostas automáticas no chat                     |
| `digest.enabled`           | `true`  | ✅       | Digest diário (morning/evening)                   |
| `worldcup.update.enabled`  | `false` | ✅       | Atualização automática do JSON da Copa            |
| `prompts.external.enabled` | `false` | ✅       | Uso de prompts e personas externalizados em JSON  |
| `migration.enabled`        | `false` | ❌       | Migração SQLite → Postgres/Mongo (requer restart) |

### Detalhamento por flag

#### `podcast.enabled`

- **Onde é usada:** `PodcastPublisherService.publishWeeklyPodcast()` (cron).
- **Desabilitar NÃO bloqueia:** endpoints admin `/admin/test-podcast`, `/admin/podcast/resend`.
  O admin pode testar mesmo com o cron desabilitado.
- **Caso de uso:** Azure TTS instável → desliga o cron até estabilizar.

#### `weekly.reminder.enabled`

- **Onde é usada:** `WeeklyReminderService.sendWednesdayReminder()` (cron).
- **Desabilitar NÃO bloqueia:** `/admin/test-weekly-reminder-showcase`.
- **Caso de uso:** silenciar o bot durante uma semana de férias coletivas.

#### `daily.releases.enabled`

- **Onde é usada:** `DailyReleasesService.sendHourlyReleases()` (cron de 6h em 6h).
- **Desabilitar NÃO bloqueia:** `/admin/test-daily-releases`, `/admin/test-weekly-digest`.
- **Caso de uso:** TMDB/Watchmode com rate limit → desliga temporariamente.

#### `worldcup.reminders30min.enabled`

- **Onde é usada:** `WorldCupSchedulerService.checkThirtyMinutesBeforeEachMatch()` (cron a cada minuto).
- **Precedência:** só funciona se `worldcup.enabled` também estiver `true`.
- **Desabilitar NÃO bloqueia:** noon/evening/results.
- **Caso de uso:** evitar spam de avisos em jogos simultâneos.

#### `easter.eggs.enabled`

- **Onde é usada:** `MovieService.buscarPorId()`.
- **Desabilitar:** pula a consulta ao `EasterEggService` e remove o texto extra da resposta.
- **Caso de uso:** debug da formatação do filme sem o ruído do easter egg.

#### `admin.features.toggle-enabled`

- **Onde é usada:** `FeatureFlagAdminService.toggleWithCheck()`.
- **⚠️ Nota:** os controllers hoje usam `toggle()` (sem check). O `toggleWithCheck` está
  disponível para um PR futuro que ative a guarda-chuva de fato.
- **Caso de uso futuro:** congelar o painel admin durante uma auditoria/incidente.

#### `worldcup.enabled`

- **Onde é usada:** todos os métodos públicos do `WorldCupSchedulerService`.
- **Caso de uso:** desligar tudo relacionado à Copa (fora de temporada).

#### `transcription.enabled`

- **Onde é usada:** `AudioHandler.handleAudioUpdate()`.
- **Caso de uso:** Groq com problema → desliga a transcrição de áudio.

#### `auto.response.enabled`

- **Onde é usada:** `AutoResponseService.getResponseRule()`.
- **Caso de uso:** silenciar respostas automáticas durante manutenção.

#### `digest.enabled`

- **Onde é usada:** `DailyDigestService.generateMorningDigest()` e `generateEveningDigest()`.
- **Caso de uso:** silenciar os resumos em feriados.

#### `worldcup.update.enabled`

- **Onde é usada:** `WorldCupUpdaterService.updateWorldCupData()`.
- **Caso de uso:** desabilitar auto-update do JSON durante testes manuais.

#### `prompts.external.enabled`

- **Onde é usada:** `PromptRegistryService` (roadmap).
- **Caso de uso:** usar prompts hardcoded em vez dos JSONs externos.

#### `migration.enabled`

- **Onde é usada:** `MigrationService.migrateAll()`.
- **Read-only:** exige restart para alterar.
- **Caso de uso:** ligar apenas durante a migração real.

---

## Como usar o painel admin

1. Acesse `/admin-web` e autentique com o Google.
2. Clique na aba **🎛️ Features**.
3. Cada flag aparece com:
   - **Chave** em fonte monoespaçada.
   - **Descrição** legível.
   - **Badge `read-only`** se não for editável.
   - **Switch** para alternar.
4. Ao alternar, o estado é aplicado **imediatamente** e uma notificação aparece no canto.
5. Botão **Recarregar** atualiza a lista (útil se outra sessão alterou algo).

---

## Como usar via API

### Listar flags

```bash
curl -X GET http://localhost:8082/admin/features \
  -H "Accept: application/json"
```

Resposta (array):

```json
[
  {
    "key": "podcast.enabled",
    "enabled": true,
    "description": "Publicação automática semanal do podcast",
    "readOnly": false
  }
]
```

### Toggle de uma flag

```bash
curl -X POST http://localhost:8082/admin/features/podcast.enabled \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "enabled=false"
```

Resposta (JSON):

```json
{
  "key": "podcast.enabled",
  "enabled": false,
  "changed": true
}
```

### Listar via WebController (formato mapa)

O `AdminWebController` expõe o mesmo conceito em `/admin-web/features` no formato:

```json
{
  "podcast.enabled": {
    "enabled": true,
    "description": "...",
    "readOnly": false
  }
}
```

Esse formato é o que o `admin.html` consome via `fetchJson('/admin-web/features')`.

---

## Persistência

### `feature-flags.json`

Se `feature-flags.persist.enabled=true`, o estado é gravado em
`feature-flags.persist.path` (default: `./config/feature-flags.json`).

Formato:

```json
{
  "podcast.enabled": {
    "key": "podcast.enabled",
    "enabled": true,
    "description": "Publicação automática semanal do podcast",
    "readOnly": false
  },
  "weekly.reminder.enabled": {
    "key": "weekly.reminder.enabled",
    "enabled": false,
    "description": "Lembrete semanal de quarta-feira",
    "readOnly": false
  }
}
```

### Escrita atômica

Para evitar corrupção se o processo morrer durante o write, o `FeatureFlagService` grava em
`feature-flags.json.tmp` e depois faz `Files.move(..., ATOMIC_MOVE)`. Se o `.tmp` aparecer
sozinho em produção, significa que o processo foi interrompido entre o `write` e o `move` —
o `.json` original está intacto.

### Sobrevivência a restart

No boot, `FeatureFlagService.loadFromDisk()`:

1. Se o arquivo não existe → usa defaults.
2. Se existe mas está corrompido → loga warning e usa defaults.
3. Se existe e é válido → aplica **exceto** em flags `readOnly` (que sempre usam o valor do
   `application.properties`).

### `.gitignore`

Adicione ao `.gitignore`:

```
config/feature-flags.json
config/feature-flags.json.tmp
```

**Não versione** o arquivo — ele é estado de runtime, não configuração.

---

## Como adicionar uma nova flag

Passo a passo:

### 1. Registrar em `FeatureFlagAdminService.registerKnownFlags`

```java
registerBool("minha.nova.flag", true, "Descrição legível da flag");
```

Para uma flag **read-only** (requer restart):

```java
registerBoolReadOnly("minha.nova.flag", false, "Requer restart para alterar");
```

### 2. Documentar o default em `application.properties`

```properties
# Seção 12.1 — Alta prioridade
minha.nova.flag=${MINHA_NOVA_FLAG:true}
```

Isso permite override por env var em prod:

```bash
export MINHA_NOVA_FLAG=false
```

### 3. Adicionar a checagem no ponto de uso

```java
if (!featureFlags.isEnabled("minha.nova.flag")) {
    log.info("🛑 Funcionalidade desabilitada por feature flag — pulando.");
    return;
}
```

### 4. Escrever testes

- **Teste de bloqueio:** flag off → método não executa.
- **Teste de liberação:** flag on → método executa.
- **Teste de precedência:** se a nova flag depende de outra, testar a ordem.

Se o mock `Environment` for consultado por `registerKnownFlags`, considere
`@MockitoSettings(strictness = LENIENT)` na classe de teste (evita `PotentialStubbingProblem`).

### 5. Atualizar esta documentação

Adicione a nova flag na tabela do [catálogo](#catálogo-de-flags) e escreva um parágrafo em
[Detalhamento por flag](#detalhamento-por-flag).

### 6. Aplicar

```bash
./gradlew spotlessApply unitTest
```

Suba em dev, acesse `/admin-web` → aba Features, confirme que a nova flag aparece.

---

## Troubleshooting

### Flag não aparece no painel

- Verifique se está registrada em `FeatureFlagAdminService.registerKnownFlags`.
- Confirme que a aplicação reiniciou após a mudança (o registro só ocorre no `@PostConstruct`).
- Confira os logs: `🎛️ N feature flags registradas`.

### Toggle retorna 400 (bad request)

- Flag desconhecida → erro `Flag desconhecida: <key>`.
- Verifique se a key está correta (case-sensitive).

### Toggle retorna 409 (conflict)

- Flag é `readOnly` → erro `Flag '<key>' é read-only`.
- Só `migration.enabled` é read-only hoje.

### Alterei a flag mas o comportamento não mudou

- **Cache:** algumas features são cacheadas (`@Cacheable`). Ex.: `MovieService.buscarPorId`
  tem cache por `id`; mudar `easter.eggs.enabled` não invalida o cache. Espere o TTL
  (`expireAfterWrite=1h` em `CaffeineConfig`).
- **Cron:** features que rodam por cron (`podcast`, `weekly.reminder`, `daily.releases`)
  só veem a mudança na próxima execução.
- **Flags guarda-chuva:** hoje `admin.features.toggle-enabled` **não bloqueia** o toggle
  (só `toggleWithCheck` respeita). Se você desligou a guarda-chuva esperando congelar o
  painel, esse comportamento ainda não está ativo.

### Persistência não funciona em prod

- Confirme que `feature-flags.persist.enabled=true` no `application-prod.properties`.
- Confirme que o diretório `/app/config/` é gravável.
- Verifique os logs: `💾 Feature flags persistidas em /app/config/feature-flags.json`.

### `PotentialStubbingProblem` nos testes

- O mock do `Environment` é consultado com **13 chaves diferentes** em `registerKnownFlags`.
- Em testes que stubam apenas uma chave, use `@MockitoSettings(strictness = LENIENT)`.
- Veja `FeatureFlagAdminServiceTest` como referência.

### `UnnecessaryStubbingException` nos testes

- Sintoma: você stubou uma flag mas ela não foi consultada no caminho testado.
- **Causa comum:** teste do tipo "feature X não afeta este fluxo".
- **Solução:** remova o `when(...)` e adicione
  `verify(featureFlags, never()).isEnabled("...")`.
- **Alternativa:** `lenient().when(...)` se o stub for intencionalmente órfão.

---

## Referências

- Código: `src/main/java/net/ddns/adambravo79/tmill/service/feature/`
- Testes: `src/test/java/net/ddns/adambravo79/tmill/service/feature/`
- Painel admin: `src/main/resources/templates/admin.html` (aba **Features**)
- Endpoints REST: `src/main/java/net/ddns/adambravo79/tmill/controller/AdminController.java`
- Endpoints Web: `src/main/java/net/ddns/adambravo79/tmill/controller/AdminWebController.java`
- Configuração: `src/main/resources/application.properties` (seção 12)
