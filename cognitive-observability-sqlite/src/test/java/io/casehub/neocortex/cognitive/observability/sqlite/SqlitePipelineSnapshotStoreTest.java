package io.casehub.neocortex.cognitive.observability.sqlite;

import io.casehub.neocortex.cognitive.observability.PipelineSnapshotStore;
import io.casehub.neocortex.cognitive.observability.testing.PipelineSnapshotStoreContractTest;
import org.junit.jupiter.api.AfterEach;

class SqlitePipelineSnapshotStoreTest extends PipelineSnapshotStoreContractTest {

    private SqlitePipelineSnapshotStore sqliteStore;

    @Override
    protected PipelineSnapshotStore createStore() {
        sqliteStore = new SqlitePipelineSnapshotStore(":memory:");
        return sqliteStore;
    }

    @AfterEach
    void cleanup() {
        if (sqliteStore != null) sqliteStore.close();
    }
}
