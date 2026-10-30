package io.casehub.neocortex.annotation.sqlite;

import io.casehub.neocortex.annotation.EntityAnnotationIndex;
import io.casehub.neocortex.annotation.testing.EntityAnnotationIndexContractTest;
import io.casehub.neocortex.sqlite.SqliteDataSourceFactory;

class SqliteEntityAnnotationIndexTest extends EntityAnnotationIndexContractTest {

    @Override
    protected EntityAnnotationIndex createIndex() {
        var ds = SqliteDataSourceFactory.create(":memory:", 1, 3000);
        return new SqliteEntityAnnotationIndex(ds);
    }
}
