-- Tabela de Snapshots (histórico semanal)
CREATE TABLE sala_vip_snapshot (
    id              BIGSERIAL PRIMARY KEY,
    captured_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    item_count      INT NOT NULL
);

-- Tabela de Filmes
CREATE TABLE sala_vip_movie (
    id                  BIGSERIAL PRIMARY KEY,
    snapshot_id         BIGINT NOT NULL REFERENCES sala_vip_snapshot(id) ON DELETE CASCADE,
    sala_vip_number     INT, -- Extraído do comentário. Pode ser NULL se esquecerem de colocar.
    sala_vip_episode    INT,
    letterboxd_slug     VARCHAR(255) NOT NULL,
    title               VARCHAR(255) NOT NULL,
    year                INT,
    letterboxd_url      VARCHAR(512) NOT NULL,
    note_text           TEXT
);

-- Índices para busca rápida (por número e por título)
CREATE INDEX idx_sala_vip_number ON sala_vip_movie (sala_vip_number);
CREATE INDEX idx_sala_vip_episode ON sala_vip_movie (sala_vip_episode);
CREATE INDEX idx_sala_vip_title_lower ON sala_vip_movie (lower(title));