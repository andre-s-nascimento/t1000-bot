# Dev Guide — T-Mill Bot

Documentação técnica para desenvolvedores do T-Mill Bot. Centraliza guias de arquitetura,
configuração e operação do projeto.

## Sumário

- [Como ler este guia](#como-ler-este-guia)
- [Índice de guias](#índice-de-guias)
- [Stack](#stack)
- [Estrutura do repositório](#estrutura-do-repositório)
- [Como rodar localmente](#como-rodar-localmente)
- [Como rodar os testes](#como-rodar-os-testes)
- [Convenções do projeto](#convenções-do-projeto)
- [Fluxo de desenvolvimento](#fluxo-de-desenvolvimento)
- [Referências externas](#referências-externas)

---

## Como ler este guia

Cada arquivo deste diretório é **autocontido** e cobre um tópico específico. Comece pelo que
você precisa no momento:

- **Novo no projeto?** → [Stack](#stack) e [Como rodar localmente](#como-rodar-localmente).
- **Vai mexer em feature flags?** → [Feature Flags](feature-flags.md).
- **Vai mexer em arquivos de configuração?** → em breve: `config-files.md`.
- **Vai adicionar um serviço/cliente?** → leia as [Convenções do projeto](#convenções-do-projeto).

---

## Índice de guias

| Guia                              | Descrição                                                                                                               | Status      |
| --------------------------------- | ----------------------------------------------------------------------------------------------------------------------- | ----------- |
| [Feature Flags](feature-flags.md) | Sistema de flags runtime, catálogo, como adicionar e usar                                                               | ✅          |
| `config-files.md`                 | Todos os arquivos JSON de configuração (audio, groq, refinement, admin, worldcup, tmdb-aliases, feature-flags, prompts) | ⏳ em breve |

> **Nota:** o guia de arquivos de configuração (`config-files.md`) está planejado para o
> próximo ciclo. Até lá, os arquivos podem ser encontrados em `src/main/resources/` e
> `config/` (runtime).

---

## Stack

| Camada              | Tecnologia                                 | Versão | Notas                                                     |
| ------------------- | ------------------------------------------ | ------ | --------------------------------------------------------- |
| Linguagem           | Java                                       | 21     | Virtual Threads habilitadas                               |
| Framework           | Spring Boot                                | 4.x    | Web, Cache, JDBC, Kafka, Flyway, Validation, Data MongoDB |
| Telegram            | `ksilisk/telegram-bot-spring-boot-starter` | 0.7.0  | Starter + meta                                            |
| Banco relacional    | PostgreSQL                                 | 15     | Produção; SQLite legado                                   |
| Banco de documentos | MongoDB                                    | 6.0    | Logs estruturados, ideias                                 |
| Mensageria          | Apache Kafka                               | 4.x    | Tópicos `t1000.audio.*`                                   |
| Cache               | Caffeine                                   | 3.x    | `movieDetails`, `providerCache`                           |
| HTTP                | OkHttp                                     | 5.x    | Timeouts configuráveis                                    |
| IA (transcrição)    | Groq (Whisper)                             | —      | Modelo `whisper-large-v3`                                 |
| IA (refinamento)    | Groq (Llama)                               | —      | `llama-3.1-8b-instant`, `openai/gpt-oss-120b`             |
| IA (TTS)            | Azure Speech                               | —      | Voz `pt-BR-AntonioNeural`                                 |
| Filmes              | TMDB                                       | v3     | Busca, detalhes, providers                                |
| Streaming           | Watchmode                                  | —      | Onde assistir                                             |
| Build               | Gradle                                     | 8.x    | Spotless, JaCoCo, SonarQube                               |
| Testes              | JUnit 5, Mockito, AssertJ, Awaitility      | —      | Testcontainers para integração                            |

---

## Estrutura do repositório

```
t1000-bot/
├── build.gradle                    # Build + plugins (Spotless, JaCoCo, Sonar)
├── docker-compose.yml              # Kafka, Postgres, Mongo (dev local)
├── docker-compose.observability.yml # Prometheus, Grafana, ELK (opcional)
├── docs/
│   └── dev-guide/                  # ← você está aqui
│       ├── README.md               # este arquivo
│       └── feature-flags.md        # guia de feature flags
├── src/
│   ├── main/
│   │   ├── java/net/ddns/adambravo79/tmill/
│   │   │   ├── client/             # Clientes HTTP (Groq, TMDB, Azure, Watchmode)
│   │   │   ├── config/             # @Configuration (Security, Kafka, Caffeine, ...)
│   │   │   ├── constant/           # Constantes (BotMessages)
│   │   │   ├── controller/         # Controllers REST + handlers Telegram
│   │   │   ├── document/           # @Document MongoDB
│   │   │   ├── dto/                # Records de transporte
│   │   │   ├── exception/          # Exceções de domínio
│   │   │   ├── model/              # Records de domínio (Movie, Score, ...)
│   │   │   ├── prompt/             # Personas e prompts (digest)
│   │   │   ├── repository/         # JdbcTemplate + MongoRepository
│   │   │   ├── service/            # Lógica de negócio
│   │   │   │   ├── cache/          # Caches específicos
│   │   │   │   ├── config/         # Services de config (JSON loaders)
│   │   │   │   ├── digest/         # Digest pipeline
│   │   │   │   ├── feature/        # ← sistema de feature flags
│   │   │   │   ├── kafka/          # Producers/Consumers
│   │   │   │   └── prompt/         # Prompt registry
│   │   │   ├── telegram/           # Fachada Telegram, handlers, utils
│   │   │   └── util/               # Utilitários (LogSanitizer)
│   │   └── resources/
│   │       ├── application.properties         # dev
│   │       ├── application-prod.properties    # prod
│   │       ├── db/migration/                  # Flyway
│   │       ├── prompts/                       # Personas e prompts externos
│   │       └── templates/                     # Thymeleaf (admin.html, index.html)
│   └── test/
│       └── java/net/ddns/adambravo79/tmill/
│           ├── client/             # Testes de clientes HTTP
│           ├── config/             # Testes de configuração
│           ├── controller/         # Testes de controllers
│           ├── exception/          # Testes de exceções
│           ├── integration/        # Testes com Testcontainers
│           ├── model/              # Testes de records
│           ├── prompt/             # Testes de prompts
│           ├── repository/         # Testes de repositórios
│           ├── service/            # Testes de serviços
│           └── util/               # Testes de utilitários
└── config/                         # Arquivos de config externos (não versionados)
    ├── audio-config.json
    ├── groq-config.json
    ├── feature-flags.json          # gerado em runtime
    └── ...
```

---

## Como rodar localmente

### Pré-requisitos

- **JDK 21** (`java -version` → 21.x)
- **Docker** + **Docker Compose** (para Postgres, Mongo, Kafka)
- **FFmpeg** instalado em `/usr/bin/ffmpeg` (ou ajustar `AudioConfigService`)
- **Chaves de API**:
  - `TELEGRAM_BOT_TOKEN` (BotFather)
  - `GROQ_API_KEY` (groq.com)
  - `TMDB_READ_TOKEN` (themoviedb.org)
  - `WATCHMODE_API_KEY` (watchmode.com)
  - `AZURE_SPEECH_KEY` + `AZURE_SPEECH_REGION`
  - `GOOGLE_CLIENT_ID` + `GOOGLE_CLIENT_SECRET` (para login admin)

### Passo a passo

```bash
# 1. Subir infraestrutura
docker compose up -d

# 2. Criar arquivo .env (usado por bootRun)
cat > .env <<'EOF'
TELEGRAM_BOT_TOKEN=
GROQ_API_KEY=
TMDB_READ_TOKEN=
WATCHMODE_API_KEY=
AZURE_SPEECH_KEY=
AZURE_SPEECH_REGION=
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
POSTGRES_DATABASE_USER=
POSTGRES_DATABASE_PASSWORD=
POSTGRES_DB=
MONGO_INITDB_ROOT_USERNAME=
MONGO_INITDB_ROOT_PASSWORD=
EOF

# 3. Rodar a aplicação
./gradlew bootRun
```

A aplicação sobe em `http://localhost:8082`. O bot começa a fazer long polling do Telegram.

### Painel admin

Acesse `http://localhost:8082/admin-web`. Você vai ser redirecionado para o login Google.
Se a conta não estiver em `admin.allowed-emails`, você recebe 403.

Em dev, você pode desabilitar a segurança:

```bash
export ADMIN_SECURITY_DISABLED=true
./gradlew bootRun
```

> ⚠️ **Nunca** use `ADMIN_SECURITY_DISABLED=true` em produção — o `SecurityConfig` bloqueia
> o boot com essa flag ativa se o perfil `prod` estiver ativo.

---

## Como rodar os testes

### Suíte completa (unit + integration)

```bash
./gradlew test
```

Testes de integração usam **Testcontainers** (Postgres, Kafka, Mongo efêmeros). Requer Docker.

### Apenas testes unitários (sem Docker)

```bash
./gradlew unitTest
```

Exclui `net.ddns.adambravo79.tmill.integration.*`.

### Um teste específico

```bash
./gradlew unitTest --tests "net.ddns.adambravo79.tmill.controller.AdminControllerTest"
```

### Cobertura (JaCoCo)

```bash
./gradlew jacocoTestReport
# Relatório HTML em build/reports/jacoco/test/html/index.html
```

### Formatação

```bash
./gradlew spotlessApply    # aplica formatação
./gradlew spotlessCheck    # verifica (usado em CI)
```

### Verificação completa (o que o CI roda)

```bash
./gradlew spotlessCheck test jacocoTestReport
```

---

## Convenções do projeto

### Pacotes

- **`client/`** — comunicação com APIs externas (HTTP). Só orquestra; não contém regra de negócio.
- **`service/`** — regra de negócio. Pode chamar `client/`, `repository/`, outros `service/`.
- **`controller/`** — entrada HTTP/Telegram. Delega para `service/`.
- **`config/`** — `@Configuration` do Spring. Sem regra de negócio.
- **`model/`** — records de domínio. Imutáveis.
- **`dto/`** — records de transporte entre camadas.
- **`document/`** — `@Document` do MongoDB.
- **`repository/`** — acesso a dados. `JdbcTemplate` (SQL) ou `MongoRepository`.
- **`exception/`** — exceções de domínio (`extends RuntimeException`).
- **`util/`** — classes utilitárias estáticas (`private` constructor).

### Records vs classes

- **Records** para dados imutáveis (DTOs, models, states).
- **Classes** com Lombok (`@RequiredArgsConstructor`, `@Slf4j`) para serviços.

### Testes

- **Nomes:** `deveFazerAlgo` ou `metodo_cenario_resultado`.
- **Padrão:** Arrange / Act / Assert (implícito pelo Mockito).
- **`@MockitoSettings(strictness = LENIENT)`** apenas em classes onde o mock é chamado com
  múltiplos argumentos (ex.: `Environment` consultado com 13 chaves).
- **`verify(featureFlags, never()).isEnabled("...")`** em testes do tipo "feature X não afeta
  este fluxo" — evita `UnnecessaryStubbingException` sem `lenient()`.

### Logs

- **Emojis** para facilitar `grep`: `🎧` (podcast), `📅` (calendário), `🎬` (filmes), `🛡️`
  (segurança), `🎛️` (flags).
- **Sanitização:** use `LogSanitizer.sanitize*` para textos de usuário antes de logar.
- **Nunca** logue tokens/chaves — use `AdminUtils.maskToken(...)`.

### Feature flags

- **Sempre** cheque no **ponto de entrada** (cron, handler, service público).
- **Nunca** bloqueie chamadas administrativas (ex.: `sendReminderToChat` ignora a flag).
- Documente cada nova flag em `docs/dev-guide/feature-flags.md`.

---

## Fluxo de desenvolvimento

```
┌─────────────────┐
│ Branch develop  │
└────────┬────────┘
         │
         │  feature/xyz ou fix/xyz
         ▼
┌─────────────────┐
│ Branch local    │
│                 │
│ 1. edita        │
│ 2. spotlessCheck│
│ 3. unitTest     │
│ 4. commit       │
│    (commitlint) │
└────────┬────────┘
         │
         │  push + PR para develop
         ▼
┌─────────────────┐
│ PR (CI roda)    │
│                 │
│ - spotlessCheck │
│ - unitTest      │
│ - jacocoReport  │
│ - sonarcloud    │
└────────┬────────┘
         │
         │  aprovação + merge
         ▼
┌─────────────────┐
│ develop         │
│ (pronto p/ nova │
│  release)       │
└─────────────────┘
```

### Commit lint

O projeto usa [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(escopo): descrição curta
fix(escopo): descrição curta
docs(escopo): descrição curta
refactor(escopo): descrição curta
test(escopo): descrição curta
chore(escopo): descrição curta
```

Tipos aceitos: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `ci`, `perf`,
`build`, `revert`, `wip`.

### Branches

- `main` — produção.
- `develop` — integração.
- `feature/*` — novas features.
- `fix/*` — correções.
- `hotfix/*` — correções urgentes em produção.
- `release/*` — preparação de release.

O fluxo `hotfix/*` e `release/*` gera PRs automáticos para `main`.

---

## Referências externas

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Ksilisk Telegram Bot Starter](https://github.com/ksilisk/telegram-bot-spring-boot-starter)
- [Groq API Reference](https://console.groq.com/docs)
- [TMDB API](https://developers.themoviedb.org/3)
- [Watchmode API](https://api.watchmode.com/docs/)
- [Azure Speech SDK](https://learn.microsoft.com/azure/ai-services/speech-service/)
- [Testcontainers](https://www.testcontainers.org/)
- [Conventional Commits](https://www.conventionalcommits.org/)
