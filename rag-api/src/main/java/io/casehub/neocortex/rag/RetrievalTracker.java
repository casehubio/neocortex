package io.casehub.neocortex.rag;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface RetrievalTracker {

    String record(RetrievalQuery query, CorpusRef corpus,
                  List<RetrievedChunk> results, int maxResults);

    void feedback(String retrievalId, String sourceDocumentId,
                  RetrievalOutcome outcome, FeedbackContext context);

    default void feedback(String retrievalId, String sourceDocumentId,
                          RetrievalOutcome outcome) {
        feedback(retrievalId, sourceDocumentId, outcome, null);
    }

    List<RetrievalRecord> findRecords(CorpusRef corpus, Instant since, Instant until);

    List<RetrievalFeedback> findFeedback(CorpusRef corpus, Instant since, Instant until);

    default List<RetrievalFeedback> findFeedback(CorpusRef corpus, Instant since,
                                                  Instant until, FeedbackFilter filter) {
        if (filter == null || FeedbackFilter.NONE.equals(filter)) {
            return findFeedback(corpus, since, until);
        }
        return findFeedback(corpus, since, until).stream()
            .filter(f -> FeedbackFilter.matches(f, filter))
            .toList();
    }

    Set<String> findRetrievedDocumentIds(CorpusRef corpus, Instant since, Instant until);

    int purgeOlderThan(Instant cutoff);
}
