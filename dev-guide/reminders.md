# 📢 Reminders (Anúncios Configuráveis)

Sistema genérico de envios agendados. Substitui o `WeeklyReminderService` hardcoded.

## Visão Geral

Cada **reminder** é um envio agendado de uma mensagem (opcionalmente com mídia) para uma lista de chats, disparado por uma expressão cron.

- **Fonte de verdade:** `config/reminders.json`
- **Agendador:** `ThreadPoolTaskScheduler` dinâmico
- **Persistência:** escrita atômica (`.tmp` + `ATOMIC_MOVE`)
- **Feature flag:** `reminders.enabled` (pausa global)

## Fluxo (resumo)

```
Admin → UI → REST → ReminderService → (notifica) → ReminderScheduler → (a cada cron) → ReminderDispatcher → TelegramFacade
```

## API REST

[Ver tabela em README.md#-anúncios--reminders-configuráveis]

## Arquivos de Configuração

Ver [`config-files.md#remindersjson`](./config-files.md#remindersjson).

## Componentes

| Componente           | Responsabilidade                               |
| -------------------- | ---------------------------------------------- |
| `Reminder`           | Modelo imutável (validação de ID/cron/message) |
| `ReminderJson`       | Envelope JSON (normalização)                   |
| `ReminderService`    | Registry + persistência + listener             |
| `ReminderScheduler`  | Agendamento dinâmico                           |
| `ReminderDispatcher` | Envio efetivo                                  |
| `ReminderController` | REST                                           |

## Ciclo de vida

1. Boot: `ReminderService.init()` carrega JSON → `ReminderScheduler.init()` agenda todos os `enabled` → registra listener.
2. Mutação: `ReminderService` persiste → notifica → `reloadAll()` reagenda.
3. Shutdown: `ReminderScheduler.@PreDestroy` cancela todas as tasks.

## Como adicionar um reminder

### Via UI

1. Aba `📢 Anúncios` → **Novo Anúncio**.
2. Preencha: ID (slug), cron (6 campos), mensagem, chats.
3. **Salvar** → agendado automaticamente.

### Via arquivo

1. Editar `config/reminders.json`.
2. Adicionar novo item em `reminders`.
3. `POST /admin/reminders/reload` (ou reiniciar).

### Via API

```bash
curl -X POST http://localhost:8082/admin/reminders \
  -H "Content-Type: application/json" \
  -d '{
    "id": "promo-sexta",
    "name": "Promoção Sexta",
    "enabled": true,
    "cron": "0 0 10 * * FRI",
    "timezone": "America/Sao_Paulo",
    "message": "🎬 <b>Promoção da Sexta!</b>",
    "targetChatIds": ["-100123"],
    "tags": ["promo"]
  }'
```

## Troubleshooting

| Sintoma                                                              | Causa provável                        | Solução                                                                     |
| -------------------------------------------------------------------- | ------------------------------------- | --------------------------------------------------------------------------- |
| `📴 Reminder '...' ignorado — flag 'reminders.enabled' desabilitada` | Flag global desligada                 | `/admin/features/reminders.enabled?enabled=true`                            |
| Reminder habilitado mas não dispara                                  | Cron inválido (scheduler ignorou)     | Verificar log `❌ Cron inválido no reminder '...'`; recriar com cron válido |
| `reminders.json` corrompido                                          | Escrita interrompida (kill -9)        | `git checkout config/reminders.json` (dev) ou restaurar backup (prod)       |
| Mutações não persistem                                               | `reminders.data.file` em `classpath:` | Trocar para `file:/app/config/reminders.json`                               |
| `status` mostra `scheduled < enabled`                                | Cron inválido ou falha de agendamento | Logs do `ReminderScheduler`                                                 |

## Testes

- `ReminderTest` (~26), `ReminderJsonTest` (~24): records.
- `ReminderServiceTest` (~45): CRUD, persistência, concorrência.
- `ReminderSchedulerTest` (~25): agendamento, cron inválido, cancelamento.
- `ReminderDispatcherTest` (~30): envio, isolamento, feature flag.
- `ReminderControllerTest` (~25): REST.

Cobertura: ~95% (global), ~95% (código novo).
