package io.casehub.neocortex.corpus.testing;

import io.casehub.neocortex.corpus.CorpusReader;
import io.casehub.neocortex.corpus.CorpusStore;
import io.casehub.neocortex.corpus.zip.CorpusConfig;
import io.casehub.neocortex.corpus.zip.ZipCorpusStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class ZipCorpusStoreContractTest extends CorpusStoreContractTest {

    @TempDir
    Path tempDir;
    private ZipCorpusStore store;

    @BeforeEach
    void setUp() {
        store = new ZipCorpusStore(new CorpusConfig("test-corpus", tempDir));
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
