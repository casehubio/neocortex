package io.casehub.neocortex.rag;

import java.util.Map;
import java.util.Objects;

public record FeedbackFilter(
    String issueRepo,
    Integer issueNumber,
    Map<String, String> attributes
) {
    public static final FeedbackFilter NONE = new FeedbackFilter(null, null, Map.of());

    public FeedbackFilter {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static FeedbackFilter byIssue(String issueRepo, int issueNumber) {
        return new FeedbackFilter(issueRepo, issueNumber, Map.of());
    }

    public static boolean matches(RetrievalFeedback feedback, FeedbackFilter filter) {
        if (filter == null || NONE.equals(filter)) return true;
        FeedbackContext ctx = feedback.context();
        if (ctx == null) return false;
        if (filter.issueRepo() != null && !filter.issueRepo().equals(ctx.issueRepo())) return false;
        if (filter.issueNumber() != null && !filter.issueNumber().equals(ctx.issueNumber())) return false;
        for (var entry : filter.attributes().entrySet()) {
            if (!Objects.equals(entry.getValue(), ctx.attributes().get(entry.getKey()))) return false;
        }
        return true;
    }
}
