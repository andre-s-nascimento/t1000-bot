# Arquivos de Configuração

Documentação dos arquivos JSON externos usados pelo T-Mill Bot. Cada arquivo é carregado por
um `ConfigService` dedicado, com suporte a **reload em runtime** e **fallback seguro**.

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

---

## Visão geral

O T-Mill Bot externaliza configuração em **arquivos JSON** para permitir ajuste sem recompilar
e sem deploy. Cada arquivo tem um **service dedicado** que:

1. Carrega o JSON no boot (via `JsonConfigLoader`).
2. Aplica os valores em **records imutáveis** mantidos em `AtomicReference`.
3. Mantém o **valor anterior** se o JSON estiver ausente ou malformado (nunca quebra o boot).
4. Expõe um método `reload()` para recarga em runtime.
5. Pode ser disparado por endpoint admin (`/admin/reload-*`) ou por `SIGHUP` (futuro).

### Vantagens

- **Zero downtime:** ajusta em prod sem reiniciar.
- **Segurança:** um JSON malformado não derruba a aplicação — mantém o estado anterior.
- **Auditoria:** o arquivo em disco é a fonte da verdade para troubleshooting.
- **Testabilidade:** os services aceitam `JsonConfigLoader` mockado, permitindo testes puros.

---

## Localização e precedência

Cada arquivo é procurado em **4 locais**, nesta ordem:

| Ordem | Local                                                                           | Uso                                                                     |
| ----- | ------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| 1     | **Property específica** (ex.: `easter-egg.file=file:./config/easter-eggs.json`) | Override explícito por variável de ambiente ou `application.properties` |
| 2     | **`./config/<nome>.json`**                                                      | Dev local (fora do JAR)                                                 |
| 3     | **`/app/config/<nome>.json`**                                                   | Container em produção (volume montado)                                  |
| 4     | **`classpath:<nome>.json`**                                                     | Fallback empacotado no JAR                                              |

Se nenhum existir, o service usa um **default hardcoded** e loga um `warning`.

Exemplo de property no `application.properties`:

```properties
audio.config.file=${AUDIO_CONFIG_FILE:classpath:audio-config.json}
groq.config.file=${GROQ_CONFIG_FILE:classpath:groq-config.json}
refinement.config.file=${REFINEMENT_CONFIG_FILE:classpath:refinement-config.json}
admin.config.file=${ADMIN_CONFIG_FILE:classpath:admin-config.json}
worldcup.teams.file=${WORLD_CUP_TEAMS_FILE:classpath:worldcup-teams.json}
tmdb.aliases.file=${TMDB_ALIASES_FILE:classpath:tmdb-aliases.json}
feature-flags.persist.path=${FEATURE_FLAGS_PERSIST_PATH:./config/feature-flags.json}
```

### Estrutura de diretórios em dev

```
./config/
├── audio-config.json
├── groq-config.json
├── refinement-config.json
├── admin-config.json
├── worldcup-teams.json
├── tmdb-aliases.json
├── feature-flags.json          # gerado em runtime
└── prompts/
    ├── digest-personas.json
    ├── podcast-system.json
    ├── podcast-config.json
    └── weekly-reminder.json
```

### Estrutura de diretórios em prod

```
/app/config/
├── audio-config.json
├── groq-config.json
├── refinement-config.json
├── admin-config.json
├── worldcup-teams.json
├── tmdb-aliases.json
├── feature-flags.json
├── worldcup2026.json
├── auto-responses.json
├── easter-eggs.json
├── telegram-error-messages.json
└── prompts/
    ├── digest-personas.json
    ├── podcast-system.json
    └── podcast-config.json
```

---

## Ciclo de vida

