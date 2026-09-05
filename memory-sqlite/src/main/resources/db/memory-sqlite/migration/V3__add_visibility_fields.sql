ALTER TABLE memory_entry ADD COLUMN subject_type TEXT NOT NULL DEFAULT 'unknown';
ALTER TABLE memory_entry ADD COLUMN principal_id TEXT;
ALTER TABLE memory_entry ADD COLUMN shared_with TEXT;
