# 🎬 T-1000 Bot

> **Versão atual: v2.6.3** | Branch: `main` | Último build: 2026-10-26
>
> _Evolução do T1000 Bot — de bot simples para plataforma de entretenimento inteligente, assíncrona e observável._

Bot para Telegram que **transcreve áudios** com IA (Whisper + refinamento OpenAI), **busca filmes** no TMDB, **gera resumos automáticos** das conversas do grupo e **publica podcasts semanais** com TTS.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.1-brightgreen)
![Build](https://img.shields.io/badge/build-passing-success)
![Tests](https://img.shields.io/badge/tests-passing-success)
![Coverage](https://img.shields.io/badge/coverage-90%25-yellowgreen)
![License](https://img.shields.io/badge/license-MIT-blue)
![Status](https://img.shields.io/badge/status-v2.6.3-blueviolet)
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
- 🔨 **Moderação de grupos** (Mute/Unmute via comando, painel ou API)
- 🎛️ **23 feature flags runtime** com persistência opcional em disco
- ⚙️ Backend moderno com **Spring Boot 4.1.1**, **Java 21 (Virtual Threads)**, **Kafka**, **PostgreSQL**, **MongoDB** e **SQLite legado**

---

## 🚀 Funcionalidades

### 🎬 Busca de filmes

- `t1000 buscar <nome>`
- Retorna pôster, sinopse, elenco, diretor, nota, onde assistir (streaming) e easter eggs.
- Desambiguação automática quando há vários resultados (botões inline).
- Cache de detalhes por ID via `CacheManager` programático (controlado pela flag `tmdb.cache.enabled`).

### 🎙️ Transcrição de áudio

- Mensagem de voz ou arquivo de áudio.
- **Privado**: recebe a transcrição bruta e a versão refinada (pontuação corrigida, vícios de fala removidos).
- **Grupo**: botões para escolher bruta/refinada; o resultado é enviado no privado do usuário.
- Pipeline assíncrono via **Kafka** (`t1000.audio.received` → worker → `t1000.audio.processed`).
- Cache de 24h evita reprocessar o mesmo áudio.
- Fallback automático para transcrição bruta quando o refinamento falha.

### 🔨 Moderação de grupos

- **Comando no Chat**: Responda a mensagem de um usuário com `/mute` (permanente) ou `/mute 60` (60 minutos). Use `/unmute` para reverter.
- **Painel Admin**: Nova aba "Moderação" para aplicar restrições por ID de usuário e chat.
- **API REST**: Endpoints `/admin/mute` e `/admin/unmute` para integração via `curl`.
- **Requisito**: O bot precisa ser Administrador do grupo e ter a permissão "Restringir membros".

### 💡 Anotar ideias

- `t1000 anotar ideia <texto>`
- A ideia é salva em arquivo de log **e no MongoDB** (`user_ideas`), e reenviada para o administrador do bot.

### 📊 Resumo diário das conversas (Digest)

- Automático às **08:30** (madrugada/manhã) e **20:30** (dia).
- Coleta mensagens e transcrições do banco (PostgreSQL) e gera um resumo narrativo via Groq Llama.
- Múltiplas personas: **T-1000**, **Bicentenário** e **Arquiteto da Matrix**.
- Trunca prompts longos e sanitiza HTML para evitar erros de parse no Telegram.
- Pode ser acionado manualmente via endpoints administrativos (`/admin/test-morning-digest`, `/admin/custom-digest` — este último controlado pela flag `ai.digest.custom-period.enabled`).

### 🎧 Podcast semanal (Silas Cast)

- Toda **sexta-feira às 12:00** (BRT), gera e publica um podcast semanal.
- Roteiro gerado por LLM a partir das transcrições da semana.
- Síntese via **Azure TTS** com compressão automática (FFmpeg) se o áudio ultrapassar 5 MB.
- Fallback de envio como texto se o Telegram rejeitar o áudio (controlado por `podcast.fallback-text.enabled`).
- **Reenvio por sexta-feira**: endpoint `POST /admin/podcast/resend` permite republicar a edição de uma semana específica, respeitando o intervalo exato de **sexta 12:00 → sexta 11:59:59**.

### 🗓️ Lembrete semanal

- Toda **quarta-feira às 16h** (BRT) envia uma mensagem nos grupos autorizados.
- Controlado pela flag `weekly.reminder.enabled`.

### 🎂 Aniversários

- `t1000 anotar aniversário DD/MM` — registra o aniversário do usuário.
- Todo dia à **00:01** (BRT) envia parabéns no privado e nos grupos autorizados, com GIF temático e citação da Marilyn Monroe.
- Duas flags: `birthday.enabled` (feature geral) e `birthday.group-notification.enabled` (envio nos grupos).

### ⚽ Copa do Mundo 2026

- `t1000 jogos` / `t1000 copa` — jogos do dia.
- `t1000 resultados [data]` — resultados de ontem/hoje/data específica.
- Envio automático ao meio-dia e à noite.
- Lembrete 30 minutos antes de cada jogo (controlado por `worldcup.reminders30min.enabled`).
- Atualização automática do JSON da Copa (opcional, via `worldcup.update.enabled`).

### 📺 Lançamentos de streaming

- Verificação a cada 6 horas de novos lançamentos (filmes e séries).
- Notificação com pôster, sinopse, nota e onde assistir (via Watchmode).
- Giro semanal consolidado às quintas-feiras (controlado por `weekly.digest.enabled`).

### 🤖 Respostas automáticas (Auto-Response)

O bot pode responder automaticamente a palavras‑chave ou frases específicas, com suporte a:

- **Gatilhos configuráveis** via arquivo `auto-responses.json` (montado como volume).
- **Restrição de horário** (ex.: "bom dia" só entre 06h e 12h).
- **Menção ao usuário** que acionou a resposta.
- **Múltiplas animações por regra** (sorteio aleatório no disparo, com pares texto+animação).
- **Respostas por usuário** (`userOverrides`).
- **Limite de uma resposta por dia** para gatilhos configuráveis (`once-per-day-triggers`).

#### 🎲 Múltiplas animações (Sorteio)

A partir da versão **2.6.1**, cada regra pode ter uma lista de pares `(texto, animação)`. A cada disparo, o bot sorteia um par inteiro. Formatos suportados (com precedência):

1. `responses`: lista de pares `{"text": "...", "animation": "..."}` (recomendado).
2. `animations`: lista de animações (texto fixo em `response`).
3. `animation`: animação única (formato legado).

```json
{
  "triggers": ["bom dia"],
  "responses": [
    {
      "text": "Bom dia, Vietnã!",
      "animation": "https://exemplo.com/vietnan.gif"
    },
    {
      "text": "Acorda, campeão!",
      "animation": "https://exemplo.com/campeao.gif"
    }
  ]
}
```

**Exemplo de resposta:**

> `<a href="tg://user?id=123456">André Nascimento</a>, Olá! Como posso ajudar? 😊`

Para adicionar novos gatilhos, edite o arquivo `auto-responses.json` e recarregue as regras com:

```bash
curl -X POST http://localhost:8082/admin/reload-auto-responses
```

### 🎛️ Feature Flags (23 no total)

Todas as funcionalidades críticas podem ser ligadas/desligadas em runtime via painel admin ou API.

📖 **Documentação completa**: [Dev Guide → Feature Flags](./docs/dev-guide/feature-flags.md)

#### Alta prioridade — Onda 3.5.1

| Flag                              | Default | Read-only | Descrição                                |
| --------------------------------- | ------- | --------- | ---------------------------------------- |
| `podcast.enabled`                 | ✅      | ❌        | Publicação automática do podcast semanal |
| `weekly.reminder.enabled`         | ✅      | ❌        | Lembrete de quarta-feira                 |
| `daily.releases.enabled`          | ✅      | ❌        | Notificação de lançamentos (6 em 6h)     |
| `worldcup.reminders30min.enabled` | ✅      | ❌        | Aviso 30 min antes de cada jogo          |
| `easter.eggs.enabled`             | ✅      | ❌        | Easter eggs nas buscas de filme          |
| `admin.features.toggle-enabled`   | ✅      | ❌        | Habilita toggle via painel admin         |

#### Média prioridade — Onda 3.5.2

| Flag                                  | Default | Read-only | Descrição                                 |
| ------------------------------------- | ------- | --------- | ----------------------------------------- |
| `weekly.digest.enabled`               | ✅      | ❌        | Giro semanal dos streamings               |
| `birthday.enabled`                    | ✅      | ❌        | Parabéns automáticos de aniversário       |
| `birthday.group-notification.enabled` | ✅      | ❌        | Notificar aniversário nos grupos          |
| `podcast.fallback-text.enabled`       | ✅      | ❌        | Enviar roteiro como texto se áudio falhar |
| `ai.digest.custom-period.enabled`     | ✅      | ❌        | Custom digest com período arbitrário      |

#### Baixa prioridade — Onda 3.5.3

| Flag                              | Default | Read-only | Descrição                     |
| --------------------------------- | ------- | --------- | ----------------------------- |
| `metrics.prometheus.enabled`      | ✅      | ❌        | Expor `/actuator/prometheus`  |
| `admin.debug-cache.enabled`       | ✅      | ❌        | Endpoints de debug de cache   |
| `tmdb.cache.enabled`              | ✅      | ❌        | Cache de detalhes de filme    |
| `telegram.error-fallback.enabled` | ✅      | ❌        | Fallback genérico do Telegram |
| `kafka.audit.enabled`             | ❌      | ❌        | Audit trail no MongoDB        |

#### Já existentes

| Flag                       | Default | Read-only | Descrição                                         |
| -------------------------- | ------- | --------- | ------------------------------------------------- |
| `worldcup.enabled`         | ❌      | ❌        | Envio de jogos da Copa                            |
| `transcription.enabled`    | ✅      | ❌        | Transcrição de áudio                              |
| `auto.response.enabled`    | ✅      | ❌        | Respostas automáticas                             |
| `digest.enabled`           | ✅      | ❌        | Digest diário                                     |
| `worldcup.update.enabled`  | ❌      | ❌        | Auto-update do JSON da Copa                       |
| `prompts.external.enabled` | ❌      | ❌        | Prompts/personas externos via JSON                |
| `migration.enabled`        | ❌      | ✅        | Migração SQLite → Postgres/Mongo (requer restart) |

### 🔧 Administração (endpoints HTTP)

**Painel Web (Thymeleaf):** `/admin-web` (autenticado via Google OAuth2)

**API REST:**

| Endpoint                        | Método | Descrição                                                               |
| ------------------------------- | ------ | ----------------------------------------------------------------------- |
| `/admin/reload-easter-eggs`     | POST   | Recarrega frases especiais                                              |
| `/admin/reload-auto-responses`  | POST   | Recarrega auto-respostas                                                |
| `/admin/reload-worldcup`        | POST   | Recarrega dados da Copa                                                 |
| `/admin/reload-worldcup-teams`  | POST   | Recarrega traduções de times                                            |
| `/admin/reload-tmdb-aliases`    | POST   | Recarrega atalhos TMDB                                                  |
| `/admin/reload-telegram-errors` | POST   | Recarrega regras de erro do Telegram                                    |
| `/admin/reload-admin-config`    | POST   | Recarrega `admin-config.json`                                           |
| `/admin/reload-prompts`         | POST   | Recarrega prompts e personas                                            |
| `/admin/podcast/resend`         | POST   | Reenvia podcast por sexta-feira (`?friday=yyyy-MM-dd&chatId=xxx`)       |
| `/admin/custom-digest`          | GET    | Gera resumo sob demanda (`?start=yyyy-MM-dd&end=yyyy-MM-dd&chatId=xxx`) |
| `/admin/mute`                   | POST   | Silencia usuário (`?chatId=...&userId=...&durationMinutes=...`)         |
| `/admin/unmute`                 | POST   | Remove silenciamento (`?chatId=...&userId=...`)                         |
| `/admin/features`               | GET    | Lista feature flags                                                     |
| `/admin/features/{key}`         | POST   | Altera feature flag (`?enabled=true`)                                   |
| `/admin/cache-stats`            | GET    | Estatísticas do cache de transcrições                                   |
| `/admin/debug/cache/{fileId}`   | GET    | Inspeciona conteúdo do cache (flag `admin.debug-cache.enabled`)         |
| `/admin/debug-auto-response`    | GET    | Debug do sorteio de auto-response (`?message=...&time=HH:mm`)           |
| `/admin/migrate-sqlite`         | POST   | Migra dados do SQLite legado (`?dryRun=true`)                           |
| `/admin/config-files`           | GET    | Inspeciona arquivos de configuração                                     |
| `/admin/properties`             | GET    | Lista propriedades (tokens mascarados)                                  |

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

| Serviço                                                                         | Responsabilidade                                                                        | Como modularizar                                                                                  |
| ------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| `MovieService` + `TmdbClient`                                                   | Busca filmes no TMDB e formata resposta.                                                | Cache programático via `CacheManager` (flag `tmdb.cache.enabled`).                                |
| `AudioPipelineService` + `AudioService` + `GroqClient`                          | Pipeline completo de transcrição (conversão → Whisper → refinamento).                   | Autocontido. O `GroqClient` é reutilizável para qualquer chamada LLM.                             |
| `AudioWorkerService`                                                            | Consome `t1000.audio.received` do Kafka, processa e publica em `t1000.audio.processed`. | Escalável horizontalmente (múltiplas instâncias worker).                                          |
| `FileTranscriptionCacheService`                                                 | Cache em memória de transcrições por `fileId`.                                          | Pode ser generalizado para qualquer cache chave-valor com TTL.                                    |
| `DailyDigestService`                                                            | Geração de resumos de conversas via Groq Llama.                                         | Configurável via `DigestPromptFactory` e `DigestPersona` (T-1000, Bicentenário, Arquiteto).       |
| `EasterEggService`                                                              | Carrega frases de um JSON e as associa a IDs de filme.                                  | Controlado por `easter.eggs.enabled`.                                                             |
| `PodcastScriptService` + `PodcastPublisherService`                              | Geração de roteiro + síntese TTS + envio.                                               | Respeita intervalo `12:00 → 11:59:59`. Suporta reenvio por sexta-feira.                           |
| `DailyReleasesService` + `WeeklyReleasesService`                                | Notificações de lançamentos de streaming.                                               | Usa `TmdbClient` + `WatchmodeClient` + cache Caffeine.                                            |
| `BirthdayService` + `BirthdayScheduler`                                         | Registro e envio de parabéns.                                                           | Duas flags: geral + notificação em grupos.                                                        |
| `WorldCupSchedulerService` + `StaticWorldCupService` + `WorldCupUpdaterService` | Jogos, resultados, lembretes e atualização.                                             | Dados em JSON, com tradução PT-BR e bandeiras.                                                    |
| `AutoResponseService`                                                           | Respostas automáticas configuráveis via JSON.                                           | Suporta time-range, user-overrides, once-per-day e múltiplos pares texto+animação.                |
| `FeatureFlagService` + `FeatureFlagAdminService`                                | 23 feature flags em runtime com persistência opcional.                                  | Mapa `key → FeatureFlagState` + persistência em disco.                                            |
| `MigrationService`                                                              | Migração do SQLite legado para Postgres/Mongo.                                          | Suporta `dryRun` e relatório por tabela.                                                          |
| `TelegramFacade` / `TelegramSafeExecutor`                                       | Camada de abstração da API do Telegram.                                                 | Fallback configurável via `telegram.error-fallback.enabled`.                                      |
| `BotAnalyticsService`                                                           | Persistência de conversas, interações e ideias no MongoDB.                              | Três repositórios: `BotConversationRepository`, `InteractionLogRepository`, `UserIdeaRepository`. |
| `PrometheusFeatureFlagFilter`                                                   | Intercepta `/actuator/prometheus` e bloqueia se flag off.                               | `OncePerRequestFilter` — desacoplado do Actuator.                                                 |

### 💡 Como criar um novo serviço (exemplo: módulo de enquete)

1. Crie uma nova classe `PollService` com a lógica de criar/responder enquetes.
2. Injete `TelegramFacade` para enviar mensagens e botões.
3. No `TelegramController`, adicione um handler para comandos como `/poll`.
4. Registre uma feature flag (`poll.enabled`) no `FeatureFlagAdminService`.
5. Pronto: o novo serviço fica isolado, testável e controlável via feature flag.

📖 Documentação: [Dev Guide → Feature Flags](./docs/dev-guide/feature-flags.md)

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

| Variável                     | Descrição                                            |
| ---------------------------- | ---------------------------------------------------- |
| `TELEGRAM_BOT_TOKEN`         | Token do bot (BotFather).                            |
| `GROQ_API_KEY`               | Chave da API Groq.                                   |
| `TMDB_READ_TOKEN`            | Token de leitura da API TMDB (v3 auth).              |
| `TELEGRAM_OWNER_ID`          | Seu ID de usuário do Telegram (para receber ideias). |
| `WATCHMODE_API_KEY`          | Chave da API Watchmode (streaming).                  |
| `AZURE_SPEECH_KEY`           | Chave do Azure Speech (TTS).                         |
| `AZURE_SPEECH_REGION`        | Região do Azure Speech (ex.: `brazilsouth`).         |
| `PODCAST_PUBLISH_CHAT_ID`    | Chat ID onde o podcast semanal será publicado.       |
| `POSTGRES_DATABASE_URL`      | URL JDBC do PostgreSQL.                              |
| `POSTGRES_DATABASE_USER`     | Usuário do PostgreSQL.                               |
| `POSTGRES_DATABASE_PASSWORD` | Senha do PostgreSQL.                                 |
| `MONGODB_URI`                | URI do MongoDB.                                      |
| `KAFKA_BOOTSTRAP_SERVERS`    | Endereço do broker Kafka.                            |

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

**Feature flags via env var** (todas as 23 suportam override):

- `PODCAST_ENABLED`, `WEEKLY_REMINDER_ENABLED`, `DAILY_RELEASES_ENABLED`, etc.

📖 Ver [Dev Guide → Feature Flags](./docs/dev-guide/feature-flags.md) para a lista completa.

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

---

## 📚 Documentação de Desenvolvimento

Toda a documentação técnica está em [`docs/dev-guide/`](./docs/dev-guide/):

- 📖 [Dev Guide — Índice](./docs/dev-guide/README.md)
- 🎛️ [Feature Flags](./docs/dev-guide/feature-flags.md) — 23 flags runtime, catálogo, como adicionar e usar
- ⚙️ [Arquivos de Configuração](./docs/dev-guide/config-files.md) — todos os JSONs do projeto (audio, groq, refinement, admin, worldcup, tmdb-aliases, feature-flags, prompts)

---

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
│   ├── config
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
- **Intervalo preciso**: respeita exatamente `sexta 12:00 → sexta 11:59:59`, evitando capturar mensagens da edição seguinte.
- **Fallback**: se o TTS falhar ou o áudio for rejeitado, envia o roteiro como texto (dividido em partes de 4000 chars).
- **Reenvio**: o endpoint `POST /admin/podcast/resend` permite republicar qualquer sexta-feira anterior.
- **Nome do arquivo**: `SilasCast-Semana-XX-do-Mes-XX-do-Ano-YYYY.mp3`.

### 🧩 `AutoResponseService`

- **O que faz**: consulta um JSON preparado, verifica se preenche os requisitos temporais e retorna uma resposta ao usuário baseado em palavra-chave ou frase.
- **Recursos**:
  - `triggers` (lista de palavras-chave)
  - `timeRange` (start/end em `HH:mm`)
  - `userOverrides` (resposta específica por userId)
  - `once-per-day-triggers` (só responde uma vez por dia por usuário)
  - **Múltiplos pares texto+animação** sorteados aleatoriamente (formato `responses`)
- **Ordenação**: triggers mais longos têm prioridade (evita conflito entre "bom dia" e "bom dia família").

### 🧩 `FeatureFlagService` + `FeatureFlagAdminService`

- **O que faz**: gerencia 23 feature flags em runtime com persistência opcional em disco.
- **Modelo híbrido**: estado em memória (`ConcurrentHashMap`) + persistência em `./config/feature-flags.json`.
- **Ordem de precedência no boot**: defaults do `application.properties` → valores do JSON (exceto read-only).
- **Flags read-only**: `migration.enabled` (requer restart).
- **Guarda-chuva**: `admin.features.toggle-enabled` permite congelar o painel em cenários de auditoria.
- 📖 Ver [Dev Guide → Feature Flags](./docs/dev-guide/feature-flags.md).

### 🧩 `MigrationService`

- **O que faz**: migra dados do SQLite legado para PostgreSQL (dados estruturados) e MongoDB (documentos).
- **Dry-run**: suporta `dryRun=true`, que faz rollback no Postgres e não toca no Mongo.
- **Relatório**: retorna `MigrationResult` com contadores por tabela, erros e duração.
- **Idempotência**: **não é idempotente** — rode uma única vez em ambiente limpo.

### 🧩 `TelegramFacade` / `TelegramSafeExecutor`

- **Camada de abstração da API do Telegram**. Todos os métodos que enviam mensagens ou gerenciam arquivos passam por ela.
- **Vantagem**: se um dia você migrar para webhook em vez de long polling, ou trocar a biblioteca Telegram, só precisa alterar essa classe.
- **Timeouts configuráveis**: `connect`, `read` e `write` via `application.properties`.
- **Fallback configurável**: `telegram.error-fallback.enabled` permite silenciar erros do Telegram sem perder o log.

### 🧩 Serviços de log e persistência

- **`BotAnalyticsService`**: persiste conversas, interações e ideias no MongoDB via três repositórios.
- **`MessageStoreService`** e **`TranscriptStoreService`**: persistência no PostgreSQL (dados estruturados do digest).
- **`IdeasLoggerService`** e **`UserInteractionLogger`**: logs em arquivo para auditoria.

### 🧩 `EasterEggService`

- **Muito simples e bem isolado**. Carrega um JSON e retorna `Optional<String>`.
- **Controlado por flag**: `easter.eggs.enabled`.
- **Modularização**: poderia ser transformado em um serviço genérico `ContentEnricher`.

### 🧩 `WorldCupSchedulerService` + `StaticWorldCupService` + `WorldCupUpdaterService`

- **Scheduler**: envia jogos ao meio-dia e à noite, lembretes 30min antes (controlado por flag), resultados sob demanda.
- **Static service**: carrega o JSON da Copa e agrupa jogos por data.
- **Updater**: baixa JSON atualizado de uma URL configurável (opcional, via feature flag).
- **Tradução**: mapas PT-BR para nomes de times e bandeiras em emoji.

### 🧩 `BirthdayService` + `BirthdayScheduler`

- **Scheduler**: roda todo dia à 00:01 BRT e dispara parabéns.
- **Service**: envia no privado e nos grupos autorizados (se `birthday.group-notification.enabled`); se o usuário não iniciou o bot, loga warn e segue.
- **Idempotência**: `last_sent_year` evita duplicação anual.

### 🧩 `PrometheusFeatureFlagFilter`

- **O que faz**: intercepta `/actuator/prometheus` e retorna 404 se `metrics.prometheus.enabled` estiver desabilitada.
- **Por que existe**: o endpoint do Actuator é habilitado por `application.properties`, e não dá para desligá-lo em runtime sem um filtro.
- **Testável isoladamente**: `PrometheusFeatureFlagFilterTest` cobre todos os paths.

---

## 📄 Licença

MIT

## 👨‍💻 Autor

Andre Nascimento

---

## 📋 Changelog resumido

### 🆕 v2.6.3 — Moderação de Grupos e Comando de Ajuda

**Nova feature:**

- ✅ **Comando `/help`**: lista todos os comandos e funcionalidades do bot com formatação HTML.
- ✅ **Módulo de Moderação (`TelegramModerationService`)**: permite silenciar (mute) e dessilenciar (unmute) usuários em grupos.
- ✅ **Comandos no Telegram**: `/mute [minutos]` e `/unmute` (funcionam respondendo à mensagem do alvo).
- ✅ **Painel Web**: nova aba "🔨 Moderação" para aplicar as ações visualmente.
- ✅ **API REST**: endpoints `POST /admin/mute` e `POST /admin/unmute` para automações.

**Melhorias:**

- ✅ Ajuste no `MessageUpdateRuleHandler` para ignorar comandos com barra (`/`) e evitar conflito com o roteador do ksilisk.
- ✅ `TelegramFacade` expõe o método genérico `executar(BaseRequest)` para maior flexibilidade.

### 🆕 v2.6.2 — Hotfix: Ciclo de vida do Token de Transcrição

**Bug fix:**

- ✅ Correção no `AudioHandler`: o token gerado para os botões de transcrição (Bruto/Refinado) não é mais removido do cache imediatamente após o primeiro clique. Agora ele respeita o TTL de 7 dias, permitindo que o usuário clique no segundo botão sem receber "Token inválido ou expirado".

### 🆕 v2.6.1 — Múltiplas animações no Auto-Response

**Nova feature:**

- ✅ Regras de auto-response podem ter **múltiplos pares `(texto, animação)`** via campo `responses`.
- ✅ Sorteio aleatório do par inteiro a cada disparo, garantindo variação temática (ex.: "bom dia" com cenas de _Bom Dia, Vietnã_, _O Show de Truman_, _O Hobbit_, etc.).
- ✅ Precedência clara: `responses` > `animations` > `animation` (retrocompatibilidade total com formatos anteriores).
- ✅ Fallback `"Olá!"` quando o par não tem texto e a regra não tem `response`.
- ✅ `AdminController.debugAutoResponse` exibe o par sorteado no JSON (campo `sorteio`).

**Melhorias:**

- ✅ Refatoração de `getResponseRule` para reduzir complexidade cognitiva (Sonar `S3776`).
- ✅ Extração de `resolverPares` e `escolherPar` para testes determinísticos (sem mockar `ThreadLocalRandom`).
- ✅ 13 novos testes unitários cobrindo todos os caminhos do sorteio.

### 🆕 v2.6.0 — Onda 3: Feature Flags & Documentação

_(Mantido igual ao anterior...)_

### 🆕 v2.5.5 — Onda 2: Estabilidade & Testes

_(Mantido igual ao anterior...)_

### 🔄 Mudanças arquiteturais (v2.1.x → v2.6.3)

- 🔀 Migração de **SQLite** para **PostgreSQL** (dados estruturados) + **MongoDB** (documentos)
- 🔀 Processamento de áudio movido para **workers Kafka** (escalável horizontalmente)
- 🔀 **Virtual Threads** habilitados (`spring.threads.virtual.enabled=true`)
- 🔀 **Spring Boot 4.1.1** + **Spring Security 7.1.1** + **Java 21**
- 🔀 **Spotless** + **JaCoCo** + **SonarCloud** no CI
- 🔀 **Feature flags runtime** com persistência em disco e UI no painel admin