```
   BOOT                              RUNTIME
 ┌──────────────────┐              ┌──────────────────┐
 │ JsonConfigLoader │              │ /admin/reload-*  │
 │ .loadConfig(...) │              │ POST             │
 └────────┬─────────┘              └────────┬─────────┘
          │                                 │
          ▼                                 │
 ┌──────────────────┐                       │
 │ ConfigService    │  ◀────────────────────┘
 │ .reload()        │
 └────────┬─────────┘
          │
          ▼
 ┌──────────────────┐
 │ AtomicReference  │
 │ .set(novo)       │
 │                  │
 │ ou               │
 │                  │
 │ .set(anterior)   │ ← se JSON inválido/ausente
 └──────────────────┘
```

**Regra de ouro:** um `reload()` que falha **nunca** desfaz o estado atual. Só substitui se o
novo carregamento for bem-sucedido.

---

## Catálogo de arquivos

| Arquivo                  | Service                                    | Reload                              | Propósito                             |
| ------------------------ | ------------------------------------------ | ----------------------------------- | ------------------------------------- |
| `audio-config.json`      | `AudioConfigService`                       | `POST /admin/reload-*` (futuro)     | Limites, compressão, timeout de áudio |
| `groq-config.json`       | `GroqConfigService` + `RetryConfigService` | `POST /admin/reload-*` (futuro)     | Modelos, temperatura, retry           |
| `refinement-config.json` | `RefinementConfigService`                  | `POST /admin/reload-*` (futuro)     | Regras de truncamento do refinamento  |
| `admin-config.json`      | `AdminConfigService`                       | `POST /admin/reload-admin-config`   | Chat showcase padrão                  |
| `worldcup-teams.json`    | `WorldCupTeamsService`                     | `POST /admin/reload-worldcup-teams` | Traduções pt-BR e bandeiras           |
| `tmdb-aliases.json`      | `TmdbClient.reloadAliases()`               | `POST /admin/reload-tmdb-aliases`   | Atalhos de busca de filmes            |
| `feature-flags.json`     | `FeatureFlagService`                       | Gravado automaticamente             | Estado persistido das flags           |
| `prompts/*.json`         | `PromptRegistryService`                    | `POST /admin/reload-prompts`        | Personas, prompts, config de podcast  |

> **Legado:** arquivos como `easter-eggs.json`, `auto-responses.json`, `worldcup2026.json`,
> `telegram-error-messages.json` são carregados por services específicos (não pelo
> `JsonConfigLoader`) e têm seu próprio padrão de reload (`/admin/reload-easter-eggs`, etc.).

---

## Detalhamento por arquivo

### `audio-config.json`

**Service:** `AudioConfigService`
**Propósito:** limites e parâmetros de compressão de áudio.

**Schema:**

```json
{
  "tts": {
    "max-audio-size-bytes": 5242880,
    "compress-bitrate": "64k",
    "compress-channels": 1,
    "compress-sample-rate": 22050,
    "compress-timeout-seconds": 120
  },
  "telegram": {
    "audio-max-size-mb": 20,
    "use-document-when-over-mb": 5
  },
  "podcast": {
    "max-audio-size-bytes": 5242880,
    "compress-timeout-seconds": 180
  }
}
```

**Campos:**

| Campo                                | Tipo     | Default          | Descrição                             |
| ------------------------------------ | -------- | ---------------- | ------------------------------------- |
| `tts.max-audio-size-bytes`           | `long`   | `5242880` (5 MB) | Limite para compressão do TTS         |
| `tts.compress-bitrate`               | `String` | `"64k"`          | Bitrate da compressão FFmpeg          |
| `tts.compress-channels`              | `int`    | `1`              | Número de canais (mono/stereo)        |
| `tts.compress-sample-rate`           | `int`    | `22050`          | Sample rate em Hz                     |
| `tts.compress-timeout-seconds`       | `int`    | `120`            | Timeout do FFmpeg                     |
| `telegram.audio-max-size-mb`         | `int`    | `20`             | Limite de áudio aceito pelo bot       |
| `telegram.use-document-when-over-mb` | `int`    | `5`              | A partir de X MB, envia como Document |
| `podcast.max-audio-size-bytes`       | `long`   | `5242880`        | Limite para compressão do podcast     |
| `podcast.compress-timeout-seconds`   | `int`    | `180`            | Timeout do FFmpeg para podcast        |

