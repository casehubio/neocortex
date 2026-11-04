-- Rename ge_id column to document_id across all provenance tables.
-- Refs #800

ALTER TABLE provenance RENAME COLUMN ge_id TO document_id;
ALTER TABLE staleness_reports RENAME COLUMN ge_id TO document_id;
ALTER TABLE feedback_context RENAME COLUMN ge_id TO document_id;

-- Recreate indexes with updated column name.
-- SQLite does not rename indexes automatically.
DROP INDEX IF EXISTS idx_provenance_ge;
CREATE INDEX idx_provenance_doc ON provenance(document_id);

DROP INDEX IF EXISTS idx_staleness_ge;
CREATE INDEX idx_staleness_doc ON staleness_reports(document_id);

DROP INDEX IF EXISTS idx_feedback_ctx_ge;
CREATE INDEX idx_feedback_ctx_doc ON feedback_context(document_id);
