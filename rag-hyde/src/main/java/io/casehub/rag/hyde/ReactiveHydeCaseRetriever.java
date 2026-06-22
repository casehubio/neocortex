package io.casehub.rag.hyde;

import io.casehub.rag.CorpusRef;
import io.casehub.rag.PayloadFilter;
import io.casehub.rag.QueryExpander;
import io.casehub.rag.ReactiveCaseRetriever;
import io.casehub.rag.RetrievalQuery;
import io.casehub.rag.RetrievedChunk;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Decorator
@Priority(200)
@IfBuildProperty(name = "casehub.rag.hyde.enabled", stringValue = "true")
public class ReactiveHydeCaseRetriever implements ReactiveCaseRetriever {

    private static final Logger LOG = Logger.getLogger(ReactiveHydeCaseRetriever.class.getName());

    private final ReactiveCaseRetriever delegate;
    private final QueryExpander expander;

    @Inject
    public ReactiveHydeCaseRetriever(@Delegate @Any ReactiveCaseRetriever delegate,
                                     QueryExpander expander) {
        this.delegate = delegate;
        this.expander = expander;
    }

    @Override
    public Uni<List<RetrievedChunk>> retrieve(RetrievalQuery query, CorpusRef corpus,
                                               int maxResults, PayloadFilter filter) {
        return Uni.createFrom().item(() -> expander.expand(query))
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
            .onFailure().recoverWithItem(t -> {
                LOG.log(Level.WARNING, "Query expansion failed, using original query", t);
                return query;
            })
            .chain(expanded -> delegate.retrieve(expanded, corpus, maxResults, filter));
    }
}