**Uso no código:**

- `TelegramFacade.sendAudioOrDocument` consulta `telegram.use-document-when-over-mb`.
- `PodcastPublisherService.checkAndCompressAudio` consulta `podcast.max-audio-size-bytes`.
- `AzureTtsClient.compressIfNeeded` consulta `tts.*`.

---

### `groq-config.json`

**Service:** `GroqConfigService` (parâmetros de LLM) e `RetryConfigService` (retry).
**Propósito:** ajustar modelos, temperatura, max tokens e política de retry.

**Schema:**

```json
{
  "digest": {
    "temperature": 0.5,
    "max-tokens": 2200
  },
  "refinement": {
    "temperature": 0.18,
    "max-tokens": 4000
  },
  "retry": {
    "transcription": {
      "max-retries": 2,
      "delay-ms": 1000,
      "multiplier": 2,
      "max-delay-ms": 5000
    },
    "refinement": {
      "max-retries": 3,
      "delay-ms": 2000,
      "multiplier": 3,
      "max-delay-ms": 30000
    },
    "chat-completion": {
      "max-retries": 4,
      "delay-ms": 2000,
      "multiplier": 3,
      "max-delay-ms": 60000
    }
  }
}
```

**Campos:**

| Campo                       | Tipo     | Default | Descrição                      |
| --------------------------- | -------- | ------- | ------------------------------ |
| `digest.temperature`        | `double` | `0.5`   | Temperatura do LLM para digest |
| `digest.max-tokens`         | `int`    | `2200`  | Max tokens para digest         |
| `refinement.temperature`    | `double` | `0.18`  | Temperatura para refinamento   |
| `refinement.max-tokens`     | `int`    | `4000`  | Max tokens para refinamento    |
| `retry.<tipo>.max-retries`  | `int`    | `3`     | Número máximo de tentativas    |
| `retry.<tipo>.delay-ms`     | `long`   | `1000`  | Delay base em ms               |
| `retry.<tipo>.multiplier`   | `int`    | `2`     | Multiplicador do backoff       |
| `retry.<tipo>.max-delay-ms` | `long`   | `30000` | Teto do backoff                |

**Fallback:** se o JSON estiver ausente, cada `Spec` retorna o default hardcoded.

---

### `refinement-config.json`

**Service:** `RefinementConfigService`
**Propósito:** regras de negócio do refinamento (não parâmetros de LLM).

**Schema:**

```json
{
  "max-refinamento-reducao": 0.3,
  "min-length-para-checar-truncamento": 200,
  "refinement-max-tokens": 4000,
  "refinement-temperature": 0.18
}
```

**Campos:**

| Campo                                | Tipo     | Default | Descrição                                                       |
| ------------------------------------ | -------- | ------- | --------------------------------------------------------------- |
| `max-refinamento-reducao`            | `double` | `0.30`  | Redução máxima permitida (30%) antes de descartar o refinamento |
| `min-length-para-checar-truncamento` | `int`    | `200`   | Tamanho mínimo do input para checar truncamento                 |
| `refinement-max-tokens`              | `int`    | `4000`  | Max tokens (duplicado em `groq-config.json`)                    |
| `refinement-temperature`             | `double` | `0.18`  | Temperatura (duplicado em `groq-config.json`)                   |

> **Nota:** os dois últimos campos são mantidos por compatibilidade histórica. O
> `GroqClient.resolveRefinementLlmSpec()` consulta `GroqConfigService`. O
> `RefinementConfigService` só usa os dois primeiros para as regras de truncamento.

---

### `admin-config.json`

**Service:** `AdminConfigService`
**Propósito:** configurações do painel admin (chat showcase padrão).

**Schema:**

```json
{
  "showcaseChatId": -1003703557250
}
```

**Campos:**

