package io.casehub.neocortex.corpus.testing;

import io.casehub.neocortex.corpus.ChangeSource;
import io.casehub.neocortex.corpus.CorpusStore;
import io.casehub.neocortex.corpus.zip.FlatChangeSource;
import io.casehub.neocortex.corpus.zip.FlatCorpusStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class FlatChangeSourceContractTest extends ChangeSourceContractTest {

    @TempDir
    Path tempDir;
    private FlatCorpusStore store;
    private FlatChangeSource changeSource;

    @BeforeEach
    void setUp() {
        store = new FlatCorpusStore(tempDir);
        changeSource = new FlatChangeSource(store, tempDir);
    }

    @Override
    protected ChangeSource changeSource() {
        return changeSource;
    }

    @Override
    protected CorpusStore store() {
        return store;
    }
}
