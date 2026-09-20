package io.casehub.neocortex.memory.cbr.inmem;

import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.testing.CbrRecordStoreContractTest;

class InMemoryCbrRecordStoreTest extends CbrRecordStoreContractTest {

    private final InMemoryCbrRecordStore store = new InMemoryCbrRecordStore();

    @Override
    protected CbrRecordStore store() {
        return store;
    }

    @Override
    protected void clearStore() {
        store.clearCases();
    }
}
