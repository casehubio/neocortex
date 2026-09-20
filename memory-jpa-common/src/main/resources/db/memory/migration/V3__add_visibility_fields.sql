ALTER TABLE memory_entry ADD COLUMN subject_type VARCHAR(255) NOT NULL DEFAULT 'unknown';
ALTER TABLE memory_entry ADD COLUMN principal_id VARCHAR(255);
ALTER TABLE memory_entry ADD COLUMN shared_with TEXT;
