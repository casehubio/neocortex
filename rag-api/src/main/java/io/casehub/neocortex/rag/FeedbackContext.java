package io.casehub.neocortex.rag;

import java.util.Map;

public record FeedbackContext(
    String issueRepo,
    Integer issueNumber,
    Map<String, String> attributes
) {
    public static final FeedbackContext EMPTY = new FeedbackContext(null, null, Map.of());

    public FeedbackContext {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static FeedbackContext ofIssue(String issueRepo, int issueNumber) {
        return new FeedbackContext(issueRepo, issueNumber, Map.of());
    }
}
