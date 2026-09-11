CREATE TABLE IF NOT EXISTS cbr_retrieval_feedback (
    trace_id TEXT NOT NULL,
    traced_case_id TEXT NOT NULL,
    outcome TEXT NOT NULL,
    recorded_at TEXT NOT NULL,
    PRIMARY KEY (trace_id, traced_case_id)
);

CREATE INDEX idx_cbr_feedback_recorded_at ON cbr_retrieval_feedback(recorded_at);
