CREATE TABLE pipeline_snapshot (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id TEXT NOT NULL,
    agent_id TEXT NOT NULL,
    tick_number INTEGER NOT NULL,
    captured_at TEXT NOT NULL,
    trace_json TEXT NOT NULL,
    UNIQUE(tenant_id, agent_id, tick_number)
);

CREATE INDEX idx_pipeline_tenant_agent_tick
    ON pipeline_snapshot(tenant_id, agent_id, tick_number);
