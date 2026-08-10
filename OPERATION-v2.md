# ⚙️ Funcionamento Tmill Bot v2

> **Versão do documento**: v2.0.0 | **Atualizado em**: 2026-07-21
>
> _Guia operacional completo da plataforma. Inclui histórico v1 e detalhamento das mudanças v2._

---

## 📜 Histórico de Operação

| Versão   | Período    | Modo de Operação                                                                                                                                                               |
| -------- | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **v1.x** | 2024–2025  | Bot operava em modo "tudo-em-um": polling manual, transcrição síncrona, sem cache, sem persistência de transcrições. Comandos básicos: busca de filmes e transcrição de áudio. |
| **v2.0** | 2026–atual | **Plataforma modular** com processamento assíncrono, cache multi-camadas, persistência completa, notificações automáticas e múltiplas integrações LLM.                         |

> 🔄 **Transição v1 → v2**: A mudança principal foi a **desacoplagem do fluxo de áudio** (de síncrono para pipeline assíncrono com cache) e a **introdução de serviços agendados** (digest, lançamentos, Copa).

---

## 🚀 Inicialização

### 1. Startup Sequence

```
1. Spring Boot Context Load
   ├── Virtual Threads Executor (primary)
   ├── SQLite Database Initialization
   │   └── Criação/atualização de tabelas (messages, transcripts, releases_notified)
   ├── Cache Warm-up
   │   └── Caffeine caches registrados
   ├── Config Files Load
   │   ├── easter-eggs.json
   │   ├── auto-responses.json
   │   └── worldcup2026.json (se habilitado)
   └── Telegram Bot Connection
       └── Ksilisk polling iniciado

2. Scheduled Tasks Registration
   ├── DailyDigest: 08:30 e 20:30 (America/Sao_Paulo)
   ├── DailyReleases: a cada 6 horas
   ├── WeeklyReminder: quartas 16:00
   ├── WorldCup: 12:00 e 18:30 (se habilitado)
   ├── WorldCup Reminders: a cada minuto (30min antes dos jogos)
   └── WorldCup Cleanup: 00:05 (limpa lembretes do dia)

3. Admin Endpoints Disponíveis
   └── Porta 8082 (server.port)
```

### 2. Verificação de Saúde

```bash
# Verificar se o bot está conectado
curl http://localhost:8082/admin/properties

# Verificar estatísticas de cache
curl http://localhost:8082/admin/cache-stats

# Verificar build info
cat src/main/resources/build-info.properties
```

---

## 🎬 Fluxo de Comandos do Usuário

### Comando: Buscar Filme

```
Usuário: "t1000 buscar Duna"

1. CommandHandler.handleTextUpdate()
   ├── Normaliza: "t1000" (remove hífen, case-insensitive)
   ├── Extrai termo: "Duna"
   ├── Valida: ≥3 chars, ≤100 chars
   ├── Sanitiza: remove caracteres especiais
   └── Chama: MovieService.buscarFilme("Duna")

2. MovieService.buscarFilme()
   ├── TmdbClient.pesquisarFilme("Duna")
   │   └── ATALHOS: "duna" → "Dune 2021" (hardcoded)
   └── Retorna: MovieSearchResponse

3. Se 1 resultado:
   └── MovieService.buscarPorId(id) [cacheable]
       ├── buscarDetalhes()     // TMDB /movie/{id}
       ├── buscarElenco()       // TMDB /movie/{id}/credits
       ├── buscarDiretor()      // TMDB /movie/{id}/credits
       └── buscarOndeAssistir() // TMDB /movie/{id}/watch/providers
           └── Fallback: WatchmodeClient.getProviders()
       → MovieOrchestrationResponse

4. Se >1 resultado:
   └── CallbackHandler.criarBotoesDesambiguacao()
       → InlineKeyboardMarkup com "id:{movieId}"

5. Resposta formatada (HTML):
   🎬 <b>TÍTULO</b>
   <i>Título Original</i>
   📅 Ano: 2021 🇧🇷
   ⭐ <b>Nota:</b> <a href="tmdb_link">8.5/10</a>
   🎬 <b>Diretor:</b> Nome
   👥 <b>Elenco:</b> Ator1, Ator2, Ator3, Ator4, Ator5 e mais X atores
   📖 <b>Sinopse:</b> Texto...
   📺 <b>Onde assistir:</b> Netflix, Prime Video
   [+ Easter egg se existir]
```

