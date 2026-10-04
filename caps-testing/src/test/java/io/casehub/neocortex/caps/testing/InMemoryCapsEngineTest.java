package io.casehub.neocortex.caps.testing;

import io.casehub.neocortex.caps.CapsEngine;

class InMemoryCapsEngineTest extends CapsEngineContractTest {
    @Override
    protected CapsEngine createEngine() {
        return new InMemoryCapsEngine();
    }
}
