package io.casehub.neocortex.rag.runtime;

import io.casehub.neocortex.rag.CursorStore;
import io.casehub.neocortex.rag.testing.CursorStoreContractTest;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class FileCursorStoreContractTest extends CursorStoreContractTest {

    @TempDir
    Path tempDir;

    @Override
    protected CursorStore createStore() {
        return new FileCursorStore(tempDir.toString());
    }
}