### Comando: Transcrever Áudio

#### Em Chat Privado

```
Usuário envia áudio/voz

1. AudioHandler.handleAudioUpdate()
   ├── Verifica: transcriptionEnabled=true
   ├── Verifica: fileSize ≤ 20MB
   ├── Identifica: chat.type = Private
   └── processPrivateAudio()

2. processPrivateAudio()
   ├── TelegramFileService.baixarArquivo(fileId) [retry 3x]
   └── AudioPipelineService.processarFluxoAudio(file, chatId, userId, userName, callback)
       ├── AudioService.converterParaWav(oga) [Virtual Thread @Async]
       │   └── FFmpeg: -ar 16000 -ac 1
       ├── GroqClient.transcrever(wav) [retry 2x]
       │   └── POST /openai/v1/audio/transcriptions
       ├── callback: "🎙️ *Bruto:*
_texto_"
       ├── GroqClient.refinarTexto(bruto) [retry 3x]
       │   └── POST /openai/v1/chat/completions (llama-3.1-8b-instant)
       ├── TranscriptStoreService.saveTranscript(chatId, userId, name, refinado)
       ├── ChatTranscriptionCache.salvar(chatId, refinado)
       └── callback: "✨ *Refinado:*
texto"

3. Resposta enviada diretamente no chat
   └── Se texto > 4000 chars: split em múltiplas mensagens
```

#### Em Grupo

```
Usuário envia áudio/voz em grupo

1. AudioHandler.handleAudioUpdate()
   ├── Verifica autorização (GroupAuthorizationService)
   ├── Identifica: chat.type = supergroup
   └── processGroupAudio()

2. processGroupAudio()
   ├── Download do arquivo (assíncrono)
   ├── AudioPipelineService.processarEArmazenar(file, groupId, senderId, senderName)
   │   └── Retorna: ProcessedAudio(bruto, refinado)
   ├── FileTranscriptionCacheService.put(fileId, bruto, refinado) [TTL 24h]
   ├── TranscriptStoreService.saveTranscriptWithRaw(groupId, senderId, name, bruto, refinado)
   └── Gera token único → pendingRequests.put(token, AudioRequest)

3. Envia mensagem no grupo com botões:
   "🎧 Áudio de <b>Usuário</b> (2min e 30s) processado!
    Clique num botão para receber a transcrição no seu privado:"
   [🎙️ Transcrição Bruta]    [✨ Transcrição Refinada]
   callbackData: "trans_bruto|{token}" / "trans_refinado|{token}"

4. Usuário clica em botão
   └── CallbackHandler → AudioHandler.handleTranscriptionCallback()
       ├── Valida token (não expirado: 7 dias)
       ├── Verifica cache: FileTranscriptionCacheService.get(fileId)
       │   ├── HIT: entrega diretamente no privado do user
       │   └── MISS: reprocessa áudio e entrega
       └── Envia: "🎙️ Transcrição Bruta:
texto" ou "✨ Transcrição Refinada:
texto"
           └── Se erro 403 (user não iniciou bot): avisa no grupo
```

### Comando: Anotar Ideia

```
Usuário: "t1000 anotar ideia: Implementar busca por ator"

1. CommandHandler.handleAnotarIdeia()
   ├── Extrai ideia: "Implementar busca por ator"
   ├── Valida: não vazia
   ├── IdeasLogger.saveIdea(userId, name, chatId, idea, chatName)
   │   └── Escreve em: logs/ideas_YYYY-MM-DD.txt
   └── Notifica admin (TELEGRAM_OWNER_ID):
       "💡 <b>Nova ideia</b>
        📝 <i>Implementar busca por ator</i>
        👤 <b>Usuário:</b> @user
        📍 <b>Local:</b> privado
        🕒 21/07/2026 09:54:00"

2. Responde no chat original:
   "✅ Ideia registrada! Obrigado pela contribuição."
```

