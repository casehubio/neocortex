CREATE TABLE corpus_catalogue (
    id TEXT PRIMARY KEY,
    corpus_name TEXT NOT NULL,
    tenant_id TEXT NOT NULL,
    type_name TEXT NOT NULL,
    lifecycle TEXT NOT NULL DEFAULT 'ACTIVE',
    qdrant_collection TEXT NOT NULL,
    upstream_json TEXT NOT NULL DEFAULT '[]',
    peers_json TEXT NOT NULL DEFAULT '[]',
    metadata_json TEXT NOT NULL DEFAULT '{}',
    created_at TEXT NOT NULL,
    last_modified_at TEXT NOT NULL,
    UNIQUE(tenant_id, corpus_name)
);

CREATE INDEX idx_catalogue_tenant ON corpus_catalogue(tenant_id);
CREATE INDEX idx_catalogue_type ON corpus_catalogue(type_name);
CREATE INDEX idx_catalogue_lifecycle ON corpus_catalogue(lifecycle);
