# 🏗️ Arquitetura de Pacotes - Tmill

Este documento faz parte do **Dev Guide** e descreve a organização dos pacotes e dependências principais da aplicação **Tmill**.

> **Última atualização:** v2.7.1 (2026-11-05)

---

## 📁 Estrutura Geral

```bash
src/main/java/net/ddns/adambravo79/tmill
├── TmillApplication.java
├── client
│   ├── AzureTtsClient.java
│   ├── GroqClient.java
│   ├── TmdbClient.java
│   └── WatchmodeClient.java
├── config
│   ├── AdminEmailAuthorizationFilter.java
│   ├── AdminFilterConfig.java
│   ├── AdminIpFilter.java
│   ├── AppConfig.java
│   ├── CaffeineConfig.java
│   ├── ClockConfig.java
│   ├── DatabaseInitializer.java
│   ├── GlobalExceptionHandler.java
│   ├── KafkaConfig.java
│   ├── PrometheusFeatureFlagFilter.java
│   └── SecurityConfig.java
├── constant
│   └── BotMessages.java
├── controller
│   ├── AccessDeniedController.java
│   ├── AdminController.java
│   ├── AdminUtils.java
│   ├── AdminWebController.java
│   ├── AudioHandler.java
│   ├── CallbackHandler.java
│   ├── CommandHandler.java            ← 🆕 v2.7.1: roteador puro
│   └── kafka
│       └── AudioResultConsumer.java
├── document
│   ├── BotConversationDocument.java
│   ├── InteractionLogDocument.java
│   └── UserIdeaDocument.java
├── dto
│   ├── AudioProcessedEvent.java
│   ├── AudioReceivedEvent.java
│   ├── AudioRequest.java
│   ├── ErrorResponse.java
│   ├── MigrationResult.java
│   ├── MovieResult.java
│   ├── SalaVipJson.java
│   ├── StreamingAvailabilityResponse.java
│   ├── TmdbDiscoverMovieResponse.java
│   ├── TmdbDiscoverTvResponse.java
│   └── TvResult.java
├── exception
│   ├── AudioProcessingException.java
│   ├── ConfigLoadException.java
│   ├── DigestGenerationException.java
│   ├── DigestSendException.java
│   ├── GroqRateLimitException.java
│   └── MovieNotFoundException.java
├── model
│   ├── AutoResponseConfig.java
│   ├── AutoResponseOverride.java
│   ├── AutoResponseRule.java
│   ├── AutoResponseRuleEntry.java
│   ├── Birthday.java
│   ├── CastRecord.java
│   ├── ChatCompletionResponse.java
│   ├── Choice.java
│   ├── CountryProviders.java
│   ├── CreditsResponse.java
│   ├── CrewRecord.java
│   ├── DirectorRecord.java
│   ├── FullRelease.java
│   ├── Goal.java
│   ├── Message.java
│   ├── MovieOrchestrationResponse.java
│   ├── MovieRecord.java
│   ├── MovieResponse.java
│   ├── MovieSearchResponse.java
│   ├── Provider.java
│   ├── ReleaseNotified.java
│   ├── ResponseAnimationPair.java
│   ├── SalaVipMovie.java
│   ├── SalaVipSnapshot.java
│   ├── Score.java
│   ├── TranscriptionCacheEntry.java
│   ├── TranscriptionResponse.java
│   ├── TvRecord.java
│   ├── UserOverride.java
│   ├── WatchProviderResponse.java
│   ├── WatchProvidersResponse.java
│   └── WorldCupMatch.java
├── prompt
│   └── DigestPersona.java
├── repository
│   ├── BirthdayRepository.java
│   ├── BotConversationRepository.java
│   ├── InteractionLogRepository.java
│   ├── ReleaseNotifiedRepository.java
│   ├── SalaVipRepository.java
│   └── UserIdeaRepository.java
├── service
│   ├── AudioPipelineService.java
│   ├── AudioService.java
│   ├── AutoResponseService.java
│   ├── BirthdayScheduler.java
│   ├── BirthdayService.java
│   ├── BotAnalyticsService.java
│   ├── BuildInfoService.java
│   ├── DailyDigestService.java
│   ├── DailyReleasesService.java
│   ├── EasterEggService.java
│   ├── IdeasLoggerService.java
│   ├── MessageStoreService.java
│   ├── MigrationService.java
│   ├── MovieService.java
│   ├── PodcastPublisherService.java
│   ├── PodcastScriptService.java
│   ├── SalaVipHandler.java             ← 🆕 v2.7.1: movido para telegram/handler
│   ├── SalaVipScheduler.java
│   ├── SalaVipService.java
│   ├── StaticWorldCupService.java
│   ├── StreamingAvailabilityService.java
│   ├── TelegramFileService.java
│   ├── TelegramModerationService.java
│   ├── TempDirService.java
│   ├── TranscriptStoreService.java
│   ├── UserInteractionLogger.java
│   ├── WeeklyReleasesService.java
│   ├── WeeklyReminderService.java
│   ├── WorldCupSchedulerService.java
│   ├── WorldCupUpdaterService.java
│   ├── cache
│   │   ├── ChatTranscriptionCache.java
│   │   └── FileTranscriptionCacheService.java
│   ├── config
│   │   ├── AdminConfigService.java
│   │   ├── AudioConfigService.java
│   │   ├── ConfigRegistry.java
│   │   ├── GroqConfigService.java
│   │   ├── JsonConfigLoader.java
│   │   ├── RefinementConfigService.java
│   │   ├── RetryConfigService.java
│   │   ├── SalaVipBootstrap.java
│   │   ├── SalaVipConfigService.java
│   │   ├── SalaVipJsonLoader.java
│   │   └── WorldCupTeamsService.java
│   ├── digest
│   │   └── DigestPromptFactory.java
│   ├── feature
│   │   ├── FeatureFlagAdminService.java
│   │   ├── FeatureFlagDefinition.java
│   │   ├── FeatureFlagService.java
│   │   └── FeatureFlagState.java
│   ├── kafka
│   │   ├── AudioEventPublisher.java
│   │   └── AudioWorkerService.java
│   └── prompt
│       └── PromptRegistryService.java
├── telegram
│   ├── core
│   │   ├── GroupAuthorizationService.java
│   │   ├── TelegramFacade.java
│   │   ├── TelegramSafeExecutor.java
│   │   └── TelegramSender.java
│   ├── exception
│   │   ├── TelegramApiException.java
│   │   ├── TelegramBotException.java
│   │   ├── TelegramExceptionHandler.java
│   │   └── TelegramFileException.java
│   ├── handler                         ← 🆕 v2.7.1: handlers especializados
│   │   ├── AbstractCommandHandler.java
│   │   ├── BirthdayHandler.java
│   │   ├── HelpCommandHandler.java
│   │   ├── IdeaHandler.java
│   │   ├── MovieHandler.java
│   │   ├── SalaVipHandler.java
│   │   └── WorldCupHandler.java
│   └── util
│       ├── MetricsService.java
│       ├── RetryPolicy.java
│       ├── TelegramMessageSplitter.java
│       └── TelegramUtils.java
└── util
    ├── DateParserUtils.java            ← 🆕 v2.7.1
    ├── LogSanitizer.java
    └── RetryUtils.java
```

