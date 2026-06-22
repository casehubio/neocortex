package io.casehub.rag.hyde;

import io.casehub.rag.CaseRetriever;
import io.casehub.rag.CorpusRef;
import io.casehub.rag.PayloadFilter;
import io.casehub.rag.QueryExpander;
import io.casehub.rag.RetrievalQuery;
import io.casehub.rag.RetrievedChunk;
import io.quarkus.arc.properties.IfBuildProperty;
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
public class HydeCaseRetriever implements CaseRetriever {

    private static final Logger LOG = Logger.getLogger(HydeCaseRetriever.class.getName());

    private final CaseRetriever delegate;
    private final QueryExpander expander;

    @Inject
    public HydeCaseRetriever(@Delegate @Any CaseRetriever delegate,
                             QueryExpander expander) {
        this.delegate = delegate;
        this.expander = expander;
    }

    @Override
    public List<RetrievedChunk> retrieve(RetrievalQuery query, CorpusRef corpus,
                                          int maxResults, PayloadFilter filter) {
        RetrievalQuery expanded;
        try {
            expanded = expander.expand(query);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Query expansion failed, using original query", e);
            expanded = query;
        }
        return delegate.retrieve(expanded, corpus, maxResults, filter);
    }
}
