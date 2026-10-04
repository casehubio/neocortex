ALTER TABLE entity_metadata ADD COLUMN tenant_id TEXT NOT NULL DEFAULT '';
ALTER TABLE entity_metadata ADD COLUMN latitude REAL;
ALTER TABLE entity_metadata ADD COLUMN longitude REAL;
ALTER TABLE entity_metadata ADD COLUMN expires_at TEXT NOT NULL DEFAULT '';

CREATE INDEX idx_entity_tenant ON entity_metadata(tenant_id);
CREATE INDEX idx_entity_expires ON entity_metadata(tenant_id, expires_at);

CREATE VIRTUAL TABLE entity_spatial USING rtree(
    id,
    min_lat, max_lat,
    min_lng, max_lng
);