---

## 🔄 Fluxo de Dependências

```
Telegram Update
      ↓
MessageUpdateRuleHandler (telegram/handler/)   ← intercepta updates
      ↓
CommandHandler (controller/)                     ← roteador puro (v2.7.1)
      ↓
┌─────────────────────────────────────────────────┐
│ Handlers especializados (telegram/handler/)      │
│  - BirthdayHandler                              │
│  - HelpCommandHandler                           │
│  - IdeaHandler                                  │
│  - MovieHandler                                 │
│  - SalaVipHandler                               │
│  - WorldCupHandler                              │
└─────────────────────────────────────────────────┘
      ↓ (herdam de)
┌─────────────────────────────────────────────────┐
│ AbstractCommandHandler (telegram/handler/)       │
│  - autorização (GroupAuthorizationService)       │
│  - envio seguro (TelegramFacade)                 │
│  - tratamento de exceções                        │
└─────────────────────────────────────────────────┘
      ↓
Services (service/) → Clients (client/) → APIs externas
```

**Legenda:**

- **`controller/`**: entrada HTTP/Telegram. Delega para `service/` ou `telegram/handler/`.
- **`telegram/handler/`**: handlers especializados (v2.7.1+). Cada handler estende `AbstractCommandHandler`.
- **`service/`**: lógica de negócio. Pode chamar `client/`, `repository/`, outros `service/`.
- **`client/`**: integrações externas (TMDB, Groq, Azure, Watchmode).
- **`model/`**: records de domínio. Imutáveis.
- **`telegram/core/`**: abstrações de envio/recebimento.
- **`telegram/util/`**: utilitários de suporte (métricas, retry).

