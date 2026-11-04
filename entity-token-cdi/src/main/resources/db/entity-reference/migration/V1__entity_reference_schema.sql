CREATE TABLE entity_references (
    entity_id   TEXT NOT NULL,
    source_type TEXT NOT NULL,
    source_id   TEXT NOT NULL,
    tenant_id   TEXT NOT NULL,
    PRIMARY KEY (tenant_id, entity_id, source_type, source_id)
);

CREATE INDEX idx_ref_source ON entity_references(tenant_id, source_type, source_id);
