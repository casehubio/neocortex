CREATE TABLE dedup_index (
    source          TEXT NOT NULL,
    external_id     TEXT NOT NULL,
    cache_entity_id TEXT NOT NULL,
    mindmap_node_id TEXT,
    first_seen      TEXT NOT NULL,
    last_seen       TEXT NOT NULL,
    PRIMARY KEY (source, external_id)
);

CREATE TABLE entity_metadata (
    entity_id           TEXT PRIMARY KEY,
    name                TEXT NOT NULL,
    category            TEXT,
    source              TEXT NOT NULL,
    external_id         TEXT NOT NULL,
    properties          TEXT,
    fetched_at          TEXT NOT NULL,
    detail_fetched_at   TEXT,
    has_detail          INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE entity_sessions (
    entity_id  TEXT NOT NULL,
    session_id TEXT NOT NULL,
    PRIMARY KEY (entity_id, session_id)
);
CREATE INDEX idx_entity_session ON entity_sessions(session_id);

CREATE TABLE query_cache (
    cache_key   TEXT NOT NULL,
    tenant_id   TEXT NOT NULL,
    entity_ids  TEXT NOT NULL,
    answered_at TEXT NOT NULL,
    expires_at  TEXT NOT NULL,
    PRIMARY KEY (cache_key, tenant_id)
);
CREATE INDEX idx_query_tenant ON query_cache(tenant_id);
