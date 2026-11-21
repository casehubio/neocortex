package io.casehub.neocortex.rag.tracking.quarkus;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.rag.RetrievalTracker;
import io.casehub.neocortex.rag.tracking.SqliteRetrievalTracker;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Produces the {@link SqliteRetrievalTracker} from {@code casehub.rag.tracking.sqlite.*}. */
@ApplicationScoped
public class TrackingProducers {

    // @Singleton, not @ApplicationScoped: the core class has no no-arg constructor for a client proxy.
    @Produces
    @Singleton
    @Typed({SqliteRetrievalTracker.class, RetrievalTracker.class})
    SqliteRetrievalTracker sqliteRetrievalTracker(
            @ConfigProperty(name = "casehub.rag.tracking.sqlite.path") String path,
            @ConfigProperty(name = "casehub.rag.tracking.sqlite.pool.max-size", defaultValue = "5") int maxPoolSize,
            @ConfigProperty(name = "casehub.rag.tracking.sqlite.busy-timeout-ms", defaultValue = "5000") int busyTimeoutMs,
            ObjectMapper objectMapper) {
        return new SqliteRetrievalTracker(path, maxPoolSize, busyTimeoutMs, objectMapper);
    }

    void close(@Disposes SqliteRetrievalTracker tracker) {
        tracker.close();
    }
}
