-- ============================================================================
-- V4__create_reminders_table.sql
-- ----------------------------------------------------------------------------
-- Move a fonte de verdade dos reminders de config/reminders.json para o
-- PostgreSQL.
--
-- Estrutura:
--   reminders        — tabela principal (1 linha por lembrete)
--   reminder_chats   — N:N com chats alvo (targetChatIds)
--   reminder_tags    — N:N com tags (tags)
--
-- Decisões de design:
--   - id (slug) como PK: mantém compatibilidade com Reminder.id do JSON.
--   - ON DELETE CASCADE: remover um reminder remove chats/tags automaticamente.
--   - updated_at gerenciado por trigger BEFORE UPDATE (não pelo app).
--   - version INTEGER para optimistic locking (@Version).
--   - deleted_at TIMESTAMPTZ NULL para soft delete (retenção de histórico).
--   - Índice parcial em enabled (só as linhas TRUE) para o scheduler.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- reminders
-- ----------------------------------------------------------------------------
CREATE TABLE reminders (
    id              VARCHAR(100) PRIMARY KEY,
    name            VARCHAR(255),
    enabled         BOOLEAN       NOT NULL DEFAULT TRUE,
    cron            VARCHAR(100)  NOT NULL,
    timezone        VARCHAR(100),
    message         TEXT          NOT NULL,
    media_url       VARCHAR(2048),
    version         INTEGER       NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ   NULL
);

-- Busca do scheduler: só reminders ativos e habilitados
CREATE INDEX idx_reminders_enabled
    ON reminders (enabled)
    WHERE enabled = TRUE AND deleted_at IS NULL;

-- Busca por soft-deleted (admin/auditoria)
CREATE INDEX idx_reminders_deleted_at
    ON reminders (deleted_at)
    WHERE deleted_at IS NOT NULL;

-- ----------------------------------------------------------------------------
-- Trigger: updated_at = NOW() a cada UPDATE
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_reminders_updated_at
    BEFORE UPDATE ON reminders
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- ----------------------------------------------------------------------------
-- reminder_chats (1:N)
-- ----------------------------------------------------------------------------
CREATE TABLE reminder_chats (
    reminder_id     VARCHAR(100) NOT NULL
                    REFERENCES reminders (id) ON DELETE CASCADE,
    chat_id         BIGINT       NOT NULL,
    PRIMARY KEY (reminder_id, chat_id)
);

-- Permite consultar "quais reminders vão para este chat?"
CREATE INDEX idx_reminder_chats_chat_id
    ON reminder_chats (chat_id);

-- ----------------------------------------------------------------------------
-- reminder_tags (1:N)
-- ----------------------------------------------------------------------------
CREATE TABLE reminder_tags (
    reminder_id     VARCHAR(100) NOT NULL
                    REFERENCES reminders (id) ON DELETE CASCADE,
    tag             VARCHAR(100) NOT NULL,
    PRIMARY KEY (reminder_id, tag)
);

CREATE INDEX idx_reminder_tags_tag
    ON reminder_tags (tag);