### Comando: Estreias da Semana

```
Usuário: "t1000 estreias da semana"

1. CommandHandler → WeeklyReleasesService.getWeeklyReleasesMessage()
   ├── Calcula período: quinta anterior → próxima quinta
   ├── TMDB Discover:
   │   ├── /discover/movie (release_date.gte/lte)
   │   └── /discover/tv (first_air_date.gte/lte)
   ├── Agrupa por data
   └── Formata HTML:
       "🎞️ | <b>Estreias da Semana</b>
        Confira os principais lançamentos entre 17/07 – 24/07.

        🗓️ <b>18/07 (sexta-feira)</b>
        ▪️ Filme A
        ▪️ Filme B (série)
        ..."

2. Envia mensagem formatada no chat
```

---

## 🤖 Serviços Automáticos (Background)

### 1. Daily Digest

```
Trigger: Cron "0 30 8 * * *" e "0 30 20 * * *" (America/Sao_Paulo)

1. Define período:
   - Manhã: ontem 20:30 → hoje 08:30
   - Noite: hoje 08:30 → hoje 20:30

2. Consulta SQLite:
   SELECT user_name, text, timestamp FROM messages WHERE timestamp BETWEEN ? AND ?
   SELECT user_name, text, timestamp FROM transcripts WHERE timestamp BETWEEN ? AND ?

3. Agrega e ordena por timestamp
   ├── Detecta gaps >20min → insere separador "NOVO BLOCO DE CONVERSA"
   └── Formata: "[HH:mm] Usuário (áudio?): texto"

4. Se prompt > 32000 chars:
   └── Truncamento inteligente: início + meio + fim

5. GroqClient.gerarResumoDigest(messages, persona, periodLabel)
   ├── Prompt system: persona T1000/BICENTENNIAL/MATRIX_ARCHITECT
   └── max_tokens: 2200, temperature: 0.5

6. Sanitiza HTML (remove tags não permitidas)
7. Envia para todos os chats em digest.chat-ids
   └── Split se necessário (limite Telegram: 3900 chars)
```

### 2. Notificações de Lançamentos

```
Trigger: Cron "0 0 */6 * * *" (a cada 6 horas)

1. LocalDate hoje = now(América/São_Paulo)

2. Busca lançamentos:
   ├── TMDB /discover/movie?release_date.gte={hoje}&release_date.lte={hoje}
   └── TMDB /discover/tv?first_air_date.gte={hoje}&first_air_date.lte={hoje}

3. Para cada resultado:
   ├── Verifica: já notificado? (releases_notified)
   ├── Busca provedores: WatchmodeClient.getProviders(tmdbId, type)
   │   └── Cache Caffeine (24h)
   └── Se TEM provedor válido:
       ├── Salva em releases_notified (título, sinopse, nota, provedores, poster)
       └── Envia notificação para todos os chats
           "<b>Título</b>
            <b>JÁ DISPONÍVEL</b>
            ⭐ Nota: X.X/10
            👩‍🎓 Sinopse...
            📺 Onde assistir: Netflix, Prime Video"
           [+ poster se disponível]

4. Limita a 15 resultados por execução
```

### 3. Giro Semanal (Streamings)

```
Trigger: Cron "0 30 18 * * 4" (quintas 18:30)

1. Período: quinta anterior → hoje
2. Consulta: SELECT * FROM releases_notified WHERE notified_at BETWEEN ? AND ?
3. Formata resumo HTML com todos os lançamentos da semana
4. Envia para chats configurados
```

### 4. Copa do Mundo 2026

```
Trigger: Vários crons (se worldcup.enabled=true)

┌─────────────────────────────────────────────────────────────┐
│  12:00  → sendNoonMatches()                                  │
│  18:30  → sendEveningMatches()                              │
│  *:*    → checkThirtyMinutesBeforeEachMatch()               │
│  00:05  → cleanReminders() (reset diário)                   │
└─────────────────────────────────────────────────────────────┘

Dados: JSON estático (worldcup2026.json) carregado em memória
       → Map<LocalDate, List<WorldCupMatch>>

Formato de saída:
"🏆 JOGOS DE HOJE

Brasil (🇧🇷) x (🇦🇷) Argentina - 16:00 - Estádio X
Alemanha (🇩🇪) x (🇫🇷) França - 19:30 - Estádio Y"

Resultados:
"📊 RESULTADOS - 20/07/2026

🇧🇷 Brasil 2 x 1 Argentina 🇦🇷
  ⚽ 🇧🇷 Neymar 23'
  ⚽ 🇦🇷 Messi 45'
  ⚽ 🇧🇷 Vini Jr. 78'"
```

