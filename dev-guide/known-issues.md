## [KI-001] Podcast reporta sucesso falso em timeout de upload

**Sintoma:** `PodcastPublisherService.sendWithRetry` retorna `true` mesmo quando
o `TelegramFacade.enviarMidia` falha por timeout de rede. A métrica
`podcast_publicado` é incrementada indevidamente e o log diz "áudio enviado"
sem que o áudio tenha chegado ao chat.

**Causa raiz:**

- `TelegramFacade.enviarMidia` engole exceções (try/catch dentro do `safeExecutor.run`).
- `PodcastPublisherService.sendWithRetry` só faz retry em exceções propagadas.

**Correção proposta (PR separado):**

1. Mudar `TelegramFacade.enviarMidia` para retornar `boolean` (sucesso real).
2. `PodcastPublisherService.sendWithRetry` verifica o retorno e faz retry.
3. Para áudios > 5 MB, usar `SendDocument` (mais tolerante a timeout).
4. Investigar se `telegram.bot.client.*` está sendo aplicado ao executor Ksilisk.

**Impacto:** perda silenciosa de podcasts. Prioridade alta.
