# 🏗️ Arquitetura Tmill Bot v2

> **Versão do documento**: v2.0.0 | **Atualizado em**: 2026-07-21
>
> _Documento arquitetural da plataforma Tmill Bot. Preserva decisões da v1 e detalha evoluções da v2._

---

## 📜 Histórico de Versões

| Versão   | Data        | Autor                   | Mudanças Principais                                                                                                                  |
| -------- | ----------- | ----------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| v1.0     | 2024-05     | André S. Nascimento     | Arquitetura inicial: bot monolítico com polling direto, TMDB básico, transcrição Whisper local                                       |
| v1.1     | 2024-08     | André S. Nascimento     | Adicionado SQLite para persistência de transcrições                                                                                  |
| v1.2     | 2024-11     | André S. Nascimento     | Introdução de cache em memória, retry básico                                                                                         |
| **v2.0** | **2026-07** | **André S. Nascimento** | **Reescrita arquitetural completa: camadas desacopladas, Virtual Threads, múltiplos LLMs, pipeline modular, sistema de autorização** |

---

## 🎯 Visão Geral

O Tmill Bot v2 é uma **plataforma de entretenimento inteligente** para Telegram, arquitetada como um conjunto de microsserviços internos (módulos) dentro de uma aplicação Spring Boot monolítica. A arquitetura prioriza:

1. **Resiliência**: Retry com backoff, circuit breakers implícitos, fallback em cascata
2. **Performance**: Virtual Threads (Java 21), cache multi-camadas, processamento assíncrono
3. **Extensibilidade**: Novos módulos plugáveis via Spring DI
4. **Observabilidade**: Métricas em memória, logging estruturado, endpoints de diagnóstico

---

## 🏛️ Diagrama de Componentes (C4 — Nível 2: Containers)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Tmill Bot v2                                    │
│                    (Spring Boot 3.x + Java 21)                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────┐  │
│  │   Telegram   │◄──►│   Telegram   │◄──►│   Update     │◄──►│  Auth    │  │
│  │    API       │    │   Facade     │    │  Handlers    │    │ Service  │  │
│  └──────────────┘    └──────────────┘    └──────────────┘    └──────────┘  │
│         ▲                  │                      │                        │
│         │                  │                      ▼                        │
│         │                  │           ┌────────────────────┐                │
│         │                  │           │  Command Handler   │                │
│         │                  │           │  Callback Handler  │                │
│         │                  │           │  Audio Handler     │                │
│         │                  │           └────────────────────┘                │
│         │                  │                      │                        │
│         │                  ▼                      ▼                        │
│         │           ┌──────────────────────────────────────┐               │
│         │           │         Service Layer (Core)          │               │
│         │           ├──────────┬──────────┬────────────────┤               │
│         │           │  Movie   │  Audio   │    Digest      │               │
│         │           │ Service  │ Pipeline │    Service     │               │
│         │           ├──────────┼──────────┼────────────────┤               │
│         │           │  Daily   │  World   │  Auto-Response │               │
│         │           │ Releases │   Cup    │    Service     │               │
│         │           ├──────────┴──────────┴────────────────┤               │
│         │           │         Client Layer (HTTP)           │               │
│         │           │  GroqClient │ TmdbClient │ Watchmode  │               │
│         │           └──────────────────────────────────────┘               │
│         │                           │                                      │
│         │                           ▼                                      │
│         │           ┌──────────────────────────────────────┐               │
│         │           │         Infrastructure Layer          │               │
│         │           │  SQLite  │  Caffeine  │  Scheduler  │               │
│         │           │   (JDBC) │   Cache    │  (Cron)     │               │
│         │           └──────────────────────────────────────┘               │
│         │                                                                  │
│         └──────────────────────────────────────────────────────────────────┘
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                         GitHub Actions CI/CD                         │   │
│  │  Build → Test → Spotless → Docker Build → Push → Release Tag        │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 🔧 Camadas Arquiteturais