| Campo            | Tipo               | Default          | Descrição                                 |
| ---------------- | ------------------ | ---------------- | ----------------------------------------- |
| `showcaseChatId` | `long` ou `String` | `-1003703557250` | Chat usado como fallback nos testes admin |

**Fallback:** `AdminConfigService.FALLBACK_SHOWCASE_CHAT_ID` (hardcoded).

**Uso:** endpoints `/admin/test-*` sem `chatId` explícito caem no showcase.

---

### `worldcup-teams.json`

**Service:** `WorldCupTeamsService`
**Propósito:** tradução pt-BR e bandeira (emoji) de cada time da Copa.

**Schema:**

```json
{
  "teams": {
    "brazil": { "namePt": "Brasil", "flag": "🇧🇷" },
    "argentina": { "namePt": "Argentina", "flag": "🇦🇷" },
    "united states": { "namePt": "Estados Unidos", "flag": "🇺🇸" },
    "dr congo": { "namePt": "Republica Democratica do Congo", "flag": "🇨🇩" }
  }
}
```

**Campos:**

| Campo                  | Tipo     | Descrição                          |
| ---------------------- | -------- | ---------------------------------- |
| `teams.<chave>`        | `String` | Nome do time em inglês (lowercase) |
| `teams.<chave>.namePt` | `String` | Nome em português                  |
| `teams.<chave>.flag`   | `String` | Emoji da bandeira                  |

**Uso:**

- `translateTeam(name)`: match exato → substring → retorna o input original.
- `flagEmoji(name)`: match exato → substring → `🏳️`.

**Fallback:** mapa vazio. Sem tradução, o time aparece com o nome original.

---

### `tmdb-aliases.json`

**Service:** carregado por `TmdbClient.reloadAliases()`.
**Propósito:** atalhos de busca (ex.: "duna" → "Dune 2021").

**Schema:**

```json
{
  "aliases": {
    "duna": "Dune 2021",
    "batman": "The Batman 2022",
    "matrix": "The Matrix"
  }
}
```

**Campos:**

| Campo                     | Tipo     | Descrição                          |
| ------------------------- | -------- | ---------------------------------- |
| `aliases.<chave>`         | `String` | Termo de busca lowercase           |
| `aliases.<chave>` (valor) | `String` | Termo efetivamente enviado ao TMDB |

**Uso:** em `TmdbClient.pesquisarFilme`, a query é normalizada (lowercase) e substituída pelo
alias, se existir.

**Fallback:** mapa vazio. A busca usa o termo original.

---

### `feature-flags.json`

**Service:** `FeatureFlagService` (não usa `JsonConfigLoader`).
**Propósito:** persistir o estado das flags entre restarts.

**Schema:**

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

**Campos:**

| Campo               | Tipo      | Descrição                                           |
| ------------------- | --------- | --------------------------------------------------- |
| `<key>.key`         | `String`  | Chave da flag (redundante, mas útil para debugging) |
| `<key>.enabled`     | `boolean` | Estado atual                                        |
| `<key>.description` | `String`  | Descrição legível                                   |
| `<key>.readOnly`    | `boolean` | Se pode ser alterada em runtime                     |

**Escrita:**

- Gravado automaticamente a cada `toggle(...)` (se `feature-flags.persist.enabled=true`).
- Escrita **atômica**: grava em `.tmp` e faz `Files.move(..., ATOMIC_MOVE)`.

**Boot:**

- Se o arquivo não existe → usa defaults do `application.properties`.
- Se está corrompido → loga warning e usa defaults.
- Se é válido → aplica **exceto** em flags `readOnly`.

**Ver também:** [feature-flags.md](feature-flags.md).

---

### `prompts/`

**Service:** `PromptRegistryService`
**Propósito:** externalizar personas, prompts e config do podcast/digest.

#### `prompts/digest-personas.json`

