ALTER TABLE retrieval_feedback ADD COLUMN issue_repo TEXT;
ALTER TABLE retrieval_feedback ADD COLUMN issue_number INTEGER;
ALTER TABLE retrieval_feedback ADD COLUMN attributes TEXT;

CREATE INDEX idx_feedback_issue ON retrieval_feedback(issue_repo, issue_number);
