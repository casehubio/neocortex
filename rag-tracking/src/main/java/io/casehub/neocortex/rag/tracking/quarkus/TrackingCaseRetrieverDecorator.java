package io.casehub.neocortex.rag.tracking.quarkus;

import io.casehub.neocortex.rag.CaseRetriever;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.PayloadFilter;
import io.casehub.neocortex.rag.RetrievalQuery;
import io.casehub.neocortex.rag.RetrievalRecorded;
import io.casehub.neocortex.rag.RetrievalTracker;
import io.casehub.neocortex.rag.RetrievedChunk;
import io.casehub.neocortex.rag.tracking.TrackingCaseRetriever;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import java.util.List;
import java.util.function.Consumer;

/** CDI decorator applying {@link TrackingCaseRetriever} to every {@link CaseRetriever}. */
@Decorator
@Priority(40)
@IfBuildProperty(name = "casehub.rag.tracking.enabled", stringValue = "true")
public class TrackingCaseRetrieverDecorator implements CaseRetriever {

    private final TrackingCaseRetriever tracking;

    @Inject
    TrackingCaseRetrieverDecorator(@Delegate @Any CaseRetriever delegate, RetrievalTracker tracker,
                                   Event<RetrievalRecorded> recordedEvent) {
        this(delegate, tracker, recordedEvent::fire);
    }

    TrackingCaseRetrieverDecorator(CaseRetriever delegate, RetrievalTracker tracker,
                                   Consumer<RetrievalRecorded> eventSink) {
        this.tracking = new TrackingCaseRetriever(delegate, tracker, eventSink);
    }

    @Override
    public List<RetrievedChunk> retrieve(RetrievalQuery query, CorpusRef corpus,
                                          int maxResults, PayloadFilter filter) {
        return tracking.retrieve(query, corpus, maxResults, filter);
    }
}