```json
{
  "activePersona": "T1000",
  "defaultPersona": "T1000",
  "personas": {
    "T1000": {
      "systemPrompt": "Você é o T-1000...",
      "description": "Frio e eficiente"
    },
    "BICENTENNIAL": {
      "systemPrompt": "Você é o homem bicentenário...",
      "description": "Reflexivo e filosófico"
    },
    "MATRIX_ARCHITECT": {
      "systemPrompt": "Você é o Arquiteto da Matrix...",
      "description": "Analítico e técnico"
    }
  },
  "periodContexts": {
    "MADRUGADA": "Contexto da madrugada (00:00 - 08:00)",
    "DEFAULT": "Contexto padrão (08:00 - 00:00)"
  },
  "userPromptTemplate": "Aqui estão as mensagens do período:\n\n%s"
}
```

**Campos:**

| Campo                        | Tipo     | Descrição                               |
| ---------------------------- | -------- | --------------------------------------- |
| `activePersona`              | `String` | Persona usada por padrão                |
| `defaultPersona`             | `String` | Fallback se `activePersona` não existir |
| `personas.<id>.systemPrompt` | `String` | System prompt do LLM                    |
| `personas.<id>.description`  | `String` | Descrição legível                       |
| `periodContexts.MADRUGADA`   | `String` | Contexto adicional para período noturno |
| `periodContexts.DEFAULT`     | `String` | Contexto adicional para os outros       |
| `userPromptTemplate`         | `String` | Template com `%s` para as mensagens     |

#### `prompts/podcast-system.json`

```json
{
  "systemPrompt": "Você é o Silas Cast, apresentador do podcast...",
  "userPrompt": "Aqui estão as mensagens da semana:\n\n"
}
```

#### `prompts/podcast-config.json`

```json
{
  "cron": "0 0 12 * * 5",
  "rules": {
    "closingLine": "E caso eu não veja vocês, bom dia, boa tarde e boa noite!",
    "temperature": 0.7
  }
}
```

**Campos:**

| Campo               | Tipo     | Default          | Descrição                      |
| ------------------- | -------- | ---------------- | ------------------------------ |
| `cron`              | `String` | `"0 0 12 * * 5"` | Cron do podcast (sexta às 12h) |
| `rules.closingLine` | `String` | (hardcoded)      | Frase de encerramento          |
| `rules.temperature` | `Number` | `0.7`            | Temperatura do LLM             |

---

## Como adicionar um novo arquivo de configuração

Passo a passo:

### 1. Definir o schema (record)

```java
public record MinhaConfig(String campoA, int campoB) {
    public static final MinhaConfig DEFAULT = new MinhaConfig("valor-padrao", 42);
}
```

### 2. Criar o service

```java
@Slf4j
@Service
public class MinhaConfigService {

    private static final MinhaConfig DEFAULT = new MinhaConfig("valor-padrao", 42);

    private final JsonConfigLoader jsonConfigLoader;

    @Value("${minha.config.file:classpath:minha-config.json}")
    private String configFile;

    private final AtomicReference<MinhaConfig> configRef =
            new AtomicReference<>(DEFAULT);

    public MinhaConfigService(JsonConfigLoader jsonConfigLoader) {
        this.jsonConfigLoader = jsonConfigLoader;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void reload() {
        try {
            var loaded = jsonConfigLoader.loadConfig(
                    "minha-config.json",
                    Map.class,
                    configFile != null && !configFile.isBlank() ? configFile : null);

            if (loaded.isEmpty()) {
                log.warn("⚠️ minha-config.json não encontrado — mantendo valores atuais.");
                return;
            }

            // Parsear campos do Map e setar configRef
            MinhaConfig nova = parse(loaded.get());
            configRef.set(nova);
            log.info("✅ MinhaConfig carregado: {}", nova);

        } catch (Exception e) {
            log.error("❌ Falha ao carregar minha-config.json — mantendo anterior", e);
        }
    }

    public MinhaConfig getConfig() {
        return configRef.get();
    }
}
```

### 3. Expor endpoint de reload (opcional)

