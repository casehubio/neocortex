package io.casehub.neocortex.cognitive.observability.testing;

import io.casehub.neocortex.cognitive.observability.PipelineSnapshotStore;

class InMemoryPipelineSnapshotStoreTest extends PipelineSnapshotStoreContractTest {
    @Override
    protected PipelineSnapshotStore createStore() {
        return new InMemoryPipelineSnapshotStore();
    }
}
