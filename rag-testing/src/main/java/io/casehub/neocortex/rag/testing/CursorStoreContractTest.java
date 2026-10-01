package io.casehub.neocortex.rag.testing;

import io.casehub.neocortex.rag.CursorStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class CursorStoreContractTest {

    protected abstract CursorStore createStore();

    @Test
    void loadReturnsEmptyForUnknownCorpus() {
        assertThat(createStore().load("unknown")).isEmpty();
    }

    @Test
    void saveThenLoadReturnsSavedCursor() {
        CursorStore store = createStore();
        store.save("garden", "cursor-1");
        assertThat(store.load("garden")).contains("cursor-1");
    }

    @Test
    void saveOverwritesPreviousCursor() {
        CursorStore store = createStore();
        store.save("garden", "cursor-1");
        store.save("garden", "cursor-2");
        assertThat(store.load("garden")).contains("cursor-2");
    }

    @Test
    void deleteRemovesCursor() {
        CursorStore store = createStore();
        store.save("garden", "cursor-1");
        store.delete("garden");
        assertThat(store.load("garden")).isEmpty();
    }

    @Test
    void deleteNonExistentDoesNotThrow() {
        createStore().delete("nonexistent");
    }

    @Test
    void multipleCorpusIsolation() {
        CursorStore store = createStore();
        store.save("garden", "cg");
        store.save("legal", "cl");
        assertThat(store.load("garden")).contains("cg");
        assertThat(store.load("legal")).contains("cl");
    }

    @Test
    void deleteOneDoesNotAffectOther() {
        CursorStore store = createStore();
        store.save("garden", "cg");
        store.save("legal", "cl");
        store.delete("garden");
        assertThat(store.load("garden")).isEmpty();
        assertThat(store.load("legal")).contains("cl");
    }
}
