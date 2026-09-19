-- V1__Initial_schema.sql: Estrutura completa migrada para PostgreSQL

-- 1. Tabela de Mensagens
CREATE TABLE IF NOT EXISTS messages (
    id BIGSERIAL PRIMARY KEY,
    chat_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(255),
    text TEXT NOT NULL,
    ignore_in_digest BOOLEAN DEFAULT FALSE,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Tabela de Transcrições de Áudio
CREATE TABLE IF NOT EXISTS transcripts (
    id BIGSERIAL PRIMARY KEY,
    chat_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(255),
    text TEXT NOT NULL,
    raw_text TEXT,
    ignore_in_digest BOOLEAN DEFAULT FALSE,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Tabela de Lançamentos Notificados
CREATE TABLE IF NOT EXISTS releases_notified (
    id BIGSERIAL PRIMARY KEY,
    tmdb_id BIGINT NOT NULL,
    media_type VARCHAR(50) NOT NULL,
    release_date DATE NOT NULL,
    title TEXT,
    overview TEXT,
    rating REAL,
    providers TEXT,
    poster_path TEXT,
    notified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. Tabela de Aniversários
CREATE TABLE IF NOT EXISTS birthdays (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    user_name VARCHAR(255) NOT NULL,
    day INTEGER NOT NULL CHECK (day BETWEEN 1 AND 31),
    month INTEGER NOT NULL CHECK (month BETWEEN 1 AND 12),
    last_sent_year INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Índices úteis para performance
CREATE INDEX IF NOT EXISTS idx_birthdays_day_month ON birthdays(day, month);
CREATE INDEX IF NOT EXISTS idx_releases_tmdb ON releases_notified(tmdb_id, media_type);