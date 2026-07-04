package io.casehub.neocortex.examples.cbr;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;

import java.util.Map;

/**
 * Quarkus test resource that starts Qdrant via Testcontainers and exposes connection config.
 */
public class QdrantTestResource implements QuarkusTestResourceLifecycleManager {

    private GenericContainer<?> qdrant;

    @Override
    public Map<String, String> start() {
        qdrant = new GenericContainer<>("qdrant/qdrant:v1.18.0")
            .withExposedPorts(6334);
        qdrant.start();

        return Map.of(
            "casehub.memory.cbr.qdrant.host", qdrant.getHost(),
            "casehub.memory.cbr.qdrant.port", String.valueOf(qdrant.getMappedPort(6334)),
            "casehub.memory.cbr.qdrant.use-tls", "false"
        );
    }

    @Override
    public void stop() {
        if (qdrant != null) {
            qdrant.stop();
        }
    }
}