```java
@PostMapping("/reload-minha-config")
public ResponseEntity<String> reloadMinhaConfig() {
    minhaConfigService.reload();
    return ResponseEntity.ok("minha-config.json recarregado");
}
```

### 4. Criar o JSON em `src/main/resources/`

```json
{
  "campoA": "valor-padrao",
  "campoB": 42
}
```

### 5. Testar

- Teste unitário com `JsonConfigLoader` mockado.
- Teste de fallback (JSON ausente → usa default).
- Teste de JSON inválido (mantém anterior).
- Teste de reload (JSON novo sobrescreve).

### 6. Documentar

Adicione o arquivo no [catálogo](#catálogo-de-arquivos) e um bloco em
[Detalhamento por arquivo](#detalhamento-por-arquivo).

---

## Reload em runtime

### Via endpoint REST

```bash
# Recarregar prompt registry
curl -X POST http://localhost:8082/admin/reload-prompts

# Recarregar times da Copa
curl -X POST http://localhost:8082/admin/reload-worldcup-teams

# Recarregar atalhos TMDB
curl -X POST http://localhost:8082/admin/reload-tmdb-aliases

# Recarregar admin-config
curl -X POST http://localhost:8082/admin/reload-admin-config
```

### Via painel admin

Aba **Admin** → botões **Recarregar**.

### Via programática

```java
@Autowired
private AudioConfigService audioConfigService;

public void exemplo() {
    audioConfigService.reload();  // relê o JSON e aplica
}
```

### Comportamento em caso de falha

- **JSON ausente:** mantém o valor atual e loga warning.
- **JSON malformado:** mantém o valor atual e loga error.
- **Campo inválido dentro do JSON:** usa o default **daquele campo** e mantém os outros.

---

## Troubleshooting

### Arquivo não está sendo carregado

- Verifique a property específica:

  ```bash
  curl http://localhost:8082/admin/properties | jq '.["audio.config.file"]'
  ```

- Verifique os logs do boot:

  ```
  ✅ AudioConfig carregado: tts=..., telegram=..., podcast=...
  ```

- Se aparecer `⚠️ audio-config.json não encontrado — mantendo valores atuais`, o arquivo não
  foi achado em nenhum dos 4 locais.

### Reload não surte efeito

- Verifique se o service tem `reload()` público.
- Verifique se o endpoint `/admin/reload-*` está registrado.
- Verifique se o arquivo realmente mudou (`md5sum config/audio-config.json`).
- Se o service usa `AtomicReference`, o efeito é **imediato**. Se usa `@Value`, requer restart.

### JSON malformado mantém valor antigo

Isso é **intencional**. O log mostra:

```
❌ Falha ao carregar audio-config.json — mantendo anteriores
```

Corrija o JSON e chame `reload()` novamente.

### Valores default não batem

- Verifique se o campo está no JSON com o **nome exato** (kebab-case: `max-audio-size-bytes`,
  não `maxAudioSizeBytes`).
- Verifique se o tipo é compatível (String vs Number vs Boolean).
- O service usa `getLong`/`getInt`/`getDouble` que aceitam Number ou String numérica.

### Testes falhando com `PotentialStubbingProblem`

O `JsonConfigLoader` é mockado nos testes. Se o teste stubar `loadConfig("audio-config.json", ...)`
mas o service chamar `loadConfig("audio-config.json", Map.class, null)`, o Mockito strict
reclama. Use:

```java
doReturn(Optional.of(json))
        .when(jsonConfigLoader)
        .loadConfig(eq("audio-config.json"), eq(Map.class), any());
```

---

## Referências

- Loader: `src/main/java/net/ddns/adambravo79/tmill/service/config/JsonConfigLoader.java`
- Services: `src/main/java/net/ddns/adambravo79/tmill/service/config/`
- Exemplo de JSON: `src/main/resources/audio-config.json`
- Exemplo em prod: `/app/config/audio-config.json`
- [Feature Flags](feature-flags.md) — sistema de flags (usa `feature-flags.json`)
- [Dev Guide](README.md) — este guia
