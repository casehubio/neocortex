package io.casehub.memory.cbr.inmem;

import io.casehub.memory.cbr.CbrCaseMemoryStore;
import io.casehub.memory.cbr.testing.CbrCaseMemoryStoreContractTest;

class InMemoryCbrCaseMemoryStoreTest extends CbrCaseMemoryStoreContractTest {

    private final InMemoryCbrCaseMemoryStore store = new InMemoryCbrCaseMemoryStore();

    @Override
    protected CbrCaseMemoryStore store() {
        return store;
    }
}
