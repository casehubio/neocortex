package io.casehub.neocortex.rag.tracking;

import io.casehub.neocortex.rag.CaseRetriever;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.PayloadFilter;
import io.casehub.neocortex.rag.RetrievalRecorded;
import io.casehub.neocortex.rag.RetrievalQuery;
import io.casehub.neocortex.rag.RetrievalTracker;
import io.casehub.neocortex.rag.RetrievedChunk;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.function.Consumer;

/**
 * Records every retrieval of the wrapped {@link CaseRetriever} into a {@link RetrievalTracker},
 * publishes a {@link RetrievalRecorded} and stamps the returned chunks with the retrieval ID
 * ({@code _trackingId}). A retrieval already stamped upstream is not recorded again; a tracking
 * failure is logged and the delegate's chunks are returned unchanged.
 */
public class TrackingCaseRetriever implements CaseRetriever {

    private static final Logger LOG = Logger.getLogger(TrackingCaseRetriever.class);

    private final CaseRetriever delegate;
    private final RetrievalTracker tracker;
    private final Consumer<RetrievalRecorded> eventSink;

    public TrackingCaseRetriever(CaseRetriever delegate, RetrievalTracker tracker,
                                 Consumer<RetrievalRecorded> eventSink) {
        this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
        this.tracker = java.util.Objects.requireNonNull(tracker, "tracker");
        this.eventSink = java.util.Objects.requireNonNull(eventSink, "eventSink");
    }

    /** The retriever this one records for. */
    public CaseRetriever delegate() {
        return delegate;
    }

    @Override
    public List<RetrievedChunk> retrieve(RetrievalQuery query, CorpusRef corpus,
                                          int maxResults, PayloadFilter filter) {
        List<RetrievedChunk> chunks = delegate.retrieve(query, corpus, maxResults, filter);
        if (TrackingLogic.isAlreadyTracked(chunks)) {
            return chunks;
        }
        try {
            String retrievalId = tracker.record(query, corpus, chunks, maxResults);
            var docRefs = TrackingLogic.toDocumentRefs(chunks);
            eventSink.accept(new RetrievalRecorded(retrievalId, query, corpus, docRefs));
            return TrackingLogic.stamp(chunks, retrievalId);
        } catch (Exception e) {
            LOG.warn("Retrieval tracking failed — returning results unchanged", e);
            return chunks;
        }
    }
}
