package io.casehub.neocortex.rag.expansion;

import io.casehub.neocortex.rag.CaseRetriever;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.RetrievalQuery;
import io.casehub.neocortex.rag.testing.InMemoryQueryExpander;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class QueryExpandingDecoratorIntegrationTest {

    private static final CorpusRef CORPUS = new CorpusRef("tenant", "test-corpus");

    @Inject
    CaseRetriever retriever;

    @Inject
    InMemoryQueryExpander queryExpander;

    @BeforeEach
    void clearExpander() {
        queryExpander.clear();
    }

    @Test
    void decoratorInterceptsRetrieveAndCallsExpander() {
        retriever.retrieve(RetrievalQuery.of("find similar cases"), CORPUS, 10, null);

        assertThat(queryExpander.expandedQueries())
            .as("Decorator must call QueryExpander.expand()")
            .hasSize(1);

        var expanded = queryExpander.expandedQueries().get(0);
        assertThat(expanded.text()).isEqualTo("find similar cases");
        assertThat(expanded.expandedText()).isEqualTo("hypothetical: find similar cases");
    }

    @Test
    void threeArgOverloadAlsoIntercepted() {
        retriever.retrieve(RetrievalQuery.of("default method call"), CORPUS, 5);

        assertThat(queryExpander.expandedQueries())
            .as("Default 3-arg overload must also route through the decorator")
            .hasSize(1);
        assertThat(queryExpander.expandedQueries().get(0).expandedText())
            .startsWith("hypothetical:");
    }
}