### 5. Lembrete Semanal

```
Trigger: Cron "0 0 16 * * 3" (quartas 16:00)

Mensagem:
"<i>"São quatro horas da tarde de uma quarta-feira, não é?
 Semana praticamente encerrada..."</i>

<b>Muito Prazer (1979) - David Neves</b>"

[+ vídeo/GIF se configurado em weekly.reminder.media-file]
```

---

## 🛠️ Operações Administrativas

### Endpoints REST (/admin)

| Endpoint                       | Método | Descrição                               |
| ------------------------------ | ------ | --------------------------------------- |
| `/admin/reload-auto-responses` | POST   | Recarrega regras de auto-resposta       |
| `/admin/reload-easter-eggs`    | POST   | Recarrega easter eggs                   |
| `/admin/reload-worldcup`       | POST   | Recarrega dados da Copa                 |
| `/admin/test-weekly-reminder`  | POST   | Dispara lembrete semanal                |
| `/admin/test-morning-digest`   | GET    | Dispara digest da manhã                 |
| `/admin/test-evening-digest`   | GET    | Dispara digest da noite                 |
| `/admin/test-daily-releases`   | POST   | Dispara verificação de lançamentos      |
| `/admin/test-weekly-digest`    | POST   | Dispara giro semanal                    |
| `/admin/custom-digest`         | GET    | Digest customizado (start, end, chatId) |
| `/admin/cache-stats`           | GET    | Estatísticas de cache                   |
| `/admin/properties`            | GET    | Propriedades mascaradas                 |
| `/admin/config-files`          | GET    | Conteúdo dos JSONs de config            |
| `/admin/clear-releases`        | POST   | Limpa tabela releases_notified          |
| `/admin/clear-all-data`        | POST   | Limpa todos os dados                    |
| `/admin/test-auto-response`    | POST   | Testa auto-resposta                     |
| `/admin/debug-auto-response`   | GET    | Debug de auto-resposta                  |
| `/admin/auto-response-rules`   | GET    | Lista regras carregadas                 |

### Operações de Manutenção

```bash
# Recarregar configs sem restart
curl -X POST http://localhost:8082/admin/reload-auto-responses
curl -X POST http://localhost:8082/admin/reload-easter-eggs
curl -X POST http://localhost:8082/admin/reload-worldcup

# Verificar saúde
curl http://localhost:8082/admin/cache-stats
# {"hits": 150, "misses": 23, "size": 45}

# Testar funcionalidade
curl -X GET "http://localhost:8082/admin/custom-digest?start=2026-07-01&end=2026-07-21&chatId=12345"
```

---

## 🔐 Configuração de Ambiente

### Variáveis Obrigatórias

```bash
# === TELEGRAM ===
export TELEGRAM_BOT_TOKEN="..."
export TELEGRAM_BOT_USERNAME="seu_bot"
export TELEGRAM_OWNER_ID="123456789"

# === APIs EXTERNAS ===
export TMDB_READ_TOKEN="eyJhbGciOiJIUzI1NiJ9..."
export GROQ_API_KEY="gsk_..."
export WATCHMODE_API_KEY="..."

# === FEATURE FLAGS ===
export TRANSCRIPTION_ENABLED="true"
export WORLD_CUP_ENABLED="false"
export DIGEST_ALLOWED_CHATS="-1001890"
export BOT_ALLOWED_CHATS="-1001890,-1001891"

# === CONFIGS OPCIONAIS ===
export EASTER_EGG_FILE="classpath:easter-eggs.json"
export AUTO_RESPONSE_FILE="classpath:auto-responses.json"
export WORLD_CUP_FILE="classpath:worldcup2026.json"
export QUATRO_HORAS_QUARTA_FILE="https://.../video.mp4"
```

