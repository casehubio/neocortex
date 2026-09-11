package io.casehub.neocortex.memory.cbr;

import java.util.Objects;

public record CbrRetrievalFeedback(
    String tracedCaseId,
    CbrFeedbackOutcome outcome
) {
    public CbrRetrievalFeedback {
        Objects.requireNonNull(tracedCaseId, "tracedCaseId must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
    }
}
