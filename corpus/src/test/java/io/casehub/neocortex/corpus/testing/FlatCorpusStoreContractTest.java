package io.casehub.neocortex.corpus.testing;

import io.casehub.neocortex.corpus.CorpusReader;
import io.casehub.neocortex.corpus.CorpusStore;
import io.casehub.neocortex.corpus.zip.FlatCorpusStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class FlatCorpusStoreContractTest extends CorpusStoreContractTest {

    @TempDir
    Path tempDir;
    private FlatCorpusStore store;

    @BeforeEach
    void setUp() {
        store = new FlatCorpusStore(tempDir);
    }

    @Override
    protected CorpusStore store() {
        return store;
    }

    @Override
    protected CorpusReader reader() {
        return store;
    }
}