### 1. Telegram Layer (Inbound)

```
┌─────────────────────────────────────────────────────────────┐
│                    Telegram Layer                             │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Ksilisk Telegram Bot (Polling)                             │
│       │                                                     │
│       ▼                                                     │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐  │
│  │   Matcher    │───►│  Interceptor │───►│   Handler    │  │
│  │ (AllUpdate)  │    │  (Auth +     │    │   (Router)   │  │
│  │              │    │   Logging)   │    │              │  │
│  └──────────────┘    └──────────────┘    └──────────────┘  │
│                                                   │         │
│                              ┌────────────────────┘         │
│                              ▼                              │
│                    ┌─────────────────┐                      │
│                    │  MessageUpdate  │                      │
│                    │  CallbackUpdate │                      │
│                    │  EditedMessage  │                      │
│                    └─────────────────┘                      │
│                              │                              │
│                              ▼                              │
│                    ┌─────────────────┐                      │
│                    │  Controllers    │                      │
│                    │  (Command,      │                      │
│                    │   Callback,     │                      │
│                    │   Audio)        │                      │
│                    └─────────────────┘                      │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**Decisões v2:**

- **Migração de polling manual → Ksilisk framework**: Reduz boilerplate, adiciona interceptors nativos
- **Separação de handlers por tipo de update**: Cada handler tem responsabilidade única (SRP)
- **GroupAuthorizationService**: Filtro centralizado de autorização (whitelist de grupos)

### 2. Service Layer (Core Business)

#### 2.1 MovieService

```java
// Fluxo de busca por ID (cacheable)
buscarPorId(id) → CompletableFuture.allOf(
    buscarDetalhes(id),      // TMDB /movie/{id}
    buscarElenco(id),        // TMDB /movie/{id}/credits
    buscarDiretor(id),       // TMDB /movie/{id}/credits
    buscarOndeAssistir(id)   // TMDB /movie/{id}/watch/providers
) → MovieOrchestrationResponse
```

**Cache**: `@Cacheable(value="movieDetails", key="#id", unless="#result == null")` — TTL 1h

#### 2.2 AudioPipelineService

```
OGA File → FFmpeg (16kHz/mono/WAV) → Whisper (Groq) → Texto Bruto
                                                          │
                                                          ▼
                                               Llama Refinement (Groq)
                                                          │
                              ┌───────────────────────────┴───────────────────────────┐
                              │                                                       │
                              ▼                                                       ▼
                    ┌─────────────────┐                                     ┌─────────────────┐
                    │   Chat Privado  │                                     │    Grupo        │
                    │  (envio direto) │                                     │ (botões inline) │
                    └─────────────────┘                                     └─────────────────┘
                                                                                │
                                                                                ▼
                                                                       Callback: entrega
                                                                       no privado do user
```

**Decisões v2:**

- **Virtual Threads para @Async**: `TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor())`
- **Retry manual para rate limit Groq**: Parse da mensagem de erro para extrair tempo de espera
- **Cache de transcrições por fileId**: Evita reprocessamento em grupos grandes

#### 2.3 DailyDigestService

```
SQLite (messages + transcripts) → Agregação por período → Prompt Builder
                                                              │
                                                              ▼
                                                    Groq Chat Completion
                                                    (Persona selecionada)
                                                              │
                                                              ▼
                                                    Sanitização HTML → Telegram
