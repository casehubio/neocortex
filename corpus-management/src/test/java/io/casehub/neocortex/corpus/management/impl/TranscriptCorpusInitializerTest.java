package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.CorpusCatalogue;
import io.casehub.neocortex.corpus.management.CorpusDescriptor;
import io.casehub.neocortex.corpus.management.CorpusLifecycle;
import io.casehub.neocortex.corpus.management.CorpusManager;
import io.casehub.neocortex.corpus.management.CorpusType;
import io.casehub.neocortex.corpus.management.FederationRef;
import io.casehub.neocortex.corpus.management.HealthReport;
import io.casehub.neocortex.rag.CorpusRef;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TranscriptCorpusInitializerTest {

    @Test
    void createsCorpusWhenNotExists() {
        var catalogue = new StubCatalogue();
        var manager = new RecordingManager();
        var initializer = new TranscriptCorpusInitializer(catalogue, manager);

        CorpusRef ref = initializer.ensureCorpusExists("agent-01", "tenant-1");

        assertThat(ref.corpusName()).isEqualTo("agent-01-interactions");
        assertThat(ref.tenantId()).isEqualTo("tenant-1");
        assertThat(manager.created).hasSize(1);
        assertThat(manager.created.get(0).name).isEqualTo("agent-01-interactions");
        assertThat(manager.created.get(0).type).isEqualTo(BuiltInCorpusTypes.TRANSCRIPT);
        assertThat(manager.created.get(0).metadata).containsEntry("agent_id", "agent-01");
    }

    @Test
    void skipsCreationWhenAlreadyExists() {
        var descriptor = new CorpusDescriptor(
            "id-1", "agent-01-interactions", BuiltInCorpusTypes.TRANSCRIPT,
            CorpusLifecycle.ACTIVE, new CorpusRef("tenant-1", "agent-01-interactions"),
            "qdrant-coll", List.of(), List.of(), Map.of(), Instant.now(), Instant.now()
        );
        var catalogue = new StubCatalogue(descriptor);
        var manager = new RecordingManager();
        var initializer = new TranscriptCorpusInitializer(catalogue, manager);

        CorpusRef ref = initializer.ensureCorpusExists("agent-01", "tenant-1");

        assertThat(ref.corpusName()).isEqualTo("agent-01-interactions");
        assertThat(manager.created).isEmpty();
    }

    @Test
    void corpusNameConvention() {
        assertThat(TranscriptCorpusInitializer.corpusNameFor("agent-42"))
            .isEqualTo("agent-42-interactions");
    }

    static class StubCatalogue implements CorpusCatalogue {
        private final CorpusDescriptor existing;

        StubCatalogue() { this.existing = null; }
        StubCatalogue(CorpusDescriptor existing) { this.existing = existing; }

        @Override
        public Optional<CorpusDescriptor> get(CorpusRef corpus) {
            if (existing != null && existing.corpusRef().equals(corpus)) {
                return Optional.of(existing);
            }
            return Optional.empty();
        }

        @Override public List<CorpusDescriptor> list(String tenantId, CorpusLifecycle status) { return List.of(); }
        @Override public List<CorpusDescriptor> forType(CorpusType type, String tenantId) { return List.of(); }
        @Override public List<CorpusDescriptor> searchScope(CorpusRef corpus) { return List.of(); }
        @Override public HealthReport reconcile(String tenantId) { return HealthReport.HEALTHY; }
    }

    static class RecordingManager implements CorpusManager {
        final List<CreateCall> created = new ArrayList<>();

        @Override
        public CorpusDescriptor create(String name, CorpusType type, String tenantId,
                                        List<FederationRef> upstream, List<FederationRef> peers,
                                        Map<String, String> metadata) {
            created.add(new CreateCall(name, type, tenantId, metadata));
            return new CorpusDescriptor("gen-id", name, type, CorpusLifecycle.ACTIVE,
                new CorpusRef(tenantId, name), "qdrant-" + name,
                upstream, peers, metadata, Instant.now(), Instant.now());
        }

        @Override public void archive(CorpusRef corpus) {}
        @Override public void revive(CorpusRef corpus) {}
        @Override public void delete(CorpusRef corpus) {}

        record CreateCall(String name, CorpusType type, String tenantId, Map<String, String> metadata) {}
    }
}