---

## 📦 Pacotes e Responsabilidades

### `cache/`

Caches em memória específicos do domínio:

- `ChatTranscriptionCache`: última transcrição refinada por `chatId`.
- `FileTranscriptionCacheService`: cache de transcrições por `fileId` (com TTL e estatísticas).

### `client/`

Integrações com APIs externas:

- `GroqClient`: transcrição (Whisper) + chat completions (Llama).
- `TmdbClient`: busca de filmes, detalhes, elenco, provedores de streaming.
- `WatchmodeClient`: onde assistir (com cache Caffeine).
- `AzureTtsClient`: síntese de voz (TTS) + compressão FFmpeg.

### `config/`

Configuração da aplicação:

- `SecurityConfig`: OAuth2 + filtros de autorização (`/admin/**`, `/admin-web/**`).
- `CaffeineConfig`: cache manager Caffeine.
- `KafkaConfig`: tópicos do Kafka.
- `AdminEmailAuthorizationFilter`: valida e-mail do usuário OAuth2.
- `AdminIpFilter`: restringe `/admin/**` a IPs específicos.
- `PrometheusFeatureFlagFilter`: bloqueia `/actuator/prometheus` se `metrics.prometheus.enabled=false`.
- `GlobalExceptionHandler`: handler global de exceções (REST).

### `constant/`

- `BotMessages`: mensagens, emojis, formatos, timezones, feature flag defaults.

### `controller/`

Camada de entrada:

- `CommandHandler` (**roteador puro** v2.7.1): analytics, salvamento no DB, auto-resposta, roteamento.
- `AudioHandler`: áudio (privado/grupo) + botões de transcrição.
- `CallbackHandler`: seleção de filme e transcrição via botões inline.
- `AdminController` / `AdminWebController`: API REST e painel Thymeleaf.
- `AdminUtils`: utilitários compartilhados (máscaras, parse de data, loaders).

### `document/`

`@Document` do MongoDB:

- `BotConversationDocument`: conversas.
- `InteractionLogDocument`: logs de interação.
- `UserIdeaDocument`: ideias dos usuários.

### `dto/`

Records de transporte entre camadas (não persistem, não têm lógica).

### `exception/`

Exceções de domínio (`extends RuntimeException`).

### `model/`

Records de domínio. Imutáveis. Incluem `MovieRecord`, `SalaVipMovie`, `WorldCupMatch`, `AutoResponseRule`, etc.

### `prompt/`

- `DigestPersona`: enum de personas para o digest (T1000, BICENTENNIAL, MATRIX_ARCHITECT).

### `repository/`

Acesso a dados:

- `BirthdayRepository`, `ReleaseNotifiedRepository`, `SalaVipRepository`: PostgreSQL (JdbcTemplate).
- `BotConversationRepository`, `InteractionLogRepository`, `UserIdeaRepository`: MongoDB.

