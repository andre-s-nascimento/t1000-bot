# 🎬 Tmill Bot v2

> **Versão atual: v2.0.0** | Branch: `main` | Último build: 2026-07-20
>
> _Evolução do T1000 Bot — de bot simples para plataforma de entretenimento inteligente._

<div align="center">

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-brightgreen)
![Build](https://img.shields.io/badge/build-passing-success)
![Tests](https://img.shields.io/badge/tests-85%25-success)
![License](https://img.shields.io/badge/license-MIT-blue)
![Status](https://img.shields.io/badge/status-v2.0.0-blueviolet)
![CI](https://github.com/andre-s-nascimento/t1000/actions/workflows/ci.yml/badge.svg)

</div>

---

## 📜 Histórico

| Versão   | Período      | Descrição                                                                                                                                                                                                                                          |
| -------- | ------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **v1.x** | 2024 — 2025  | Bot inicial focado em busca de filmes (TMDB) e transcrição de áudio básica. Arquitetura monolítica simples com polling direto.                                                                                                                     |
| **v2.0** | 2026 — atual | **Migração para arquitetura modular** com pipeline de áudio aprimorado, digest inteligente com múltiplas personas LLM, notificações de lançamentos automáticas, Copa do Mundo 2026, auto-respostas contextuais e sistema de autorização por grupo. |

> 🔄 **Mudança para v2**: A v2 representa uma reescrita arquitetural mantendo a compatibilidade de comandos. O core foi desacoplado em serviços especializados, adotou Virtual Threads (Java 21), cache Caffeine multi-camadas e integração com múltiplos provedores LLM (Groq).

---

## 🚀 Sobre o Projeto (v2)

O **Tmill Bot v2** é uma plataforma de entretenimento inteligente para Telegram que combina:

- 🎥 **Busca de filmes e séries** via API do TMDB com descoberta de lançamentos
- 🎙️ **Transcrição de áudio com IA** via Groq (Whisper) + refinamento LLM
- 🧠 **Digest inteligente** com múltiplas personas (T-1000, Bicentennial Man, Matrix Architect)
- 📺 **Notificações automáticas** de lançamentos em streaming (TMDB + Watchmode)
- ⚽ **Copa do Mundo 2026** — jogos, resultados e lembretes automáticos
- 🤖 **Auto-respostas contextuais** com triggers, time ranges e overrides por usuário
- 🥚 **Easter eggs** por filme
- 📝 **Logger de ideias** direto para o administrador

### Stack Técnica v2

| Camada         | Tecnologia                         |
| -------------- | ---------------------------------- |
| Runtime        | Java 21 (Virtual Threads)          |
| Framework      | Spring Boot 3.x                    |
| Bot Framework  | Ksilisk Telegram Bot + Pengrad API |
| Banco de Dados | SQLite (embedded)                  |
| Cache          | Caffeine (multi-camadas)           |
| LLM/IA         | Groq API (Whisper + Llama/GPT-OSS) |
| Filmes         | TMDB API v3 + Watchmode            |
| Build          | Gradle + Spotless                  |
| CI/CD          | GitHub Actions                     |
| Deploy         | Docker (Docker Hub)                |

---

## 📁 Estrutura do Projeto

```
tmill-bot/
├── .github/
│   ├── labels.yml              # Labels padronizadas
│   └── workflows/
│       ├── ci.yml              # Build, testes e cobertura
│       ├── commitlint.yml      # Validação de commits (Conventional Commits)
│       ├── docker-build-push.yml
│       ├── hotfix-pr.yml       # PR automático para hotfixes
│       ├── labels.yml          # Sync de labels
│       ├── milestones.yml      # Milestones do projeto
│       ├── release-pr.yml      # PR automático para releases
│       ├── release.yml         # Geração de releases com changelog
│       ├── sync-develop.yml    # Sincronização main → develop
│       └── tag-on-merge.yml    # Tag automática após merge
├── docs/
│   └── dev-guide/              # Documentação técnica completa
├── src/
│   ├── main/java/.../
│   │   ├── client/             # Clientes HTTP (Groq, TMDB, Watchmode)
│   │   ├── config/             # Configurações (DB, Virtual Threads, Exception Handler)
│   │   ├── controller/         # Handlers de comandos e callbacks
│   │   ├── dto/                # Data Transfer Objects
│   │   ├── exception/          # Exceções customizadas
│   │   ├── model/              # Records e entidades
│   │   ├── prompt/             # Prompts LLM e personas
│   │   ├── repository/         # Acesso a dados (SQLite)
│   │   ├── service/            # Lógica de negócio
│   │   │   └── cache/          # Serviços de cache
│   │   └── telegram/           # Camada de integração Telegram
│   │       ├── core/           # Facade, autorização, executor seguro
│   │       ├── handler/        # Update handlers (mensagem, callback, edição)
│   │       ├── matcher/        # Matchers de update
│   │       └── util/           # Utilitários (splitter, retry, métricas)
│   ├── main/resources/
│   │   ├── application.properties
│   │   ├── build-info.properties
│   │   └── *.json              # Configurações (easter eggs, auto-responses, worldcup)
│   └── test/                   # Testes unitários e de integração
├── commitlint.config.js        # Regras de commit
├── gradle.properties           # Otimizações Gradle
├── index.html                  # Página do projeto (GitHub Pages)
└── README.md                   # Este arquivo
```

---

## 🛠️ Funcionalidades por Módulo

### 🎬 Módulo de Filmes (`MovieService`)

- Busca por nome com desambiguação (botões inline)
- Detalhes completos: elenco (top 5), diretor, sinopse, nota TMDB
- Provedores de streaming (TMDB Watch Providers + Watchmode fallback)
- Bandeiras de país de origem
- Easter eggs por ID de filme
- Cache Caffeine de 1h para detalhes

### 🎙️ Módulo de Áudio (`AudioPipelineService`)

- Conversão OGA → WAV via FFmpeg (16kHz, mono)
- Transcrição Whisper (Groq) + refinamento LLM
- **Pipeline em grupo**: botões "Bruta" / "Refinada" com entrega no privado
- **Pipeline privado**: transcrição direta
- Cache de transcrições por fileId (TTL 24h)
- Retry automático com backoff exponencial (rate limits Groq)
- Armazenamento no SQLite (texto bruto + refinado)

### 🧠 Módulo de Digest (`DailyDigestService`)

- Geração automática: manhã (08:30) e noite (20:30)
- **3 personas LLM**:
  - `T1000` — sarcástico, cinematográfico
  - `BICENTENNIAL` — humano, contemplativo, gentil
  - `MATRIX_ARCHITECT` — lógico, frio, analítico
- Agregação de mensagens + transcrições do período
- Sanitização de HTML para Telegram
- Split automático de mensagens longas

### 📺 Módulo de Lançamentos (`DailyReleasesService`)

- Verificação a cada 6 horas de filmes/séries novos
- Filtro por provedor de streaming (evita spam de indisponíveis)
- **Giro Semanal** às quartas 18:30 com resumo dos lançamentos
- Persistência em `releases_notified` (evita duplicatas)
- Cache de provedores Watchmode (24h)

### ⚽ Módulo Copa do Mundo (`WorldCupSchedulerService`)

- Dados estáticos em JSON (carregáveis dinamicamente)
- Lembretes 30 minutos antes de cada jogo
- Envio diário: meio-dia e noite
- Resultados com gols, prorrogação e pênaltis
- Tradução de nomes de times + emojis de bandeira

### 🤖 Módulo Auto-Response (`AutoResponseService`)

- Triggers com correspondência de palavra exata (regex `\b`)
- Time ranges (ex: "tchau" só das 18h às 23h59)
- Overrides por userId
- Suporte a mídia (GIF/vídeo) nas respostas
- Recarregamento dinâmico via endpoint admin

### 🔧 Módulo Admin (`AdminController`)

Endpoints REST para teste e gerenciamento:

- `/admin/test-*` — disparo manual de todos os serviços
- `/admin/cache-stats` — estatísticas de cache
- `/admin/custom-digest` — digest por período customizado
- `/admin/properties` — propriedades mascaradas
- `/admin/config-files` — visualização de JSONs de config
- `/admin/clear-*` — limpeza de dados

---

## ⚙️ Configuração

### Variáveis de Ambiente Obrigatórias

```bash
# Telegram
TELEGRAM_BOT_TOKEN=...
TELEGRAM_BOT_USERNAME=...
TELEGRAM_OWNER_ID=...

# APIs Externas
TMDB_READ_TOKEN=...
GROQ_API_KEY=.
WATCHMODE_API_KEY=...

# Grupos Autorizados (IDs negativos, separados por vírgula)
BOT_ALLOWED_CHATS=-1001,-1002

# Feature Flags
TRANSCRIPTION_ENABLED=true
WORLD_CUP_ENABLED=false
DIGEST_ALLOWED_CHATS=-1001
```

### Arquivos de Configuração JSON

| Arquivo               | Descrição                            |
| --------------------- | ------------------------------------ |
| `easter-eggs.json`    | Mapa `tmdbId → mensagem`             |
| `auto-responses.json` | Regras de auto-resposta com triggers |
| `worldcup2026.json`   | Dados dos jogos da Copa              |

---

## 🐳 Docker

```bash
# Build e push automático via GitHub Actions
# Imagem: andresnascimento/t1000-bot:latest

docker run -d \
  -e TELEGRAM_BOT_TOKEN=${TELEGRAM_TOKEN} \
  -e GROQ_API_KEY=${GROQ_KEY} \
  -e TMDB_READ_TOKEN=${TMDB_TOKEN} \
  -v $(pwd)/data:/app/data \
  andresnascimento/t1000-bot:latest
```

---

## 🧪 Testes

```bash
./gradlew clean test jacocoTestReport
```

- **Cobertura**: ~85% de cobertura de linhas
- **Testes unitários**: Serviços, controllers, clientes HTTP
- **Testes de integração**: Fluxo completo de busca de filme
- **Spotless**: Verificação automática de formatação

---

## 📋 Convenções

### Commits (Conventional Commits)

```
feat: nova funcionalidade
fix: correção de bug
docs: documentação
style: formatação
refactor: refatoração
test: testes
chore: manutenção
ci: pipeline
perf: performance
build: build system
revert: reversão
wip: trabalho em progresso
```

### Versionamento

- Segue [SemVer](https://semver.org/): `v{major}.{minor}.{patch}`
- Tags automáticas após merge de release PR
- Changelog gerado automaticamente por labels

---

## 📚 Documentação Técnica

A documentação completa de desenvolvimento está em [`./docs/dev-guide/README.md`](./docs/dev-guide/README.md), incluindo:

- Arquitetura e fluxo de dados
- Guia de contribuição
- Diagrama de sequência do pipeline de áudio
- Mapa de decisões LLM (personas)

---

## 📝 Licença

MIT © 2024-2026 André S. Nascimento

---

<div align="center">

**Desenvolvido com 🧠 e ☕ Java 21 + Spring Boot + Virtual Threads**

_[GitHub Pages](https://andre-s-nascimento.github.io/t1000-bot/)_

</div>
