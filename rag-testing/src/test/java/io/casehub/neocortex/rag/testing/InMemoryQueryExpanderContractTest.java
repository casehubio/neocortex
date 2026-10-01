package io.casehub.neocortex.rag.testing;

import io.casehub.neocortex.rag.QueryExpander;

class InMemoryQueryExpanderContractTest extends QueryExpanderContractTest {
    @Override
    protected QueryExpander expander() {
        return new InMemoryQueryExpander();
    }
}