### Arquivos JSON de Configuração

**auto-responses.json:**

```json
{
  "saudacao": {
    "triggers": ["bom dia", "boa tarde"],
    "response": "Olá! Como vai?",
    "animation": "https://exemplo.com/gif.gif",
    "timeRange": { "start": "06:00", "end": "18:00" },
    "userOverrides": {
      "123456789": {
        "response": "Bom dia, chefe!",
        "animation": "https://exemplo.com/boss.gif"
      }
    }
  }
}
```

**easter-eggs.json:**

```json
{
  "550": "🥚 Curiosidade: O primeiro Matrix foi lançado em 1999 e revolucionou...",
  "155": "🥚 O Poderoso Chefão foi recusado por vários estúdios antes..."
}
```

---

## 📊 Monitoramento

### Logs Importantes

```
# Startup
🚀 Build info - branch: main, commit: abc1234, data: 2026-07-20 15:05:40
🤖 TelegramBotExecutor injetado? true
✅ Bot conectado: @tmill_bot
⚙️ Configurando AsyncTaskExecutor com Virtual Threads

# Operação normal
🔎 TMDB: Pesquisando filme query='Duna'
✅ TMDB: Busca concluída query='Duna' resultados=1
🎙️ Transcrevendo arquivo=audio_123.oga
✅ Transcrição entregue via cache para userId=123 tipo=trans_refinado
✅ Trigger 'bom dia' ativado (horário simulado: 14:30)

# Erros
❌ Erro no processamento de áudio tipo=AudioProcessingException msg=...
⚠️ Grupo não autorizado: -1009999999999
⏳ Muitas requisições. Tente novamente em alguns segundos.
```

### Métricas

| Métrica           | Fonte                         | Acesso                       |
| ----------------- | ----------------------------- | ---------------------------- |
| Cache hits/misses | FileTranscriptionCacheService | `/admin/cache-stats`         |
| Regras carregadas | AutoResponseService           | `/admin/auto-response-rules` |
| Propriedades      | Environment                   | `/admin/properties`          |
| Build info        | build-info.properties         | Arquivo no classpath         |

---

## 🐛 Troubleshooting

| Problema               | Causa Provável                      | Solução                                                  |
| ---------------------- | ----------------------------------- | -------------------------------------------------------- |
| Bot não responde       | Token inválido ou bot não iniciado  | Verificar `TELEGRAM_BOT_TOKEN`, iniciar conversa com bot |
| Transcrição falha      | FFmpeg não instalado                | Instalar `ffmpeg` no container/host                      |
| Rate limit Groq        | Muitas requisições                  | Aguardar ou verificar plano Groq                         |
| Cache miss constante   | TTL muito curto                     | Ajustar `cache.transcription.ttl-seconds`                |
| Grupo não autorizado   | ID não em `bot.allowed-chats`       | Adicionar ID negativo do grupo                           |
| Digest não envia       | `digest.enabled=false` ou sem chats | Habilitar e configurar `digest.chat-ids`                 |
| Lançamentos duplicados | `releases_notified` corrompida      | `/admin/clear-releases`                                  |

---

## 📝 Changelog Operacional v2

### v2.0.0 (2026-07)

- **Novo**: Arquitetura modular com serviços especializados
- **Novo**: Pipeline de áudio assíncrono com cache e retry
- **Novo**: Digest inteligente com 3 personas LLM
- **Novo**: Notificações automáticas de lançamentos em streaming
- **Novo**: Módulo Copa do Mundo 2026
- **Novo**: Auto-respostas com triggers, time ranges e overrides
- **Novo**: Sistema de autorização por grupo
- **Novo**: Cache multi-camadas (Caffeine)
- **Novo**: Virtual Threads (Java 21)
- **Novo**: CI/CD completo com GitHub Actions
- **Melhorado**: Transcrição com texto bruto + refinado persistido
- **Melhorado**: Retry inteligente com parse de rate limit
- **Melhorado**: Formatação HTML sanitizada para Telegram

---

_Documento mantido por: André S. Nascimento_
_Última atualização: 2026-07-21_
