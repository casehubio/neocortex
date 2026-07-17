ALTER TABLE cbr_case ADD COLUMN scope VARCHAR(1024) NOT NULL DEFAULT '';

DROP INDEX cbr_case_lookup_idx;
CREATE INDEX cbr_case_lookup_idx ON cbr_case (tenant_id, domain, case_type, scope);
