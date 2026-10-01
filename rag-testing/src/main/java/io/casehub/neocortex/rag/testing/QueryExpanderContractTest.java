package io.casehub.neocortex.rag.testing;

import io.casehub.neocortex.rag.QueryExpander;
import io.casehub.neocortex.rag.RetrievalQuery;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class QueryExpanderContractTest {

    protected abstract QueryExpander expander();

    @Test
    void expand_returnsNonNullList() {
        var result = expander().expand(RetrievalQuery.of("test query"));
        assertThat(result).isNotNull();
    }

    @Test
    void expand_returnsNonEmptyList() {
        var result = expander().expand(RetrievalQuery.of("test query"));
        assertThat(result).isNotEmpty();
    }

    @Test
    void expand_allResultsHaveNonBlankText() {
        var result = expander().expand(RetrievalQuery.of("test query"));
        for (RetrievalQuery q : result) {
            assertThat(q.text()).isNotBlank();
        }
    }

    @Test
    void expand_preservesOriginalQueryText() {
        var result = expander().expand(RetrievalQuery.of("original text"));
        assertThat(result).anyMatch(q -> q.text().equals("original text")
                || (q.searchText() != null && q.searchText().contains("original text")));
    }
}
