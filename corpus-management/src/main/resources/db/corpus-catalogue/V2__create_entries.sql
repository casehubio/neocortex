CREATE TABLE corpus_entries (
    id TEXT NOT NULL,
    corpus_tenant_id TEXT NOT NULL,
    corpus_name TEXT NOT NULL,
    frontmatter_json TEXT NOT NULL,
    body TEXT NOT NULL,
    artifact_ref TEXT,
    created_at TEXT NOT NULL,
    last_modified_at TEXT NOT NULL,
    PRIMARY KEY (id, corpus_tenant_id, corpus_name)
);

CREATE INDEX idx_entries_corpus ON corpus_entries(corpus_tenant_id, corpus_name);