### `service/`

Lógica de negócio e orquestração. Ver [README](README.md#estrutura-de-serviços-modular) para o catálogo completo.

### `telegram/`

Abstração da API do Telegram:

- **`core/`**: `TelegramFacade` (envio), `TelegramSafeExecutor` (fallback), `GroupAuthorizationService` (autorização de grupos).
- **`exception/`**: `TelegramExceptionHandler` (mapeia exceções para mensagens).
- **`handler/`** (v2.7.1): handlers especializados. Cada um estende `AbstractCommandHandler`.
- **`util/`**: `MetricsService` (Prometheus), `TelegramUtils` (helpers), `RetryPolicy`, `TelegramMessageSplitter`.

### `util/`

Utilitários:

- `LogSanitizer`: sanitiza strings antes de logar.
- `DateParserUtils` (v2.7.1): parser de datas centralizado.
- `RetryUtils`: retry com backoff exponencial.

---

## 🧪 Testes

A estrutura de testes em `src/test/java` espelha a hierarquia de `main`:

```bash
src/test/java/net/ddns/adambravo79/tmill
├── client/             # Testes de clientes HTTP
├── config/             # Testes de @Configuration
├── controller/         # Testes de controllers
│   ├── CommandHandlerTest.java     ← 🆕 v2.7.1: foca só no roteamento
│   └── ...
├── exception/          # Testes de exceções
├── integration/        # Testcontainers (Postgres, Mongo, Kafka)
├── model/              # Testes de records
├── prompt/             # Testes de personas/prompts
├── repository/         # Testes de repositórios
├── service/            # Testes de serviços
├── telegram/
│   ├── core/           # Testes de TelegramFacade, TelegramSafeExecutor
│   ├── exception/      # Testes de TelegramExceptionHandler
│   ├── handler/        # 🆕 v2.7.1: testes dos handlers
│   │   ├── AbstractCommandHandlerTest.java
│   │   ├── BirthdayHandlerTest.java
│   │   ├── HelpCommandHandlerTest.java
│   │   ├── IdeaHandlerTest.java
│   │   ├── MovieHandlerTest.java
│   │   ├── SalaVipHandlerTest.java
│   │   └── WorldCupHandlerTest.java
│   └── util/           # Testes de utilitários Telegram
└── util/               # Testes de utilitários
    └── DateParserUtilsTest.java   ← 🆕 v2.7.1
```

### Estratégia de testes (v2.7.1)

- **Handlers isolados**: cada handler é testado separadamente, com mocks dos services. Ex.: `MovieHandlerTest` mocka `MovieService` e `WeeklyReleasesService`.
- **Roteador isolado**: `CommandHandlerTest` mocka **todos os handlers**. Verifica apenas que o texto é roteado para o handler correto.
- **Superclasse**: `AbstractCommandHandlerTest` testa a autorização, wrappers de envio, tratamento de exceções e helpers.
- **Utilitário**: `DateParserUtilsTest` cobre todos os formatos aceitos.

---

## ✅ Conclusão

A arquitetura é modular, escalável e testável. A refatoração de v2.7.1 introduziu uma **hierarquia de handlers** que isola cada comando, reduz a complexidade do `CommandHandler` (agora um roteador puro) e centraliza autorização e tratamento de erro na superclasse `AbstractCommandHandler`.

Pontos-chave:

- **Novos comandos** = nova classe em `telegram/handler/` + 1 linha no roteador.
- **Autorização** = sempre verificada em `AbstractCommandHandler.processarComando`.
- **Tratamento de erro** = centralizado em `AbstractCommandHandler.executarComProtecao`.
- **Envio de mensagem** = use `sendHtml`, `sendPlain`, `sendWithButtons`, `sendPhoto` (herdados).
- **Testes** = cada handler isolado; roteador testado à parte.
