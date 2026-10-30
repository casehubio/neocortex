package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.annotation.AnnotationMethod;
import io.casehub.neocortex.annotation.EntityAnnotation;
import io.casehub.neocortex.annotation.inmem.InMemoryEntityAnnotationIndex;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.PrincipalId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BulkReExtractionServiceTest {

    private static final CurrentPrincipal CURRENT_PRINCIPAL = new CurrentPrincipal() {
        @Override public String actorId() { return "test-agent"; }
        @Override public Set<String> groups() { return Set.of(); }
        @Override public String tenancyId() { return "t1"; }
        @Override public boolean isCrossTenantAdmin() { return true; }
    };
    private static final PrincipalId AGENT = PrincipalId.agent("test-agent");
    private static final String TENANT = "t1";
    private static final MemoryDomain EXPERIENCE = new MemoryDomain("experience");

    private InMemoryMindMapStore mindMapStore;
    private InMemoryEntityAnnotationIndex annotationIndex;
    private InMemoryMemoryStore memoryStore;

    @BeforeEach
    void setUp() {
        mindMapStore = new InMemoryMindMapStore();
        annotationIndex = new InMemoryEntityAnnotationIndex();
        memoryStore = new InMemoryMemoryStore(CURRENT_PRINCIPAL);
        mindMapStore.createSubgraph(
                new SubgraphInput("General", SubgraphTypes.GENERAL, null), TENANT);
    }

    @Test
    void reExtractsMemoriesAndCreatesAnnotations() {
        String memoryId = memoryStore.store(
                MemoryInput.of(new Subject("test", "entity-1"), EXPERIENCE, TENANT,
                        "Alice met Bob at the park."));

        annotationIndex.addAnnotation(new EntityAnnotation(
                "old-1", TENANT, "memory", memoryId, "old-node", 0, 5, "Alice",
                null, AnnotationMethod.LLM_EXTRACTION, null, Instant.now()));

        var extractor = stubExtractor(
                new ParsedExtraction(
                        List.of(new ParsedEntity("Alice", "person", Map.of(), ConfidenceOrigin.STATED)),
                        List.of(), List.of(),
                        List.of(new ParsedMention("Alice", "Alice", 0))),
                new ExtractionResult(
                        List.of(new ExtractedEntity("node-alice", "Alice", true, "person", Map.of())),
                        List.of(), List.of(), List.of("Alice")));

        var service = new BulkReExtractionService(extractor, annotationIndex, memoryStore);
        ReExtractionResult result = service.reExtract("memory", TENANT, AGENT);

        assertThat(result.sourcesProcessed()).isEqualTo(1);
        assertThat(result.annotationsCreated()).isEqualTo(1);
        assertThat(result.errors()).isEmpty();

        List<EntityAnnotation> annotations = annotationIndex.findBySource("memory", memoryId, TENANT);
        assertThat(annotations).hasSize(1);
        assertThat(annotations.getFirst().entityNodeId()).isEqualTo("node-alice");
        assertThat(annotations.getFirst().mentionText()).isEqualTo("Alice");

        assertThat(annotationIndex.findByEntity("old-node", TENANT)).isEmpty();
    }

    @Test
    void noAnnotationsWhenParseReturnsNull() {
        memoryStore.store(
                MemoryInput.of(new Subject("test", "entity-1"), EXPERIENCE, TENANT,
                        "Nothing interesting here."));

        var extractor = stubExtractor(null, ExtractionResult.EMPTY);
        var service = new BulkReExtractionService(extractor, annotationIndex, memoryStore);
        ReExtractionResult result = service.reExtract("memory", TENANT, AGENT);

        assertThat(result.sourcesProcessed()).isEqualTo(1);
        assertThat(result.annotationsCreated()).isZero();
    }

    @Test
    void returnsErrorForUnsupportedSourceType() {
        var extractor = stubExtractor(null, ExtractionResult.EMPTY);
        var service = new BulkReExtractionService(extractor, annotationIndex, memoryStore);
        ReExtractionResult result = service.reExtract("unknown", TENANT, AGENT);

        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().getFirst()).contains("Unsupported source type");
    }

    @Test
    void gracefulWhenAnnotationIndexUnavailable() {
        var extractor = stubExtractor(null, ExtractionResult.EMPTY);
        var service = new BulkReExtractionService(extractor, null, memoryStore);
        ReExtractionResult result = service.reExtract("memory", TENANT, AGENT);

        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().getFirst()).contains("EntityAnnotationIndex not available");
    }

    private MindMapExtractor stubExtractor(ParsedExtraction parsed, ExtractionResult result) {
        return new MindMapExtractor(mindMapStore, null) {
            @Override
            public ParsedExtraction parse(String text, String tenantId,
                                          List<String> recentEntityNames) {
                return parsed;
            }

            @Override
            public ExtractionResult apply(ParsedExtraction p, String tenantId,
                                          PrincipalId principalId) {
                return result;
            }
        };
    }
}
