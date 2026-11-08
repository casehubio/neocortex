CREATE TABLE corpus_annotations (
    id TEXT NOT NULL PRIMARY KEY,
    entry_id TEXT NOT NULL,
    corpus_tenant_id TEXT NOT NULL,
    corpus_name TEXT NOT NULL,
    note TEXT NOT NULL,
    annotation_type TEXT NOT NULL,
    annotated_by TEXT NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY (entry_id, corpus_tenant_id, corpus_name)
        REFERENCES corpus_entries(id, corpus_tenant_id, corpus_name) ON DELETE CASCADE
);

CREATE INDEX idx_annotations_entry ON corpus_annotations(entry_id, corpus_tenant_id, corpus_name);
