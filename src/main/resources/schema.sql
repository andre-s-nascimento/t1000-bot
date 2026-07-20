CREATE TABLE IF NOT EXISTS messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    chat_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    user_name TEXT,
    text TEXT NOT NULL,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS transcripts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    chat_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    user_name TEXT,
    text TEXT NOT NULL,
    raw_text TEXT,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS releases_notified (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    tmdb_id INTEGER NOT NULL,
    media_type TEXT NOT NULL,          -- 'movie' ou 'tv'
    release_date TEXT NOT NULL,        -- data de estreia (YYYY-MM-DD)
    title TEXT NOT NULL,
    overview TEXT,
    rating REAL,
    providers TEXT,                    -- "Netflix, Prime Video"
    poster_path TEXT,
    notified_at DATETIME DEFAULT CURRENT_TIMESTAMP
);