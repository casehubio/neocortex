CREATE TABLE embedding_cache (
    content_hash  TEXT    NOT NULL,
    model_version TEXT    NOT NULL,
    dense         BLOB    NOT NULL,
    sparse        BLOB,
    colbert       BLOB,
    created_at    INTEGER NOT NULL DEFAULT (unixepoch()),
    PRIMARY KEY (content_hash, model_version)
);
