# 🎬 T-1000 Bot

> **Versão atual: v2.5.5** | Branch: `main` | Último build: 2026-09-30
>
> _Evolução do T1000 Bot — de bot simples para plataforma de entretenimento inteligente, assíncrona e observável._

Bot para Telegram que **transcreve áudios** com IA (Whisper + refinamento OpenAI), **busca filmes** no TMDB, **gera resumos automáticos** das conversas do grupo e **publica podcasts semanais** com TTS.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.1-brightgreen)
![Build](https://img.shields.io/badge/build-passing-success)
![Tests](https://img.shields.io/badge/tests-passing-success)
![Coverage](https://img.shields.io/badge/coverage-90%25-yellowgreen)
![License](https://img.shields.io/badge/license-MIT-blue)
![Status](https://img.shields.io/badge/status-v2.5.5-blueviolet)
![CI](https://github.com/andre-s-nascimento/t1000-bot/actions/workflows/ci.yml/badge.svg)

---

## 🚀 Sobre o Projeto

O **T1000-Bot** é uma plataforma de entretenimento para Telegram que combina:

- 🎥 Busca de filmes via API do TMDB (com desambiguação, elenco, streaming e easter eggs)
- 🎙️ Transcrição de áudio com IA via Groq (Whisper + refinamento Llama)
- 📊 Digest diário de conversas com personas cinematográficas (T-1000, Bicentenário, Arquiteto)
- 🎧 Podcast semanal ("Silas Cast") com roteiro gerado por LLM e síntese via Azure TTS
- 🎂 Lembretes de aniversário com GIF temático
- ⚽ Acompanhamento da Copa do Mundo 2026 (jogos, resultados e lembretes)
- 📺 Notificações de lançamentos de streaming (TMDB + Watchmode)
- 💡 Registro de ideias dos usuários (MongoDB)
- ⚙️ Backend moderno com **Spring Boot 4.1.1**, **Java 21 (Virtual Threads)**, **Kafka**, **PostgreSQL**, **MongoDB** e **SQLite legado**

---

## 🚀 Funcionalidades

### 🎬 Busca de filmes

- `t1000 buscar <nome>`
- Retorna pôster, sinopse, elenco, diretor, nota, onde assistir (streaming) e easter eggs.
- Desambiguação automática quando há vários resultados (botões inline).
- Cache de detalhes por ID via `@Cacheable`.

### 🎙️ Transcrição de áudio

- Mensagem de voz ou arquivo de áudio.
- **Privado**: recebe a transcrição bruta e a versão refinada (pontuação corrigida, vícios de fala removidos).
- **Grupo**: botões para escolher bruta/refinada; o resultado é enviado no privado do usuário.
- Pipeline assíncrono via **Kafka** (`t1000.audio.received` → worker → `t1000.audio.processed`).
- Cache de 24h evita reprocessar o mesmo áudio.
- Fallback automático para transcrição bruta quando o refinamento falha.

### 💡 Anotar ideias

- `t1000 anotar ideia <texto>`
- A ideia é salva em arquivo de log **e no MongoDB** (`user_ideas`), e reenviada para o administrador do bot.

### 📊 Resumo diário das conversas (Digest)

- Automático às **08:30** (madrugada/manhã) e **20:30** (dia).
- Coleta mensagens e transcrições do banco (PostgreSQL) e gera um resumo narrativo via Groq Llama.
- Múltiplas personas: **T-1000**, **Bicentenário** e **Arquiteto da Matrix**.
- Trunca prompts longos e sanitiza HTML para evitar erros de parse no Telegram.
- Pode ser acionado manualmente via endpoints administrativos (`/admin/test-morning-digest`, `/admin/custom-digest`).

### 🎧 Podcast semanal (Silas Cast)

- Toda **sexta-feira às 12:00** (BRT), gera e publica um podcast semanal.
- Roteiro gerado por LLM a partir das transcrições da semana.
- Síntese via **Azure TTS** com compressão automática (FFmpeg) se o áudio ultrapassar 5 MB.
- Fallback de envio como texto se o Telegram rejeitar o áudio.

### 🗓️ Lembrete semanal

- Toda **quarta-feira às 16h** (BRT) envia uma mensagem nos grupos autorizados.

### 🎂 Aniversários

- `t1000 anotar aniversário DD/MM` — registra o aniversário do usuário.
- Todo dia à **00:01** (BRT) envia parabéns no privado e nos grupos autorizados, com GIF temático e citação da Marilyn Monroe.

### ⚽ Copa do Mundo 2026

- `t1000 jogos` / `t1000 copa` — jogos do dia.
- `t1000 resultados [data]` — resultados de ontem/hoje/data específica.
- Envio automático ao meio-dia e à noite.
- Lembrete 30 minutos antes de cada jogo.
- Atualização automática do JSON da Copa (opcional, via feature flag).

### 📺 Lançamentos de streaming

- Verificação a cada 6 horas de novos lançamentos (filmes e séries).
- Notificação com pôster, sinopse, nota e onde assistir (via Watchmode).
- Giro semanal consolidado às quintas-feiras.

### 🤖 Respostas automáticas (Auto-Response)

O bot pode responder automaticamente a palavras‑chave ou frases específicas, com suporte a:

- **Gatilhos configuráveis** via arquivo `auto-responses.json` (montado como volume).
- **Restrição de horário** (ex.: "bom dia" só entre 06h e 12h).
- **Menção ao usuário** que acionou a resposta.
- **Animações (GIFs)** com legenda.
- **Respostas por usuário** (`userOverrides`).
- **Limite de uma resposta por dia** para gatilhos configuráveis (`once-per-day-triggers`).

**Exemplo de resposta:**

> `<a href="tg://user?id=123456">André Nascimento</a>, Olá! Como posso ajudar? 😊`

Para adicionar novos gatilhos, edite o arquivo `auto-responses.json` e recarregue as regras com:

```bash
curl -X POST http://localhost:8082/admin/reload-auto-responses
```

### 🎛️ Feature Flags

Todas as funcionalidades críticas podem ser ligadas/desligadas em runtime via painel admin ou API:

| Flag | Descrição | Read-only |
|------|-----------|-----------|
| `worldcup.enabled` | Envio de jogos da Copa | ❌ |
| `transcription.enabled` | Transcrição de áudio | ❌ |
| `auto.response.enabled` | Respostas automáticas | ❌ |
| `digest.enabled` | Digest diário | ❌ |
| `worldcup.update.enabled` | Atualização automática do JSON da Copa | ❌ |
| `migration.enabled` | Migração SQLite → Postgres/Mongo | ✅ |

### 🔧 Administração (endpoints HTTP)

**Painel Web (Thymeleaf):** `/admin-web` (autenticado via Google OAuth2)

**API REST:**
- `POST /admin/reload-easter-eggs` – recarrega frases especiais.
- `POST /admin/reload-auto-responses` – recarrega a lista de auto-respostas.
- `POST /admin/reload-worldcup` – recarrega dados da Copa.
- `GET /admin/cache-stats` – estatísticas do cache de transcrições.
- `GET /admin/custom-digest?start=yyyy-MM-dd&end=yyyy-MM-dd&chatId=xxx` – gera resumo sob demanda.
- `GET /admin/features` – lista feature flags.
- `POST /admin/features/{key}?enabled=true` – altera feature flag.
- `POST /admin/migrate-sqlite?dryRun=true` – migra dados do SQLite legado.
- `GET /admin/config-files` – inspeciona arquivos de configuração.

---

## 🏗️ Tecnologias

### Core
- **Java 21** com virtual threads
- **Spring Boot 4.1.1**
- **Spring Security 7.1.1** (OAuth2 / Google)
- **Gradle** com **Spotless** (google-java-format AOSP) e **JaCoCo**

### Mensageria e Persistência
- **Apache Kafka** (Spring Kafka)
- **PostgreSQL** + **Flyway** (dados estruturados)
- **MongoDB** (logs, conversas, ideias)
- **SQLite** (legado — em processo de migração)

### IA e APIs Externas
- **Groq Cloud** (Whisper `whisper-large-v3`, Llama `llama-3.1-8b-instant`, `openai/gpt-oss-120b`)
- **TMDB API** (filmes, séries, provedores de streaming)
- **Watchmode API** (streaming nos EUA/BR)
- **Azure Speech Services** (TTS `pt-BR-AntonioNeural`)
- **FFmpeg** (conversão e compressão de áudio)

### Integrações
- **Telegram Bots** (ksilisk starter + Pengrad)
- **Docker + GitHub Actions** (CI, build, release)
- **SonarCloud** (análise estática)
- **Micrometer + Prometheus + Actuator** (observabilidade)
- **Logstash Logback Encoder** (logs estruturados)

---

## 🧩 Estrutura de serviços (modular)

O projeto é organizado em serviços especializados. Veja abaixo cada um e como podem ser reutilizados ou estendidos.

| Serviço | Responsabilidade | Como modularizar |
|---------|------------------|------------------|
| `MovieService` + `TmdbClient` | Busca filmes no TMDB e formata resposta. | Pode ser extraído como biblioteca independente. Cache `@Cacheable` para detalhes. |
| `AudioPipelineService` + `AudioService` + `GroqClient` | Pipeline completo de transcrição (conversão → Whisper → refinamento). | Autocontido. O `GroqClient` é reutilizável para qualquer chamada LLM. |
| `AudioWorkerService` | Consome `t1000.audio.received` do Kafka, processa e publica em `t1000.audio.processed`. | Escalável horizontalmente (múltiplas instâncias worker). |
| `FileTranscriptionCacheService` | Cache em memória de transcrições por `fileId`. | Pode ser generalizado para qualquer cache chave-valor com TTL. |
| `DailyDigestService` | Geração de resumos de conversas via Groq Llama. | Configurável via `DigestPromptFactory` e `DigestPersona` (T-1000, Bicentenário, Arquiteto). |
| `EasterEggService` | Carrega frases de um JSON e as associa a IDs de filme. | Pode ser transformado em serviço genérico de "conteúdo extra". |
| `PodcastScriptService` + `PodcastPublisherService` | Geração de roteiro + síntese TTS + envio. | Pipeline reutilizável para qualquer podcast automatizado. |
| `DailyReleasesService` + `WeeklyReleasesService` | Notificações de lançamentos de streaming. | Usa `TmdbClient` + `WatchmodeClient` + cache Caffeine. |
| `BirthdayService` + `BirthdayScheduler` | Registro e envio de parabéns. | Persistência via `BirthdayRepository` (JdbcTemplate). |
| `WorldCupSchedulerService` + `StaticWorldCupService` + `WorldCupUpdaterService` | Jogos, resultados, lembretes e atualização. | Dados em JSON, com tradução PT-BR e bandeiras. |
| `AutoResponseService` | Respostas automáticas configuráveis via JSON. | Suporta time-range, user-overrides e once-per-day. |
| `FeatureFlagService` + `FeatureFlagAdminService` | Feature flags em runtime com persistência opcional. | Mapa `key → FeatureFlagState` + persistência em disco. |
| `MigrationService` | Migração do SQLite legado para Postgres/Mongo. | Suporta `dryRun` e relatório por tabela. |
| `TelegramFacade` / `TelegramSafeExecutor` | Camada de abstração da API do Telegram. | Pode ser usada como biblioteca comum para qualquer bot Telegram. |
| `BotAnalyticsService` | Persistência de conversas, interações e ideias no MongoDB. | Três repositórios: `BotConversationRepository`, `InteractionLogRepository`, `UserIdeaRepository`. |

### 💡 Como criar um novo serviço (exemplo: módulo de enquete)

1. Crie uma nova classe `PollService` com a lógica de criar/responder enquetes.
2. Injete `TelegramFacade` para enviar mensagens e botões.
3. No `TelegramController`, adicione um handler para comandos como `/poll`.
4. Registre uma feature flag (`poll.enabled`) no `FeatureFlagAdminService`.
5. Pronto: o novo serviço fica isolado, testável e controlável via feature flag.

---

## 🏗️ Arquitetura assíncrona

```
┌─────────────┐    ┌──────────────────┐    ┌──────────────────┐
│  Telegram   │───▶│  AudioHandler    │───▶│  Kafka           │
│  (Update)   │    │  (publica evento)│    │  audio.received  │
└─────────────┘    └──────────────────┘    └────────┬─────────┘
                                                     │
                                                     ▼
                                          ┌──────────────────────┐
                                          │  AudioWorkerService  │
                                          │  (baixa + processa)  │
                                          └──────────┬───────────┘
                                                     │
                                                     ▼
                                          ┌──────────────────────┐
                                          │  Kafka               │
                                          │  audio.processed     │
                                          └──────────┬───────────┘
                                                     │
                                                     ▼
                                          ┌──────────────────────┐
                                          │  AudioHandler        │
                                          │  (envia botões)      │
                                          └──────────────────────┘
```

---

## 🐳 Docker

A imagem Docker é construída e publicada automaticamente no Docker Hub (`andresnascimento/t1000-bot`) via GitHub Actions quando há push na `main` ou criação de tag `v*`.

```bash
docker run \
  -e TELEGRAM_BOT_TOKEN=... \
  -e GROQ_API_KEY=... \
  -e TMDB_READ_TOKEN=... \
  -e WATCHMODE_API_KEY=... \
  -e AZURE_SPEECH_KEY=... \
  -e AZURE_SPEECH_REGION=brazilsouth \
  -e POSTGRES_DATABASE_URL=... \
  -e MONGODB_URI=... \
  -e KAFKA_BOOTSTRAP_SERVERS=... \
  andresnascimento/t1000-bot
```

## ⚙️ Variáveis de ambiente obrigatórias

| Variável | Descrição |
|----------|-----------|
| `TELEGRAM_BOT_TOKEN` | Token do bot (BotFather). |
| `GROQ_API_KEY` | Chave da API Groq. |
| `TMDB_READ_TOKEN` | Token de leitura da API TMDB (v3 auth). |
| `TELEGRAM_OWNER_ID` | Seu ID de usuário do Telegram (para receber ideias). |
| `WATCHMODE_API_KEY` | Chave da API Watchmode (streaming). |
| `AZURE_SPEECH_KEY` | Chave do Azure Speech (TTS). |
| `AZURE_SPEECH_REGION` | Região do Azure Speech (ex.: `brazilsouth`). |
| `PODCAST_PUBLISH_CHAT_ID` | Chat ID onde o podcast semanal será publicado. |
| `POSTGRES_DATABASE_URL` | URL JDBC do PostgreSQL. |
| `POSTGRES_DATABASE_USER` | Usuário do PostgreSQL. |
| `POSTGRES_DATABASE_PASSWORD` | Senha do PostgreSQL. |
| `MONGODB_URI` | URI do MongoDB. |
| `KAFKA_BOOTSTRAP_SERVERS` | Endereço do broker Kafka. |

Opcionais:

- `BOT_ALLOWED_CHATS` – IDs de grupos/canais (negativos) separados por vírgula.
- `DIGEST_ALLOWED_CHATS` – IDs onde os resumos diários serão enviados.
- `EASTER_EGG_FILE` – Caminho para arquivo JSON de easter eggs.
- `AUTO_RESPONSE_FILE` – Caminho para arquivo JSON de auto-respostas.
- `AUTO_RESPONSE_ONCE_PER_DAY_TRIGGERS` – Gatilhos que só respondem uma vez por dia.
- `WORLDCUP_DATA_FILE` – Caminho para o JSON da Copa.
- `WORLDCUP_UPDATE_ENABLED` – Habilita atualização automática do JSON da Copa.
- `ADMIN_ALLOWED_EMAILS` – E-mails autorizados a acessar `/admin-web` (Google OAuth2).
- `ADMIN_ALLOWED_IPS` – IPs autorizados a acessar `/admin/**`.
- `ADMIN_GOOGLE_CLIENT_ID` / `ADMIN_GOOGLE_CLIENT_SECRET` – Credenciais OAuth2.
- `ADMIN_SECURITY_DISABLED` – Desabilita Spring Security (apenas dev; bloqueado em prod).
- `TRANSCRIPTION_ENABLED` – `true`/`false`.
- `CACHE_TRANSCRIPTION_TTL_SECONDS` – TTL do cache de transcrições (padrão: 86400).
- `MIGRATION_SQLITE_PATH` – Caminho do SQLite legado para migração.

---

## 🔧 Desenvolvimento

```bash
# Clonar
git clone https://github.com/seu-usuario/t1000-bot.git
cd t1000-bot

# Build (pula testes se não houver)
./gradlew build -x test

# Rodar apenas testes unitários (sem Testcontainers)
./gradlew unitTest

# Aplicar formatação (Spotless)
./gradlew spotlessApply

# Verificar cobertura
./gradlew jacocoTestReport

# Executar
export TELEGRAM_BOT_TOKEN=...
./gradlew bootRun
```

### 🧪 Testes

O projeto usa uma estratégia de testes em camadas:

- **Testes unitários** (mockito + assertj) — executados com `./gradlew unitTest` ou `./gradlew test`.
- **Testes de integração** (Testcontainers com PostgreSQL, MongoDB e Kafka) — executados como parte de `./gradlew test`.
- **Cobertura mínima**: 90% global, 70% por classe (excluindo `config`, `client`, `dto`, `model`, `document`).
- **Análise estática**: SonarCloud integrado ao CI.

### 🎨 Formatação

O projeto usa **Spotless** com **google-java-format AOSP**:

```bash
./gradlew spotlessApply   # aplica formatação
./gradlew spotlessCheck   # verifica formatação (executado no CI)
```

---

## 🧠 Arquitetura

```
Telegram → Interceptors → Handlers → Services → Clients → APIs externas
                                 ↓
                            Kafka Topics
                                 ↓
                         Workers assíncronos
                                 ↓
                    PostgreSQL + MongoDB
```

📌 Documentação detalhada da arquitetura está disponível em:
[🏗️ Arquitetura de Pacotes](./dev-guide/arquitetura.md)

## 📚 Documentação de Desenvolvimento

Toda a documentação técnica e guias estão centralizados em:
[Dev Guide](./dev-guide/README.md)

Inclui:

- Guia de Logging Estruturado
- Guia de Arquitetura de Pacotes
- Guia de Rebase, PRs e Proteção da Branch Develop
- Configuração do Spotless
- Reversão Segura na Branch Develop

## 📦 Estrutura

```bash
src/main/java/net/ddns/adambravo79/tmill
├── cache
├── client
├── config
├── constant
├── controller
│   └── kafka
├── document
├── dto
├── exception
├── model
├── prompt
├── repository
├── service
│   ├── cache
│   ├── feature
│   └── kafka
├── telegram
│   ├── core
│   ├── exception
│   ├── handler
│   └── util
└── util
```

---

## Explicação detalhada dos serviços e modularização

### 🧩 `GroqClient`

- **O que faz**: comunicação com a API Groq (transcrição `whisper-large-v3`, chat completions com Llama).
- **Por que é modular**: não conhece nada sobre Telegram ou lógica de negócio. Apenas recebe arquivos ou textos e retorna respostas.
- **Recursos avançados**:
  - Retry com backoff exponencial (`@Retryable`) para `IOException` e `HttpClientErrorException`.
  - Tratamento explícito de `finish_reason=length` (truncamento) e `content_filter`.
  - `reasoning_effort=low` para modelos de raciocínio (gpt-oss-*).
  - Fallback automático para texto bruto quando o refinamento é vazio ou truncado (>30% de redução).

### 🧩 `AudioPipelineService`

- **Responsabilidade**: orquestra a conversão (`AudioService`), transcrição e refinamento, além de salvar no cache e no banco.
- **Modularização**: se você quiser um bot que apenas transcreva sem refinar ou sem salvar em banco, pode criar uma subclasse ou um serviço similar que ignore essas etapas.
- **Retry específico**: `retryRefinamento` trata `429 Too Many Requests` do Groq, extraindo o tempo de espera da mensagem de erro.

### 🧩 `AudioWorkerService`

- **O que faz**: consome eventos de `t1000.audio.received`, baixa o arquivo do Telegram, processa via `AudioPipelineService` e publica o resultado em `t1000.audio.processed`.
- **Escalabilidade**: pode rodar múltiplas instâncias (consumer group `t1000-workers-v1`).
- **Métricas**: `audio_worker_sucesso`, `audio_worker_erro`, `audio_worker_arquivo_nao_encontrado`, `audio_worker_resposta_publicada`, `audio_worker_resposta_falha`.

### 🧩 `DailyDigestService`

- **O que faz**: consulta o Postgres, monta um prompt, chama `GroqClient.gerarResumoDigest` e envia via `TelegramFacade`.
- **Por que é modular**: a lógica de coleta de mensagens está desacoplada da geração do resumo.
- **Extensão**: já suporta múltiplas personas via enum `DigestPersona` e `DigestPromptFactory`. Basta adicionar um novo enum e o respectivo prompt.
- **Robustez**: trunca prompts longos, sanitiza HTML (preservando tags permitidas) e tem fallback para texto puro quando o parse HTML falha.

### 🧩 `PodcastPublisherService` + `PodcastScriptService`

- **O que faz**: gera roteiro semanal a partir das transcrições, sintetiza via Azure TTS, comprime se >5MB e publica no Telegram.
- **Fallback**: se o TTS falhar ou o áudio for rejeitado, envia o roteiro como texto (dividido em partes de 4000 chars).
- **Nome do arquivo**: `SilasCast-Semana-XX-do-Mes-XX-do-Ano-YYYY.mp3`.

### 🧩 `AutoResponseService`

- **O que faz**: consulta um JSON preparado, verifica se preenche os requisitos temporais e retorna uma resposta ao usuário baseado em palavra-chave ou frase.
- **Recursos**:
  - `triggers` (lista de palavras-chave)
  - `timeRange` (start/end em `HH:mm`)
  - `userOverrides` (resposta específica por userId)
  - `once-per-day-triggers` (só responde uma vez por dia por usuário)
- **Ordenação**: triggers mais longos têm prioridade (evita conflito entre "bom dia" e "bom dia família").

### 🧩 `FeatureFlagService` + `FeatureFlagAdminService`

- **O que faz**: gerencia feature flags em runtime com persistência opcional em disco.
- **Modelo híbrido**: estado em memória (`ConcurrentHashMap`) + persistência em `./config/feature-flags.json`.
- **Ordem de precedência no boot**: defaults do `application.properties` → valores do JSON (exceto read-only).
- **Flags read-only**: `migration.enabled` (requer restart).

### 🧩 `MigrationService`

- **O que faz**: migra dados do SQLite legado para PostgreSQL (dados estruturados) e MongoDB (documentos).
- **Dry-run**: suporta `dryRun=true`, que faz rollback no Postgres e não toca no Mongo.
- **Relatório**: retorna `MigrationResult` com contadores por tabela, erros e duração.
- **Idempotência**: **não é idempotente** — rode uma única vez em ambiente limpo.

### 🧩 `TelegramFacade`

- **Camada de abstração da API do Telegram**. Todos os métodos que enviam mensagens ou gerenciam arquivos passam por ela.
- **Vantagem**: se um dia você migrar para webhook em vez de long polling, ou trocar a biblioteca Telegram, só precisa alterar essa classe.
- **Timeouts configuráveis**: `connect`, `read` e `write` via `application.properties`.

### 🧩 Serviços de log e persistência

- **`BotAnalyticsService`**: persiste conversas, interações e ideias no MongoDB via três repositórios.
- **`MessageStoreService`** e **`TranscriptStoreService`**: persistência no PostgreSQL (dados estruturados do digest).
- **`IdeasLoggerService`** e **`UserInteractionLogger`**: logs em arquivo para auditoria.

### 🧩 `EasterEggService`

- **Muito simples e bem isolado**. Carrega um JSON e retorna `Optional<String>`.
- **Modularização**: poderia ser transformado em um serviço genérico `ContentEnricher`.

### 🧩 `WorldCupSchedulerService` + `StaticWorldCupService` + `WorldCupUpdaterService`

- **Scheduler**: envia jogos ao meio-dia e à noite, lembretes 30min antes, resultados sob demanda.
- **Static service**: carrega o JSON da Copa e agrupa jogos por data.
- **Updater**: baixa JSON atualizado de uma URL configurável (opcional, via feature flag).
- **Tradução**: mapas PT-BR para nomes de times e bandeiras em emoji.

### 🧩 `BirthdayService` + `BirthdayScheduler`

- **Scheduler**: roda todo dia à 00:01 BRT e dispara parabéns.
- **Service**: envia no privado e nos grupos autorizados; se o usuário não iniciou o bot, loga warn e segue.
- **Idempotência**: `last_sent_year` evita duplicação anual.

---

## 📄 Licença

MIT

## 👨‍💻 Autor

Andre Nascimento

---

## 📋 Changelog resumido (2.1.x → 2.5.5)

### 🆕 Novas funcionalidades
- ✅ Pipeline assíncrono com **Kafka** para processamento de áudio
- ✅ **Podcast semanal** ("Silas Cast") com Azure TTS
- ✅ **Digest com múltiplas personas** (T-1000, Bicentenário, Arquiteto)
- ✅ **Feature flags** em runtime com persistência
- ✅ **Notificações de lançamentos** de streaming (TMDB + Watchmode)
- ✅ **Copa do Mundo 2026** (jogos, resultados, lembretes)
- ✅ **Aniversários** com GIF temático
- ✅ **Migração** de SQLite → PostgreSQL/MongoDB
- ✅ **Painel admin** em Thymeleaf com Google OAuth2
- ✅ **Auto-response** com time-range e user-overrides
- ✅ **Observabilidade** com Micrometer + Prometheus + Actuator

### 🔄 Mudanças arquiteturais
- 🔀 Migração de **SQLite** para **PostgreSQL** (dados estruturados) + **MongoDB** (documentos)
- 🔀 Processamento de áudio movido para **workers Kafka** (escalável horizontalmente)
- 🔀 **Virtual Threads** habilitados (`spring.threads.virtual.enabled=true`)
- 🔀 **Spring Boot 4.1.1** + **Spring Security 7.1.1** + **Java 21**
- 🔀 **Spotless** + **JaCoCo** + **SonarCloud** no CI

### 🧪 Testes
- ✅ Cobertura mínima de **90%** global, **70%** por classe
- ✅ **Testcontainers** para PostgreSQL, MongoDB e Kafka
- ✅ **MockWebServer** para testes de clientes HTTP
- ✅ Testes parametrizados (`@ParameterizedTest`) para casos de borda
