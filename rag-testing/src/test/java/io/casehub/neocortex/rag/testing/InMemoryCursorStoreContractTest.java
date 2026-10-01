package io.casehub.neocortex.rag.testing;

import io.casehub.neocortex.rag.CursorStore;

class InMemoryCursorStoreContractTest extends CursorStoreContractTest {

    @Override
    protected CursorStore createStore() {
        return new InMemoryCursorStore();
    }
}
