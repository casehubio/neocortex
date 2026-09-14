package io.casehub.neocortex.cognitive.observability.testing;

import io.casehub.neocortex.cognitive.observability.SnapshotStore;

class InMemorySnapshotStoreTest extends SnapshotStoreContractTest {

    @Override
    protected SnapshotStore createStore() {
        return new InMemorySnapshotStore();
    }
}
