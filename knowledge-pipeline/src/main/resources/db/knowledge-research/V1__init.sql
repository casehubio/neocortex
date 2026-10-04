CREATE TABLE research_sessions (
    id           TEXT PRIMARY KEY,
    name         TEXT NOT NULL,
    tenant_id    TEXT NOT NULL,
    criteria     TEXT,
    subgraph_id  TEXT NOT NULL,
    state        TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at   TEXT NOT NULL,
    last_active  TEXT NOT NULL
);
CREATE INDEX idx_sessions_tenant_state ON research_sessions(tenant_id, state);
