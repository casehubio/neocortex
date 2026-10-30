CREATE TABLE entity_annotations (
    annotation_id    TEXT NOT NULL,
    tenant_id        TEXT NOT NULL,
    source_type      TEXT NOT NULL,
    source_id        TEXT NOT NULL,
    entity_node_id   TEXT NOT NULL,
    start_offset     INTEGER NOT NULL,
    end_offset       INTEGER NOT NULL,
    mention_text     TEXT NOT NULL,
    confidence       REAL,
    method           TEXT NOT NULL,
    owner_subject_id TEXT,
    created_at       TEXT NOT NULL,
    PRIMARY KEY (annotation_id)
);

CREATE INDEX idx_ann_entity ON entity_annotations(tenant_id, entity_node_id);
CREATE INDEX idx_ann_source ON entity_annotations(tenant_id, source_type, source_id);
CREATE INDEX idx_ann_owner  ON entity_annotations(tenant_id, source_type, owner_subject_id);
