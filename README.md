# 🎬 T-1000 Bot

Bot para Telegram que **transcreve áudios** com IA (Whisper + refinamento Llama), **busca filmes** no TMDB e **gera resumos automáticos** das conversas do grupo.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.6-brightgreen)
![Build](https://img.shields.io/badge/build-passing-success)
![Tests](https://img.shields.io/badge/tests-coming_soon-yellow)
![License](https://img.shields.io/badge/license-MIT-blue)
![Status](https://img.shields.io/badge/status-v1.2.0-blueviolet)
![CI](https://github.com/andre-s-nascimento/t1000-bot/actions/workflows/ci.yml/badge.svg)

---

## 🚀 Sobre o Projeto

O **T1000-Bot** é um bot para Telegram que combina:

- 🎥 Busca de filmes via API do TMDB
- 🎙️ Transcrição de áudio com IA via Groq
- 🧠 Refinamento de texto com modelos LLM
- ⚙️ Backend moderno com Spring Boot + Java 21 (Virtual Threads)

---

## 🚀 Funcionalidades

### 🎬 Busca de filmes

- `t1000 buscar <nome>`
- Retorna pôster, sinopse, elenco, diretor, nota, onde assistir (streaming) e easter eggs.
- Desambiguação automática quando há vários resultados.

### 🎙️ Transcrição de áudio

- Mensagem de voz ou arquivo de áudio.
- Privado: recebe a transcrição bruta e a versão refinada (pontuação corrigida, vícios de fala removidos).
- Grupo: botões para escolher bruta/refinada; o resultado é enviado no privado do usuário.
- Cache de 24h evita reprocessar o mesmo áudio.

### 💡 Anotar ideias

- `t1000 anotar ideia <texto>`
- A ideia é salva em arquivo de log e reenviada para o administrador do bot.

### 📊 Resumo diário das conversas (Digest)

- Automático às 08:30 (madrugada/manhã) e 20:30 (dia).
- Coleta mensagens e transcrições do banco SQLite e gera um resumo narrativo (com personalidade T-1000, Bicentenário ou Arquiteto) via Groq Llama.
- Pode ser acionado manualmente via endpoints administrativos (`/admin/test-morning-digest` etc.).

### 🗓️ Lembrete semanal

- Toda quarta-feira às 16h (horário SP) envia uma mensagem nos grupos autorizados.

### 🔧 Administração (endpoints HTTP)

- `/admin/reload-easter-eggs` – recarrega frases especiais.
- `/admin/cache-stats` – estatísticas do cache de transcrições.
- `/admin/custom-digest?start=yyyy-MM-dd&end=yyyy-MM-dd&chatId=xxx` – gera resumo sob demanda.

---

## 🏗️ Tecnologias

- **Java 21** com virtual threads
- **Spring Boot 4.0.6**
- **Telegram Bots Long Polling API**
- **Groq Cloud** (Whisper + Llama 3.1/4)
- **TMDB API**
- **SQLite** (persistência de mensagens e transcrições)
- **FFmpeg** (conversão de áudio)
- **Docker + GitHub Actions** (CI, build, release)

---

## 🧩 Estrutura de serviços (modular)

O projeto é organizado em serviços especializados. Veja abaixo cada um e como podem ser reutilizados ou estendidos.

| Serviço                                                                                 | Responsabilidade                                                                                  | Como modularizar                                                                                                                                                                                          |
| --------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `MovieService` + `TmdbClient`                                                           | Busca filmes no TMDB e formata resposta.                                                          | Pode ser extraído como biblioteca independente para qualquer bot que precise consultar filmes.                                                                                                            |
| `AudioPipelineService` + `AudioService` + `GroqClient`                                  | Pipeline completo de transcrição (conversão → Whisper → refinamento).                             | Já é autocontido; pode ser usado em qualquer bot que precise transcrever áudio. O `GroqClient` pode ser reutilizado para qualquer chamada a LLM (resumos, etc.).                                          |
| `TranscriptionCacheService`                                                             | Cache em memória de transcrições por `fileId`.                                                    | Pode ser generalizado para qualquer cache chave-valor com TTL. Basta trocar o tipo da chave.                                                                                                              |
| `DailyDigestService`                                                                    | Geração de resumos de conversas.                                                                  | É altamente configurável: a coleta de dados (SQL), o prompt builder (`DigestPromptFactory`) e a persona são intercambiáveis. Pode ser adaptado para outros tipos de resumo (ex: resumo de e-mails, logs). |
| `EasterEggService`                                                                      | Carrega frases de um JSON e as associa a IDs de filme.                                            | Pode ser transformado em um serviço genérico de "conteúdo extra" para qualquer entidade.                                                                                                                  |
| `UserInteractionLogger`, `IdeasLogger`, `MessageStoreService`, `TranscriptStoreService` | Persistência em SQLite e arquivos.                                                                | Podem ser substituídos por outras implementações (ex: MongoDB, PostgreSQL) sem impacto no restante, pois são acessados via interfaces (embora não explicitamente declaradas).                             |
| `TelegramFacade` / `TelegramSafeExecutor`                                               | Camada de abstração da API do Telegram (envio de mensagens, fotos, botões, download de arquivos). | Pode ser usada como biblioteca comum para **qualquer bot Telegram**, independente da lógica de negócio.                                                                                                   |
| `WeeklyReminderService`                                                                 | Envio de mensagem agendada.                                                                       | Padrão `@Scheduled` fácil de replicar.                                                                                                                                                                    |

### 💡 Como criar um novo serviço (exemplo: módulo de enquete)

1. Crie uma nova classe `PollService` com a lógica de criar/responder enquetes.
2. Injete `TelegramFacade` para enviar mensagens e botões.
3. No `TelegramController`, adicione um handler para comandos como `/poll`.
4. Pronto: o novo serviço fica isolado e testável.

---

## 🐳 Docker

A imagem Docker é construída e publicada automaticamente no Docker Hub (`andresnascimento/t1000-bot`) via GitHub Actions quando há push na `main` ou criação de tag `v*`.

```bash
docker run -e TELEGRAM_BOT_TOKEN=... -e GROQ_API_KEY=... -e TMDB_READ_TOKEN=... andresnascimento/t1000-bot
```

## ⚙️ Variáveis de ambiente obrigatórias

| Variável             | Descrição                                            |
| -------------------- | ---------------------------------------------------- |
| `TELEGRAM_BOT_TOKEN` | Token do bot (BotFather).                            |
| `GROQ_API_KEY`       | Chave da API Groq.                                   |
| `TMDB_READ_TOKEN`    | Token de leitura da API TMDB (v3 auth).              |
| `TELEGRAM_OWNER_ID`  | Seu ID de usuário do Telegram (para receber ideias). |

Opcionais:

- `BOT_ALLOWED_CHATS` – IDs de grupos/canais (negativos) separados por vírgula.
- `DIGEST_ALLOWED_CHATS` – IDs onde os resumos diários serão enviados.
- `EASTER_EGG_FILE` – Caminho para arquivo JSON de easter eggs.
- `TRANSCRIPTION_ENABLED` – `true`/`false`.

---

## 🔧 Desenvolvimento

```bash
# Clonar
git clone https://github.com/seu-usuario/t1000-bot.git
cd t1000-bot

# Build (pula testes se não houver)
./gradlew build -x test

# Executar
export TELEGRAM_BOT_TOKEN=...
./gradlew bootRun
```

## 🧠 Arquitetura

Telegram → Controller → Services → Clients → APIs externas

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
src/main/java
├── cache
├── client
├── config
├── controller
├── dto
├── exception
├── model
├── prompt
├── service
└── telegram
    ├── core
    ├── exception
    └── util
```

## Explicação detalhada dos serviços e modularização

### 🧩 `GroqClient`

- **O que faz**: comunicação com a API Groq (transcrição `whisper-large-v3`, chat completions com Llama).
- **Por que é modular**: não conhece nada sobre Telegram ou lógica de negócio. Apenas recebe arquivos ou textos e retorna respostas.
- **Como reutilizar**:
  - Para criar um novo bot que traduz textos, você injeta `GroqClient` e chama `chatCompletion` com um system prompt apropriado.
  - Para trocar para OpenAI, bastaria alterar a URL e o formato do payload (mantendo a interface).

### 🧩 `AudioPipelineService`

- **Responsabilidade**: orquestra a conversão (`AudioService`), transcrição e refinamento, além de salvar no cache e no banco.
- **Modularização**: se você quiser um bot que apenas transcreva sem refinar ou sem salvar em banco, pode criar uma subclasse ou um serviço similar que ignore essas etapas. A dependência de `TranscricaoCache` e `TranscriptStoreService` é opcional – eles podem ser `null` ou mocks.

### 🧩 `DailyDigestService`

- **O que faz**: consulta o SQLite, monta um prompt, chama `GroqClient.gerarResumoDigest` e envia via `TelegramFacade`.
- **Por que é modular**: a lógica de coleta de mensagens está desacoplada da geração do resumo. Você poderia criar um `DigestDataSource` (interface) que busca mensagens de diferentes origens (API externa, arquivos, etc.) e a mesma `DailyDigestService` funcionaria.
- **Extensão**: já suporta múltiplas personas (T-1000, Bicentenário, Arquiteto) através do enum `DigestPersona` e da `DigestPromptFactory`. Basta adicionar um novo enum e o respectivo prompt.

### 🧩 `TelegramFacade`

- **Camada de abstração da API do Telegram**. Todos os métodos que enviam mensagens ou gerenciam arquivos passam por ela.
- **Vantagem**: se um dia você migrar para webhook em vez de long polling, ou trocar a biblioteca Telegram, só precisa alterar essa classe.
- **Reutilização**: qualquer outro bot Telegram pode copiar essa classe e o `TelegramSafeExecutor` e já terá um envio seguro com fallback.

### 🧩 Serviços de log e persistência (`MessageStoreService`, `TranscriptStoreService`, etc.)

- **Atualmente acoplados a SQLite via `JdbcTemplate`**. Para tornar mais modular, poderíamos definir interfaces:

```java
  public interface TranscriptRepository {
      void save(Transcript transcript);
      List<Transcript> findByDateRange(LocalDateTime from, LocalDateTime to);
  }
```

Assim, a implementação com SQLite pode ser substituída por MongoDB, PostgreSQL ou mesmo um mock em memória para testes.

### 🧩 `EasterEggService`

- **Muito simples e bem isolado**. Carrega um JSON e retorna `Optional<String>`.
- **Modularização**: poderia ser transformado em um serviço genérico `FeatureFlagService` ou `ContentEnricher` que, dado um ID e um tipo, retorna conteúdo extra. No T-1000, é usado apenas para filmes, mas poderia ser usado para outros contextos.

## 🗺️ Roadmap

- [x] [v1.1.0 — Stability & Hardening](https://github.com/andre-s-nascimento/t1000-bot/milestone/1)
- [ ] [v1.2.0 — Performance & Testing](https://github.com/andre-s-nascimento/t1000-bot/milestone/2)
- [ ] [v1.3.0 — UX Improvements](https://github.com/andre-s-nascimento/t1000-bot/milestone/3)
- [ ] [v1.4.0 — DevOps](https://github.com/andre-s-nascimento/t1000-bot/milestone/4)
- [ ] [v2.0.0 — Scalability](https://github.com/andre-s-nascimento/t1000-bot/milestone/5)

## 📄 Licença

MIT

## 👨‍💻 Autor

Andre Nascimento
