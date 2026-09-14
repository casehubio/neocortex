package io.casehub.neocortex.cognitive.observability.sqlite;

import io.casehub.neocortex.cognitive.observability.SnapshotStore;
import io.casehub.neocortex.cognitive.observability.testing.SnapshotStoreContractTest;
import org.junit.jupiter.api.AfterEach;

class SqliteSnapshotStoreTest extends SnapshotStoreContractTest {

    private SqliteSnapshotStore sqliteStore;

    @Override
    protected SnapshotStore createStore() {
        sqliteStore = new SqliteSnapshotStore(":memory:");
        return sqliteStore;
    }

    @AfterEach
    void cleanup() {
        if (sqliteStore != null) sqliteStore.close();
    }
}
