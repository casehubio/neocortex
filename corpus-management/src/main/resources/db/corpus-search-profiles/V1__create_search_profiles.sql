CREATE TABLE search_profiles (
    name TEXT PRIMARY KEY,
    stack TEXT NOT NULL,
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);