```

**Personas:**
| Persona | Modelo | Característica |
|---------|--------|----------------|
| T1000 | `meta-llama/llama-4-scout-17b-16e-instruct` | Sarcástico, cinematográfico |
| BICENTENNIAL | `openai/gpt-oss-20b` | Humano, contemplativo, gentil |
| MATRIX_ARCHITECT | `openai/gpt-oss-120b` | Lógico, frio, analítico |

#### 2.4 DailyReleasesService

```
Cron (a cada 6h) → TMDB Discover (movie + tv) → Watchmode Providers
                                                      │
                              ┌───────────────────────┴───────────────────────┐
                              │                                               │
                              ▼                                               ▼
                    ┌─────────────────┐                             ┌─────────────────┐
                    │  Novo + Tem     │                             │  Já Notificado  │
                    │  Provedor?      │                             │  ou Sem Provedor│
                    └─────────────────┘                             └─────────────────┘
                              │                                               │
                              ▼                                               ▼
                    ┌─────────────────┐                             (ignora)
                    │  Salva SQLite   │
                    │  + Notifica     │
                    └─────────────────┘
```

### 3. Client Layer (Outbound HTTP)

| Cliente                        | Base URL                                | Retry                               | Timeout                   |
| ------------------------------ | --------------------------------------- | ----------------------------------- | ------------------------- |
| `GroqClient`                   | `https://api.groq.com`                  | 2-4 tentativas, backoff exponencial | 5s connect / 30-120s read |
| `TmdbClient`                   | `https://api.themoviedb.org/3`          | 1-2 tentativas                      | 5s connect / 10s read     |
| `WatchmodeClient`              | `https://api.watchmode.com/v1`          | 1 tentativa (cache)                 | Padrão                    |
| `StreamingAvailabilityService` | `streaming-availability.p.rapidapi.com` | Sem retry                           | Padrão                    |

**Decisão v2**: Uso de `RestClient` (Spring 6.1+) ao invés de `WebClient` para simplicidade em chamadas síncronas.

### 4. Infrastructure Layer

```
┌─────────────────────────────────────────────────────────────┐
│              Infrastructure Layer                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐  │
│  │   SQLite     │    │   Caffeine   │    │   Scheduled  │  │
│  │   (JDBC)     │    │    Cache     │    │   Tasks      │  │
│  │              │    │              │    │              │  │
│  │  messages    │    │  movieDetails│    │  Digest      │  │
│  │  transcripts │    │  watchmode   │    │  Releases    │  │
│  │  releases_   │    │  providers   │    │  World Cup   │  │
│  │  notified    │    │  fileTrans   │    │  Weekly Rem. │  │
│  └──────────────┘    └──────────────┘    └──────────────┘  │
│                                                             │
│  ┌────────────────────────────────────────────────────────┐│
│  │              GlobalExceptionHandler                     ││
│  │  AudioProcessingException → 500 + log                   ││
│  │  TelegramFileException    → 400 + log                   ││
│  │  MovieNotFoundException   → 404 + log                   ││
│  │  RuntimeException         → 500 + log                   ││
│  └────────────────────────────────────────────────────────┘│
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 📊 Diagrama de Sequência — Pipeline de Áudio (Grupo)

```
User          Telegram        AudioHandler    AudioPipeline    GroqClient    SQLite    Cache
  │               │                │               │               │           │         │
  │──[voice]────►│               │               │               │           │         │
  │               │──[update]───►│               │               │           │         │
  │               │               │──baixar──────►│               │           │         │
  │               │               │               │──converter───►│           │         │
  │               │               │               │◄──WAV────────│           │         │
  │               │               │               │──transcrever─►│           │         │
  │               │               │               │◄──"bruto"────│           │         │
  │               │               │               │──refinar─────►│           │         │
  │               │               │               │◄──"refinado"─│           │         │
  │               │               │               │               │           │──save──►│
  │               │               │               │               │           │         │──put──►
  │               │◄─[botoes]────│               │               │           │         │
  │◄──[botoes]────│               │               │               │           │         │
  │               │               │               │               │           │         │
  │──[click]─────►│               │               │               │           │         │
  │               │──[callback]─►│               │               │           │         │
  │               │               │──get cache──────────────────────────────────────────►│
  │               │               │◄──hit/miss─────────────────────────────────────────│
  │               │               │               │               │           │         │
  │               │               │──enviar privado─────────────────────────────────────►│
  │◄──[texto]─────│               │               │               │           │         │
