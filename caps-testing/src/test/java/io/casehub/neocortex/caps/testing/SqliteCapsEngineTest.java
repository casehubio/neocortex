package io.casehub.neocortex.caps.testing;

import io.casehub.neocortex.caps.CapsEngine;
import io.casehub.neocortex.caps.engine.SqliteCapsEngine;
import org.junit.jupiter.api.AfterEach;

class SqliteCapsEngineTest extends CapsEngineContractTest {

    private SqliteCapsEngine engine;

    @Override
    protected CapsEngine createEngine() {
        engine = new SqliteCapsEngine(":memory:");
        return engine;
    }

    @AfterEach
    void tearDown() {
        if (engine != null) engine.close();
    }
}
