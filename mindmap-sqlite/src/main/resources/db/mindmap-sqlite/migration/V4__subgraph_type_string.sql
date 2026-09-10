-- Convert SubgraphType enum names to lowercase strings
UPDATE mindmap_subgraph SET type = LOWER(type);

-- Add unique constraint on (tenant_id, type) to prevent duplicate type names per tenant
CREATE UNIQUE INDEX IF NOT EXISTS mindmap_subgraph_tenant_type_idx
    ON mindmap_subgraph (tenant_id, type);