```

---

## 🗄️ Modelo de Dados (SQLite)

```sql
-- Mensagens do chat (para digest)
CREATE TABLE messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    chat_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    user_name TEXT,
    text TEXT NOT NULL,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Transcrições de áudio
CREATE TABLE transcripts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    chat_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    user_name TEXT,
    text TEXT NOT NULL,
    raw_text TEXT,           -- v2: texto bruto do Whisper
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Lançamentos já notificados (deduplicação)
CREATE TABLE releases_notified (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    tmdb_id INTEGER NOT NULL,
    media_type TEXT NOT NULL,     -- 'movie' | 'tv'
    release_date TEXT NOT NULL,
    title TEXT,
    overview TEXT,
    rating REAL,
    providers TEXT,
    poster_path TEXT,
    notified_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

---

## 🔐 Segurança

| Aspecto               | Implementação                                               |
| --------------------- | ----------------------------------------------------------- |
| Token masking         | `maskToken()` — exibe apenas primeiros/últimos 4 caracteres |
| Autorização de grupos | Whitelist via `bot.allowed-chats` (IDs negativos apenas)    |
| Rate limiting         | Retry com backoff exponencial nos clientes HTTP             |
| Validação de entrada  | Sanitização de queries TMDB (`[^\p{L}\p{N}\s]`)             |
| File size limit       | Áudio limitado a 20MB                                       |

---

## 📈 Escalabilidade e Performance

| Estratégia           | Implementação                                    | Impacto                                             |
| -------------------- | ------------------------------------------------ | --------------------------------------------------- |
| Virtual Threads      | `@Async` com `newVirtualThreadPerTaskExecutor()` | Milhares de operações I/O concorrentes sem bloqueio |
| Cache Caffeine       | 3 caches independentes                           | Reduz chamadas API em ~70%                          |
| Connection Pooling   | Tomcat max 10 threads, 20 conexões               | Evita exaustão de recursos                          |
| Gradle optimizations | Daemon, parallel, configuration-cache            | Build ~40% mais rápido                              |
| Lazy initialization  | Desativado para startup previsível               | Startup em <3s                                      |

---

## 🔄 CI/CD Pipeline

```
┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐
│  Push   │───►│   CI    │───►│  Build  │───►│  Test   │───►│ Spotless│
│ /PR     │    │ Trigger │    │ Gradle  │    │ + JaCoCo│    │ Check   │
└─────────┘    └─────────┘    └─────────┘    └─────────┘    └─────────┘
                                                                   │
                              ┌────────────────────────────────────┘
                              ▼
                    ┌─────────────────┐
                    │  Docker Build   │
                    │  & Push         │
                    │  (main + tags)  │
                    └─────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │  Release Tag    │
                    │  (auto on merge)│
                    └─────────────────┘
```

---

## 🧩 Extensibilidade

Para adicionar um novo módulo:

1. **Criar Service** em `net.ddns.adambravo79.tmill.service`
2. **Criar Controller/Handler** em `net.ddns.adambravo79.tmill.controller` ou `telegram.handler`
3. **Adicionar config** em `application.properties`
4. **Adicionar testes** em `src/test/java/...`
5. **Adicionar endpoint admin** em `AdminController` (opcional)

Exemplo: Módulo de "Quotes de Filmes" → `QuoteService` + `QuoteCommandHandler` + `quotes.json`

---

## 📚 Referências

- [Spring Boot 3 Docs](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Java 21 Virtual Threads](https://openjdk.org/jeps/444)
- [Ksilisk Telegram Bot](https://github.com/ksilisk/telegram-bot)
- [TMDB API v3](https://developer.themoviedb.org/reference/intro/getting-started)
- [Groq API](https://console.groq.com/docs)
- [Conventional Commits](https://www.conventionalcommits.org/)

---

_Documento mantido por: André S. Nascimento_
_Última atualização: 2026-07-21_
