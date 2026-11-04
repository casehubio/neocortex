package io.casehub.neocortex.examples.corpus.domains.garden;

import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.CorpusLifecycle;
import io.casehub.neocortex.corpus.management.CorpusQuery;
import io.casehub.neocortex.examples.corpus.CorpusTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("smoke")
class GardenScenarioTest {

    private static final String TENANT = "garden-tenant";
    private CorpusTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = CorpusTestHarness.create();
    }

    @Test
    void fullLifecycle() {
        var desc = harness.catalogue().register(
            "java-garden", GardenCorpusTypes.GARDEN, TENANT,
            "vec-java-garden", List.of(), List.of(), Map.of());
        var corpus = desc.corpusRef();
        assertThat(desc.type().name()).isEqualTo("knowledge-garden-example");
        assertThat(desc.lifecycle()).isEqualTo(CorpusLifecycle.ACTIVE);

        harness.addEntry(corpus, new CorpusEntry("ge-001",
            Map.of("title", "Quarkus CDI quirks", "type", "gotcha", "domain", "jvm",
                   "tags", List.of("quarkus", "cdi", "injection")),
            "ArC does not support private injection points in Quarkus.", null));
        harness.addEntry(corpus, new CorpusEntry("ge-002",
            Map.of("title", "Flyway migration ordering", "type", "technique", "domain", "database",
                   "tags", List.of("flyway", "sqlite", "migration")),
            "Use V-prefixed timestamps for Flyway migration scripts to avoid ordering conflicts.", null));
        harness.addEntry(corpus, new CorpusEntry("ge-003",
            Map.of("title", "ONNX session thread safety", "type", "gotcha", "domain", "inference",
                   "tags", List.of("onnx", "threading")),
            "OrtSession.run() is thread-safe but session creation is not.", null));
        harness.addEntry(corpus, new CorpusEntry("ge-004",
            Map.of("title", "AssertJ soft assertions", "type", "technique", "domain", "testing",
                   "tags", List.of("assertj", "testing")),
            "Use SoftAssertions to collect multiple failures in one test run.", null));
        harness.addEntry(corpus, new CorpusEntry("ge-005",
            Map.of("title", "SQLite WAL mode", "type", "decision", "domain", "database",
                   "tags", List.of("sqlite", "wal", "performance")),
            "WAL mode allows concurrent reads during writes — essential for HikariCP.", null));

        var results = harness.search().search(corpus, CorpusQuery.of("sqlite"));
        assertThat(results).isNotEmpty();
        assertThat(results).extracting(r -> r.entry().id())
            .contains("ge-005");

        assertThat(harness.catalogue().list(TENANT, CorpusLifecycle.ACTIVE)).hasSize(1);
        assertThat(harness.catalogue().get(corpus)).isPresent();
        assertThat(harness.catalogue().forType(GardenCorpusTypes.GARDEN, TENANT)).hasSize(1);

        harness.catalogue().updateLifecycle(corpus, CorpusLifecycle.ARCHIVED);
        assertThat(harness.catalogue().list(TENANT, CorpusLifecycle.ACTIVE)).isEmpty();
        assertThat(harness.catalogue().list(TENANT, CorpusLifecycle.ARCHIVED)).hasSize(1);

        harness.catalogue().updateLifecycle(corpus, CorpusLifecycle.ACTIVE);
        assertThat(harness.catalogue().list(TENANT, CorpusLifecycle.ACTIVE)).hasSize(1);
    }

    @Test
    void entryStoreRoundTrip() {
        var desc = harness.catalogue().register(
            "roundtrip-garden", GardenCorpusTypes.GARDEN, TENANT,
            "vec-roundtrip-garden", List.of(), List.of(), Map.of());
        var corpus = desc.corpusRef();

        var entry = new CorpusEntry("ge-rt",
            Map.of("title", "Round trip test", "type", "technique", "domain", "testing",
                   "score", 0.85, "tags", List.of("test", "roundtrip")),
            "Testing metadata round-trip through the entry store.", null);
        harness.addEntry(corpus, entry);

        var retrieved = harness.entryStore().get(corpus, "ge-rt");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().stringField("title")).isEqualTo("Round trip test");
        assertThat(retrieved.get().doubleField("score")).isEqualTo(0.85);
        assertThat(retrieved.get().stringListField("tags")).containsExactly("test", "roundtrip");
    }
}
