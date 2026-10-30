package io.casehub.neocortex.annotation.inmem;

import io.casehub.neocortex.annotation.EntityAnnotationIndex;
import io.casehub.neocortex.annotation.testing.EntityAnnotationIndexContractTest;

class InMemoryEntityAnnotationIndexTest extends EntityAnnotationIndexContractTest {

    private final InMemoryEntityAnnotationIndex index = new InMemoryEntityAnnotationIndex();

    @Override
    protected EntityAnnotationIndex createIndex() {
        index.clearAll();
        return index;
    }
}
