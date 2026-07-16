ALTER TABLE cbr_case ADD COLUMN superseded_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE cbr_case ADD COLUMN superseding_case_id VARCHAR(255);
ALTER TABLE cbr_case ADD COLUMN supersession_reason TEXT;
CREATE INDEX cbr_case_superseded_idx ON cbr_case (superseded_at);
