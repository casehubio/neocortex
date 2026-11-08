CREATE TABLE corpus_entry_relationships (
    id TEXT NOT NULL PRIMARY KEY,
    from_entry_id TEXT NOT NULL,
    to_entry_id TEXT NOT NULL,
    corpus_tenant_id TEXT NOT NULL,
    corpus_name TEXT NOT NULL,
    relationship_type TEXT NOT NULL,
    created_by TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE INDEX idx_relationships_from ON corpus_entry_relationships(from_entry_id, corpus_tenant_id, corpus_name);
CREATE INDEX idx_relationships_to ON corpus_entry_relationships(to_entry_id, corpus_tenant_id, corpus_name);
