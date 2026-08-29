-- Nodes: rename confidence → confidence_value, confirmed_at → decay_reference
ALTER TABLE mindmap_node RENAME COLUMN confidence TO confidence_value;
ALTER TABLE mindmap_node RENAME COLUMN confirmed_at TO decay_reference;

-- Edges: rename confidence → confidence_value, add decay_reference from updated_at
ALTER TABLE mindmap_edge RENAME COLUMN confidence TO confidence_value;
ALTER TABLE mindmap_edge ADD COLUMN decay_reference TEXT;
UPDATE mindmap_edge SET decay_reference = updated_at;
