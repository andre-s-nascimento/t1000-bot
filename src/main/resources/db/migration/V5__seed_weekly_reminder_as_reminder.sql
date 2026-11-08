-- ============================================================================
-- V5__seed_weekly_reminder_as_reminder.sql
-- ----------------------------------------------------------------------------
-- Migra o WeeklyReminderService (legado, hardcoded) para o novo sistema de
-- Reminders configuráveis (ReminderScheduler + ReminderDispatcher).
--
-- IDEMPOTENTE:
--   - CREATE TABLE IF NOT EXISTS (não quebra se já existir)
--   - INSERT ... ON CONFLICT (id) DO NOTHING (não sobrescreve edições do admin)
--
-- CONTEÚDO IDÊNTICO AO WeeklyReminderService:
--   - id:        quatro-horas-quarta
--   - cron:      0 0 16 * * WED  (quarta às 16h)
--   - timezone:  America/Sao_Paulo
--   - message:   REMINDER_MESSAGE (byte-a-byte)
--   - mediaUrl:  QUATRO_HORAS_QUARTA_FILE (env var)
--   - chats:     os mesmos grupos de bot.allowed-chats (filtrados por id < 0)
-- ============================================================================

-- 1. Tabelas (idempotente)
CREATE TABLE IF NOT EXISTS reminders (
    id          VARCHAR(64)  PRIMARY KEY,
    name        VARCHAR(255),
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    cron        VARCHAR(64)  NOT NULL,
    timezone    VARCHAR(64),
    message     TEXT         NOT NULL,
    media_url   TEXT,
    version     INT          NOT NULL DEFAULT 1,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMP
);

CREATE TABLE IF NOT EXISTS reminder_chats (
    reminder_id VARCHAR(64) NOT NULL REFERENCES reminders(id) ON DELETE CASCADE,
    chat_id     BIGINT      NOT NULL,
    PRIMARY KEY (reminder_id, chat_id)
);

CREATE TABLE IF NOT EXISTS reminder_tags (
    reminder_id VARCHAR(64) NOT NULL REFERENCES reminders(id) ON DELETE CASCADE,
    tag         VARCHAR(64) NOT NULL,
    PRIMARY KEY (reminder_id, tag)
);

-- 2. Trigger de updated_at (idempotente)
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_reminders_updated_at ON reminders;
CREATE TRIGGER trg_reminders_updated_at
    BEFORE UPDATE ON reminders
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- 3. Seed do reminder "Quatro Horas de Quarta"
-- A mensagem é EXATAMENTE a mesma de WeeklyReminderService.REMINDER_MESSAGE.
-- O mediaUrl fica NULL aqui — o bootstrap JSON (config/reminders.json) preenche
-- com a env var QUATRO_HORAS_QUARTA_FILE no boot, via ReminderBootstrapService.
-- (Não usar ${...} aqui: o Flyway trata ${...} como placeholder próprio.)
INSERT INTO reminders (id, name, enabled, cron, timezone, message, media_url)
VALUES (
    'quatro-horas-quarta',
    'Quatro Horas de Quarta',
    TRUE,
    '0 0 16 * * WED',
    'America/Sao_Paulo',
    E'<i>"São quatro horas da tarde de uma quarta-feira, não é? Semana praticamente encerrada...</i>"\n\n<b>Muito Prazer (1979) - David Neves</b>',
    NULL
)
ON CONFLICT (id) DO NOTHING;

-- 4. Chats alvo — só insere se o reminder acabou de ser criado
-- (usa NOT EXISTS para não sobrescrever edições do admin)
INSERT INTO reminder_chats (reminder_id, chat_id)
SELECT 'quatro-horas-quarta', chat_id
FROM (
    -- Mantém os mesmos grupos do WeeklyReminderService:
    -- parse de bot.allowed-chats filtrado por id < 0 (só grupos)
    -- Como não temos acesso a env vars em SQL puro, inserimos via bootstrap JSON
    -- (config/reminders.json) que é carregado pelo ReminderBootstrapService.
    -- Aqui, só criamos o reminder; os chats vêm do bootstrap JSON.
    SELECT NULL::BIGINT AS chat_id WHERE FALSE
) AS grupos
WHERE NOT EXISTS (
    SELECT 1 FROM reminder_chats WHERE reminder_id = 'quatro-horas-quarta'
);

-- 5. Tags
INSERT INTO reminder_tags (reminder_id, tag)
VALUES
    ('quatro-horas-quarta', 'cinema'),
    ('quatro-horas-quarta', 'semanal')
ON CONFLICT (reminder_id, tag) DO NOTHING;