CREATE TABLE mutations (
    id TEXT PRIMARY KEY,
    tenant_id TEXT NOT NULL,
    subgraph_id TEXT,
    source TEXT NOT NULL,
    mutation_type TEXT NOT NULL,
    timestamp TEXT NOT NULL,
    data TEXT NOT NULL
);

CREATE INDEX idx_mutations_tenant_subgraph_time
    ON mutations(tenant_id, subgraph_id, timestamp);

CREATE TABLE mutation_nodes (
    mutation_id TEXT NOT NULL REFERENCES mutations(id) ON DELETE CASCADE,
    node_id TEXT NOT NULL,
    tenant_id TEXT NOT NULL,
    PRIMARY KEY (mutation_id, node_id)
);

CREATE INDEX idx_mutation_nodes_lookup
    ON mutation_nodes(tenant_id, node_id);

CREATE TABLE keyframes (
    id TEXT PRIMARY KEY,
    tenant_id TEXT NOT NULL,
    subgraph_id TEXT NOT NULL,
    captured_at TEXT NOT NULL,
    data TEXT NOT NULL
);

CREATE INDEX idx_keyframes_tenant_subgraph_time
    ON keyframes(tenant_id, subgraph_id, captured_at);

CREATE TABLE audit_entries (
    id TEXT PRIMARY KEY,
    tenant_id TEXT NOT NULL,
    started_at TEXT NOT NULL,
    completed_at TEXT NOT NULL,
    data TEXT NOT NULL
);

CREATE INDEX idx_audit_tenant_time
    ON audit_entries(tenant_id, completed_at);
